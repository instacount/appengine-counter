/**
 * Copyright (C) 2016 Instacount Inc. (developers@instacount.io)
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
package io.instacount.appengine.counter.service;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import io.instacount.appengine.counter.CounterOperation;
import io.instacount.appengine.counter.data.CounterData;
import io.instacount.appengine.counter.data.CounterShardData;
import io.instacount.appengine.counter.service.ShardedCounterServiceConfiguration.Builder;

/**
 * Unit tests for incrementing a counter via {@link ShardedCounterServiceImpl}.
 *
 * @author David Fuelling
 */
public class ShardedCounterServiceShardIncrementInExistingTXTest extends ShardedCounterServiceShardIncrementTest
{
	private static final BigInteger BD_ONE = BigInteger.valueOf(1L);

	protected ShardedCounterService singleShardShardedCounterService;
	protected ShardedCounterServiceImpl impl;

	private CounterShardData testCounterShardData1 = new CounterShardData(TEST_COUNTER1, 0);
	private CounterShardData testCounterShardData2 = new CounterShardData(TEST_COUNTER2, 0);

	@Before
	public void setUp() throws Exception
	{
		super.setUp();

		final ShardedCounterServiceConfiguration config = new Builder().withNumInitialShards(1).build();
		this.singleShardShardedCounterService = new ShardedCounterServiceTxWrapper(super.memcache, config);
		impl = (ShardedCounterServiceImpl) this.singleShardShardedCounterService;

		// clear the cache
		this.clearAllCaches();
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
		 * @param requestedIncrementAmount
		 * @return
		 */
		@Override
		public CounterOperation increment(final String counterName, final long requestedIncrementAmount)
		{
			return ObjectifyService.ofy().transact(new Work<CounterOperation>()
			{
				@Override
				public CounterOperation run()
				{
					// 1.) Create a random CounterShardData for simulation purposes. It doesn't do anything except
					// to allow us to do something else in the Datastore in the same transactional context whilst
					// performing all unit tests. This effectively allows us to simulate a parent transactional context
					// occuring with some other data operation being performed against the database.
					final CounterShardData counterShardData = new CounterShardData(UUID.randomUUID().toString(), 1);
					ObjectifyService.ofy().save().entity(counterShardData);

					// 2.) Operate on the counter and return.
					return ShardedCounterServiceTxWrapper.super.increment(counterName, requestedIncrementAmount);
				}
			});
		}
	}

	/**
	 * An override of the parent-class test. There is a bug in the implementation when a counter is incremented in a
	 * parent-transaction and there is no value in memcache. In these cases, the counter will be off by one until the
	 * next cache flush. See Github #17 for more details.
	 */
	@Test
	@Override
	public void testIncrementDecrementInterleaving()
	{
		// Use "impl" to ensure that only 1 shard-per-counter exists.

		CounterOperation increment = impl.increment(TEST_COUNTER1, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		increment = impl.increment(TEST_COUNTER1, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		increment = impl.increment(TEST_COUNTER1, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		assertEquals(BigInteger.valueOf(3L), impl.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.valueOf(4L), impl.getCounter(TEST_COUNTER2).get().getCount());

		increment = impl.increment(TEST_COUNTER1, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		increment = impl.increment(TEST_COUNTER1, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		increment = impl.increment(TEST_COUNTER1, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		increment = impl.increment(TEST_COUNTER2, 1);
		assertThat(increment.getAppliedAmount(), is(1L));
		assertThat(increment.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		assertEquals(BigInteger.valueOf(6L), impl.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.valueOf(8L), impl.getCounter(TEST_COUNTER2).get().getCount());

		CounterOperation decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		assertEquals(BigInteger.valueOf(3L), impl.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.valueOf(4L), impl.getCounter(TEST_COUNTER2).get().getCount());

		decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));

		MemcacheServiceFactory.getMemcacheService().clearAll();

		assertEquals(BigInteger.ZERO, impl.getCounter(TEST_COUNTER1).get().getCount());
		assertEquals(BigInteger.ZERO, impl.getCounter(TEST_COUNTER2).get().getCount());

		decrement = impl.decrement(TEST_COUNTER1, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData1.getTypedKey()));

		decrement = impl.decrement(TEST_COUNTER2, 1);
		assertThat(decrement.getAppliedAmount(), is(1L));
		assertThat(decrement.getCounterShardDataKey(), is(this.testCounterShardData2.getTypedKey()));
	}

	// //////////
	// Increment
	// //////////
	// ExistingTx: F; ExistingCounter: F (Cache is F - always)
	// ExistingTx: F; ExistingCounter: T (Cache is F)
	// ExistingTx: F; ExistingCounter: T (Cache is T)
	// ExistingTx: T; ExistingCounter: F (Cache is F - always)
	// ExistingTx: T; ExistingCounter: T (Cache is F)
	// ExistingTx: T; ExistingCounter: T (Cache is T)

	/**
	 * Test an increment where there is no active parent transaction, and the counter does not yet exist (ExistingTx: F;
	 * ExistingCounter: F)
	 */
	@Test
	public void increment_NoTransactionActive_NoExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		this.singleShardShardedCounterService.increment(counterName, 1);

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BD_ONE));
		this.assertCounterShardValue(counterName, 1L);
	}

	/**
	 * Test an increment where there is no active parent transaction, but the counter does exist (ExistingTx: F;
	 * ExistingCounter: T)
	 */
	@Test
	public void increment_NoTransactionActive_ExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		this.impl.getOrCreateCounterData(counterName);

		this.clearAllCaches();

		this.shardedCounterService.increment(counterName, 1L);

		this.clearAllCaches();

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);
	}

	/**
	 * Test an increment where there is no active parent transaction, but the counter does exist (ExistingTx: F;
	 * ExistingCounter: T)
	 */
	@Test
	public void increment_NoTransactionActive_ExistingCounter_CounterCached()
	{
		final String counterName = UUID.randomUUID().toString();
		this.impl.getOrCreateCounterData(counterName);

		this.shardedCounterService.increment(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);
	}

	/**
	 * Test an increment where there is an active parent transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: F)
	 */
	@Test
	public void increment_TransactionActive_NoExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// Do something else as part of the TX.
				final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
					CounterData.key(UUID.randomUUID().toString()), 0);
				final CounterShardData counterShardData = new CounterShardData(counterShardDataKey);
				ObjectifyService.ofy().save().entity(counterShardData);

				// The actual test.
				singleShardShardedCounterService.increment(counterName, 10L);
			}
		});

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(10L)));
		this.assertCounterShardValue(counterName, 10L);
	}

	/**
	 * Test an increment where there is an active parent transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T)
	 */
	@Test
	public void increment_TransactionActive_ExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));

		this.clearAllCaches();

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// Do something else as part of the TX.
				final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
					CounterData.key(UUID.randomUUID().toString()), 0);
				final CounterShardData counterShardData = new CounterShardData(counterShardDataKey);
				ObjectifyService.ofy().save().entity(counterShardData);

				// The actual test.
				singleShardShardedCounterService.increment(counterName, 10L);
			}
		});

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(11L)));
		this.assertCounterShardValue(counterName, 11L);

		this.clearAllCaches();

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(11L)));
		this.assertCounterShardValue(counterName, 11L);
	}

	/**
	 * Test an increment where there is an active parent transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T)
	 */
	@Test
	public void increment_TransactionActive_ExistingCounter_CounterCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// Do something else as part of the TX.
				final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
					CounterData.key(UUID.randomUUID().toString()), 0);
				final CounterShardData counterShardData = new CounterShardData(counterShardDataKey);
				ObjectifyService.ofy().save().entity(counterShardData);

				// The actual test.
				singleShardShardedCounterService.increment(counterName, 10L);
			}
		});

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(11L)));
		this.assertCounterShardValue(counterName, 11L);
	}

	// Abort Parent Transaction Testing
	// ExistingTx: T; ExistingCounter: F (Cache is F - always)
	// ExistingTx: T; ExistingCounter: T (Cache is F)
	// ExistingTx: T; ExistingCounter: T (Cache is T)

	/**
	 * Test an increment where there is an active transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: F), and then the transaction aborts after the call to increment.
	 */
	@Test
	public void increment_Abort_TransactionActive_NoExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();

		try
		{
			// Perform another increment in a Work, but abort it before it can commit.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// Do something else as part of the TX.
					final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
						CounterData.key(UUID.randomUUID().toString()), 0);
					ObjectifyService.ofy().save().entity(counterShardDataKey);

					// The actual test.
					singleShardShardedCounterService.increment(counterName, 10L);

					throw new RuntimeException("Abort the Transaction!");
				}
			});
			fail();
		}
		catch (Exception e)
		{
			// Eat the Exception.
		}

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).isPresent(), is(false));
		this.assertCounterShardValue(counterName, null);
	}

	/**
	 * Test an increment where there is an active transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T), and then the transaction aborts after the call to increment.
	 */
	@Test
	public void increment_Abort_TransactionActive_ExistingCounter_CounterCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.ONE));
		this.assertCounterShardValue(counterName, 1L);

		try
		{
			// Perform another increment in a Work, but abort it before it can commit.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// Do something else as part of the TX.
					final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
						CounterData.key(UUID.randomUUID().toString()), 0);
					ObjectifyService.ofy().save().entity(counterShardDataKey);

					// The actual test.
					singleShardShardedCounterService.increment(counterName, 10L);

					throw new RuntimeException("Abort the Transaction!");
				}
			});
			fail();
		}
		catch (Exception e)
		{
			// Eat the Exception.
		}

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);
	}

	/**
	 * Test an increment where there is an active transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T), and then the transaction aborts after the call to increment.
	 */
	@Test
	public void increment_Abort_TransactionActive_ExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 1L);
		this.clearAllCaches();
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);

		this.clearAllCaches();

		try
		{
			// Perform another increment in a Work, but abort it before it can commit.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// Do something else as part of the TX.
					final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
						CounterData.key(UUID.randomUUID().toString()), 0);
					ObjectifyService.ofy().save().entity(counterShardDataKey);

					// The actual test.
					singleShardShardedCounterService.increment(counterName, 10L);

					throw new RuntimeException("Abort the Transaction!");
				}
			});
			fail();
		}
		catch (Exception e)
		{
			// Eat the Exception.
		}

		this.clearAllCaches();

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);
	}

	@Test
	public void incrementAndDecrement()
	{
		// Make sure the counter exists
		this.singleShardShardedCounterService.getCounter(TEST_COUNTER1);

		// Increment the counter's 1 shard so it has a count of 1.
		this.singleShardShardedCounterService.increment(TEST_COUNTER1, 1);
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).get().getCount(),
			is(BigInteger.valueOf(1L)));

		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(CounterData.key(TEST_COUNTER1), 0);
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
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).get().getCount(),
			is(BigInteger.valueOf(11L)));

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				singleShardShardedCounterService.decrement(TEST_COUNTER1, 10L);
			}
		});

		// Both increments should have succeeded
		counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();
		assertThat(counterShard, is(not(nullValue())));
		assertThat(counterShard.getCount(), is(1L));
		assertThat(this.singleShardShardedCounterService.getCounter(TEST_COUNTER1).get().getCount(), is(BigInteger.ONE));
	}

	// /////////////////////////
	// Decrement
	// //////////
	// ExistingTx: F; ExistingCounter: F (Cache is F - always)
	// ExistingTx: F; ExistingCounter: T (Cache is F)
	// ExistingTx: F; ExistingCounter: T (Cache is T)
	// ExistingTx: T; ExistingCounter: F (Cache is F - always)
	// ExistingTx: T; ExistingCounter: T (Cache is F)
	// ExistingTx: T; ExistingCounter: T (Cache is T)

	/**
	 * Test a decrement where there is no active parent transaction, and the counter does not yet exist (ExistingTx: F;
	 * ExistingCounter: F), and nothing is in the cache.
	 */
	@Test
	public void decrement_NoTransactionActive_NoExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		this.singleShardShardedCounterService.decrement(counterName, 1);

		// clear the cache
		memcache.clearAll();

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(-1L)));
		this.assertCounterShardValue(counterName, -1L);
	}

	/**
	 * Test an increment where there is no active parent transaction, but the counter does exist (ExistingTx: F;
	 * ExistingCounter: T), and nothing is in the cache.
	 */
	@Test
	public void decrement_NoTransactionActive_ExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		this.singleShardShardedCounterService.increment(counterName, 1L);

		// clear the cache
		memcache.clearAll();

		this.singleShardShardedCounterService.decrement(counterName, 1);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.ZERO));
		this.assertCounterShardValue(counterName, 0L);
	}

	/**
	 * Test an increment where there is no active parent transaction, but the counter does exist (ExistingTx: F;
	 * ExistingCounter: T), and the count is in the cache.
	 */
	@Test
	public void decrement_NoTransactionActive_ExistingCounter_CounterIsCached()
	{
		final String counterName = UUID.randomUUID().toString();
		this.singleShardShardedCounterService.increment(counterName, 10L);

		this.singleShardShardedCounterService.decrement(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(9L)));
		this.assertCounterShardValue(counterName, 9L);

		this.singleShardShardedCounterService.decrement(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(8L)));
		this.assertCounterShardValue(counterName, 8L);
	}

	/**
	 * Test an increment where there is an active parent transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: F)
	 */
	@Test
	public void decrement_TransactionActive_NoExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// Do something else as part of the TX.
				final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
					CounterData.key(UUID.randomUUID().toString()), 0);
				final CounterShardData counterShardData = new CounterShardData(counterShardDataKey);
				ObjectifyService.ofy().save().entity(counterShardData);

				// The actual test.
				singleShardShardedCounterService.increment(counterName, 10L);
			}
		});

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.TEN));
		this.assertCounterShardValue(counterName, 10L);
	}

	/**
	 * Test an increment where there is an active parent transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T)
	 */
	@Test
	public void decrement_TransactionActive_ExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 10L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.TEN));
		this.assertCounterShardValue(counterName, 10L);

		// clear the cache.
		memcache.clearAll();

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(1, new VoidWork()
		{
			@Override
			public void vrun()
			{
				// The actual test.
				CounterOperation amountDecrement = singleShardShardedCounterService.decrement(counterName, 1L);
				assertThat(amountDecrement.getAppliedAmount(), is(1L));
			}
		});

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(9L)));
		this.assertCounterShardValue(counterName, 9L);

		memcache.clearAll();

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(9L)));
		this.assertCounterShardValue(counterName, 9L);
	}

	/**
	 * Test an increment where there is an active parent transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T)
	 */
	@Test
	public void decrement_TransactionActive_ExistingCounter_CounterCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 10L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.TEN));
		this.assertCounterShardValue(counterName, 10L);

		// Perform another increment in a Work, but abort it before it can commit.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// The actual test.
				singleShardShardedCounterService.decrement(counterName, 1L);
			}
		});

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(9L)));
		this.assertCounterShardValue(counterName, 9L);
	}

	// Abort
	// ExistingTx: T; ExistingCounter: F (Cache is F - always)
	// ExistingTx: T; ExistingCounter: T (Cache is F)
	// ExistingTx: T; ExistingCounter: T (Cache is T)

	/**
	 * Test an increment where there is an active transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: F), and then the transaction aborts after the call to increment.
	 */
	@Test
	public void decrement_Abort_TransactionActive_NoExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();

		try
		{
			// Perform another increment in a Work, but abort it before it can commit.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// The actual test.
					CounterOperation decrementAmount = singleShardShardedCounterService.decrement(counterName, 10L);
					assertThat(decrementAmount.getAppliedAmount(), is(10L));
					throw new RuntimeException("Abort the Transaction!");
				}
			});
		}
		catch (Exception e)
		{
			// Eat the Exception.
		}

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).isPresent(), is(false));
		this.assertCounterShardValue(counterName, null);
	}

	/**
	 * Test an increment where there is an active transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T), and then the transaction aborts after the call to increment.
	 */
	@Test
	public void decrement_Abort_TransactionActive_ExistingCounter_CounterCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 1L);
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.ONE));
		this.assertCounterShardValue(counterName, 1L);

		try
		{
			// Perform another increment in a Work, but abort it before it can commit.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// Do something else as part of the TX.
					final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
						CounterData.key(UUID.randomUUID().toString()), 0);
					ObjectifyService.ofy().save().entity(counterShardDataKey);

					// The actual test.
					CounterOperation decrementAmount = singleShardShardedCounterService.decrement(counterName, 10L);
					assertThat(decrementAmount.getAppliedAmount(), is(1L));
					throw new RuntimeException("Abort the Transaction!");
				}
			});
		}
		catch (Exception e)
		{
			// Eat the Exception.
		}

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(), is(BigInteger.ONE));
		this.assertCounterShardValue(counterName, 1L);
	}

	/**
	 * Test an increment where there is an active transaction, but the counter does not exist (ExistingTx: T;
	 * ExistingCounter: T), and then the transaction aborts after the call to increment.
	 */
	@Test
	public void decrement_Abort_TransactionActive_ExistingCounter_CounterNotCached()
	{
		final String counterName = UUID.randomUUID().toString();
		singleShardShardedCounterService.increment(counterName, 1L);
		this.clearAllCaches();
		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);

		this.clearAllCaches();

		try
		{
			// Perform another increment in a Work, but abort it before it can commit.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// Do something else as part of the TX.
					final Key<CounterShardData> counterShardDataKey = CounterShardData.key(
						CounterData.key(UUID.randomUUID().toString()), 0);
					ObjectifyService.ofy().save().entity(counterShardDataKey);

					// The actual test.
					final CounterOperation decrementAmount = singleShardShardedCounterService.decrement(counterName,
						10L);
					assertThat(decrementAmount.getAppliedAmount(), is(1L));
					throw new RuntimeException("Abort the Transaction!");
				}
			});
		}
		catch (Exception e)
		{
			// Eat the Exception.
		}

		this.clearAllCaches();

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);

		assertThat(this.singleShardShardedCounterService.getCounter(counterName).get().getCount(),
			is(BigInteger.valueOf(1L)));
		this.assertCounterShardValue(counterName, 1L);
	}

	// //////////////////////////////
	// Private Helpers
	// //////////////////////////////

	/**
	 * Asssert that the counter named {@code counterName} has an actual value that matches {@code expectedValue}.
	 * 
	 * @param counterName
	 * @param expectedValue
	 */
	private void assertCounterShardValue(final String counterName, final Long expectedValue)
	{
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(CounterData.key(counterName), 0);
		final CounterShardData counterShard = ObjectifyService.ofy().load().key(counterShardDataKey).now();

		if (expectedValue == null)
		{
			assertThat(counterShard, is(nullValue()));
		}
		else
		{
			assertThat(counterShard, is(not(nullValue())));
			assertThat(counterShard.getCount(), is(expectedValue));
		}
	}

	/**
	 * Clear memcache and Objectify session cache.
	 */
	private void clearAllCaches()
	{
		memcache.clearAll();
		ObjectifyService.ofy().flush();
		ObjectifyService.ofy().clear();
	}
}
