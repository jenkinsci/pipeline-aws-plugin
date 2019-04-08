package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.model.ImageIdentifier;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class JenkinsImageIdentifier extends ImageIdentifier {

	@DataBoundConstructor
	public JenkinsImageIdentifier() {
	}

	@Override
	@DataBoundSetter
	public void setImageDigest(String imageDigest) {
		super.setImageDigest(imageDigest);
	}

	@Override
	@DataBoundSetter
	public void setImageTag(String imageTag) {
		super.setImageTag(imageTag);
	}
}
