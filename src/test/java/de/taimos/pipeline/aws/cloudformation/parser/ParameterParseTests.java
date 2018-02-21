package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Parameter;
import de.taimos.pipeline.aws.cloudformation.ParameterProvider;
import hudson.FilePath;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class ParameterParseTests {

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void parseStringArray() throws IOException {
		ParameterProvider parameterProvider = Mockito.mock(ParameterProvider.class);
		Mockito.when(parameterProvider.getParams()).thenReturn(new String[]{"foo=bar", "baz=true"});
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(temporaryFolder.newFolder()), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter().withParameterKey("foo").withParameterValue("bar"),
				new Parameter().withParameterKey("baz").withParameterValue("true")
		);
	}

	@Test
	public void parseStringList() throws IOException {
		ParameterProvider parameterProvider = Mockito.mock(ParameterProvider.class);
		Mockito.when(parameterProvider.getParams()).thenReturn(Arrays.asList("foo=bar", "baz=true"));
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(temporaryFolder.newFolder()), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter().withParameterKey("foo").withParameterValue("bar"),
				new Parameter().withParameterKey("baz").withParameterValue("true")
		);
	}

	@Test
	public void parseMap() throws IOException {
		ParameterProvider parameterProvider = Mockito.mock(ParameterProvider.class);
		Mockito.when(parameterProvider.getParams()).thenReturn(new HashMap<String, Object>() {
			{
				put("foo", "true");
				put("baz", false);
				put("bar", 25);
			}
		});
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(temporaryFolder.newFolder()), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				new Parameter().withParameterKey("foo").withParameterValue("true"),
				new Parameter().withParameterKey("baz").withParameterValue("false"),
				new Parameter().withParameterKey("bar").withParameterValue("25")
		);
	}
}
