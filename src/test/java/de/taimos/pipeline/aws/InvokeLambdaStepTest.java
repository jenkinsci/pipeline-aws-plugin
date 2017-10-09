package de.taimos.pipeline.aws;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class InvokeLambdaStepTest {

	@Test
	public void shouldConvertPayloadObjectToString() throws Exception {
		InvokeLambdaStep invokeLambdaStep = new InvokeLambdaStep("test-lambda",
				Collections.singletonMap("key", "value"));
		Assert.assertEquals("{\"key\":\"value\"}", invokeLambdaStep.getPayloadAsString());
	}

	@Test
	public void shouldConvertPayloadListToString() throws Exception {
		InvokeLambdaStep invokeLambdaStep = new InvokeLambdaStep("test-lambda", Collections.singletonList("elem"));
		Assert.assertEquals("[\"elem\"]", invokeLambdaStep.getPayloadAsString());
	}
}
