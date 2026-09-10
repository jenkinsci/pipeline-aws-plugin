package de.taimos.pipeline.aws;

import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jenkins doesn't support returning the API response directly.
 * Converting it into a map/list construct allows the full API response to be returned to be used in the jenkinsfile.
 *
 * Under the AWS SDK v1 this was a Jackson round-trip over the response object, which worked because
 * those models had JavaBean getters. The v2 models expose fluent accessors instead, so Jackson
 * produces nothing usable and the conversion walks the SDK's own field metadata instead.
 *
 * The keys are deliberately the lower-camel-case form of each member name, which is what the v1
 * output produced and what existing Jenkinsfiles index into. Unset members keep v1's split, which
 * fell out of how its models were generated and is easy to get wrong here: an unset scalar is an
 * explicit null, but an unset list or map is an empty collection, because v1's getters lazily
 * initialised those before Jackson ever saw them. v2's builders fill unset collections with an
 * auto-construct empty collection, so walking the fields reproduces that split as-is - mapping
 * those back to null would break a v1 pipeline doing response.capabilities.contains(...).
 *
 * One difference from v1: the sdkResponseMetadata and sdkHttpMetadata keys are gone. They appeared
 * only because Jackson picked up inherited HTTP plumbing getters, were never documented, and are
 * not part of the SDK's modelled fields.
 */
public class AwsSdkResponseToJson {
	private AwsSdkResponseToJson() {
	}

	public static Map<String, Object> convertToMap(SdkPojo pojo) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (SdkField<?> field : pojo.sdkFields()) {
			map.put(toKey(field.memberName()), convertValue(field.getValueOrDefault(pojo)));
		}
		return map;
	}

	private static String toKey(String memberName) {
		if (memberName == null || memberName.isEmpty()) {
			return memberName;
		}
		return Character.toLowerCase(memberName.charAt(0)) + memberName.substring(1);
	}

	private static Object convertValue(Object value) {
		if (value instanceof SdkPojo) {
			return convertToMap((SdkPojo) value);
		}
		if (value instanceof List) {
			List<Object> converted = new ArrayList<>();
			for (Object element : (List<?>) value) {
				converted.add(convertValue(element));
			}
			return converted;
		}
		if (value instanceof Map) {
			Map<String, Object> converted = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				converted.put(String.valueOf(entry.getKey()), convertValue(entry.getValue()));
			}
			return converted;
		}
		return value;
	}
}
