/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2018 Taimos GmbH
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

package de.taimos.pipeline.aws.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import hudson.EnvVars;
import hudson.model.TaskListener;

public class StepUtils {

	public static <T extends Class<?>> Set<T> requires(T... classes) {
		return new HashSet<>(Arrays.asList(classes));
	}

	@SuppressWarnings("unchecked")
	public static Set<? extends Class<?>> requiresDefault() {
		return requires(EnvVars.class, TaskListener.class);
	}

}
