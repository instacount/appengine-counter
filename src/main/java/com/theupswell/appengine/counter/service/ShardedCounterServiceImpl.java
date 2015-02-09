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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.memcache.InvalidValueException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;
import com.google.appengine.api.memcache.MemcacheService.SetPolicy;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.CounterBuilder;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * A durable implementation of a {@link ShardedCounterService} that provides counter increment, decrement, and delete
 * functionality. This implementation is is backed by one or more Datastore "shard" entities which each hold a discrete
 * count in order to provide for high throughput. When aggregated, the sum total of all CounterShard entity counts is
 * the value of the counter. See the google link below for more details on Sharded Counters in appengine. Note that
 * CounterShards cannot go negative, and there is no difference between a counter being "zero" and a counter not
 * existing.<br/>
 * <br/>
 * Note that this implementation is capable of incrementing/decrementing various counter shard counts but does not
 * automatically increase or reduce the <b>number</b> of shards for a given counter in response to load.<br/>
 * <br/>
 * All datastore operations are performed using Objectify.<br/>
 * <br/>
 * <br/>
 * <b>Incrementing a Counter</b><br/>
 * When incrementing, a random shard is selected to prevent a single shard from being written to too frequently.<br/>
 * <br/>
 * <b>Decrementing a Counter</b><br/>
 * This implementation does not support negative counts, so CounterShard counts can not go below zero. Thus, when
 * decrementing, a random shard is selected to prevent a single shard from being written to too frequently. However, if
 * a particular shard cannot be decremented then other shards are tried until all shards have been tried. If no shard
 * can be decremented, then the decrement function is considered complete, even though nothing was decremented. Because
 * of this, it is possible that a request to reduce a counter by more than its available count will succeed with a
 * lesser count having been reduced. <b>Getting the Count</b><br/>
 * Aggregate Counter counter lookups are first attempted using Memcache. If the counter value is not in the cache, then
 * the shards are read from the datastore and accumulated to reconstruct the current count. This operation has a cost of
 * O(numShards), or O(N). Increase the number of shards to improve counter increment throughput, but beware that this
 * has a cost - it makes counter lookups from the Datastore more expensive.<br/>
 * <br/>
 * <b>Throughput</b><br/>
 * As an upper-bound calculation of throughput and shard-count, the Psy "Gangham Style" youtube video (arguably one of
 * the most viral videos of all time) reached 750m views in approximately 60 days. If that video's 'hit-counter' was
 * using appengine-counter as its underlying implementation, then the counter would have needed to sustain an increment
 * rate of 145 updates per second for 60 days. Since each CounterShard could have provided up to 5 updates per second
 * (this seems to be the average indicated by the appengine team and in various documentation), then the counter would
 * have required at least 29 CounterShard entities, which in the grand scheme of the Appengine Datastore seems prett
 * small. In reality, a counter with this much traffic would not need to be highly consistent, but it could have been
 * using appengine-counter.<br/>
 * <br/>
 * <b>Future Improvements</b><br/>
 * <ul>
 * <li><b>CounterShard Expansion</b>: A shard-expansion mechanism can be envisioned to increase the number of
 * CounterShard entities for a particular Counter when load increases to a specified amount for a given Counter.</li>
 * <li><b>CounterShard Contraction</b>: A shard-reduction mechanism can be envisioned to aggregate multiple shards (and
 * their counts) into fewer shards to improve datastore counter lookup performance when Counter load falls below some
 * threshold.</li>
 * <li><b>Counter Reset</b>: Reset a counter to zero by resetting all counter shards 'counts' to zero. This would need
 * to be, by nature of this implementation, async.</li>
 * </ul>
 *
 * @author David Fuelling
 * @see "https://developers.google.com/appengine/articles/sharding_counters"
 */
public class ShardedCounterServiceImpl implements ShardedCounterService
{
	private static final Logger logger = Logger.getLogger(ShardedCounterServiceImpl.class.getName());

	// Helper constant for counterName keys.
	public static final String COUNTER_NAME = "counterName";

	/**
	 * A random number generating, for distributing writes across shards.
	 */
	protected final Random generator = new Random();

	protected final MemcacheService memcacheService;
	protected final CapabilitiesService capabilitiesService;
	protected final ShardedCounterServiceConfiguration config;

	// /////////////////////////////
	// Constructors
	// /////////////////////////////

	/**
	 * Default Constructor for Dependency-Injection that uses {@link MemcacheServiceFactory} to construct the
	 * {@link MemcacheService} and {@link CapabilitiesServiceFactory} to construct the {@link CapabilitiesService}.
	 * dependency for this service.
	 */
	public ShardedCounterServiceImpl()
	{
		this(MemcacheServiceFactory.getMemcacheService(), CapabilitiesServiceFactory.getCapabilitiesService());
	}

	/**
	 * Default Constructor for Dependency-Injection that uses a default number of counter shards (set to 1) and a
	 * default configuration per {@link ShardedCounterServiceConfiguration#defaultConfiguration}.
	 *
	 * @param memcacheService
	 * @param capabilitiesService
	 */
	public ShardedCounterServiceImpl(final MemcacheService memcacheService,
			final CapabilitiesService capabilitiesService)
	{
		this(memcacheService, capabilitiesService, ShardedCounterServiceConfiguration.defaultConfiguration());
	}

	/**
	 * Default Constructor for Dependency-Injection.
	 *
	 * @param memcacheService
	 * @param capabilitiesService
	 * @param config The configuration for this service
	 */
	public ShardedCounterServiceImpl(final MemcacheService memcacheService,
			final CapabilitiesService capabilitiesService, final ShardedCounterServiceConfiguration config)
	{
		Preconditions.checkNotNull(memcacheService, "Invalid memcacheService!");
		Preconditions.checkNotNull(capabilitiesService, "Invalid capabilitiesService!");
		Preconditions.checkNotNull(config);

		this.memcacheService = memcacheService;
		this.capabilitiesService = capabilitiesService;
		this.config = config;

		Preconditions.checkArgument(config.getNumInitialShards() > 0,
			"Number of Shards for a new CounterData must be greater than 0!");
		if (config.getRelativeUrlPathForDeleteTaskQueue() != null)
		{
			// The relativeUrlPathForDeleteTaskQueue may be null, but if it's non-null, then it must not be blank.
			Preconditions.checkArgument(!StringUtils.isBlank(config.getRelativeUrlPathForDeleteTaskQueue()),
				"Must be null (for the Default Queue) or a non-blank String!");
		}
	}

	// /////////////////////////////
	// Retrieval Functions
	// /////////////////////////////

	/**
	 * The cache will expire after {@code defaultExpiration} seconds, so the counter will be accurate after a minute
	 * because it performs a load from the datastore.
	 *
	 * @param counterName
	 * @return
	 */
	@Override
	public Counter getCounter(final String counterName)
	{
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");

		// We always load the CounterData from the Datastore (or its Objectify
		// cache), but we sometimes return the cached count value.
		final CounterData counterData = this.getOrCreateCounterData(counterName);
		// If the counter is DELETING, then its count is always 0!
		if (CounterData.CounterStatus.DELETING == counterData.getCounterStatus())
		{
			return new CounterBuilder(counterData).withCount(0L).build();
		}

		final String memCacheKey = this.assembleCounterKeyforMemcache(counterName);
		final Long cachedCounterCount = this.memcacheSafeGet(memCacheKey);

		if (cachedCounterCount != null)
		{
			// /////////////////////////////////////
			// The count was found in memcache, so return it.
			// /////////////////////////////////////
			if (getLogger().isLoggable(Level.FINE))
			{
				getLogger().log(Level.FINE,
					"Cache Hit for Counter Named \"" + counterName + "\": value=" + cachedCounterCount);
			}
			return new CounterBuilder(counterData).withCount(cachedCounterCount).build();
		}
		else
		{
			// /////////////////////////////////////
			// The count was NOT found in memcache!
			// /////////////////////////////////////

			if (getLogger().isLoggable(Level.FINE))
			{
				getLogger().log(
					Level.FINE,
					"Cache Miss for CounterData Named \"" + counterName + "\": value=" + cachedCounterCount
						+ ".  Checking Datastore instead!");
				getLogger().log(
					Level.FINE,
					"Aggregating counts from " + counterData.getNumShards()
						+ " CounterDataShards for CounterData named '" + counterData.getCounterName() + "'!");
			}

			// ///////////////////
			// Assemble a List of CounterShardData Keys to retrieve in parallel!
			final List<Key<CounterShardData>> keysToLoad = Lists.newArrayList();
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				final Key<CounterShardData> counterShardKey = CounterShardData.key(counterData.getCounterName(), i);
				keysToLoad.add(counterShardKey);
			}

			long sum = 0;

			// For added performance, we could spawn multiple threads to wait for each value to be returned from the
			// DataStore, and then aggregate that way. However, the simple summation below is not very expensive, so
			// creating multiple threads to get each value would probably be overkill. Just let objectify do this for
			// us, even though we have to wait for all entities to return before summation begins.

			// No TX - get is Strongly consistent by default, and we will exceed the TX limit for high-shard-count
			// counters if we try to do this in a TX.
			final Map<Key<CounterShardData>, CounterShardData> counterShardDatasMap = ObjectifyService.ofy()
				.transactionless().load().keys(keysToLoad);
			final Collection<CounterShardData> counterShardDatas = counterShardDatasMap.values();
			for (CounterShardData counterShardData : counterShardDatas)
			{
				if (counterShardData != null)
				{
					sum += counterShardData.getCount();
				}
			}

			if (getLogger().isLoggable(Level.FINE))
			{
				getLogger().log(
					Level.FINE,
					"The Datastore is reporting a count of " + sum + " for CounterData \""
						+ counterData.getCounterName() + "\" count.  Resetting memcache count to " + sum
						+ " for this counter name");
			}

			try
			{
				memcacheService.put(memCacheKey, new Long(sum), config.getDefaultExpiration(), SetPolicy.SET_ALWAYS);
			}
			catch (MemcacheServiceException mse)
			{
				// Do nothing. The method will still return even though memcache is not available.
			}

			return new CounterBuilder(counterData).withCount(sum).build();
		}
	}

	/**
	 * NOTE: We don't allow the counter's "count" to be updated by this method. Instead, {@link #increment} and
	 * {@link #decrement} should be used.
	 *
	 * @param incomingCounter
	 */
	@Override
	public void updateCounterDetails(final Counter incomingCounter)
	{
		Preconditions.checkNotNull(incomingCounter);

		// First, assert the counter is in a proper state. If done consistently (i.e., in a TX, then this will function
		// as an effective CounterData lock).
		// Second, Update the counter details.

		ObjectifyService.ofy().transact(new Work<Void>()
		{
			@Override
			public Void run()
			{
				// First, load the incomingCounter from the datastore via transaction get to ensure it has the proper
				// state.
				final CounterData counterDataInDatastore = getOrCreateCounterData(incomingCounter.getCounterName());
				assertCounterDetailsMutatable(counterDataInDatastore.getCounterName(),
					counterDataInDatastore.getCounterStatus());

				// NOTE: READ_ONLY_COUNT status means the count can't be incremented/decremented. However, it's details
				// can still be mutated.

				// NOTE: The counterName/counterId may not change!

				// Update the Description
				counterDataInDatastore.setCounterDescription(incomingCounter.getCounterDescription());

				// Update the numShards. Aside from setting this value, nothing explicitly needs to happen in the
				// datastore since shards will be created when a counter in incremented (if the shard doesn't already
				// exist). However, if the number of shards is being reduced, then throw an exception since this
				// requires counter shard reduction and some extra thinking. We can't allow the shard-count to go down
				// unless we collapse the entire counter's shards into a single shard or zero, and it's ambiguous if
				// this is even required. Note that if we allow this the numShards value to decrease without capturing
				// the count from any of the shards that might no longer be used, then we might lose counts from the
				// shards that would no longer be factored into the #getCount method.
				if (incomingCounter.getNumShards() < counterDataInDatastore.getNumShards())
				{
					throw new RuntimeException(
						"Reducing the number of counter shards is not currently allowed!  See https://github.com/theupswell/appengine-counter/issues/4 for more details.");
				}

				counterDataInDatastore.setNumShards(incomingCounter.getNumShards());

				// The Exception above disallows any invalid states.
				counterDataInDatastore.setCounterStatus(incomingCounter.getCounterStatus());

				// Update the counter in the datastore.
				ObjectifyService.ofy().save().entity(counterDataInDatastore).now();

				// return this to satisfy Java...
				return null;
			}
		});
	}

	// /////////////////////////////
	// Increment Functions
	// /////////////////////////////

	@Override
	public Counter increment(final String counterName)
	{
		return this.increment(counterName, 1L);
	}

	@Override
	public Counter increment(final String counterName, final long amount)
	{
		// ///////////
		// Precondition Checks
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "CounterData increments must be positive numbers!");

		// Create the Work to be done for this increment, which will be done inside of a TX. See
		// "https://developers.google.com/appengine/docs/java/datastore/transactions#Java_Isolation_and_consistency"
		final Work<Long> atomicIncrementShardWork = new IncrementShardWork(counterName, amount);

		// Note that this operation is idempotent from the perspective of a ConcurrentModificationException. In that
		// case, the increment operation will fail will not have been applied. An Objectify retry of the increment will
		// occur in a new transaction, and the increment will only ever happen
		// once (if the Appengine datastore is functioning properly).
		//
		// WARNING: Be aware that per the GAE docs, in certain rare cases "If your application receives an exception
		// when committing a transaction, it
		// does not always mean that the transaction failed. You can receive DatastoreTimeoutException or
		// DatastoreFailureException exceptions in cases where transactions have been committed and eventually will
		// be applied successfully." See more at
		// https://cloud.google.com/appengine/docs/java/datastore/transactions. In these cases, it's possible that
		// the increment will actually succeed on a particular shard, and it won't be easily discernable if
		// the increment should be retried. This problem is compounded for high-load counters. For more details, see
		// https://github.com/theupswell/appengine-counter/issues/15. Fortunately, Objectify will only retry if a
		// ConcurrentModification is encounterd. In cases of a DatastoreTimeoutException or
		// DatastoreFailureException
		// exception, Objectify will not retry (though the increment may have succeeded).

		// We use the "amountIncrementedInTx" to pause this thread until the work inside of "atomicIncrementShardWork"
		// completes. This is because we don't want to increment memcache (below) until after that point.
		Long amountIncrementedInTx = ObjectifyService.ofy().transactNew(atomicIncrementShardWork);

		// /////////////////
		// Increment this counter in memcache atomically, with retry until it succeeds (with some governor). If this
		// fails, it's ok because memcache is merely a cache of the actual count data, and will eventually become
		// accurate when the cache is reloaded via a call to getCount.
		// /////////////////
		this.incrementMemcacheAtomic2(counterName, amountIncrementedInTx.longValue());

		// return #getCount because this will either return the memcache value or get the actual count from the
		// Datastore, which will do the same thing.
		return getCounter(counterName);
	}

	// Deprecated. See https://github.com/theupswell/appengine-counter/issues/17
	@Override
	public Counter increment(final String counterName, final long amount, boolean isolatedTransactionContext)
	{
		// ///////////
		// Precondition Checks
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "CounterData increments must be positive numbers!");

		if (isolatedTransactionContext)
		{
			return this.increment(counterName, amount);
		}
		else
		{
			// Create the Work to be done for this increment, which will be done inside of a TX. See
			// "https://developers.google.com/appengine/docs/java/datastore/transactions#Java_Isolation_and_consistency"
			final Work<Long> atomicIncrementShardWork = new IncrementShardWork(counterName, amount);

			// Perform the increment inside of a potentially active parent transaction, if any.
			// The caller of this increment method is asking for this increment to take place in either a new
			// transaction or the parent transaction context. In the case that this method is being executed inside of
			// an existing transaction, the retry semantics of that transaction will be in effect. However,
			// Callers should know that, per the GAE docs, "In extremely rare cases, the transaction is fully committed
			// even if a transaction returns a timeout or internal error exception. For this reason, it's best to make
			// transactions idempotent whenever possible." In this case, it's possible that the increment
			// succeeds, but an error is thrown that triggers the retry for the parent transaction.

			// We use the "amountIncrementedInTx" to pause this thread until the work inside of
			// "atomicIncrementShardWork" completes. This is because we don't want to increment memcache (below) until
			// after a real increment in the datastore has taken place.
			final Long amountIncrementedInTx = ObjectifyService.ofy().transact(atomicIncrementShardWork);

			// /////////////////
			// Increment this counter in memcache atomically, with retry until it succeeds (with some governor). If this
			// fails, it's ok because memcache is merely a cache of the actual count data, and will eventually become
			// accurate when the cache is reloaded via a call to getCount.
			// /////////////////
			this.incrementMemcacheAtomic2(counterName, amountIncrementedInTx.longValue());

			// return #getCount because this will either return the memcache value or get the actual count from the
			// Datastore, which will do the same thing.
			return getCounter(counterName);
		}
	}

	@Override
	public void incrementInExistingTX(String counterName, long amount)
	{
		// The increment functionality of this forwarding call will work properly. We discard the results of that call,
		// however, because in certain cases that result cannot be relied upon. See Github issue #17 for more details.
		this.increment(counterName, amount);
	}

	/**
	 * A private implementation of {@link Work} that increments a shard by a specified non-negative {@code amount}.
	 */
	@VisibleForTesting
	final class IncrementShardWork implements Work<Long>
	{
		// We begin this transactional unit of work with just the counter name so we can guarantee all data in question
		// is consistent, and allow a counter shard key to vary based upon the number of shards indicated in
		// CounterData, which won't be available in a consistent manner until we enter the transaction.
		private final String counterName;
		private final long amount;

		/**
		 * Required-Args Constructor.
		 *
		 * @param counterName
		 * @param amount
		 */
		IncrementShardWork(final String counterName, final long amount)
		{
			Preconditions.checkNotNull(counterName);
			Preconditions.checkArgument(!StringUtils.isBlank(counterName));
			this.counterName = counterName;

			Preconditions.checkArgument(amount > 0);
			this.amount = amount;
		}

		/**
		 * NOTE: In order for this to work properly, the CounterShardData must be gotten, created, and updated all in
		 * the same transaction in order to remain consistent (in other words, it must be atomic).
		 *
		 * @return
		 */
		@Override
		public Long run()
		{
			// Do this inside of the TX so that we guarantee no other thread has changed the counterData in question.
			final CounterData counterData = getOrCreateCounterData(counterName);

			// Increments/Decrements can only occur on Counters with a counterStatus of AVAIALBLE.
			assertCounterAmountMutatable(counterData.getCounterName(), counterData.getCounterStatus());

			final String counterName = counterData.getCounterName();

			// Find how many shards are in this counter.
			final int currentNumShards = counterData.getNumShards();

			// Choose the shard randomly from the available shards.
			final int shardNumber = generator.nextInt(currentNumShards);

			final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterName, shardNumber);

			// Load the Shard from the DS.
			CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardDataKey).now();
			if (counterShardData == null)
			{
				// Create it in the Datastore
				counterShardData = new CounterShardData(counterName, shardNumber);
			}

			// Increment the count by {amount}
			counterShardData.setCount(counterShardData.getCount() + amount);

			if (getLogger().isLoggable(Level.FINE))
			{
				getLogger().log(
					Level.FINE,
					"Saving CounterShardData" + shardNumber + " for CounterData \"" + counterName + "\" with count "
						+ counterShardData.getCount());
			}

			// Persist the updated value.
			ObjectifyService.ofy().save().entity(counterShardData).now();
			return new Long(amount);
		}
	}

	// /////////////////////////////
	// Decrementing Functions
	// /////////////////////////////

	@Override
	public Counter decrement(String counterName)
	{
		return this.decrement(counterName, 1L);
	}

	@Override
	public Counter decrement(final String counterName, final long amount)
	{
		// ///////////
		// Precondition Checks
		Preconditions.checkNotNull(counterName, "CounterName may not be null!");
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));

		// TODO: There's a problem with decrementing if, mid-stream, the number of shards changes. This is because
		// the accessing of the CounterData happens outside of a Transaction. To mitigate this, we should consider
		// freezing the CounterData in the case of a decrement. This would preclude the number of shards from changing,
		// but would still allow any discrete counter shard to be decremented in parallel. In other words, this would
		// only affect mutating the CounterData itself, but not the parallel decrementing of shards.

		// ///////////
		// CounterData Status Checks
		final CounterData counterData = getOrCreateCounterData(counterName);

		// Increments/Decrements can only occur on Counters with a counterStatus of AVAIALBLE. This will work as a
		// governor most of the time, but we need to do it again inside of the decrement to be certain.
		assertCounterAmountMutatable(counterData.getCounterName(), counterData.getCounterStatus());

		// Find how many shards are in this counter.
		final int currentNumShards = counterData.getNumShards();

		long totalAmountDecremented;

		// Try a random shard at first -- this will generally work, but if it fails, then the code below will
		// kick-in, which is less efficient since it scans through all of the shards and will generally bias towards
		// the lower-numbered shards since the counter starts at 0. An improvement to reduce this bias would be to pick
		// a random shard, then scan up and down from there.

		// Choose the shard randomly from the available shards.
		final int randomShardNum = generator.nextInt(currentNumShards);
		final Key<CounterShardData> randomCounterShardDataKey = CounterShardData.key(counterName, randomShardNum);
		DecrementShardWork decrementShardTask = new DecrementShardWork(counterName, randomCounterShardDataKey, amount);

		Long lAmountDecrementedInTx = ObjectifyService.ofy().transactNew(decrementShardTask);
		long amountDecrementedInTx = lAmountDecrementedInTx == null ? 0L : lAmountDecrementedInTx.longValue();
		totalAmountDecremented = amountDecrementedInTx;
		long amountLeftToDecrement = amount - amountDecrementedInTx;
		if (amountLeftToDecrement > 0)
		{
			// Try to decrement an amount from one shard at a time in a serial fashion so that two shards aren't
			// decremented at the same time.
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				final Key<CounterShardData> sequentialCounterShardDataKey = CounterShardData.key(counterName, i);
				if (sequentialCounterShardDataKey.equals(randomCounterShardDataKey))
				{
					// This shard has already been decremented, so don't try again, but keep trying other shards.
					continue;
				}

				// Try to decrement amountLeftToDecrement
				if (amountLeftToDecrement > 0)
				{
					decrementShardTask = new DecrementShardWork(counterName, sequentialCounterShardDataKey,
						amountLeftToDecrement);
					lAmountDecrementedInTx = ObjectifyService.ofy().transactNew(decrementShardTask);
					amountDecrementedInTx = lAmountDecrementedInTx == null ? 0L : lAmountDecrementedInTx.longValue();
					totalAmountDecremented += amountDecrementedInTx;
					amountLeftToDecrement -= amountDecrementedInTx;
				}
				else
				{
					break;
				}
			}
		}

		// /////////////////
		// Increment this counter in memcache atomically, with retry until it succeeds (with some governor). If this
		// fails, it's ok because memcache is merely a cache of the actual count data, and will eventually become
		// accurate when the cache is reloaded.
		// /////////////////

		incrementMemcacheAtomic(counterName, (totalAmountDecremented * -1L));

		// return #getCount because this will either return the memcache value or get the actual count from the
		// Datastore, which will do the same thing.
		return getCounter(counterName);

	}

	/**
	 * An implementation of {@link Work} that decrements a specific {@link CounterShardData} by a specified amount.
	 * {@link CounterShardData} entities may not go below zero, so the amount returned by this callable may be less than
	 * the requested amount.
	 */
	@VisibleForTesting
	final class DecrementShardWork implements Work<Long>
	{
		private final String counterName;
		// This is tolerable to pass into this Work object because we don't create shards inside of this unit of
		// work.
		// Given XG transactions only support up to 5 at a time, cycling through the shards in a TX wouldn't work
		// anyway
		// for counters with more than 5 shards.
		private final Key<CounterShardData> counterShardKey;
		private final long requestedDecrementAmount;

		/*
		 * * Required args Constructor
		 * 
		 * @param counterName
		 * 
		 * @param counterShardKey
		 * 
		 * @param requestedDecrementAmount
		 */
		@VisibleForTesting
		DecrementShardWork(final String counterName, final Key<CounterShardData> counterShardKey,
				final long requestedDecrementAmount)
		{
			Preconditions.checkNotNull(counterName, "CounterName may not be null!");
			Preconditions.checkArgument(!StringUtils.isBlank(counterName), "CounterName may not be blank or empty!");
			Preconditions.checkArgument(requestedDecrementAmount >= 0, "Cannot decrement with a negative number!");
			this.counterName = counterName;

			Preconditions.checkNotNull(counterShardKey, "CounterShardKey may not be null!");
			this.counterShardKey = counterShardKey;

			Preconditions.checkArgument(requestedDecrementAmount > 0, "Amount must be greater than zero!");
			this.requestedDecrementAmount = requestedDecrementAmount;
		}

		/*
		 * * Attempt to decrement a particular CounterShardData by the {@code decrementAmount}, or something less if the
		 * shard does not have enough count to fulfill the entire decrement request. Note that CounterShardData counts
		 * are not permitted to go negative!
		 */
		@Override
		public Long run()
		{
			// This must be done in each decrement to ensure the status of the CounterData has not changed from
			// underneath us.

			CounterData counterData = getOrCreateCounterData(counterName);

			// Increments/Decrements can only occur on Counters with a counterStatus of AVAIALBLE.
			assertCounterAmountMutatable(counterData.getCounterName(), counterData.getCounterStatus());

			// Load the appropriate Shard
			CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardKey).now();
			if (counterShardData == null)
			{
				// Nothing was decremented! We don't create shards on a
				// decrement operation!
				return new Long(0L);
			}

			// This is the amount to decrement by. It may be reduced if the shard doesn't have enough count!
			long decrementAmount = computeLargestDecrementAmountForShard(counterShardData.getCount(),
				requestedDecrementAmount);

			// ///////////////////////////////
			// We have adjusted the decrementAmount, but it's possible it's still 0, in which case we should
			// short-circuit the datastore update and just return 0.
			// ///////////////////////////////

			if (decrementAmount <= 0)
			{
				if (getLogger().isLoggable(Level.FINE))
				{
					getLogger().fine(
						"Unable to Decrement CounterShardData (" + counterShardKey + ") with count "
							+ counterShardData.getCount() + " and requestedDecrementAmount of "
							+ requestedDecrementAmount);
				}

				return new Long(0);
			}
			else
			{
				counterShardData.setCount(counterShardData.getCount() - decrementAmount);
				if (getLogger().isLoggable(Level.FINE))
				{
					getLogger().fine(
						"Saving Decremented CounterShardData (" + counterShardKey + ") with count "
							+ counterShardData.getCount() + " after requestedDecrementAmount of "
							+ requestedDecrementAmount + " and actual decrementAmount of " + decrementAmount);
				}

				// Persist the updated value, but wait for it to
				// complete!
				ObjectifyService.ofy().save().entity(counterShardData).now();
				return new Long(decrementAmount);
			}

		}

		/*
		 * * Returns a decrement amount that is either zero, or a positive long amount that a particular CounterShard
		 * can be reduced by.
		 * 
		 * @param counterShardCount
		 * 
		 * @param decrementAmount
		 * 
		 * @return
		 */
		@VisibleForTesting
		protected long computeLargestDecrementAmountForShard(long counterShardCount, long decrementAmount)
		{
			if (counterShardCount - decrementAmount < 0)
			{
				// The 'delta' is the difference between the current
				// count,
				// and the requested decrement.
				long delta = counterShardCount - decrementAmount;

				// If the delta is negative, then reduce the
				// decrementAmount so that the counterShard doesn't go
				// below 0.
				if (delta < 0L)
				{
					// reduce the decrement by the delta
					decrementAmount -= Math.abs(delta);
					// One final check, which is probably redundant
					if (decrementAmount < 0L)
					{
						decrementAmount = 0L;
					}
				}
			}
			return decrementAmount;

		}
	}

	// /////////////////////////////
	// Counter Deletion Functions
	// /////////////////////////////

	@Override
	public void delete(final String counterName)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));

		// Delete the main counter in a new TX...
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// Load in a TX so that two threads don't mark the counter as
				// deleted at the same time.
				Key<CounterData> counterDataKey = CounterData.key(counterName);
				final CounterData counterData = ObjectifyService.ofy().load().key(counterDataKey).now();
				if (counterData == null)
				{
					// Nothing to delete...
					return;
				}

				Queue queue;
				if (config.getDeleteCounterShardQueueName() == null)
				{
					queue = QueueFactory.getDefaultQueue();
				}
				else
				{
					queue = QueueFactory.getQueue(config.getDeleteCounterShardQueueName());
				}

				// The TaskQueue will delete the counter once all shards are
				// deleted.
				counterData.setCounterStatus(CounterData.CounterStatus.DELETING);
				// Call this Async so that the rest of the thread can
				// continue. Everything will block till commit is called.
				ObjectifyService.ofy().save().entity(counterData);

				// Transactionally enqueue this task to the path specified
				// in the constructor (if this is null, then the default
				// queue will be used).
				TaskOptions taskOptions = TaskOptions.Builder.withParam(COUNTER_NAME, counterName);
				if (config.getRelativeUrlPathForDeleteTaskQueue() != null)
				{
					taskOptions = taskOptions.url(config.getRelativeUrlPathForDeleteTaskQueue());
				}

				// Kick off a Task to delete the Shards for this CounterData
				// and the CounterData itself, but only if the TX succeeds
				queue.add(taskOptions);
			}
		});

	}

	@Override
	public void onTaskQueueCounterDeletion(final String counterName)
	{
		Preconditions.checkNotNull(counterName);

		// Load in a TX so that two threads don't mark the counter as
		// deleted at the same time.
		final Key<CounterData> counterDataKey = CounterData.key(counterName);
		final CounterData counterData = ObjectifyService.ofy().load().key(counterDataKey).now();
		if (counterData == null)
		{
			getLogger().severe(
				"While attempting to delete CounterData named \"" + counterName
					+ "\", no CounterData was found in the Datastore!");
			// Nothing to delete...perhaps another task already did the
			// deletion?

			// Clear this counter from Memcache, just in case.
			this.memcacheSafeDelete(counterName);

			return;
		}
		else if (counterData.getCounterStatus() != CounterData.CounterStatus.DELETING)
		{
			throw new RuntimeException("Can't delete counter '" + counterName
				+ "' because it is currently not in the DELETING state!");
		}
		else
		{
			// Assemble a list of CounterShard keys, and delete them all in a batch!
			Collection<Key<CounterShardData>> counterShardDataKeys = Lists.newArrayList();
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterName, i);
				counterShardDataKeys.add(counterShardDataKey);
			}

			// No TX needed, and no need to wait.
			ObjectifyService.ofy().transactionless().delete().keys(counterShardDataKeys).now();

			// Delete the CounterData itself...No TX needed, and no need to wait.
			ObjectifyService.ofy().transactionless().delete().key(counterData.getTypedKey()).now();

			// Clear this counter from Memcache.
			this.memcacheSafeDelete(counterName);
		}
	}

	// //////////////////////////////////
	// Protected Helpers
	// //////////////////////////////////

	/**
	 * Attempt to delete a counter from memcache but swallow any exceptions from memcache if it's down.
	 * 
	 * @param counterName
	 */
	@VisibleForTesting
	void memcacheSafeDelete(final String counterName)
	{
		Preconditions.checkNotNull(counterName);
		try
		{
			memcacheService.delete(counterName);
		}
		catch (MemcacheServiceException mse)
		{
			// Do nothing. This merely indicates that memcache was unreachable, which is fine. If it's
			// unreachable, there's likely nothing in the cache anyway, but in any case there's nothing we can do here.
		}
	}

	/**
	 * Attempt to delete a counter from memcache but swallow any exceptions from memcache if it's down.
	 * 
	 * @param memcacheKey
	 */
	@VisibleForTesting
	Long memcacheSafeGet(final String memcacheKey)
	{
		Preconditions.checkNotNull(memcacheKey);

		Long cachedCounterCount;
		try
		{
			cachedCounterCount = (Long) memcacheService.get(memcacheKey);
		}
		catch (MemcacheServiceException mse)
		{
			// Do nothing. This merely indicates that memcache was unreachable, which is fine. If it's
			// unreachable,
			// there's likely nothing in the cache anyway, but in any case there's nothing we can do here.
			cachedCounterCount = null;
		}
		return cachedCounterCount;
	}

	/**
	 * Helper method to get (or create and then get) a {@link CounterData} from the Datastore with a given name. The
	 * result of this function is guaranteed to be non-null if no exception is thrown.
	 * 
	 * @param counterName
	 * 
	 * @return
	 * 
	 * @throws NullPointerException in the case where no CounterData could be loaded from the Datastore.
	 */
	@VisibleForTesting
	protected CounterData getOrCreateCounterData(final String counterName)
	{
		Preconditions.checkNotNull(counterName);

		final Key<CounterData> counterKey = CounterData.key(counterName);

		// Do this in a new TX to avoid XG transaction limits, and to ensure
		// that if two threads with different config default shard values don't
		// stomp on each other. If two threads conflict with each other, one
		// will win and create the CounterData, and the other thread will retry
		// and return the loaded CounterData.
		return ObjectifyService.ofy().transactNew(new Work<CounterData>()
		{
			@Override
			public CounterData run()
			{
				CounterData counterData = ObjectifyService.ofy().load().key(counterKey).now();
				if (counterData == null)
				{
					counterData = new CounterData(counterName, config.getNumInitialShards());
					ObjectifyService.ofy().save().entity(counterData).now();
				}
				return counterData;
			}
		});
	}

	/**
	 * <p>
	 * Increment the memcache version of the named-counter by {@code amount} (positive or negative) in an atomic fashion
	 * using {@link IdentifiableValue} semantics.
	 * </p>
	 * <p>
	 * Use memcache as a Semaphore/Mutex, and retry up to 10 times if other threads are attempting to update memcache at
	 * the same time. If nothing is in Memcache when this function is called, then do nothing because only #getCounter
	 * should "put" a value to memcache.
	 * </p>
	 * 
	 * @param counterName
	 * @param amount
	 * 
	 * @return The new count of this counter as reflected by memcache
	 */
	@VisibleForTesting
	protected Optional<Long> incrementMemcacheAtomic(final String counterName, final long amount)
	{
		// Get the cache counter at a current point in time.
		final String memCacheKey = this.assembleCounterKeyforMemcache(counterName);

		int numRetries = 10;
		while (numRetries > 0)
		{

			try
			{
				IdentifiableValue identifiableCounter = memcacheService.getIdentifiable(memCacheKey);
				// See Javadoc about a null identifiableCounter. If it's null,
				// then the named counter doesn't exist in memcache.
				if (identifiableCounter == null
					|| (identifiableCounter != null && identifiableCounter.getValue() == null))
				{
					if (getLogger().isLoggable(Level.FINE))
					{
						getLogger().fine(
							"No identifiableCounter was found in Memcache.  Unable to Atomically increment for CounterName \""
								+ counterName + "\".  Memcache will be populated on the next called to getCounter()!");
					}
					// This will return an absent value. Only #getCounter should
					// "put" a value to memcache.
					break;
				}

				// If we get here, the count exists in memcache, so it can be
				// atomically incremented.
				Long cachedCounterAmount = (Long) identifiableCounter.getValue();
				long newMemcacheAmount = cachedCounterAmount.longValue() + amount;
				if (newMemcacheAmount < 0)
				{
					newMemcacheAmount = 0;
				}

				if (getLogger().isLoggable(Level.FINE))
				{
					getLogger().fine(
						"Just before Atomic Increment of " + amount + ", Memcache has value "
							+ identifiableCounter.getValue());
				}

				if (memcacheService.putIfUntouched(counterName, identifiableCounter, new Long(newMemcacheAmount),
					config.getDefaultExpiration()))
				{
					if (getLogger().isLoggable(Level.FINE))
					{
						getLogger().fine("memcacheService.putIfUntouched SUCCESS! with value " + newMemcacheAmount);
					}

					// If we get here, the put succeeded...
					return Optional.of(new Long(newMemcacheAmount));
				}
				else
				{
					if (getLogger().isLoggable(Level.WARNING))
					{
						getLogger().log(Level.WARNING,
							"Unable to update memcache counter atomically.  Retrying " + numRetries + " more times...");
					}
				}
			}
			catch (MemcacheServiceException mse)
			{
				// Check and post-decrement the numRetries counter in one step
				if (numRetries-- > 0)
				{
					if (getLogger().isLoggable(Level.WARNING))
					{
						getLogger().log(Level.WARNING,
							"Unable to update memcache counter atomically.  Retrying " + numRetries + " more times...",
							mse);
					}
					// Keep trying...
					continue;
				}
				else
				{
					// Evict the counter here, and let the next call to
					// getCounter populate memcache
					getLogger().log(
						Level.SEVERE,
						"Unable to update memcache counter atomically, with no more allowed retries.  Evicting counter named "
							+ counterName + " from the cache!", mse);
					memcacheService.delete(memCacheKey);
					break;
				}
			}
		}

		// The increment did not work...
		return Optional.absent();
	}

	/**
	 * <p>
	 * Increment the memcache version of the named-counter by {@code amount} (positive or negative) in an atomic fashion
	 * using {@link MemcacheService#increment(Object, long, Long)}.
	 * </p>
	 * <p>
	 * Use memcache as a Semaphore/Mutex, and retry up to 10 times if other threads are attempting to update memcache at
	 * the same time. If nothing is in Memcache when this function is called, then do nothing because only #getCounter
	 * should "put" a value to memcache.
	 * </p>
	 * 
	 * @param counterName
	 * 
	 * @param amount
	 * 
	 * @return The new count of this counter as reflected by memcache
	 */
	@VisibleForTesting
	protected Optional<Long> incrementMemcacheAtomic2(final String counterName, final long amount)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(amount > 0);

		// Get the cache counter at a current point in time.
		final String memCacheKey = this.assembleCounterKeyforMemcache(counterName);

		int numRetries = 10;
		while (numRetries > 0)
			try
			{
				// Atomically increment the memcache counter by "amount". If nothing exists in the cache, then
				// either this is an existing counter in which the value has been evicted or this is a new counter.
				//
				// We can't tell the difference since we only don't have information about the true count of this
				// counter, so if there is no value in memcache, we must simply return {@link Optional#absent} and
				// allow a subsequent getCount operation to populate the cache.
				final Long postIncrementValue = memcacheService.increment(memCacheKey, amount);

				// See Javadoc about a null identifiableCounter. If it's null,
				// then the named counter doesn't exist in memcache.
				if (postIncrementValue == null)
				{
					if (getLogger().isLoggable(Level.FINE))
					{
						getLogger()
							.fine(
								String
									.format(
										"While trying to increment Memcache, no value was found for counter '%s'.  Memcache will be populated on the next called to getCounter()!",
										counterName));
					}
					// This will return an absent value. Only #getCounter should "put" a value to memcache.
					break;
				}

				// If we get here, the count existed in memcache and we have a valid postIncrementValue
				if (getLogger().isLoggable(Level.FINE))
				{
					getLogger().fine(
						String.format("Memcache: Increment SUCCESS! Post-Increment counter is %s", postIncrementValue));
				}

				// If we get here, the increment succeeded...
				return Optional.of(postIncrementValue);
			}
			catch (InvalidValueException | MemcacheServiceException memcacheException)
			{
				// Check and post-decrement the numRetries counter in one step
				if (numRetries-- > 0)
				{
					if (getLogger().isLoggable(Level.WARNING))
					{
						getLogger().log(
							Level.WARNING,
							String.format(
								"Memcache: Unable to atomically increment counter '%s'.  Retrying %s more times...",
								counterName, numRetries), memcacheException);
					}
					// Keep trying...
					continue;
				}
				else
				{
					// Evict the counter here, and let the next call to
					// getCounter populate memcache
					getLogger()
						.log(
							Level.SEVERE,
							String
								.format(
									"Memcache: Unable to atomically increment counter '%s', with no more retries.   Evicting counter from the cache!",
									counterName), memcacheException);
					memcacheService.delete(memCacheKey);
					break;
				}
			}

		// The increment did not work...
		return Optional.absent();
	}

	/**
	 * Assembles a CounterKey for Memcache
	 * 
	 * @param counterName
	 * 
	 * @return
	 */
	@VisibleForTesting
	protected String assembleCounterKeyforMemcache(final String counterName)
	{
		Preconditions.checkNotNull(counterName);
		return counterName;
	}

	/**
	 * @return
	 */
	protected Logger getLogger()
	{
		return logger;
	}

	/**
	 * Helper method to determine if a counter's amount can be mutated (incremented or decremented). In order for that
	 * to happen, the counter's status must be {@link CounterStatus#AVAILABLE}.
	 * 
	 * @param counterName
	 * @param counterStatus
	 * 
	 * @return
	 */
	@VisibleForTesting
	protected void assertCounterAmountMutatable(final String counterName, final CounterStatus counterStatus)
	{
		if (counterStatus != CounterStatus.AVAILABLE)
		{
			throw new RuntimeException(
				String
					.format(
						"Can't mutate the amount of counter '%s' because it's currently in the %s state but must be in in the %s state!",
						counterName, counterStatus.name(), CounterStatus.AVAILABLE));
		}
	}

	/**
	 * Helper method to determine if a counter's amount can be mutated (incremented or decremented). In order for that
	 * to happen, the counter's status must be {@link CounterStatus#AVAILABLE}.
	 * 
	 * @param counterName
	 * @param counterStatus
	 * 
	 * @return
	 */
	@VisibleForTesting
	protected void assertCounterDetailsMutatable(final String counterName, final CounterStatus counterStatus)
	{
		if (counterStatus != CounterStatus.AVAILABLE && counterStatus != CounterStatus.READ_ONLY_COUNT)
		{
			throw new RuntimeException("Can't mutate the details of counter \"" + counterName
				+ "\" because it's currently in the " + counterStatus + " state but must be in in the "
				+ CounterStatus.AVAILABLE + " or " + CounterStatus.READ_ONLY_COUNT + " state!");
		}
	}
}
