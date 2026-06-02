package de.taimos.pipeline.aws;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;

public interface AWSClientFactoryDelegate {
	Object create(AwsClientBuilder<?, ?> clientBuilder);
}
