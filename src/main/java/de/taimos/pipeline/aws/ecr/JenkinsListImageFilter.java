package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.model.ListImagesFilter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class JenkinsListImageFilter extends ListImagesFilter {
	@DataBoundConstructor
	public JenkinsListImageFilter() {
	}

	@Override
	@DataBoundSetter
	public void setTagStatus(String tagStatus) {
		super.setTagStatus(tagStatus);
	}
}
