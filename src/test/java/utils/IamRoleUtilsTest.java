package utils;

/*-
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 - 2017 Taimos GmbH
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

import org.junit.Test;

import de.taimos.pipeline.aws.utils.IamRoleUtils;

public class IamRoleUtilsTest {

	@Test
	public void findPartitionWithRegionName() throws Exception {
		// example of type 'aws'
		IamRoleUtils.selectPartitionName("us-east-1");

		// example of type 'aws-cn'
		IamRoleUtils.selectPartitionName("cn-north-1");

		// example of type 'aws-us-gov'
		IamRoleUtils.selectPartitionName("us-gov-west-1");
		// no exception -> ok
	}

}
