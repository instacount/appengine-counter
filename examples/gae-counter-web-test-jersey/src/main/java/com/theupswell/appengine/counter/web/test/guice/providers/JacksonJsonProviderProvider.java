/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theupswell.appengine.counter.web.test.guice.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * A Provider for a {@link JacksonJsonProvider}, which is Jersey specific.
 * 
 * @author david
 * @see "http://stackoverflow.com/questions/16757724/how-to-hook-jackson-objectmapper-with-guice-jersey"
 */
public class JacksonJsonProviderProvider implements Provider<JacksonJsonProvider>
{
	private final ObjectMapper mapper;

	/**
	 * Required Args Constructor
	 * 
	 * @param mapper
	 */
	@Inject
	public JacksonJsonProviderProvider(ObjectMapper mapper)
	{
		this.mapper = mapper;
	}

	@Override
	public JacksonJsonProvider get()
	{
		return new JacksonJsonProvider(mapper);
	}
}
