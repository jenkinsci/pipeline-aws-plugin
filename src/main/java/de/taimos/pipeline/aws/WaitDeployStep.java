/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
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

import com.amazonaws.services.codedeploy.AmazonCodeDeployClient;
import com.amazonaws.services.codedeploy.model.GetDeploymentRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentResult;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;

/**
 * @author Giovanni Gargiulo
 */
public class WaitDeployStep extends AbstractStepImpl {

    /**
     * The DeploymentId to monitor. Example: d-3GR0HQLDN
     */
    private final String deploymentId;

    /**
     * The max amount of time, before failing be build.
     * A negative number will mean indefinite wait
     */
    private final Long maxWait;

    @DataBoundConstructor
    public WaitDeployStep(String deploymentId, Long maxWait) {
        this.deploymentId = deploymentId;
        this.maxWait = maxWait;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "waitDeploymentCompletion";
        }

        @Override
        public String getDisplayName() {
            return "Wait for AWS CodeDeploy deployment completion";
        }

    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient WaitDeployStep step;
        @StepContextParameter
        private transient EnvVars envVars;
        @StepContextParameter
        private transient TaskListener listener;

        private static final Long PollingInterval = 10000L;

        private static final String SucceededStatus = "Succeeded";

        private static final String FailedStatus = "Failed";

        @Override
        protected Void run() throws Exception {
            AmazonCodeDeployClient client = AWSClientFactory.create(AmazonCodeDeployClient.class, this.envVars);

            String deploymentId = this.step.getDeploymentId();
            Long maxWait = this.step.maxWait;
            this.listener.getLogger().format("Checking Deployment(%s) status", deploymentId);

            Long startTime = System.currentTimeMillis();

            while (maxWait < 0 || (System.currentTimeMillis() - startTime) < maxWait * 1000) {
                GetDeploymentRequest getDeploymentRequest = new GetDeploymentRequest().withDeploymentId(deploymentId);
                GetDeploymentResult deployment = client.getDeployment(getDeploymentRequest);
                String deploymentStatus = deployment.getDeploymentInfo().getStatus();

                this.listener.getLogger().format("DeploymentStatus(%s)", deploymentStatus);

                if (SucceededStatus.equals(deploymentStatus)) {
                    this.listener.getLogger().println("Deployment completed successfully");
                    return null;
                } else if (FailedStatus.equals(deploymentStatus)) {
                    this.listener.getLogger().println("Deployment completed in error");
                    String errorMessage = deployment.getDeploymentInfo().getErrorInformation().getMessage();
                    throw new Exception("Deployment Failed: " + errorMessage);
                } else {
                    this.listener.getLogger().println("Deployment still in progress... sleeping");
                    try {
                        Thread.sleep(PollingInterval);
                    } catch (Exception e) {
                        throw e;
                    }
                }

            }

            throw new Exception("Maximum time elapsed: " + maxWait + " seconds");

        }

        private static final long serialVersionUID = 1L;

    }

}
