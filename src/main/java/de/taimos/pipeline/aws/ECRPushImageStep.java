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
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PushImageResultCallback;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class ECRPushImageStep extends AbstractStepImpl {

    private final String imageName;
    private final String tagName;
    private final String regionName;

    @DataBoundConstructor
    public ECRPushImageStep(String imageName, String tagName, String regionName) {
        this.imageName = imageName;
        if(null == tagName)
        {
            tagName = "latest";
        }
        this.tagName = tagName;
        this.regionName = regionName;
    }

    public String getImageName() {
        return this.imageName;
    }

    public String getTagName() {
        return this.tagName;
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
            return "ecrPushImage";
        }

        @Override
        public String getDisplayName() {
            return "Authenticates and pushes a docker image";
        }
    }

    public static class Execution extends AbstractStepExecutionImpl {

        @Inject
        private transient ECRPushImageStep step;
        @StepContextParameter
        private transient EnvVars envVars;
        @StepContextParameter
        private transient FilePath workspace;
        @StepContextParameter
        private transient TaskListener listener;

        @Override
        public boolean start() throws Exception {
            final String imageName = this.step.getImageName();
            final String tagName = this.step.getTagName();
            final String regionName = this.step.getRegionName();

            final AmazonECRClient ecrClient = AWSClientFactory.create(AmazonECRClient.class, envVars);
            if(null != regionName && !regionName.isEmpty()) {
                ecrClient.setRegion(Region.getRegion(Regions.fromName(regionName)));
            }
            final GetAuthorizationTokenResult tokenResult = ecrClient.getAuthorizationToken(new GetAuthorizationTokenRequest());
            final String token = tokenResult.getAuthorizationData().get(0).getAuthorizationToken();
            final String proxyEndpoint = tokenResult.getAuthorizationData().get(0).getProxyEndpoint();
            final String authToken = StringUtils.newStringUtf8(Base64.decodeBase64(token)).split(":")[1];
            final DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("unix:///var/run/docker.sock")
                    .build();
            final AuthConfig authConfig = new AuthConfig().withUsername("AWS").withPassword(authToken)
                    .withRegistryAddress(proxyEndpoint);

            final DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

            new Thread("ecrPushImage") {
                @Override
                public void run() {
                    try {
                        Execution.this.listener.getLogger().println("Preparing to push image");
                        final String dockerPrefix = proxyEndpoint.replaceFirst("^(http://\\.|https://|\\.)", "");
                        final String repoName = dockerPrefix + "/" + imageName;
                        Execution.this.listener.getLogger().format("Using docker tag image %s to %s with a tag of %s", imageName, repoName, tagName);
                        dockerClient.tagImageCmd(imageName, repoName, tagName).exec();
                        dockerClient.pushImageCmd(repoName)
                                .withAuthConfig(authConfig)
                                .exec(new PushImageResultCallback()).awaitSuccess();
                        Execution.this.listener.getLogger().format(" Pushed docker image %s to aws", imageName);
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
