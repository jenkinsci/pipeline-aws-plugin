package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.model.Tag;
import hudson.FilePath;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public abstract class TemplateStepBase extends Step implements ParameterProvider {
	private String file;
	private String url;
	private Object params;
	private String[] keepParams;
	private String[] tags;
	private String paramsFile;
	private Long pollInterval = 1000L;
	private Boolean create = true;

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

	protected final Collection<Tag> getAwsTags() {
		Collection<Tag> tagList = new ArrayList<>();
		if (tags == null) {
			return tagList;
		}
		for (String tag : tags) {
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
}
