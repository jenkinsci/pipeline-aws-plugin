package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import de.taimos.pipeline.aws.cloudformation.ParameterProvider;
import hudson.FilePath;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ParameterParser {

	static Collection<Parameter> parseParamsFile(FilePath workspace, String paramsFileName) {
		try {
			if (paramsFileName == null) {
				return Collections.emptyList();
			}
			final ParameterFileParser parser;
			FilePath paramsFile = workspace.child(paramsFileName);
			if (paramsFile.getName().endsWith(".json")) {
				parser = new JSONParameterFileParser();
			} else if (paramsFile.getName().endsWith(".yaml")) {
				parser = new YAMLParameterFileParser();
			} else {
				throw new IllegalArgumentException("Invalid file extension for parameter file (supports json/yaml)");
			}
			return parser.parseParams(paramsFile.read());
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	static Collection<Parameter> parseParams(String[] params) {
		Collection<Parameter> parameters = new ArrayList<>();
		if (params == null) {
			return parameters;
		}
		for (String param : params) {
			int i = param.indexOf('=');
			if (i < 0) {
				throw new IllegalArgumentException("Missing = in param " + param);
			}
			String key = param.substring(0, i);
			String value = param.substring(i + 1);
			parameters.add(new Parameter().withParameterKey(key).withParameterValue(value));
		}
		return parameters;
	}

	static Collection<Parameter> parseKeepParams(String[] params) {
		Collection<Parameter> parameters = new ArrayList<>();
		if (params == null) {
			return parameters;
		}
		for (String param : params) {
			parameters.add(new Parameter().withParameterKey(param).withUsePreviousValue(true));
		}
		return parameters;
	}

	public static Collection<Parameter> parseWithKeepParams(FilePath workspace, ParameterProvider provider) {
		Collection<Parameter> parameters = parse(workspace, provider);
		return Lists.newLinkedList(Iterables.concat(parameters, parseKeepParams(provider.getKeepParams())));
	}

	public static Collection<Parameter> parse(FilePath workspace, ParameterProvider provider) {
		return Lists.newLinkedList(
				Iterables.concat(
						parseParamsFile(workspace, provider.getParamsFile()),
						parseParams(provider.getParams())
				)
		);
	}
}
