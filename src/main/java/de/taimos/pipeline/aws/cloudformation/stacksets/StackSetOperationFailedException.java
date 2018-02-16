package de.taimos.pipeline.aws.cloudformation.stacksets;

public class StackSetOperationFailedException extends IllegalStateException {

	private final String operationId;

	public StackSetOperationFailedException(String operationId) {
		this.operationId = operationId;
	}

	public String getOperationId() {
		return operationId;
	}
}
