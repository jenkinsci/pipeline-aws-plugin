package de.taimos.pipeline.aws;

import com.amazonaws.client.builder.AwsSyncClientBuilder;

public interface AWSClientFactoryDelegate {
	Object create(AwsSyncClientBuilder<?, ?> clientBuilder);
}
