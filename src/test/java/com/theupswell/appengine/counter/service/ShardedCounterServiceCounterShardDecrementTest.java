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
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.googlecode.objectify.ObjectifyService;
import com.theupswell.appengine.counter.CounterOperation;
import com.theupswell.appengine.counter.CounterOperation.CounterOperationType;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * Unit tests for decrementing counters via {@link com.theupswell.appengine.counter.service.ShardedCounterServiceImpl}.
 * 
 * @author David Fuelling
 */
public class ShardedCounterServiceCounterShardDecrementTest extends
		com.theupswell.appengine.counter.service.AbstractShardedCounterServiceTest
{
	private static final Logger logger = Logger.getLogger(ShardedCounterServiceCounterShardDecrementTest.class
		.getName());

	@Before
	public void setUp() throws Exception
	{
		super.setUp();

		final MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
		final ShardedCounterServiceConfiguration shardedCounterServiceConfiguration = new ShardedCounterServiceConfiguration.Builder()
			.withNegativeCountAllowed(ShardedCounterServiceConfiguration.ALLOW_NEGATIVE_COUNTS).build();
		shardedCounterService = new ShardedCounterServiceImpl(memcacheService, shardedCounterServiceConfiguration);
	}

	@After
	public void tearDown()
	{
		super.tearDown();
	}

	// /////////////////////////
	// Unit Tests
	// /////////////////////////

	@Test(expected = NullPointerException.class)
	public void testIncrement_NullName() throws InterruptedException
	{
		shardedCounterService.decrement(null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrement_BlankName() throws InterruptedException
	{
		shardedCounterService.decrement("", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrement_EmptyName() throws InterruptedException
	{
		shardedCounterService.decrement("  ", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrement_NegativeIncrement() throws InterruptedException
	{
		shardedCounterService.decrement(TEST_COUNTER1, -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrement_ZeroIncrement() throws InterruptedException
	{
		shardedCounterService.decrement(TEST_COUNTER1, 0);
	}

	@Test(expected = RuntimeException.class)
	public void testDecrement_CounterIsBeingDeleted() throws InterruptedException
	{
		// Store this in the Datastore to trigger the exception below...
		CounterData counterData = new CounterData(TEST_COUNTER1, 1);
		counterData.setCounterStatus(CounterStatus.DELETING);
		ObjectifyService.ofy().save().entity(counterData).now();

		shardedCounterService.decrement(TEST_COUNTER1, 1);
	}

	@Test
	public void testDecrement_DefaultNumShards() throws InterruptedException
	{
		shardedCounterService = new ShardedCounterServiceImpl();
		doCounterDecrementAssertions(TEST_COUNTER1, 50);
	}

	@Test
	public void testDecrement_Specifiy1Shard() throws InterruptedException
	{
		shardedCounterService = initialShardedCounterService(1);
		doCounterDecrementAssertions(TEST_COUNTER1, 50);
	}

	@Test
	public void testDecrement_Specifiy3Shard() throws InterruptedException
	{
		shardedCounterService = initialShardedCounterService(3);
		doCounterDecrementAssertions(TEST_COUNTER1, 50);
	}

	@Test
	public void testDecrement_Specifiy10Shards() throws InterruptedException
	{
		shardedCounterService = initialShardedCounterService(10);
		doCounterDecrementAssertions(TEST_COUNTER1, 50);
	}

	@Test
	public void testDecrementAll() throws InterruptedException
	{
		// Use 3 shards
		shardedCounterService = initialShardedCounterService(3);
		shardedCounterService.increment(TEST_COUNTER1, 10);
		shardedCounterService.increment(TEST_COUNTER2, 10);

		// Decrement 20
		for (int i = 0; i < 10; i++)
		{
			logger.info("Decrement #" + i + " of 9 for counter 1");
			shardedCounterService.decrement(TEST_COUNTER1, 1);
			logger.info("Decrement #" + i + " of 9 for counter 2");
			shardedCounterService.decrement(TEST_COUNTER2, 1);
		}

		assertEquals(BigInteger.ZERO, shardedCounterService.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.ZERO, shardedCounterService.getCounter(TEST_COUNTER2).get().getCount());
	}

	@Test
	public void testDecrementNegative_AllowedNegative() throws InterruptedException
	{
		// Use 3 shards
		shardedCounterService = initialShardedCounterService(3,
			ShardedCounterServiceConfiguration.ALLOW_NEGATIVE_COUNTS);

		shardedCounterService.increment(TEST_COUNTER1, 10);
		shardedCounterService.increment(TEST_COUNTER2, 10);

		// Decrement 20
		for (int i = 0; i < 20; i++)
		{
			shardedCounterService.decrement(TEST_COUNTER1, 1);
			shardedCounterService.decrement(TEST_COUNTER2, 1);
		}

		assertEquals(BigInteger.valueOf(-10L), shardedCounterService.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.valueOf(-10L), shardedCounterService.getCounter(TEST_COUNTER2).get().getCount());
	}

	/**
	 * Disallowed negative counts are no longer a feature of the library.
	 * 
	 * @throws InterruptedException
	 * @deprecated
	 */
	@Test
	@Ignore
	@Deprecated
	public void testDecrementNegative_DisallowedNegative() throws InterruptedException
	{
		// Use 3 shards
		shardedCounterService = initialShardedCounterService(3,
			ShardedCounterServiceConfiguration.DISALLOW_NEGATIVE_COUNTS);

		shardedCounterService.increment(TEST_COUNTER1, 10);
		shardedCounterService.increment(TEST_COUNTER2, 10);

		// Decrement 20
		for (int i = 0; i < 20; i++)
		{
			try
			{
				shardedCounterService.decrement(TEST_COUNTER1, 1);
			}
			catch (IllegalArgumentException ae)
			{
				// Do nothing.
			}

			try
			{
				shardedCounterService.decrement(TEST_COUNTER2, 1);
			}
			catch (IllegalArgumentException ae)
			{
				// Do nothing.
			}
		}

		assertEquals(BigInteger.valueOf(0L), shardedCounterService.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.valueOf(0L), shardedCounterService.getCounter(TEST_COUNTER2).get().getCount());
	}

	// Tests counters with up to 15 shards and excerises each shard
	// (statistically, but not perfectly)
	@Test
	public void testDecrement_XShards() throws InterruptedException
	{
		for (int i = 1; i <= 15; i++)
		{
			shardedCounterService = this.initialShardedCounterService(i);

			doCounterDecrementAssertions(TEST_COUNTER1 + "-" + i, 15);
		}
	}

	@Test
	public void testDecrementResult()
	{
		this.shardedCounterService.increment(TEST_COUNTER1, 1, 2, UUID.randomUUID());
		final UUID decrementUuid = UUID.randomUUID();
		final CounterOperation result = this.shardedCounterService.decrement(TEST_COUNTER1, 1, 2, decrementUuid);

		assertThat(result.getAppliedAmount(), is(1L));
		assertThat(result.getOperationUuid(), is(decrementUuid));
		assertThat(result.getCounterOperationType(), is(CounterOperationType.DECREMENT));
		assertThat(result.getCounterShardDataKey(), is(CounterShardData.key(CounterData.key(TEST_COUNTER1), 2)));
		assertThat(result.getCreationDateTime(), is(not(nullValue())));
	}

	private void doCounterDecrementAssertions(String counterName, int numIterations) throws InterruptedException
	{
		shardedCounterService.increment(counterName + "-1", numIterations);

		// ////////////////////////
		// With Memcache Caching
		// ////////////////////////
		for (int i = 1; i <= numIterations; i++)
		{
			shardedCounterService.decrement(counterName + "-1", 1);
			assertEquals(BigInteger.valueOf(numIterations - i), shardedCounterService.getCounter(counterName + "-1")
				.get().getCount());
		}

		// /////////////////////////
		// Reset the counter
		// /////////////////////////
		shardedCounterService.increment(counterName + "-1", numIterations);
		assertEquals(BigInteger.valueOf(numIterations), shardedCounterService.getCounter(counterName + "-1").get()
			.getCount());

		// ////////////////////////
		// No Memcache Caching
		// ////////////////////////
		for (int i = 1; i <= numIterations; i++)
		{
			if (this.isMemcacheAvailable())
			{
				this.memcache.clearAll();
			}
			shardedCounterService.decrement(counterName + "-1", 1);
			if (this.isMemcacheAvailable())
			{
				this.memcache.clearAll();
			}
			assertEquals(BigInteger.valueOf(numIterations - i), shardedCounterService.getCounter(counterName + "-1")
				.get().getCount());
		}

		// /////////////////////////
		// Reset the counter
		// /////////////////////////
		shardedCounterService.increment(counterName + "-1", numIterations);
		assertEquals(BigInteger.valueOf(numIterations), shardedCounterService.getCounter(counterName + "-1").get()
			.getCount());

		// ////////////////////////
		// Memcache Cleared BEFORE Decrement Only
		// ////////////////////////
		for (int i = 1; i <= numIterations; i++)
		{
			if (this.isMemcacheAvailable())
			{
				this.memcache.clearAll();
			}
			shardedCounterService.decrement(counterName + "-1", 1);
			assertEquals(BigInteger.valueOf(numIterations - i), shardedCounterService.getCounter(counterName + "-1")
				.get().getCount());
		}

		// /////////////////////////
		// Reset the counter
		// /////////////////////////
		shardedCounterService.increment(counterName + "-1", numIterations);
		assertEquals(BigInteger.valueOf(numIterations), shardedCounterService.getCounter(counterName + "-1").get()
			.getCount());

		// ////////////////////////
		// Memcache Cleared AFTER Decrement Only
		// ////////////////////////
		for (int i = 1; i <= numIterations; i++)
		{
			shardedCounterService.decrement(counterName + "-1", 1);
			if (this.isMemcacheAvailable())
			{
				this.memcache.clearAll();
			}
			assertEquals(BigInteger.valueOf(numIterations - i), shardedCounterService.getCounter(counterName + "-1")
				.get().getCount());
		}

	}
}
