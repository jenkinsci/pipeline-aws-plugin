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

import org.junit.Assert;
import org.junit.Test;

public class SafeNameCreatorTest {
	
	@Test
	public void createSafeName() throws Exception {
		Assert.assertEquals("testaccount", ListAWSAccountsStep.SafeNameCreator.createSafeName("TestAccount"));
		Assert.assertEquals("some-test", ListAWSAccountsStep.SafeNameCreator.createSafeName("Some - Test"));
		Assert.assertEquals("some-other", ListAWSAccountsStep.SafeNameCreator.createSafeName("Some_Other"));
		Assert.assertEquals("special-chars", ListAWSAccountsStep.SafeNameCreator.createSafeName("Special%Chars"));
		Assert.assertEquals("multi-special", ListAWSAccountsStep.SafeNameCreator.createSafeName("Multi$%&Special"));
		Assert.assertEquals("account-12", ListAWSAccountsStep.SafeNameCreator.createSafeName("Account 12"));
	}
	
}