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
package com.theupswell.appengine.counter.web.test;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.inject.Inject;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.service.ShardedCounterService;

/**
 * A Resource for Counters
 */
@Path("/api/counters")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CountersResource
{
	private final ShardedCounterService shardedCounterService;

	@Inject
	public CountersResource(ShardedCounterService shardedCounterService)
	{
		this.shardedCounterService = shardedCounterService;
	}

	@GET
	@Path("/{counterName}")
	public Counter getCount(@PathParam("counterName")
	String counterName)
	{
		return this.shardedCounterService.getCounter(counterName);
	}

	/**
	 * Create an increment with a unique id.
	 * 
	 * @param counterName
	 * @return
	 */
	@POST
	@Path("/{counterName}/increment")
	public Counter createIncrement(@PathParam("counterName")
	String counterName)
	{
		return this.shardedCounterService.increment(counterName);
	}

	/**
	 * Create a decrement with a unique id.
	 * 
	 * @param counterName
	 * @return
	 */
	@POST
	@Path("/{counterName}/decrement")
	public Counter createDecrement(@PathParam("counterName")
	String counterName)
	{
		return this.shardedCounterService.decrement(counterName);
	}

	/**
	 * Create an increment with a unique id.
	 * 
	 * @param counterName
	 * @return
	 */
	@PUT
	@Path("/{counterName}/increment/{incrementId}")
	public Counter applyIncrement(@PathParam("counterName")
	String counterName, @PathParam("incrementId")
	String incrementId)
	{
		return null;
		// return
		// this.counterService.increment(counterName)getCounter(counterName);
	}

}
