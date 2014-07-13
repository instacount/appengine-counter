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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.inject.Provider;
import com.googlecode.objectify.util.jackson.ObjectifyJacksonModule;

/**
 * Give us some finer control over Jackson's behavior
 */
public class ObjectMapperProvider implements Provider<ObjectMapper>
{
	private final ObjectMapper defaultObjectMapper;

	public ObjectMapperProvider()
	{
		defaultObjectMapper = new ObjectMapper();

		defaultObjectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		defaultObjectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
		defaultObjectMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
		defaultObjectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		defaultObjectMapper.setSerializationInclusion(Include.NON_NULL);

		defaultObjectMapper.registerModule(new JodaModule());
		defaultObjectMapper.registerModule(new ObjectifyJacksonModule());
		defaultObjectMapper.registerModule(new GuavaModule());
	}

	@Override
	public ObjectMapper get()
	{
		return defaultObjectMapper;
	}
}