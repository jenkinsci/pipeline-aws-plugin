package de.taimos.pipeline.aws.cloudformation.stacksets;

import com.amazonaws.services.cloudformation.model.StackSetOperationPreferences;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collection;

public class JenkinsStackSetOperationPreferences extends StackSetOperationPreferences {

	@DataBoundConstructor
	public JenkinsStackSetOperationPreferences() {

	}

	@DataBoundSetter
	@Override
	public void setRegionOrder(Collection<String> regionOrder) {
		super.setRegionOrder(regionOrder);
	}

	@DataBoundSetter
	@Override
	public void setFailureToleranceCount(Integer failureToleranceCount) {
		super.setFailureToleranceCount(failureToleranceCount);
	}

	@DataBoundSetter
	@Override
	public void setFailureTolerancePercentage(Integer failureTolerancePercentage) {
		super.setFailureTolerancePercentage(failureTolerancePercentage);
	}

	@DataBoundSetter
	@Override
	public void setMaxConcurrentCount(Integer maxConcurrentCount) {
		super.setMaxConcurrentCount(maxConcurrentCount);
	}

	@DataBoundSetter
	@Override
	public void setMaxConcurrentPercentage(Integer maxConcurrentPercentage) {
		super.setMaxConcurrentPercentage(maxConcurrentPercentage);
	}

	@DataBoundSetter
	@Override
	public void setRegionConcurrencyType(String regionConcurrencyType) {
		super.setRegionConcurrencyType(regionConcurrencyType);
	}
}
