package de.taimos.pipeline.aws.cloudformation.stacksets;

import org.kohsuke.stapler.DataBoundConstructor;

public class BatchingOptions {
	private final boolean regions;

	@DataBoundConstructor
	public BatchingOptions(boolean regions) {
		this.regions = regions;
	}

	public boolean isRegions() {
		return regions;
	}
}
