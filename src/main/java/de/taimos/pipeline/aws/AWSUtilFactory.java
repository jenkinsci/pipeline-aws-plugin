package de.taimos.pipeline.aws;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
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
	private static Supplier<TransferManager> transferManagerSupplier;


	@Restricted(NoExternalUse.class)
	public static void setStackSupplier(Function<String, CloudFormationStack> supplier) {
		stackSupplier = supplier;
	}

	@Restricted(NoExternalUse.class)
	public static void setStackSetSupplier(Function<String, CloudFormationStackSet> supplier) {
		stackSetSupplier = supplier;
	}

	public static CloudFormationStack newCFStack(AmazonCloudFormation client, String stack, TaskListener listener) {
		if (stackSupplier != null) {
			return stackSupplier.apply(stack);
		}
		return new CloudFormationStack(client, stack, listener);
	}

	public static CloudFormationStackSet newCFStackSet(AmazonCloudFormation client,
			String stack, TaskListener listener, SleepStrategy sleepStrategy) {
		if (stackSetSupplier != null) {
			return stackSetSupplier.apply(stack);
		}
		return new CloudFormationStackSet(client, stack, listener, sleepStrategy);
	}

	public static TransferManager newTransferManager(AmazonS3 s3Client) {
		if (transferManagerSupplier != null) {
			return transferManagerSupplier.get();
		}
		return TransferManagerBuilder.standard()
				.withS3Client(s3Client)
				.build();
	}

	public static void setTransferManagerSupplier(Supplier<TransferManager> tfSupplier) {
		transferManagerSupplier = tfSupplier;
	}
}
