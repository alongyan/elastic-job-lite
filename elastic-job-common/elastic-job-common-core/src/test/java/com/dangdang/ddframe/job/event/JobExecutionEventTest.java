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

package com.dangdang.ddframe.job.event;

import com.dangdang.ddframe.job.event.type.JobExecutionEvent;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class JobExecutionEventTest {
    
    @Test
    public void assertNewJobExecutionEvent() {
        JobExecutionEvent actual = new JobExecutionEvent("test_job", "fake_task_id", 0, JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER);
        assertThat(actual.getJobName(), is("test_job"));
        assertThat(actual.getSource(), is(JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER));
        assertThat(actual.getShardingItem(), is(0));
        assertNotNull(actual.getHostname());
        assertNotNull(actual.getStartTime());
        assertNull(actual.getCompleteTime());
        assertFalse(actual.isSuccess());
        assertThat(actual.getFailureCause(), is(""));
    }
    
    @Test
    public void assertExecutionSuccess() {
        JobExecutionEvent actual = new JobExecutionEvent("test_job", "fake_task_id", 0, JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER);
        actual.executionSuccess();
        assertNotNull(actual.getCompleteTime());
        assertTrue(actual.isSuccess());
    }
    
    @Test
    public void assertExecutionFailure() {
        JobExecutionEvent actual = new JobExecutionEvent("test_job", "fake_task_id", 0, JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER);
        actual.executionFailure(new RuntimeException("failure"));
        assertNotNull(actual.getCompleteTime());
        assertFalse(actual.isSuccess());
        assertThat(actual.getFailureCause(), startsWith("java.lang.RuntimeException: failure"));
    }
}
