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

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.memcache.MemcacheService;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.service.ShardedCounterServiceConfiguration.Builder;

/**
 * Unit tests for incrementing a counter via {@link ShardedCounterServiceImpl}.
 *
 * @author David Fuelling
 */
public class ShardedCounterServiceIncrementInExistingTXTest extends ShardedCounterServiceIncrementTest
{
	protected ShardedCounterService singleShardShardedCounterService;

	@Before
	public void setUp() throws Exception
	{
		super.setUp();

		final ShardedCounterServiceConfiguration config = new Builder().withNumInitialShards(1).build();
		this.singleShardShardedCounterService = new ShardedCounterServiceTxWrapper(super.memcache, config);
	}

	@After
	public void tearDown()
	{
		super.tearDown();
	}

	// /////////////////////////
	// Unit Tests
	// /////////////////////////

	// /////////////////////////
	// Helper Class
	// /////////////////////////

	/**
	 * An extension of {@link ShardedCounterServiceImpl} that implements {@link ShardedCounterService} and wraps each
	 * interface call in order to simulate all interactions with the counter service happening inside of an existing
	 * Transaction.
	 */
	private static class ShardedCounterServiceTxWrapper extends ShardedCounterServiceImpl implements
			ShardedCounterService
	{

		/**
		 * Default Constructor for Dependency-Injection.
		 *
		 * @param memcacheService
		 * @param config The configuration for this service
		 */
		public ShardedCounterServiceTxWrapper(final MemcacheService memcacheService,
				final ShardedCounterServiceConfiguration config)
		{
			super(memcacheService, config);
		}

		/**
		 * Overidden so that all calls to {@link #increment} occur inside of an existing Transaction.
		 *
		 * @param counterName
		 * @param amount
		 * @return
		 */
		@Override
		public void increment(final String counterName, final long amount)
		{
			ObjectifyService.ofy().transact(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// 1.) Create a random CounterShardData for simulation purposes. It doesn't do anything except
					// to allow us to do something else in the Datastore in the same transactional context whilst
					// performing all unit tests.
					final CounterShardData counterShardData = new CounterShardData(UUID.randomUUID().toString(), 1);
					ObjectifyService.ofy().save().entity(counterShardData);

					// 2.) Operate on the counter and return.
					ShardedCounterServiceTxWrapper.super.increment(counterName, amount);
				}
			});
		}
	}

	/**
	 * An override of the parent-class test. There is a bug in the implementation when a counter is incremented in a
	 * parent-transaction and there is no value in memcache. In these cases, the counter will be off by one until the
	 * next cache flush. See Github #17 for more details. This bug will be fixed in version 1.1 of appengine-counter.
	 */
	@Test
	@Override
	public void testIncrementDecrementInterleaving()
	{
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);

		assertEquals(3, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(4, shardedCounterService.getCounter(TEST_COUNTER2).getCount());

		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);

		assertEquals(6, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(8, shardedCounterService.getCounter(TEST_COUNTER2).getCount());

		shardedCounterService.decrement(TEST_COUNTER1, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);
		shardedCounterService.decrement(TEST_COUNTER1, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);
		shardedCounterService.decrement(TEST_COUNTER1, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);

		assertEquals(3, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(4, shardedCounterService.getCounter(TEST_COUNTER2).getCount());

		shardedCounterService.decrement(TEST_COUNTER1, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);
		shardedCounterService.decrement(TEST_COUNTER1, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);
		shardedCounterService.decrement(TEST_COUNTER1, 1);
		shardedCounterService.decrement(TEST_COUNTER2, 1);

		assertEquals(0, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(0, shardedCounterService.getCounter(TEST_COUNTER2).getCount());
	}

	@Test
	public void incrementInParentTX()
	{
		// Make sure the counter exists
		this.singleShardShardedCounterService.getCounter(TEST_COUNTER1);

		// Increment the counter's 1 shard so it has a count of 1.
		this.singleShardShardedCounterService.increment(TEST_COUNTER1, 1);
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).getCount(), is(1L));

		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(TEST_COUNTER1, 0);
		CounterShardData counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();
		assertThat(counterShard, is(not(nullValue())));
		assertThat(counterShard.getCount(), is(1L));

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				singleShardShardedCounterService.increment(TEST_COUNTER1, 10L);
			}
		});

		// Both increments should have succeeded
		counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();
		assertThat(counterShard, is(not(nullValue())));
		assertThat(counterShard.getCount(), is(11L));
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).getCount(), is(11L));
	}

	@Test
	public void incrementInAbortedParentTX()
	{
		// Make sure the counter exists
		this.singleShardShardedCounterService.getCounter(TEST_COUNTER1);

		// Increment the counter's 1 shard so it has a count of 1.
		this.singleShardShardedCounterService.increment(TEST_COUNTER1, 1);
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).getCount(), is(1L));

		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(TEST_COUNTER1, 0);
		CounterShardData counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();
		assertThat(counterShard, is(not(nullValue())));
		assertThat(counterShard.getCount(), is(1L));

		// Perform another increment in a Work, but abort it before it can commit.
		try
		{
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					singleShardShardedCounterService.increment(TEST_COUNTER1, 10L);
					throw new RuntimeException("Aborting this increment on purpose!");
				}
			});
			fail();
		}
		catch (Exception e)
		{
			// We should get here. The Counter should not have incremented, and should still have a count of 1.
			counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();
			assertThat(counterShard, is(not(nullValue())));
			assertThat(counterShard.getCount(), is(1L));
			assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).getCount(), is(1L));
		}

		// We should get here. The Counter should not have incremented, and should still have a count of 1.
		counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();
		assertThat(counterShard, is(not(nullValue())));
		assertThat(counterShard.getCount(), is(1L));
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).getCount(), is(1L));

	}

}
