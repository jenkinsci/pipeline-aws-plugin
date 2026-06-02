package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class JenkinsImageIdentifier {
	private ImageIdentifier wrappedIdentifier;
	@DataBoundConstructor
	public JenkinsImageIdentifier() {
	}

	@DataBoundSetter
	public void setImageDigest(String imageDigest) {
		wrappedIdentifier = toBuilder().imageDigest(imageDigest).build();
	}

	@DataBoundSetter
	public void setImageTag(String imageTag) {
		wrappedIdentifier = wrappedIdentifier.toBuilder().imageTag(imageTag).build();
	}

	public ImageIdentifier getWrappedIdentifier() {
		return wrappedIdentifier;
	}

	private ImageIdentifier.Builder toBuilder() {
		return wrappedIdentifier == null ? ImageIdentifier.builder() : wrappedIdentifier.toBuilder();
	}
}
