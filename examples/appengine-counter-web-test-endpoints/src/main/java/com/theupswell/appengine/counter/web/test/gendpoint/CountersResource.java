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
package com.theupswell.appengine.counter.web.test.gendpoint;

import javax.inject.Named;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.service.ShardedCounterService;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl;

/**
 * An endpoints resource for 'counters'
 */
@Api(name = "counters", resource = "counters", version = "v1")
public class CountersResource
{
	private final ShardedCounterService shardedCounterService;

	/**
	 * Default Constructor
	 */
	public CountersResource()
	{
		this.shardedCounterService = new ShardedCounterServiceImpl();
	}

	@ApiMethod(name = "counter.get", path = "{counterName}", httpMethod = HttpMethod.GET)
	public Counter getCounter(@Named("counterName")
	String counterName)
	{
		return this.shardedCounterService.getCounter(counterName);
	}

	@ApiMethod(name = "counter.increment", path = "{counterName}/increment", httpMethod = HttpMethod.POST)
	public Counter incrementCounter(@Named("counterName")
	String counterName)
	{
		return this.shardedCounterService.increment(counterName);
	}

	@ApiMethod(name = "counter.decrement", path = "{counterName}/decrement", httpMethod = HttpMethod.POST)
	public Counter decrementCounter(@Named("counterName")
	String counterName)
	{
		return this.shardedCounterService.decrement(counterName);
	}
}
