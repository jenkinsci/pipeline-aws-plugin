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

package de.taimos.pipeline.aws.elb;

import org.junit.Assert;
import org.junit.Test;

public class ELBRegisterInstanceStepTest {
	@Test
	public void gettersWorkAsExpected() throws Exception {
		ELBRegisterInstanceStep step = new ELBRegisterInstanceStep("my-target-group-arn", "my-instance-id", 8888);
		Assert.assertEquals("my-target-group-arn", step.getTargetGroupARN());
		Assert.assertEquals("my-instance-id", step.getInstanceID());
		Assert.assertEquals(8888, step.getPort());
	}
}
