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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.oodlemud.appengine.counter.Counter;
import com.oodlemud.appengine.counter.service.ShardedCounterService;
import com.oodlemud.appengine.counter.service.ShardedCounterServiceConfiguration;
import com.oodlemud.appengine.counter.service.ShardedCounterServiceImpl;

/**
 * Constructor Test class for {@link ShardedCounterService}.
 * 
 * @author David Fuelling <dfuelling@oodlemud.com>
 */
public class ShardedCounterServiceConstructorTest extends AbstractShardedCounterServiceTest
{
	@Before
	public void setUp() throws Exception
	{
		super.setUp();
	}

	@After
	public void tearDown()
	{
		super.tearDown();
	}

	// /////////////////////////
	// Unit Tests
	// /////////////////////////

	@Test(expected = RuntimeException.class)
	public void testShardedCounterServiceConstructor_NullMemcache()
	{
		shardedCounterService = new ShardedCounterServiceImpl(null, CapabilitiesServiceFactory.getCapabilitiesService());
	}

	@Test(expected = RuntimeException.class)
	public void testShardedCounterServiceConstructor_NullCapabilities()
	{
		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(), null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShardedCounterServiceConstructor_ValidMemcache_0Shards()
	{
		ShardedCounterServiceConfiguration.Builder builder = new ShardedCounterServiceConfiguration.Builder();
		builder.withNumInitialShards(0);

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			CapabilitiesServiceFactory.getCapabilitiesService(), builder.build());
	}

	@Test(expected = RuntimeException.class)
	public void testShardedCounterServiceConstructor_ValidMemcache_NegativeShards()
	{
		ShardedCounterServiceConfiguration.Builder builder = new ShardedCounterServiceConfiguration.Builder();
		builder.withNumInitialShards(-10);

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			CapabilitiesServiceFactory.getCapabilitiesService(), builder.build());
	}

	@Test
	public void testShardedCounterServiceConstructor_DefaultShards()
	{
		shardedCounterService = new ShardedCounterServiceImpl();
		Counter counter = shardedCounterService.getCounter(TEST_COUNTER1);
		assertCounter(counter, TEST_COUNTER1, 0L);
	}

	@Test
	public void testShardedCounterServiceConstructor_NoRelativeUrlPath()
	{
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withDeleteCounterShardQueueName(DELETE_COUNTER_SHARD_QUEUE_NAME).build();

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			CapabilitiesServiceFactory.getCapabilitiesService(), config);

		Counter counter = shardedCounterService.getCounter(TEST_COUNTER1);
		assertCounter(counter, TEST_COUNTER1, 0L);
	}

	@Test
	public void testShardedCounterServiceConstructorFull()
	{
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withDeleteCounterShardQueueName(DELETE_COUNTER_SHARD_QUEUE_NAME).withNumInitialShards(10)
			.withRelativeUrlPathForDeleteTaskQueue("RELATIVE-URL-PATH-FOR-DELETE-QUEUE").build();

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			CapabilitiesServiceFactory.getCapabilitiesService(), config);

		Counter counter = shardedCounterService.getCounter(TEST_COUNTER1);
		assertCounter(counter, TEST_COUNTER1, 0L);
	}

}
