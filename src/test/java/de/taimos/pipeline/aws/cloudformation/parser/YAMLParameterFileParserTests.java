package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.util.StringInputStream;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class YAMLParameterFileParserTests {

	@Test
	public void parseParameters() throws IOException {
		YAMLParameterFileParser parser = new YAMLParameterFileParser();
		String json = "bar: foo";
		Collection<Parameter> parameters = parser.parseParams(new StringInputStream(json));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter()
				.withParameterKey("bar")
				.withParameterValue("foo")
		);
	}

	@Test
	public void parseParameterCollection() throws IOException {
		YAMLParameterFileParser parser = new YAMLParameterFileParser();
		String json = "bar:\n  - foo1\n  - foo2";
		Collection<Parameter> parameters = parser.parseParams(new StringInputStream(json));
		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter()
						.withParameterKey("bar")
						.withParameterValue("foo1,foo2")
		);
	}

}
