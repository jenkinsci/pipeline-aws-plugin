/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws.cloudformation.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;

public class YAMLParameterFileParser implements ParameterFileParser {

	@Override
	public Collection<Parameter> parseParams(InputStream fileContent) throws IOException {
		Yaml yaml = new Yaml();
		Map<String, Object> parse = (Map<String, Object>) yaml.load(new InputStreamReader(fileContent, Charsets.UTF_8));

		Collection<Parameter> parameters = new ArrayList<>();
		for (Map.Entry<String, Object> entry : parse.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Collection) {
				String val = Joiner.on(",").join((Collection) value);
				parameters.add(new Parameter().withParameterKey(entry.getKey()).withParameterValue(val));
			} else {
				parameters.add(new Parameter().withParameterKey(entry.getKey()).withParameterValue(value.toString()));
			}
		}
		return parameters;
	}

}
