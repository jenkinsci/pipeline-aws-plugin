/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.model.ScalingType;
import com.amazonaws.services.kinesis.model.UpdateShardCountRequest;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class KinesisUpdateShardCountStep extends AbstractStepImpl {

    private final String streamName;
    private final Integer targetShardCount;
    private final String regionName;

    @DataBoundConstructor
    public KinesisUpdateShardCountStep(String streamName, Integer targetShardCount, String regionName) {
        this.streamName = streamName;
        this.targetShardCount = targetShardCount;
        this.regionName = regionName;
    }

    public Integer getTargetShardCount() {
        return this.targetShardCount;
    }

    public String getStreamName() {
        return this.streamName;
    }

    public String getRegionName() {
        return this.regionName;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "kinesisUpdateShardCount";
        }

        @Override
        public String getDisplayName() {
            return "Updates the shard count for a kinesis stream";
        }
    }

    public static class Execution extends AbstractStepExecutionImpl {

        @Inject
        private transient KinesisUpdateShardCountStep step;
        @StepContextParameter
        private transient EnvVars envVars;
        @StepContextParameter
        private transient FilePath workspace;
        @StepContextParameter
        private transient TaskListener listener;

        @Override
        public boolean start() throws Exception {
            final String streamName = this.step.getStreamName();
            final Integer targetShardCount = this.step.getTargetShardCount();
            final String regionName = this.step.getRegionName();

            final AmazonKinesisClient kinesisClient = AWSClientFactory.create(AmazonKinesisClient.class, envVars);
            if(null != regionName && !regionName.isEmpty()) {
                kinesisClient.setRegion(Region.getRegion(Regions.fromName(regionName)));
            }
            final UpdateShardCountRequest request = new UpdateShardCountRequest();
            request.setStreamName(streamName);
            request.setTargetShardCount(targetShardCount);
            request.setScalingType(ScalingType.UNIFORM_SCALING);

            new Thread("kinesisUpdateShardCount") {
                @Override
                public void run() {
                    try {
                        Execution.this.listener.getLogger().format("Updating %s to have a shard count of %d", streamName, targetShardCount);
                        kinesisClient.updateShardCount(request);
                        Execution.this.listener.getLogger().println(" Finished Updating");
                        Execution.this.getContext().onSuccess(null);
                    } catch (Exception e) {
                        Execution.this.getContext().onFailure(e);
                    }
                }
            }.start();
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            //
        }

        private static final long serialVersionUID = 1L;

    }
}
