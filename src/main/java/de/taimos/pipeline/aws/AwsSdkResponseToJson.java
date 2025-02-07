package de.taimos.pipeline.aws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Jenkins doesn't support returning the API response directly.
 * Converting it into a map/list construct allows the full API response to be returned to be used in the jenkinsfile.
 */
public class AwsSdkResponseToJson {
	private AwsSdkResponseToJson() {
	}

	public static Map<String, Object> convertToMap(Object o) throws IOException {
		//convert to json so the method calls do not have to be
		ObjectMapper objectMapper = new ObjectMapper();
		String jsonResult = objectMapper.writer().writeValueAsString(o);
		return objectMapper.readValue(jsonResult, new TypeReference<>() {
		});
	}
}
