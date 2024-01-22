package de.taimos.pipeline.aws;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

public class SNSPublishStepTest {


    @Test
    public void gettersWorkAsExpectedForMessageAttributes() {
        SNSPublishStep step = new SNSPublishStep("arn:sns:1234", "subject", "message");
        Map<String, String> messageAttributes = new HashMap<>();
        messageAttributes.put("k1", "v1");
        messageAttributes.put("k2", "v2");
        messageAttributes.put("k3", "v3");
        step.setMessageAttributes(messageAttributes);

        Assert.assertEquals("arn:sns:1234", step.getTopicArn());
        Assert.assertEquals("subject", step.getSubject());
        Assert.assertEquals("message", step.getMessage());
        Assert.assertEquals(3, step.getMessageAttributes().size());
        assertThat(step.getMessageAttributes(), hasEntry("k1", "v1"));
        assertThat(step.getMessageAttributes(), hasEntry("k2", "v2"));
        assertThat(step.getMessageAttributes(), hasEntry("k3", "v3"));
    }
}