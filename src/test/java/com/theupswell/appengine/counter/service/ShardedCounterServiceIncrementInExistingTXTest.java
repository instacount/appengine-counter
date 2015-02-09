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

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Work;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * Unit tests for incrementing a counter via {@link ShardedCounterServiceImpl}.
 *
 * @author David Fuelling
 */
public class ShardedCounterServiceIncrementInExistingTXTest extends ShardedCounterServiceIncrementTest
{

	@Before
	public void setUp() throws Exception
	{
		super.setUp();

		super.shardedCounterService = new ShardedCounterServiceTxWrapper();
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
		// Indicates that increment/decrement operations should not be isolated into their own Transaction. Put another
		// way, if an existing Datastore TX exists in the current Objectify session, then that transaction will be used

		private static final boolean NOT_ISOLATED = false;

		/**
		 * Overidden so that all calls to {@link #increment} occur inside of an existing TX.
		 *
		 * @param counterName
		 * @param amount
		 * @return
		 */
		@Override
		public Counter increment(final String counterName, final long amount, boolean isolatedTransactionContext)
		{
			return ObjectifyService.ofy().transactNew(1, new Work<Counter>()
			{
				@Override
				public Counter run()
				{
					// 1.) Create a random CounterShardData for simulation purposes. It doesn't do anything except
					// to allow us to do something else in the Datastore in the same transactional context whilst
					// performing all unit tests.
					final CounterShardData counterShardData = new CounterShardData(UUID.randomUUID().toString(), 1);
					ObjectifyService.ofy().save().entity(counterShardData);

					// 2.) Operate on the counter and return.
					Counter counter = ShardedCounterServiceTxWrapper.super.increment(counterName, amount, NOT_ISOLATED);
					assert (counter.getCount() == 0L);
					return counter;
				}
			});
		}

		/**
		 * Overidden so that all calls to {@link #increment} occur inside of an existing TX.
		 *
		 * @param counterName
		 * @param amount
		 * @return
		 */
		@Override
		public Counter increment(final String counterName, final long amount)
		{
			return ObjectifyService.ofy().transactNew(1, new Work<Counter>()
			{
				@Override
				public Counter run()
				{
					// 1.) Create a random CounterShardData for simulation purposes. It doesn't do anything except
					// to allow us to do something else in the Datastore in the same transactional context whilst
					// performing all unit tests.
					final CounterShardData counterShardData = new CounterShardData(UUID.randomUUID().toString(), 1);
					ObjectifyService.ofy().save().entity(counterShardData);

					// 2.) Operate on the counter and return.
					Counter counter = ShardedCounterServiceTxWrapper.super.increment(counterName, amount, NOT_ISOLATED);
					return counter;
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

		assertEquals(2, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(3, shardedCounterService.getCounter(TEST_COUNTER2).getCount());

		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);
		shardedCounterService.increment(TEST_COUNTER1, 1);
		shardedCounterService.increment(TEST_COUNTER2, 1);

		assertEquals(5, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(7, shardedCounterService.getCounter(TEST_COUNTER2).getCount());

		shardedCounterService.decrement(TEST_COUNTER1);
		shardedCounterService.decrement(TEST_COUNTER2);
		shardedCounterService.decrement(TEST_COUNTER1);
		shardedCounterService.decrement(TEST_COUNTER2);
		shardedCounterService.decrement(TEST_COUNTER2);
		shardedCounterService.decrement(TEST_COUNTER1);
		shardedCounterService.decrement(TEST_COUNTER2);

		assertEquals(2, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(3, shardedCounterService.getCounter(TEST_COUNTER2).getCount());

		shardedCounterService.decrement(TEST_COUNTER1);
		shardedCounterService.decrement(TEST_COUNTER2);
		shardedCounterService.decrement(TEST_COUNTER1);
		shardedCounterService.decrement(TEST_COUNTER2);
		shardedCounterService.decrement(TEST_COUNTER2);
		shardedCounterService.decrement(TEST_COUNTER1);
		shardedCounterService.decrement(TEST_COUNTER2);

		assertEquals(0, shardedCounterService.getCounter(TEST_COUNTER1).getCount());
		assertEquals(0, shardedCounterService.getCounter(TEST_COUNTER2).getCount());
	}

}
