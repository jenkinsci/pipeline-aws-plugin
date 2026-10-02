package de.taimos.pipeline.aws;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SNSPublishStepTest {

	@Test
	void gettersWorkAsExpectedForMessageAttributes() {
        SNSPublishStep step = new SNSPublishStep("arn:sns:1234", "subject", "message");
        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("k1", "v1");
        messageAttributes.put("k2", "v2");
        messageAttributes.put("k3", "v3");
        step.setMessageAttributes(messageAttributes);

        assertEquals("arn:sns:1234", step.getTopicArn());
        assertEquals("subject", step.getSubject());
        assertEquals("message", step.getMessage());
        assertEquals(3, step.getMessageAttributes().size());
        assertThat(step.getMessageAttributes(), hasEntry("k1", "v1"));
        assertThat(step.getMessageAttributes(), hasEntry("k2", "v2"));
        assertThat(step.getMessageAttributes(), hasEntry("k3", "v3"));
    }
}