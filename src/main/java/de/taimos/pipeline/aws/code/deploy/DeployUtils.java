package de.taimos.pipeline.aws.code.deploy;

import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.DeploymentStatus;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentResponse;
import hudson.model.TaskListener;

public class DeployUtils {

	private static final Long POLLING_INTERVAL = 10_000L;

	private static final String SUCCEEDED_STATUS = DeploymentStatus.SUCCEEDED.toString();

	private static final String FAILED_STATUS = DeploymentStatus.FAILED.toString();

	private static final String STOPPED_STATUS = DeploymentStatus.STOPPED.toString();

	public Void waitDeployment(String deploymentId, TaskListener listener, CodeDeployClient client) throws Exception {
		while (true) {
			GetDeploymentRequest getDeploymentRequest = GetDeploymentRequest.builder().deploymentId(deploymentId).build();
			GetDeploymentResponse deployment = client.getDeployment(getDeploymentRequest);
			// statusAsString: v2 models this as an enum, and the constants above are its wire values
			String deploymentStatus = deployment.deploymentInfo().statusAsString();

			listener.getLogger().format("DeploymentStatus(%s)", deploymentStatus);

			if (SUCCEEDED_STATUS.equals(deploymentStatus)) {
				listener.getLogger().println("Deployment completed successfully");
				return null;
			} else if (FAILED_STATUS.equals(deploymentStatus)) {
				listener.getLogger().println("Deployment completed in error");
				String errorMessage = deployment.deploymentInfo().errorInformation().message();
				throw new Exception("Deployment Failed: " + errorMessage);
			} else if (STOPPED_STATUS.equals(deploymentStatus)) {
				listener.getLogger().println("Deployment was stopped");
				throw new Exception("Deployment was stopped");
			} else {
				listener.getLogger().println("Deployment still in progress... sleeping");
				// Left to propagate: Jenkins aborts a step by interrupting its thread, so swallowing
				// this made the wait uninterruptible and an aborted build kept polling. The Elastic
				// Beanstalk wait steps already let it out of run().
				Thread.sleep(POLLING_INTERVAL);
			}

		}
	}
}
