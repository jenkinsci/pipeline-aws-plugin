package de.taimos.pipeline.aws.ecr;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import software.amazon.awssdk.services.ecr.model.ListImagesFilter;

import java.io.Serializable;

/**
 * Holds the filter a pipeline passes to ecrListImages. Formerly a subclass of the SDK's
 * ListImagesFilter, which is final in v2; see JenkinsImageIdentifier for the same reasoning.
 */
public class JenkinsListImageFilter implements Serializable {

	private static final long serialVersionUID = 1L;

	private String tagStatus;

	@DataBoundConstructor
	public JenkinsListImageFilter() {
	}

	public String getTagStatus() {
		return this.tagStatus;
	}

	@DataBoundSetter
	public void setTagStatus(String tagStatus) {
		this.tagStatus = tagStatus;
	}

	public ListImagesFilter toListImagesFilter() {
		return ListImagesFilter.builder().tagStatus(this.tagStatus).build();
	}
}
