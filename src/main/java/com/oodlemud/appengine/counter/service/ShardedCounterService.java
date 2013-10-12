/**
 * Copyright (C) 2013 Oodlemud Inc. (developers@oodlemud.com)
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
package com.oodlemud.appengine.counter.service;

import com.oodlemud.appengine.counter.Counter;

/**
 * A Counter Service that can retrieve, increment, decrement, and delete a named
 * {@link Counter} using appengine Datastore shards.
 * 
 * @author David Fuelling <dfuelling@oodlemud.com>
 */
public interface ShardedCounterService extends CounterService
{

	/**
	 * Callback function used by TaskQueues to delete a particular counter and
	 * all of its shards.
	 * 
	 * @param counterName
	 */
	public void onTaskQueueCounterDeletion(final String counterName);

}
