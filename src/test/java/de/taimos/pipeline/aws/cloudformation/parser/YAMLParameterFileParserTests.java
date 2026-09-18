package de.taimos.pipeline.aws.cloudformation.parser;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class YAMLParameterFileParserTests {

	@Test
	public void parseParameters() throws IOException {
		YAMLParameterFileParser parser = new YAMLParameterFileParser();
		String json = "bar: foo";
		Collection<Parameter> parameters = parser.parseParams(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("bar").parameterValue("foo").build()
		);
	}

	@Test
	public void parseParameterCollection() throws IOException {
		YAMLParameterFileParser parser = new YAMLParameterFileParser();
		String json = "bar:\n  - foo1\n  - foo2";
		Collection<Parameter> parameters = parser.parseParams(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("bar").parameterValue("foo1,foo2").build()
		);
	}

}
