package de.taimos.pipeline.aws.cloudformation.parser;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
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
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("baz").parameterValue("true").build()
		);
	}

	@Test
	public void parseStringList() throws IOException {
		ParameterProvider parameterProvider = Mockito.mock(ParameterProvider.class);
		Mockito.when(parameterProvider.getParams()).thenReturn(Arrays.asList("foo=bar", "baz=true"));
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(temporaryFolder.newFolder()), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("baz").parameterValue("true").build()
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
				Parameter.builder().parameterKey("foo").parameterValue("true").build(),
				Parameter.builder().parameterKey("baz").parameterValue("false").build(),
				Parameter.builder().parameterKey("bar").parameterValue("25").build()
		);
	}
}
