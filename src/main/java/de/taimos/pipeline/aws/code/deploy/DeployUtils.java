package de.taimos.pipeline.aws.code.deploy;

import com.amazonaws.services.codedeploy.AmazonCodeDeploy;
import com.amazonaws.services.codedeploy.model.DeploymentStatus;
import com.amazonaws.services.codedeploy.model.GetDeploymentRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentResult;
import hudson.model.TaskListener;

public class DeployUtils {

	private static final Long POLLING_INTERVAL = 10_000L;

	private static final String SUCCEEDED_STATUS = DeploymentStatus.Succeeded.toString();

	private static final String FAILED_STATUS = DeploymentStatus.Failed.toString();

	private static final String STOPPED_STATUS = DeploymentStatus.Stopped.toString();

	public Void waitDeployment(String deploymentId, TaskListener listener, AmazonCodeDeploy client) throws Exception {
		while (true) {
			GetDeploymentRequest getDeploymentRequest = new GetDeploymentRequest().withDeploymentId(deploymentId);
			GetDeploymentResult deployment = client.getDeployment(getDeploymentRequest);
			String deploymentStatus = deployment.getDeploymentInfo().getStatus();

			listener.getLogger().format("DeploymentStatus(%s)", deploymentStatus);

			if (SUCCEEDED_STATUS.equals(deploymentStatus)) {
				listener.getLogger().println("Deployment completed successfully");
				return null;
			} else if (FAILED_STATUS.equals(deploymentStatus)) {
				listener.getLogger().println("Deployment completed in error");
				String errorMessage = deployment.getDeploymentInfo().getErrorInformation().getMessage();
				throw new Exception("Deployment Failed: " + errorMessage);
			} else if (STOPPED_STATUS.equals(deploymentStatus)) {
				listener.getLogger().println("Deployment was stopped");
				throw new Exception("Deployment was stopped");
			} else {
				listener.getLogger().println("Deployment still in progress... sleeping");
				try {
					Thread.sleep(POLLING_INTERVAL);
				} catch (InterruptedException e) {
					//
				}
			}

		}
	}
}
