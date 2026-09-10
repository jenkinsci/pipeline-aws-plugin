package de.taimos.pipeline.aws.cloudformation.parser;

import software.amazon.awssdk.services.cloudformation.model.Tag;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class TagsFileParserTests {

    @Test
    public void parseJson() throws IOException {
        Collection<Tag> tags = TagsFileParser.parseTags(getClass().getResourceAsStream("tags.json"));
        Assertions.assertThat(tags).containsExactlyInAnyOrder(
                Tag.builder().key("foo1").value("bar1").build(),
                Tag.builder().key("foo2").value("bar2").build()
        );
    }
}
