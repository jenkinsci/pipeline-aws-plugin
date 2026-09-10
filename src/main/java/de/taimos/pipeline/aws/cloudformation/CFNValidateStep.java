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

package de.taimos.pipeline.aws.cloudformation;

import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;

import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.taimos.pipeline.aws.AwsSdkResponseToJson;
import groovy.json.JsonSlurper;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateRequest;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class CFNValidateStep extends Step {

	private String file;
	private String url;

	@DataBoundConstructor
	public CFNValidateStep() {
		//
	}

	public String getFile() {
		return this.file;
	}

	@DataBoundSetter
	public void setFile(String file) {
		this.file = file;
	}

	public String getUrl() {
		return this.url;
	}

	@DataBoundSetter
	public void setUrl(String url) {
		this.url = url;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNValidateStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "cfnValidate";
		}

		@Override
		public String getDisplayName() {
			return "Validate CloudFormation template";
		}
	}

	public static class Execution extends StepExecution {

		private final transient CFNValidateStep step;

		public Execution(CFNValidateStep step, @NonNull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String file = this.step.getFile();
			final String url = this.step.getUrl();

			if ((file == null || file.isEmpty()) && (url == null || url.isEmpty())) {
				throw new IllegalArgumentException("Either a file or url for the template must be specified");
			}

			this.getContext().get(TaskListener.class).getLogger().format("Validating CloudFormation template %s %n", file);

			final String template = this.readTemplate(file);

			new Thread("cfnValidate-" + file) {
				@Override
				public void run() {
					try {
						CloudFormationClient client = AWSClientFactory.create(CloudFormationClient.builder(), Execution.this.getContext());
						ValidateTemplateRequest.Builder request = ValidateTemplateRequest.builder();
						if (template != null) {
							request.templateBody(template);
						} else {
							request.templateURL(url);
						}
						ValidateTemplateResponse result = client.validateTemplate(request.build());
						Execution.this.getContext().onSuccess(AwsSdkResponseToJson.convertToMap(result));
					} catch (Throwable e) {
						// Everything has to reach onFailure: this runs on its own thread, so anything that
						// escapes here (client construction failing on an unresolvable region, a bug in the
						// response conversion) would leave the step never completing and the build hanging.
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}

		private String readTemplate(String file) {
			if (file == null || file.isEmpty()) {
				return null;
			}
			try {
				return this.getContext().get(FilePath.class).child(file).readToString();
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		}

		@Override
		public void stop(@NonNull Throwable cause) throws Exception {
			//
		}

		private static final long serialVersionUID = 1L;

	}

}
