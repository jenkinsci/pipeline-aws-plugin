package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.model.ListImagesFilter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class JenkinsListImageFilter {

	private ListImagesFilter wrappedFilter;

	@DataBoundConstructor
	public JenkinsListImageFilter() {
	}

	@DataBoundSetter
	public void setTagStatus(String tagStatus) {
		wrappedFilter = ListImagesFilter.builder().tagStatus(tagStatus).build();
	}

	public ListImagesFilter getWrappedFilter() {
		return wrappedFilter;
	}
}
