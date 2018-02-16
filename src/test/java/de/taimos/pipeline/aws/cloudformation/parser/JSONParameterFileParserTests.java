package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.util.StringInputStream;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class JSONParameterFileParserTests {

	@Test
	public void parseParameters() throws IOException {
		JSONParameterFileParser parser = new JSONParameterFileParser();
		String json = "[{\"ParameterKey\": \"bar\", \"ParameterValue\": \"foo\"}]";
		Collection<Parameter> parameters = parser.parseParams(new StringInputStream(json));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter()
				.withParameterKey("bar")
				.withParameterValue("foo")
		);
	}

	@Test
	public void parseKeyParameters() throws IOException {
		JSONParameterFileParser parser = new JSONParameterFileParser();
		String json = "[{\"ParameterKey\": \"bar\", \"UsePreviousValue\": true}]";
		Collection<Parameter> parameters = parser.parseParams(new StringInputStream(json));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter()
						.withParameterKey("bar")
						.withUsePreviousValue(true)
		);
	}
}
