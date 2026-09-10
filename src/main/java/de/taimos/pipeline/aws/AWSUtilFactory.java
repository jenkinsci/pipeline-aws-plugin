package de.taimos.pipeline.aws;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import de.taimos.pipeline.aws.cloudformation.CloudFormationStack;
import de.taimos.pipeline.aws.cloudformation.stacksets.CloudFormationStackSet;
import de.taimos.pipeline.aws.cloudformation.stacksets.SleepStrategy;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.function.Function;
import java.util.function.Supplier;

public class AWSUtilFactory {

	private static Function<String, CloudFormationStack> stackSupplier;
	private static Function<String, CloudFormationStackSet> stackSetSupplier;
	private static Supplier<S3TransferManager> v2TransferManagerSupplier;


	@Restricted(NoExternalUse.class)
	public static void setStackSupplier(Function<String, CloudFormationStack> supplier) {
		stackSupplier = supplier;
	}

	@Restricted(NoExternalUse.class)
	public static void setStackSetSupplier(Function<String, CloudFormationStackSet> supplier) {
		stackSetSupplier = supplier;
	}

	public static CloudFormationStack newCFStack(CloudFormationClient client, String stack, TaskListener listener) {
		if (stackSupplier != null) {
			return stackSupplier.apply(stack);
		}
		return new CloudFormationStack(client, stack, listener);
	}

	public static CloudFormationStackSet newCFStackSet(CloudFormationClient client,
			String stack, TaskListener listener, SleepStrategy sleepStrategy) {
		if (stackSetSupplier != null) {
			return stackSetSupplier.apply(stack);
		}
		return new CloudFormationStackSet(client, stack, listener, sleepStrategy);
	}

	/**
	 * Note that closing the returned manager does not close this client: S3TransferManager closes only
	 * an async client it constructed itself. Callers own what they pass in and must close it.
	 */
	public static S3TransferManager newV2TransferManager(S3AsyncClient s3Client) {
		if (v2TransferManagerSupplier != null) {
			return v2TransferManagerSupplier.get();
		}
		return S3TransferManager.builder()
				.s3Client(s3Client)
				.build();
	}

	@Restricted(NoExternalUse.class)
	public static void setV2TransferManagerSupplier(Supplier<S3TransferManager> tfSupplier) {
		v2TransferManagerSupplier = tfSupplier;
	}
}
