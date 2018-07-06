package de.taimos.pipeline.aws.cloudformation.parser;

import com.amazonaws.services.cloudformation.model.Tag;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class TagsFileParserTests {

    @Test
    public void parseJson() throws IOException {
        Collection<Tag> tags = TagsFileParser.parseTags(getClass().getResourceAsStream("tags.json"));
        Assertions.assertThat(tags).containsExactlyInAnyOrder(
                new Tag().withKey("foo1").withValue("bar1"),
                new Tag().withKey("foo2").withValue("bar2")
        );
    }
}
