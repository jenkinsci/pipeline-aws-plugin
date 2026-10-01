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

package de.taimos.pipeline.aws;

import java.util.Collection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import software.amazon.awssdk.services.cloudformation.model.Parameter;

import de.taimos.pipeline.aws.cloudformation.parser.JSONParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.YAMLParameterFileParser;

class ParameterParserTest {

	@Test
	void shouldParseYAML() throws Exception {
		YAMLParameterFileParser parser = new YAMLParameterFileParser();
		Collection<Parameter> parameters = parser.parseParams(this.getClass().getResourceAsStream("/params.yaml"));
		Parameter[] array = parameters.toArray(new Parameter[0]);
		assertEquals(2, array.length);

		Parameter param1 = array[0];
		assertEquals("Param1", param1.parameterKey());
		assertEquals("Value1", param1.parameterValue());

		Parameter param2 = array[1];
		assertEquals("Param2", param2.parameterKey());
		assertEquals("Val2a,Val2b", param2.parameterValue());
	}

	@Test
	void shouldParseJSON() throws Exception {
		JSONParameterFileParser parser = new JSONParameterFileParser();
		Collection<Parameter> parameters = parser.parseParams(this.getClass().getResourceAsStream("/params.json"));
		Parameter[] array = parameters.toArray(new Parameter[0]);
		assertEquals(2, array.length);

		Parameter param1 = array[0];
		assertEquals("Param1", param1.parameterKey());
		assertEquals("Value1", param1.parameterValue());

		Parameter param2 = array[1];
		assertEquals("Param2", param2.parameterKey());
		assertEquals("Value2", param2.parameterValue());
	}
}
