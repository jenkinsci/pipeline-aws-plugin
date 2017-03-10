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
import java.util.ArrayList;
import java.util.Collection;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class JSONParameterFileParser implements ParameterFileParser {
	
	@Override
	public Collection<Parameter> parseParams(InputStream fileContent) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode tree = mapper.readTree(fileContent);
		Collection<Parameter> parameters = new ArrayList<>();
		if (tree instanceof ArrayNode) {
			ArrayNode jsonNodes = (ArrayNode) tree;
			for (JsonNode node : jsonNodes) {
				Parameter param = new Parameter();
				param.withParameterKey(node.get("ParameterKey").asText());
				if (node.has("ParameterValue")) {
					param.withParameterValue(node.get("ParameterValue").asText());
				}
				if (node.has("UsePreviousValue")) {
					param.withUsePreviousValue(node.get("UsePreviousValue").booleanValue());
				}
				parameters.add(param);
			}
		}
		return parameters;
	}
	
}
