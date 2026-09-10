package de.taimos.pipeline.aws.ecr;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;

import java.io.Serializable;

/**
 * Holds the imageIds entries a pipeline passes to ecrDeleteImages.
 *
 * This used to extend the SDK's ImageIdentifier so Stapler could bind straight onto it. The v2
 * model is final and immutable, so the values are collected here and converted on the way out.
 * The setter names are unchanged, which is what the pipeline syntax binds to.
 */
public class JenkinsImageIdentifier implements Serializable {

	private static final long serialVersionUID = 1L;

	private String imageDigest;
	private String imageTag;

	@DataBoundConstructor
	public JenkinsImageIdentifier() {
	}

	public String getImageDigest() {
		return this.imageDigest;
	}

	@DataBoundSetter
	public void setImageDigest(String imageDigest) {
		this.imageDigest = imageDigest;
	}

	public String getImageTag() {
		return this.imageTag;
	}

	@DataBoundSetter
	public void setImageTag(String imageTag) {
		this.imageTag = imageTag;
	}

	public ImageIdentifier toImageIdentifier() {
		return ImageIdentifier.builder()
				.imageDigest(this.imageDigest)
				.imageTag(this.imageTag)
				.build();
	}
}
