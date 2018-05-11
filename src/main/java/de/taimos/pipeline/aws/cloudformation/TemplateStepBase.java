package de.taimos.pipeline.aws.cloudformation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.RollbackTrigger;
import com.amazonaws.services.cloudformation.model.Tag;

import hudson.FilePath;

public abstract class TemplateStepBase extends Step implements ParameterProvider {
	private String file;
	private String template;
	private String url;
	private Object params;
	private String[] keepParams;
	private String[] tags;
	private String paramsFile;
	private Long pollInterval = 1000L;
	private Boolean create = true;
	private Integer rollbackTimeoutInMinutes;
	private String[] rollbackTriggers;

	public String getFile() {
		return this.file;
	}

	@DataBoundSetter
	public void setFile(String file) {
		this.file = file;
	}

	public String getTemplate() {
		return this.template;
	}

	@DataBoundSetter
	public void setTemplate(String template) {
		this.template = template;
	}

	public String getUrl() {
		return this.url;
	}

	@DataBoundSetter
	public void setUrl(String url) {
		this.url = url;
	}

	public Object getParams() {
		return this.params;
	}

	@DataBoundSetter
	public void setParams(Object params) {
		this.params = params;
	}

	public String[] getKeepParams() {
		return this.keepParams != null ? this.keepParams.clone() : null;
	}

	@DataBoundSetter
	public void setKeepParams(String[] keepParams) {
		this.keepParams = keepParams.clone();
	}

	public String[] getTags() {
		return this.tags != null ? this.tags.clone() : null;
	}

	@DataBoundSetter
	public void setTags(String[] tags) {
		this.tags = tags.clone();
	}

	public String getParamsFile() {
		return this.paramsFile;
	}

	@DataBoundSetter
	public void setParamsFile(String paramsFile) {
		this.paramsFile = paramsFile;
	}

	public Long getPollInterval() {
		return this.pollInterval;
	}

	@DataBoundSetter
	public void setPollInterval(Long pollInterval) {
		this.pollInterval = pollInterval;
	}

	public Boolean getCreate() {
		return this.create;
	}

	@DataBoundSetter
	public void setCreate(Boolean create) {
		this.create = create;
	}

	public Integer getRollbackTimeoutInMinutes() {
		return this.rollbackTimeoutInMinutes;
	}

	@DataBoundSetter
	public void setRollbackTimeoutInMinutes(Integer rollbackTimeoutInMinutes) {
		this.rollbackTimeoutInMinutes = rollbackTimeoutInMinutes;
	}

	public String[] getRollbackTriggers() {
		return this.rollbackTriggers != null ? this.rollbackTriggers.clone() : null;
	}

	@DataBoundSetter
	public void setRollbackTriggers(String[] rollbackTriggers) {
		this.rollbackTriggers = rollbackTriggers.clone();
	}

	protected final Collection<Tag> getAwsTags() {
		Collection<Tag> tagList = new ArrayList<>();
		if (this.tags == null) {
			return tagList;
		}
		for (String tag : this.tags) {
			int i = tag.indexOf('=');
			if (i < 0) {
				throw new IllegalArgumentException("Missing = in tag " + tag);
			}
			String key = tag.substring(0, i);
			String value = tag.substring(i + 1);
			tagList.add(new Tag().withKey(key).withValue(value));
		}
		return tagList;
	}

	protected String readTemplate(StepExecution stepExecution) {
		if (this.template != null) {
			return this.template;
		} else {
			return this.readTemplateFile(stepExecution);
		}
	}

	private String readTemplateFile(StepExecution stepExecution) {
		if (this.file == null) {
			return null;
		}

		FilePath workspace;
		try {
			workspace = stepExecution.getContext().get(FilePath.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			Thread.interrupted();
			throw new RuntimeException(e);
		}
		FilePath child = workspace.child(this.file);
		try {
			return child.readToString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected RollbackConfiguration getRollbackConfiguration() {
		RollbackConfiguration rollbackConfig = new RollbackConfiguration().withMonitoringTimeInMinutes(0);
		if (this.getRollbackTimeoutInMinutes() != null) {
			rollbackConfig.withMonitoringTimeInMinutes(this.getRollbackTimeoutInMinutes());
		}
		if (this.getRollbackTriggers() != null) {
			rollbackConfig.withRollbackTriggers(this.parseRollbackTriggers(this.getRollbackTriggers()));
		}
		return rollbackConfig;
	}

	private Collection<RollbackTrigger> parseRollbackTriggers(String[] configs) {
		Collection<RollbackTrigger> rollbackTriggers = new ArrayList<>();
		for (String cfg : configs) {
			int i = cfg.indexOf('=');
			if (i < 0) {
				throw new IllegalArgumentException("Missing = in config " + cfg);
			}
			String key = cfg.substring(0, i);
			String value = cfg.substring(i + 1);
			rollbackTriggers.add(new RollbackTrigger().withType(key).withArn(value));
		}
		return rollbackTriggers;
	}
}
