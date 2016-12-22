/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.job.cloud.scheduler.restful;

import com.dangdang.ddframe.job.cloud.scheduler.config.CloudJobConfiguration;
import com.dangdang.ddframe.job.cloud.scheduler.config.CloudJobConfigurationGsonFactory;
import com.dangdang.ddframe.job.cloud.scheduler.config.ConfigurationService;
import com.dangdang.ddframe.job.cloud.scheduler.config.JobExecutionType;
import com.dangdang.ddframe.job.cloud.scheduler.lifecycle.LifecycleService;
import com.dangdang.ddframe.job.cloud.scheduler.producer.ProducerManager;
import com.dangdang.ddframe.job.cloud.scheduler.producer.ProducerManagerFactory;
import com.dangdang.ddframe.job.cloud.scheduler.state.failover.FailoverService;
import com.dangdang.ddframe.job.cloud.scheduler.state.failover.FailoverTaskInfo;
import com.dangdang.ddframe.job.cloud.scheduler.state.ready.ReadyService;
import com.dangdang.ddframe.job.cloud.scheduler.state.running.RunningService;
import com.dangdang.ddframe.job.cloud.scheduler.statistics.StatisticManager;
import com.dangdang.ddframe.job.context.TaskContext;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbSearch;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbSearch.Condition;
import com.dangdang.ddframe.job.event.rdb.JobEventRdbSearch.Result;
import com.dangdang.ddframe.job.event.type.JobExecutionEvent;
import com.dangdang.ddframe.job.event.type.JobStatusTraceEvent;
import com.dangdang.ddframe.job.exception.JobConfigurationException;
import com.dangdang.ddframe.job.exception.JobSystemException;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import com.dangdang.ddframe.job.statistics.type.JobExecutionTypeStatistics;
import com.dangdang.ddframe.job.statistics.type.JobRegisterStatistics;
import com.dangdang.ddframe.job.statistics.type.JobTypeStatistics;
import com.dangdang.ddframe.job.statistics.type.TaskRunningResultStatistics;
import com.dangdang.ddframe.job.statistics.type.TaskRunningStatistics;
import com.dangdang.ddframe.job.util.json.GsonFactory;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import org.apache.mesos.SchedulerDriver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.sql.DataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

/**
 * 作业云的REST API.
 *
 * @author zhangliang
 * @author liguangyun
 */
@Path("/job")
public final class CloudJobRestfulApi {
    
    private static SchedulerDriver schedulerDriver;
    
    private static CoordinatorRegistryCenter regCenter;
    
    private static Optional<? extends DataSource> dataSource;
    
    private static final SimpleDateFormat DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    private final ProducerManager producerManager;
    
    private final LifecycleService lifecycleService;
    
    private final ConfigurationService configService;
    
    private final ReadyService readyService;
    
    private final RunningService runningService;
    
    private final FailoverService failoverService;
    
    private final StatisticManager statisticManager;
    
    public CloudJobRestfulApi() {
        producerManager = ProducerManagerFactory.getInstance(schedulerDriver, regCenter);
        lifecycleService = new LifecycleService(schedulerDriver);
        configService = new ConfigurationService(regCenter);
        readyService = new ReadyService(regCenter);
        runningService = new RunningService();
        failoverService = new FailoverService(regCenter);
        statisticManager = StatisticManager.getInstance(regCenter, dataSource);
    }
    
    /**
     * 初始化.
     * 
     * @param schedulerDriver Mesos控制器
     * @param regCenter 注册中心
     */
    public static void init(final SchedulerDriver schedulerDriver, final CoordinatorRegistryCenter regCenter, final Optional<? extends DataSource> dataSource) {
        CloudJobRestfulApi.schedulerDriver = schedulerDriver;
        CloudJobRestfulApi.regCenter = regCenter;
        CloudJobRestfulApi.dataSource = dataSource;
        GsonFactory.registerTypeAdapter(CloudJobConfiguration.class, new CloudJobConfigurationGsonFactory.CloudJobConfigurationGsonTypeAdapter());
    }
    
    /**
     * 注册作业.
     * 
     * @param jobConfig 作业配置
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public void register(final CloudJobConfiguration jobConfig) {
        producerManager.register(jobConfig);
    }
    
    /**
     * 更新作业配置.
     *
     * @param jobConfig 作业配置
     */
    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    public void update(final CloudJobConfiguration jobConfig) {
        producerManager.update(jobConfig);
    }
    
    /**
     * 注销作业.
     * 
     * @param jobName 作业名称
     */
    @DELETE
    @Path("/deregister")
    @Consumes(MediaType.APPLICATION_JSON)
    public void deregister(final String jobName) {
        producerManager.deregister(jobName);
        lifecycleService.killJob(jobName);
    }
    
    /**
     * 触发一次作业.
     *
     * @param jobName 作业名称
     */
    @POST
    @Path("/trigger")
    @Consumes(MediaType.APPLICATION_JSON)
    public void trigger(final String jobName) {
        Optional<CloudJobConfiguration> config = configService.load(jobName);
        if (config.isPresent() && JobExecutionType.DAEMON == config.get().getJobExecutionType()) {
            throw new JobSystemException("Daemon job '%s' cannot support trigger.", jobName);
        }
        readyService.addTransient(jobName);
    }
    
    /**
     * 查询作业详情.
     *
     * @param jobName 作业名称
     */
    @GET
    @Path("/detail")
    @Consumes(MediaType.APPLICATION_JSON)
    public CloudJobConfiguration detail(@QueryParam("jobName") final String jobName) {
        Optional<CloudJobConfiguration> config = configService.load(jobName);
        if (config.isPresent()) {
            return config.get();
        }
        throw new JobConfigurationException("Cannot find job '%s', please check the jobName.", jobName);
    }
    
    /**
     * 查找全部作业.
     * 
     * @return 全部作业
     */
    @GET
    @Path("/findAllJobs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Collection<CloudJobConfiguration> findAllJobs() {
        return configService.loadAll();
    }
    
    /**
     * 查找运行中的全部任务.
     * 
     * @return 运行中的全部任务
     */
    @GET
    @Path("/findAllRunningTasks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Collection<TaskContext> findAllRunningTasks() {
        List<TaskContext> result = new LinkedList<>();
        for (Set<TaskContext> each : runningService.getAllRunningTasks().values()) {
            result.addAll(each);
        }
        return result;
    }
    
    /**
     * 查找待运行的全部任务.
     * 
     * @return 待运行的全部任务
     */
    @GET
    @Path("/findAllReadyTasks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Collection<Map<String, String>> findAllReadyTasks() {
        Map<String, Integer> readyTasks = readyService.getAllReadyTasks();
        List<Map<String, String>> result = new ArrayList<>(readyTasks.size());
        for (Entry<String, Integer> each : readyTasks.entrySet()) {
            Map<String, String> oneTask = new HashMap<>(2, 1);
            oneTask.put("jobName", each.getKey());
            oneTask.put("times", String.valueOf(each.getValue()));
            result.add(oneTask);
        }
        return result;
    }
    
    /**
     * 查找待失效转移的全部任务.
     * 
     * @return 失效转移的全部任务
     */
    @GET
    @Path("/findAllFailoverTasks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Collection<FailoverTaskInfo> findAllFailoverTasks() {
        List<FailoverTaskInfo> result = new LinkedList<>();
        for (Collection<FailoverTaskInfo> each : failoverService.getAllFailoverTasks().values()) {
            result.addAll(each);
        }
        return result;
    }
    
    /**
     * 检索作业运行执行轨迹.
     * 
     * @return 作业运行执行轨迹结果
     */
    @GET
    @Path("/findJobExecutionEvents")
    @Consumes(MediaType.APPLICATION_JSON)
    public Result<JobExecutionEvent> findJobExecutionEvents(@Context final UriInfo info) {
        if (!dataSource.isPresent()) {
            return new Result<JobExecutionEvent>(0, Collections.<JobExecutionEvent>emptyList());
        }
        int pageSize = Integer.parseInt(info.getQueryParameters().getFirst("pageSize"));
        int pageNumber = Integer.parseInt(info.getQueryParameters().getFirst("pageNumber"));
        String sortName = info.getQueryParameters().getFirst("sortName");
        String sortOrder = info.getQueryParameters().getFirst("sortOrder");
        Date startTime = null;
        Date endTime = null;
        Map<String, Object> fields = getQueryParameters(info, new String[]{"jobName", "taskId", "ip", "isSuccess"});
        try {
            if (!Strings.isNullOrEmpty(info.getQueryParameters().getFirst("startTime"))) {
                startTime = DATETIME_FORMATTER.parse(info.getQueryParameters().getFirst("startTime"));
            }
            if (!Strings.isNullOrEmpty(info.getQueryParameters().getFirst("endTime"))) {
                endTime = DATETIME_FORMATTER.parse(info.getQueryParameters().getFirst("endTime"));
            }
        } catch (final ParseException ex) {
            throw new JobSystemException(ex);
        }
        return new JobEventRdbSearch(dataSource.get()).findJobExecutionEvents(new Condition(pageSize, pageNumber, sortName, sortOrder, startTime, endTime, fields));
    }
    
    /**
     * 检索作业运行状态轨迹.
     * 
     * @return 作业状态轨迹检索结果
     */
    @GET
    @Path("/findJobStatusTraceEvents")
    @Consumes(MediaType.APPLICATION_JSON)
    public Result<JobStatusTraceEvent> findJobStatusTraceEvents(@Context final UriInfo info) {
        if (!dataSource.isPresent()) {
            return new Result<JobStatusTraceEvent>(0, Collections.<JobStatusTraceEvent>emptyList());
        }
        int pageSize = Integer.parseInt(info.getQueryParameters().getFirst("pageSize"));
        int pageNumber = Integer.parseInt(info.getQueryParameters().getFirst("pageNumber"));
        String sortName = info.getQueryParameters().getFirst("sortName");
        String sortOrder = info.getQueryParameters().getFirst("sortOrder");
        Date startTime = null;
        Date endTime = null;
        Map<String, Object> fields = getQueryParameters(info, new String[]{"jobName", "taskId", "slaveId", "source", "executionType", "state"});
        try {
            if (!Strings.isNullOrEmpty(info.getQueryParameters().getFirst("startTime"))) {
                startTime = DATETIME_FORMATTER.parse(info.getQueryParameters().getFirst("startTime"));
            }
            if (!Strings.isNullOrEmpty(info.getQueryParameters().getFirst("endTime"))) {
                endTime = DATETIME_FORMATTER.parse(info.getQueryParameters().getFirst("endTime"));
            }
        } catch (final ParseException ex) {
            throw new JobSystemException(ex);
        }
        return new JobEventRdbSearch(dataSource.get()).findJobStatusTraceEvents(new Condition(pageSize, pageNumber, sortName, sortOrder, startTime, endTime, fields));
    }
    
    private Map<String, Object> getQueryParameters(final UriInfo info, final String[] params) {
        final Map<String, Object> result = new HashMap<String, Object>();
        for (String each : params) {
            if (!Strings.isNullOrEmpty(info.getQueryParameters().getFirst(each))) {
                result.put(each, info.getQueryParameters().getFirst(each));
            }
        }
        return result;
    }
    
    /**
     * 获取一周以来任务运行结果统计数据.
     * 
     * @return 一周以来任务运行结果统计数据
     */
    @GET
    @Path("/getTaskRunningResultStatisticsWeekly")
    @Consumes(MediaType.APPLICATION_JSON)
    public TaskRunningResultStatistics getTaskRunningResultStatisticsWeekly() {
        return statisticManager.getTaskRunningResultStatisticsOfWeekly();
    }
    
    /**
     * 获取自上线以来任务运行结果统计数据.
     * 
     * @return 自上线以来任务运行结果统计数据
     */
    @GET
    @Path("/getTaskRunningResultStatisticsSinceOnline")
    @Consumes(MediaType.APPLICATION_JSON)
    public TaskRunningResultStatistics getTaskRunningResultStatisticsSinceOnline() {
        return statisticManager.getTaskRunningResultStatisticsSinceOnline();
    }
    
    /**
     * 获取作业类型统计数据.
     * 
     * @return 作业类型统计数据
     */
    @GET
    @Path("/getJobTypeStatistics")
    @Consumes(MediaType.APPLICATION_JSON)
    public JobTypeStatistics getJobTypeStatistics() {
        return statisticManager.getJobTypeStatistics();
    }
    
    /**
     * 获取作业执行类型统计数据.
     * 
     * @return 作业执行类型统计数据
     */
    @GET
    @Path("/getJobExecutionTypeStatistics")
    @Consumes(MediaType.APPLICATION_JSON)
    public JobExecutionTypeStatistics getJobExecutionTypeStatistics() {
        return statisticManager.getJobExecutionTypeStatistics();
    }
    
    /**
     * 获取一周以来任务运行统计数据集合.
     * 
     * @return 一周以来任务运行统计数据集合
     */
    @GET
    @Path("/findTaskRunningStatisticsOfWeekly")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<TaskRunningStatistics> findTaskRunningStatisticsOfWeekly() {
        return statisticManager.findTaskRunningStatisticsOfWeekly();
    }
    
    /**
     * 获取自上线以来作业注册统计数据集合.
     * 
     * @return 自上线以来作业注册统计数据集合
     */
    @GET
    @Path("/findJobRegisterStatisticsSinceOnline")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<JobRegisterStatistics> findJobRegisterStatisticsSinceOnline() {
        return statisticManager.findJobRegisterStatisticsSinceOnline();
    }
}
