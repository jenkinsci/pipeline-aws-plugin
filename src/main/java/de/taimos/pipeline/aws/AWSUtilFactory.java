package de.taimos.pipeline.aws;

import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import de.taimos.pipeline.aws.cloudformation.CloudFormationStack;
import de.taimos.pipeline.aws.cloudformation.stacksets.CloudFormationStackSet;
import de.taimos.pipeline.aws.cloudformation.stacksets.SleepStrategy;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.function.Function;
import java.util.function.Supplier;

public class AWSUtilFactory {

	private static Function<String, CloudFormationStack> stackSupplier;
	private static Function<String, CloudFormationStackSet> stackSetSupplier;
	private static Supplier<S3TransferManager> transferManagerSupplier;


	@Restricted(NoExternalUse.class)
	public static void setStackSupplier(Function<String, CloudFormationStack> supplier) {
		stackSupplier = supplier;
	}

	@Restricted(NoExternalUse.class)
	public static void setStackSetSupplier(Function<String, CloudFormationStackSet> supplier) {
		stackSetSupplier = supplier;
	}

	public static CloudFormationStack newCFStack(CloudFormationClient client,
												 CloudFormationAsyncClient asyncClient,
												 String stack, TaskListener listener) {
		if (stackSupplier != null) {
			return stackSupplier.apply(stack);
		}
		return new CloudFormationStack(client, asyncClient, stack, listener);
	}

	public static CloudFormationStackSet newCFStackSet(CloudFormationClient client,
			String stack, TaskListener listener, SleepStrategy sleepStrategy) {
		if (stackSetSupplier != null) {
			return stackSetSupplier.apply(stack);
		}
		return new CloudFormationStackSet(client, stack, listener, sleepStrategy);
	}

	public static S3TransferManager newTransferManager(S3AsyncClient s3Client) {
		if (transferManagerSupplier != null) {
			return transferManagerSupplier.get();
		}
		return S3TransferManager.builder().s3Client(s3Client)
				.build();
	}

	public static void setTransferManagerSupplier(Supplier<S3TransferManager> tfSupplier) {
		transferManagerSupplier = tfSupplier;
	}
}
