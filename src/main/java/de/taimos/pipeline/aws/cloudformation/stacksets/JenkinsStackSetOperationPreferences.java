package de.taimos.pipeline.aws.cloudformation.stacksets;

import software.amazon.awssdk.services.cloudformation.model.StackSetOperationPreferences;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collection;

public class JenkinsStackSetOperationPreferences  {
	StackSetOperationPreferences wrappedPrefs;

	@DataBoundConstructor
	public JenkinsStackSetOperationPreferences() {

	}

	@DataBoundSetter
	public void setRegionOrder(Collection<String> regionOrder) {
		wrappedPrefs = toBuilder().regionOrder(regionOrder).build();
	}

	@DataBoundSetter
	public void setFailureToleranceCount(Integer failureToleranceCount) {
		wrappedPrefs = toBuilder().failureToleranceCount(failureToleranceCount).build();
	}

	@DataBoundSetter
	public void setFailureTolerancePercentage(Integer failureTolerancePercentage) {
		wrappedPrefs = toBuilder().failureTolerancePercentage(failureTolerancePercentage).build();
	}

	@DataBoundSetter
	public void setMaxConcurrentCount(Integer maxConcurrentCount) {
		wrappedPrefs = toBuilder().maxConcurrentCount(maxConcurrentCount).build();
	}

	@DataBoundSetter
	public void setMaxConcurrentPercentage(Integer maxConcurrentPercentage) {
		wrappedPrefs = toBuilder().maxConcurrentPercentage(maxConcurrentPercentage).build();
	}

	private StackSetOperationPreferences.Builder toBuilder() {
		return wrappedPrefs ==  null ? StackSetOperationPreferences.builder() : wrappedPrefs.toBuilder();
	}
}
