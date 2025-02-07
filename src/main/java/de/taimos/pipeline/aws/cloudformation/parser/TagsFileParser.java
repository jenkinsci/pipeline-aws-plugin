package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TagsFileParser {

	public static Collection<Tag> parseTags(InputStream is) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode tree = mapper.readTree(is);
		ArrayNode jsonNodes = (ArrayNode) tree;
		return StreamSupport.stream(jsonNodes.spliterator(), false)
				.map(node -> new Tag()
						.withKey(node.get("Key").asText())
						.withValue(node.get("Value").asText()))
				.collect(Collectors.toList());
	}
}
