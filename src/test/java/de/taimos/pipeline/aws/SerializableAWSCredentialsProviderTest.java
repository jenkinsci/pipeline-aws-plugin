package de.taimos.pipeline.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;

import org.junit.Assert;
import org.junit.Test;

public class SerializableAWSCredentialsProviderTest {

	public static final String ACCESS_KEY_ID = "access_key_id";
	public static final String SECRET_KEY_ID = "secret_key_id";
	public static final String SESSION_TOKEN = "session_token";

	@Test
	public void serializeBasicAWSCredentials() throws Exception {
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(
			ACCESS_KEY_ID,
			SECRET_KEY_ID
		);
		AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(awsCreds);
		SerializableAWSCredentialsProvider serializedProvider = new SerializableAWSCredentialsProvider(provider);
		Assert.assertEquals(ACCESS_KEY_ID, serializedProvider.getCredentials().getAWSAccessKeyId());
		Assert.assertEquals(SECRET_KEY_ID, serializedProvider.getCredentials().getAWSSecretKey());
	}

	@Test
	public void serializeBasicSessionCredentials() throws Exception {
		BasicSessionCredentials awsCreds = new BasicSessionCredentials(
			ACCESS_KEY_ID,
			SECRET_KEY_ID,
			SESSION_TOKEN
		);
		AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(awsCreds);
		SerializableAWSCredentialsProvider serializedProvider = new SerializableAWSCredentialsProvider(provider);
		BasicSessionCredentials serializedCredentials = (BasicSessionCredentials) serializedProvider.getCredentials();
		Assert.assertEquals(ACCESS_KEY_ID, serializedCredentials.getAWSAccessKeyId());
		Assert.assertEquals(SECRET_KEY_ID, serializedCredentials.getAWSSecretKey());
		Assert.assertEquals(SESSION_TOKEN, serializedCredentials.getSessionToken());
	}

}
