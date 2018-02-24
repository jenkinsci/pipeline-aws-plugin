package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import de.taimos.pipeline.aws.cloudformation.ParameterProvider;
import hudson.FilePath;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParameterParser {

	private static Collection<Parameter> parseParamsFile(FilePath workspace, String paramsFileName) {
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

	private static Collection<Parameter> parseParams(Object o) {
		if (o == null) {
			return Collections.emptyList();
		} else if (o instanceof String[]) {
			return parseParams(Arrays.asList((String[]) o));
		} else if (o instanceof List) {
			return parseParams((List<String>) o);
		} else if (o instanceof Map) {
			return parseParams((Map<Object, Object>) o);
		} else {
			throw new IllegalStateException("Invalid params type: " + o.getClass());
		}
	}

	private static Collection<Parameter> parseParams(Map<Object, Object> map) {
		Collection<Parameter> parameters = new ArrayList<>();
		for (Map.Entry<Object, Object> entry : map.entrySet()) {
			if (entry.getValue() == null) {
				throw new IllegalStateException(entry.getKey() + " has a null value");
			}
			parameters.add(new Parameter()
					.withParameterKey((String) entry.getKey())
					.withParameterValue(entry.getValue().toString())
			);
		}
		return parameters;
	}

	private static Collection<Parameter> parseParams(List<String> params) {
		Collection<Parameter> parameters = new ArrayList<>();
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

	private static Collection<Parameter> parseKeepParams(String[] params) {
		if (params == null) {
			return Collections.emptyList();
		}
		Collection<Parameter> parameters = new ArrayList<>();
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
