/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.theupswell.appengine.counter.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.memcache.MemcacheServiceFactory;

/**
 * Constructor Test class for {@link ShardedCounterService}.
 * 
 * @author David Fuelling
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
		shardedCounterService = new ShardedCounterServiceImpl(null);
	}

	@Test(expected = RuntimeException.class)
	public void testShardedCounterServiceConstructor_NullConfig()
	{
		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(), null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShardedCounterServiceConstructor_ValidMemcache_0Shards()
	{
		final ShardedCounterServiceConfiguration.Builder builder = new ShardedCounterServiceConfiguration.Builder();
		builder.withNumInitialShards(0);

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			builder.build());
	}

	@Test(expected = RuntimeException.class)
	public void testShardedCounterServiceConstructor_ValidMemcache_NegativeShards()
	{
		final ShardedCounterServiceConfiguration.Builder builder = new ShardedCounterServiceConfiguration.Builder();
		builder.withNumInitialShards(-10);

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			builder.build());
	}

	@Test
	public void testShardedCounterServiceConstructor_DefaultShards()
	{
		shardedCounterService = new ShardedCounterServiceImpl();
		assertThat(shardedCounterService.getCounter(TEST_COUNTER1).isPresent(), is(false));
	}

	@Test
	public void testShardedCounterServiceConstructor_NoRelativeUrlPath()
	{
		final ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withDeleteCounterShardQueueName(DELETE_COUNTER_SHARD_QUEUE_NAME).build();

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(), config);

		assertThat(shardedCounterService.getCounter(TEST_COUNTER1).isPresent(), is(false));
	}

	@Test
	public void testShardedCounterServiceConstructorFull()
	{
		final ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withDeleteCounterShardQueueName(DELETE_COUNTER_SHARD_QUEUE_NAME).withNumInitialShards(10)
			.withRelativeUrlPathForDeleteTaskQueue("RELATIVE-URL-PATH-FOR-DELETE-QUEUE").build();

		shardedCounterService = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(), config);

		assertThat(shardedCounterService.getCounter(TEST_COUNTER1).isPresent(), is(false));
	}

}
