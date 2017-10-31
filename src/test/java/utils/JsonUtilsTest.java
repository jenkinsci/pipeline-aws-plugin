package utils;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;

import de.taimos.pipeline.aws.utils.JsonUtils;

public class JsonUtilsTest {
	
	@Test
	public void jsonObjectShouldBeSerializable() throws Exception {	
		Object result = JsonUtils.fromString("{}");
		
		new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(result);
		// no exception -> ok
	}
	
}
