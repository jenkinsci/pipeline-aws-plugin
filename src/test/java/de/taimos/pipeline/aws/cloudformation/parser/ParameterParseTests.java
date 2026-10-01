package de.taimos.pipeline.aws.cloudformation.parser;

import software.amazon.awssdk.services.cloudformation.model.Parameter;
import de.taimos.pipeline.aws.cloudformation.ParameterProvider;
import hudson.FilePath;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParameterParseTests {

	@TempDir
	private File temporaryFolder;

	@Test
	void parseStringArray() throws IOException {
		ParameterProvider parameterProvider = mock(ParameterProvider.class);
		when(parameterProvider.getParams()).thenReturn(new String[]{"foo=bar", "baz=true"});
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(newFolder(temporaryFolder, "junit")), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("baz").parameterValue("true").build()
		);
	}

	@Test
	void parseStringList() throws IOException {
		ParameterProvider parameterProvider = mock(ParameterProvider.class);
		when(parameterProvider.getParams()).thenReturn(Arrays.asList("foo=bar", "baz=true"));
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(newFolder(temporaryFolder, "junit")), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("baz").parameterValue("true").build()
		);
	}

	@Test
	void parseMap() throws IOException {
		ParameterProvider parameterProvider = mock(ParameterProvider.class);
		when(parameterProvider.getParams()).thenReturn(new HashMap<String, Object>() {
			{
				put("foo", "true");
				put("baz", false);
				put("bar", 25);
			}
		});
		Collection<Parameter> parameters = ParameterParser.parse(new FilePath(newFolder(temporaryFolder, "junit")), parameterProvider);

		Assertions.assertThat(parameters).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("foo").parameterValue("true").build(),
				Parameter.builder().parameterKey("baz").parameterValue("false").build(),
				Parameter.builder().parameterKey("bar").parameterValue("25").build()
		);
	}

	private static File newFolder(File root, String... subDirs) throws IOException {
		String subFolder = String.join("/", subDirs);
		File result = new File(root, subFolder);
		if (!result.mkdirs()) {
			throw new IOException("Couldn't create folders " + root);
		}
		return result;
	}
}
