package de.taimos.pipeline.aws.cloudformation.parser;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class JSONParameterFileParserTests {

	@Test
	public void parseParameters() throws IOException {
		JSONParameterFileParser parser = new JSONParameterFileParser();
		String json = "[{\"ParameterKey\": \"bar\", \"ParameterValue\": \"foo\"}]";
		Collection<Parameter> parameters = parser.parseParams(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("bar").parameterValue("foo").build()
		);
	}

	@Test
	public void parseKeyParameters() throws IOException {
		JSONParameterFileParser parser = new JSONParameterFileParser();
		String json = "[{\"ParameterKey\": \"bar\", \"UsePreviousValue\": true}]";
		Collection<Parameter> parameters = parser.parseParams(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("bar").usePreviousValue(true).build()
		);
	}
}
