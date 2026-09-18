package de.taimos.pipeline.aws.cloudformation.stacksets;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import software.amazon.awssdk.services.cloudformation.model.StackSetOperationPreferences;

import java.io.Serializable;
import java.util.Collection;

/**
 * Holds the operationPreferences a pipeline passes to the stack set steps.
 *
 * This used to extend the SDK's StackSetOperationPreferences so Stapler could bind onto it
 * directly. That class is final in v2, so the values are collected here and converted on the way
 * out; the setter names are unchanged, which is what the pipeline syntax binds to.
 */
public class JenkinsStackSetOperationPreferences implements Serializable {

	private static final long serialVersionUID = 1L;

	private Collection<String> regionOrder;
	private Integer failureToleranceCount;
	private Integer failureTolerancePercentage;
	private Integer maxConcurrentCount;
	private Integer maxConcurrentPercentage;

	@DataBoundConstructor
	public JenkinsStackSetOperationPreferences() {
	}

	public Collection<String> getRegionOrder() {
		return this.regionOrder;
	}

	@DataBoundSetter
	public void setRegionOrder(Collection<String> regionOrder) {
		this.regionOrder = regionOrder;
	}

	public Integer getFailureToleranceCount() {
		return this.failureToleranceCount;
	}

	@DataBoundSetter
	public void setFailureToleranceCount(Integer failureToleranceCount) {
		this.failureToleranceCount = failureToleranceCount;
	}

	public Integer getFailureTolerancePercentage() {
		return this.failureTolerancePercentage;
	}

	@DataBoundSetter
	public void setFailureTolerancePercentage(Integer failureTolerancePercentage) {
		this.failureTolerancePercentage = failureTolerancePercentage;
	}

	public Integer getMaxConcurrentCount() {
		return this.maxConcurrentCount;
	}

	@DataBoundSetter
	public void setMaxConcurrentCount(Integer maxConcurrentCount) {
		this.maxConcurrentCount = maxConcurrentCount;
	}

	public Integer getMaxConcurrentPercentage() {
		return this.maxConcurrentPercentage;
	}

	@DataBoundSetter
	public void setMaxConcurrentPercentage(Integer maxConcurrentPercentage) {
		this.maxConcurrentPercentage = maxConcurrentPercentage;
	}

	public StackSetOperationPreferences toStackSetOperationPreferences() {
		return StackSetOperationPreferences.builder()
				.regionOrder(this.regionOrder)
				.failureToleranceCount(this.failureToleranceCount)
				.failureTolerancePercentage(this.failureTolerancePercentage)
				.maxConcurrentCount(this.maxConcurrentCount)
				.maxConcurrentPercentage(this.maxConcurrentPercentage)
				.build();
	}
}
