package de.taimos.pipeline.aws.cloudformation.parser;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import org.assertj.core.api.Assertions;

import java.io.IOException;
import java.util.Collection;

class TagsFileParserTests {

	@Test
	void parseJson() throws IOException {
        Collection<Tag> tags = TagsFileParser.parseTags(getClass().getResourceAsStream("tags.json"));
        Assertions.assertThat(tags).containsExactlyInAnyOrder(
                Tag.builder().key("foo1").value("bar1").build(),
                Tag.builder().key("foo2").value("bar2").build()
        );
    }
}
