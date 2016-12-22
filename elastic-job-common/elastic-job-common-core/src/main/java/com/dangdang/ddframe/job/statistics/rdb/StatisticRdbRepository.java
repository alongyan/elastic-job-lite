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

package com.dangdang.ddframe.job.statistics.rdb;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

import com.dangdang.ddframe.job.statistics.type.JobRegisterStatistics;
import com.dangdang.ddframe.job.statistics.type.TaskRunningResultStatistics;
import com.dangdang.ddframe.job.statistics.type.TaskRunningStatistics;
import com.google.common.base.Optional;
import com.dangdang.ddframe.job.statistics.type.TaskRunningResultStatistics.StatisticUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 统计信息数据仓库，基于数据库.
 *
 * @author liguangyun
 */
@Slf4j
public class StatisticRdbRepository {
    
    private static final String TABLE_TASK_RUNNING_RESULT_STATISTICS = "TASK_RUNNING_RESULT_STATISTICS";
    
    private static final String TABLE_TASK_RUNNING_STATISTICS = "TASK_RUNNING_STATISTICS";
    
    private static final String TABLE_JOB_REGISTER_STATISTICS = "JOB_REGISTER_STATISTICS";
    
    private final DataSource dataSource;
    
    /**
     * 构造函数.
     * @param dataSource 数据源
     * @throws SQLException
     */
    public StatisticRdbRepository(final DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        initTables();
    }
    
    private void initTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            createTaskRunningResultTableIfNeeded(conn);
            createTaskRunningTableIfNeeded(conn);
            createJobRegisterTableIfNeeded(conn);
        }
    }
    
    private void createTaskRunningResultTableIfNeeded(final Connection conn) throws SQLException {
        DatabaseMetaData dbMetaData = conn.getMetaData();
        for (StatisticUnit each : StatisticUnit.values()) {
            try (ResultSet resultSet = dbMetaData.getTables(null, null, TABLE_TASK_RUNNING_RESULT_STATISTICS + "_" + each, new String[]{"TABLE"});) {
                if (!resultSet.next()) {
                    createTaskRunningResultTable(conn, each);
                }
            }
        }
    }
    
    private void createTaskRunningResultTable(final Connection conn, final StatisticUnit unit) throws SQLException {
        String dbSchema = "CREATE TABLE `" + TABLE_TASK_RUNNING_RESULT_STATISTICS + "_" + unit + "` ("
                + "`id` BIGINT NOT NULL AUTO_INCREMENT, "
                + "`success_count` INT(11),"
                + "`failed_count` INT(11),"
                + "`statistics_time` TIMESTAMP DEFAULT 0 NOT NULL,"
                + "`creation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (`id`));";
        try (PreparedStatement preparedStatement = conn.prepareStatement(dbSchema)) {
            preparedStatement.execute();
        }
    }
    
    private void createTaskRunningTableIfNeeded(final Connection conn) throws SQLException {
        DatabaseMetaData dbMetaData = conn.getMetaData();
        try (ResultSet resultSet = dbMetaData.getTables(null, null, TABLE_TASK_RUNNING_STATISTICS, new String[]{"TABLE"});) {
            if (!resultSet.next()) {
                createTaskRunningTable(conn);
            }
        }
    }
    
    private void createTaskRunningTable(final Connection conn) throws SQLException {
        String dbSchema = "CREATE TABLE `" + TABLE_TASK_RUNNING_STATISTICS + "` ("
                + "`id` BIGINT NOT NULL AUTO_INCREMENT, "
                + "`running_count` INT(11),"
                + "`statistics_time` TIMESTAMP DEFAULT 0 NOT NULL,"
                + "`creation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (`id`));";
        try (PreparedStatement preparedStatement = conn.prepareStatement(dbSchema)) {
            preparedStatement.execute();
        }
    }
    
    private void createJobRegisterTableIfNeeded(final Connection conn) throws SQLException {
        DatabaseMetaData dbMetaData = conn.getMetaData();
        try (ResultSet resultSet = dbMetaData.getTables(null, null, TABLE_JOB_REGISTER_STATISTICS, new String[]{"TABLE"});) {
            if (!resultSet.next()) {
                createJobRegisterTable(conn);
            }
        }
    }
    
    private void createJobRegisterTable(final Connection conn) throws SQLException {
        String dbSchema = "CREATE TABLE `" + TABLE_JOB_REGISTER_STATISTICS + "` ("
                + "`id` BIGINT NOT NULL AUTO_INCREMENT, "
                + "`registered_count` INT(11),"
                + "`statistics_time` TIMESTAMP DEFAULT 0 NOT NULL,"
                + "`creation_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (`id`));";
        try (PreparedStatement preparedStatement = conn.prepareStatement(dbSchema)) {
            preparedStatement.execute();
        }
    }
    
    /**
     * 添加任务运行结果统计数据.
     * 
     * @param taskRunningResultStatistics 任务运行结果统计数据对象
     * @return
     */
    public boolean add(final TaskRunningResultStatistics taskRunningResultStatistics) {
        boolean result = false;
        String sql = "INSERT INTO `" + TABLE_TASK_RUNNING_RESULT_STATISTICS + "_" + taskRunningResultStatistics.getStatisticUnit()
                + "` (`success_count`, `failed_count`, `statistics_time`) VALUES (?, ?, ?);";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, taskRunningResultStatistics.getSuccessCount());
            preparedStatement.setInt(2, taskRunningResultStatistics.getFailedCount());
            preparedStatement.setTimestamp(3, new Timestamp(taskRunningResultStatistics.getStatisticsTime().getTime()));
            preparedStatement.execute();
            result = true;
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Insert taskRunningResultStatistics to DB error:", ex);
        }
        return result;
    }
    
    /**
     * 添加运行中的任务统计数据.
     * 
     * @param taskRunningStatistics 运行中的任务统计数据对象
     * @return
     */
    public boolean add(final TaskRunningStatistics taskRunningStatistics) {
        boolean result = false;
        String sql = "INSERT INTO `" + TABLE_TASK_RUNNING_STATISTICS + "` (`running_count`, `statistics_time`) VALUES (?, ?);";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, taskRunningStatistics.getRunningCount());
            preparedStatement.setTimestamp(2, new Timestamp(taskRunningStatistics.getStatisticsTime().getTime()));
            preparedStatement.execute();
            result = true;
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Insert taskRunningStatistics to DB error:", ex);
        }
        return result;
    }
    
    /**
     * 添加作业注册统计数据.
     * 
     * @param jobRegisterStatistics 作业注册统计数据对象
     * @return
     */
    public boolean add(final JobRegisterStatistics jobRegisterStatistics) {
        boolean result = false;
        String sql = "INSERT INTO `" + TABLE_JOB_REGISTER_STATISTICS + "` (`registered_count`, `statistics_time`) VALUES (?, ?);";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, jobRegisterStatistics.getRegisteredCount());
            preparedStatement.setTimestamp(2, new Timestamp(jobRegisterStatistics.getStatisticsTime().getTime()));
            preparedStatement.execute();
            result = true;
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Insert jobRegisterStatistics to DB error:", ex);
        }
        return result;
    }
    
    /**
     * 获取任务运行结果统计数据集合.
     * 
     * @param from 统计开始时间
     * @param statisticUnit 统计单位
     * @return 任务运行结果统计数据集合
     */
    public List<TaskRunningResultStatistics> findTaskRunningResultStatistics(final Date from, final StatisticUnit statisticUnit) {
        List<TaskRunningResultStatistics> result = new LinkedList<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("SELECT id, success_count, failed_count, statistics_time, creation_time FROM %s WHERE statistics_time >= '%s' order by id ASC", 
                TABLE_TASK_RUNNING_RESULT_STATISTICS + "_" + statisticUnit, formatter.format(from));
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                TaskRunningResultStatistics taskRunningResultStatistics = new TaskRunningResultStatistics(resultSet.getLong(1), resultSet.getInt(2), resultSet.getInt(3), 
                        statisticUnit, new Date(resultSet.getTimestamp(4).getTime()), new Date(resultSet.getTimestamp(5).getTime()));
                result.add(taskRunningResultStatistics);
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch taskRunningResultStatistics from DB error:", ex);
        }
        return result;
    }
    
    /**
     * 获取合计后的任务运行结果统计数据.
     * 
     * @param from 统计开始时间
     * @param statisticUnit 统计单位
     * @return 合计后的任务运行结果统计数据对象
     */
    public TaskRunningResultStatistics getSummedTaskRunningResultStatistics(final Date from, final StatisticUnit statisticUnit) {
        TaskRunningResultStatistics result = new TaskRunningResultStatistics(0, 0, statisticUnit, new Date());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("SELECT sum(success_count), sum(failed_count) FROM %s WHERE statistics_time >= '%s'", 
                TABLE_TASK_RUNNING_RESULT_STATISTICS + "_" + statisticUnit, formatter.format(from));
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                result = new TaskRunningResultStatistics(resultSet.getInt(1), resultSet.getInt(2), statisticUnit, new Date());
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch summed taskRunningResultStatistics from DB error:", ex);
        }
        return result;
    }
    
    /**
     * 获取最近一条任务运行结果统计数据.
     * 
     * @param statisticUnit 统计单位
     * @return 任务运行结果统计数据对象
     */
    public Optional<TaskRunningResultStatistics> findLatestTaskRunningResultStatistics(final StatisticUnit statisticUnit) {
        TaskRunningResultStatistics result = null;
        String sql = String.format("SELECT id, success_count, failed_count, statistics_time, creation_time FROM %s order by id DESC LIMIT 1", 
                TABLE_TASK_RUNNING_RESULT_STATISTICS + "_" + statisticUnit);
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                result = new TaskRunningResultStatistics(resultSet.getLong(1), resultSet.getInt(2), resultSet.getInt(3), 
                        statisticUnit, new Date(resultSet.getTimestamp(4).getTime()), new Date(resultSet.getTimestamp(5).getTime()));
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch latest taskRunningResultStatistics from DB error:", ex);
        }
        return Optional.fromNullable(result);
    }
    
    /**
     * 获取运行中的任务统计数据集合.
     * 
     * @param from 统计开始时间
     * @return 运行中的任务统计数据集合
     */
    public List<TaskRunningStatistics> findTaskRunningStatistics(final Date from) {
        List<TaskRunningStatistics> result = new LinkedList<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("SELECT id, running_count, statistics_time, creation_time FROM %s WHERE statistics_time >= '%s' order by id ASC", 
                TABLE_TASK_RUNNING_STATISTICS, formatter.format(from));
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                TaskRunningStatistics taskRunningStatistics = new TaskRunningStatistics(resultSet.getLong(1), resultSet.getInt(2), 
                        new Date(resultSet.getTimestamp(3).getTime()), new Date(resultSet.getTimestamp(4).getTime()));
                result.add(taskRunningStatistics);
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch taskRunningStatistics from DB error:", ex);
        }
        return result;
    }
    
    /**
     * 获取最近一条运行中的任务统计数据.
     * 
     * @param statisticUnit 统计单位
     * @return 运行中的任务统计数据对象
     */
    public Optional<TaskRunningStatistics> findLatestTaskRunningStatistics() {
        TaskRunningStatistics result = null;
        String sql = String.format("SELECT id, running_count, statistics_time, creation_time FROM %s order by id DESC LIMIT 1", 
                TABLE_TASK_RUNNING_STATISTICS);
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                result = new TaskRunningStatistics(resultSet.getLong(1), resultSet.getInt(2), 
                        new Date(resultSet.getTimestamp(3).getTime()), new Date(resultSet.getTimestamp(4).getTime()));
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch latest taskRunningStatistics from DB error:", ex);
        }
        return Optional.fromNullable(result);
    }
    
    /**
     * 获取作业注册统计数据集合.
     * 
     * @param from 统计开始时间
     * @return 作业注册统计数据集合
     */
    public List<JobRegisterStatistics> findJobRegisterStatistics(final Date from) {
        List<JobRegisterStatistics> result = new LinkedList<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("SELECT id, registered_count, statistics_time, creation_time FROM %s WHERE statistics_time >= '%s' order by id ASC", 
                TABLE_JOB_REGISTER_STATISTICS, formatter.format(from));
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                JobRegisterStatistics jobRegisterStatistics = new JobRegisterStatistics(resultSet.getLong(1), resultSet.getInt(2), 
                        new Date(resultSet.getTimestamp(3).getTime()), new Date(resultSet.getTimestamp(4).getTime()));
                result.add(jobRegisterStatistics);
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch jobRegisterStatistics from DB error:", ex);
        }
        return result;
    }
    
    /**
     * 获取最近一条作业注册统计数据.
     * 
     * @return 作业注册统计数据对象
     */
    public Optional<JobRegisterStatistics> findLatestJobRegisterStatistics() {
        JobRegisterStatistics result = null;
        String sql = String.format("SELECT id, registered_count, statistics_time, creation_time FROM %s order by id DESC LIMIT 1", 
                TABLE_JOB_REGISTER_STATISTICS);
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery();
                ) {
            while (resultSet.next()) {
                result = new JobRegisterStatistics(resultSet.getLong(1), resultSet.getInt(2), 
                        new Date(resultSet.getTimestamp(3).getTime()), new Date(resultSet.getTimestamp(4).getTime()));
            }
        } catch (final SQLException ex) {
            // TODO 记录失败直接输出日志,未来可考虑配置化
            log.error("Fetch latest jobRegisterStatistics from DB error:", ex);
        }
        return Optional.fromNullable(result);
    }
}
