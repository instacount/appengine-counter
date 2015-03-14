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
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * <p>
 * A durable implementation of a {@link ShardedCounterService} that provides counter increment, decrement, accessor, and
 * delete functionality. This implementation is is backed by one or more Datastore "shard" entities which each hold a
 * discrete count in order to provide for high throughput. When aggregated, the sum total of all CounterShard entity
 * counts is the value of the counter. See the google link below for more details on Sharded Counters in Appengine. Note
 * that CounterShards may not go negative depending on the configuration of the counter. Also, note that there is no
 * difference between a counter being "zero" and a counter not existing. As such, there is no concept of "creating" a
 * counter, and deleting a counter will actually just remove all shards for a given counter, thereby resetting the
 * counter to 0.
 * </p>
 * <p>
 * <b>Shard Number Adjustments</b> This implementation is capable of incrementing/decrementing various counter shard
 * counts but does not automatically increase or reduce the <b>number</b> of shards for a given counter in response to
 * load.
 * </p>
 * <p>
 * <b>Incrementing a Counter</b><br/>
 * When incrementing, a random shard is selected to prevent a single shard from being written to too frequently.<br/>
 * </p>
 * <p>
 * <b>Decrementing a Counter</b><br/>
 * This implementation does not support negative counts, so CounterShard counts can not go below zero. Thus, when
 * decrementing, a random shard is selected to prevent a single shard from being written to too frequently. However, if
 * a particular shard cannot be decremented then other shards are tried until all shards have been tried. If no shard
 * can be decremented, then the decrement function is considered complete, even though nothing was decremented. Because
 * of this, a request to reduce a counter by more than its available count will succeed with a lesser count having been
 * reduced.
 * </p>
 * <p>
 * <b>Getting the Count</b> <br/>
 * Aggregate ounter lookups are first attempted using Memcache. If the counter value is not in the cache, then the
 * shards are read from the datastore and accumulated to reconstruct the current count. This operation has a cost of
 * O(numShards), or O(N). Increase the number of shards to improve counter increment throughput, but beware that this
 * has a cost - it makes counter lookups from the Datastore more expensive.<br/>
 * </p>
 * <p>
 * <b>Throughput</b><br/>
 * As an upper-bound calculation of throughput as it relates to shard-count, the Psy "Gangham Style" youtube video
 * (arguably one of the most viral videos of all time) reached 750m views in approximately 60 days. If that video's
 * 'hit-counter' was using appengine-counter as its underlying implementation, then the counter would have needed to
 * sustain an increment rate of 145 updates per second for 60 days. Since each CounterShard could have provided up to 5
 * updates per second (this seems to be the average indicated by the appengine team and in various documentation), then
 * the counter would have required at least 29 CounterShard entities. While something like a high-traffic hit-counter
 * could be be implemented using appengine-counter, a HyperLogLog counter would be a better choice (see
 * http://antirez.com/news/75 for more details).
 * </p>
 * <p>
 * All datastore operations are performed using Objectify.
 * </p>
 * <b>Future Improvements</b><br/>
 * <ul>
 * <li><b>CounterShard Expansion</b>: A shard-expansion mechanism can be envisioned to increase the number of
 * CounterShard entities for a particular Counter when load increases to a specified incrementAmount for a given
 * Counter.</li>
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
		this(MemcacheServiceFactory.getMemcacheService());
	}

	/**
	 * Default Constructor for Dependency-Injection that uses a default number of counter shards (set to 1) and a
	 * default configuration per {@link ShardedCounterServiceConfiguration#defaultConfiguration}.
	 *
	 * @param memcacheService
	 */
	public ShardedCounterServiceImpl(final MemcacheService memcacheService)
	{
		this(memcacheService, ShardedCounterServiceConfiguration.defaultConfiguration());
	}

	/**
	 * Default Constructor for Dependency-Injection.
	 *
	 * @param memcacheService
	 * @param config The configuration for this service
	 */
	public ShardedCounterServiceImpl(final MemcacheService memcacheService,
			final ShardedCounterServiceConfiguration config)
	{
		Preconditions.checkNotNull(memcacheService, "Invalid memcacheService!");
		Preconditions.checkNotNull(config);

		this.memcacheService = memcacheService;
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

				// Update the CounterDataIndexes
				counterDataInDatastore.setIndexes(incomingCounter.getIndexes() == null ? CounterIndexes.none()
					: incomingCounter.getIndexes());

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
	public void increment(final String counterName, final long requestedIncrementAmount)
	{
		// ///////////
		// Precondition Checks
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(requestedIncrementAmount > 0, "Counter increments must be positive numbers!");

		// Create the Work to be done for this increment, which will be done inside of a TX. See
		// "https://developers.google.com/appengine/docs/java/datastore/transactions#Java_Isolation_and_consistency"
		final Work<Long> atomicIncrementShardWork = new IncrementShardWork(counterName, requestedIncrementAmount);

		// Note that this operation is idempotent from the perspective of a ConcurrentModificationException. In that
		// case, the increment operation will fail and will not have been applied. An Objectify retry of the increment
		// may occur in a new transaction, and a successful increment will only ever happen once (if the Appengine
		// datastore is functioning properly).
		//
		// WARNING: Be aware that per the GAE docs, in certain rare cases "If your application receives an exception
		// when committing a transaction, it does not always mean that the transaction failed. You can receive
		// DatastoreTimeoutException or DatastoreFailureException in cases where transactions have been
		// committed and eventually will be applied successfully." See more at
		// https://cloud.google.com/appengine/docs/java/datastore/transactions. In these cases, clients of this counter
		// service may retrieve the current count of a counter using #getCount to help determine if an increment
		// was actually applied. However, in certain cases like counters under heavy load, it may not be
		// possible to determine if an increment should be retried. If this scenario is unacceptable, you might prefer
		// the exacto counter instead.

		// We use the "amountIncrementedInTx" to pause this thread until the work inside of "atomicIncrementShardWork"
		// completes. This is because we don't want to increment memcache (below) until after that a transaction
		// successfully commits.
		final Long amountIncrementedInTx = ObjectifyService.ofy().transact(atomicIncrementShardWork);

		// /////////////////
		// Increment this counter in memcache atomically, but only if we're not inside of an existing transaction.
		// Otherwise, clear out memcache.
		if (isParentTransactionActive())
		{
			// If we're in a transaction, then don't update memcache. Instead, clear it out since we can't know if the
			// parent transaction will abort after our call to memcache.
			this.memcacheSafeDelete(counterName);
		}
		else
		{
			// If this fails, it's ok because memcache is merely a cache of the actual count data, and will eventually
			// become accurate when the cache is reloaded via a call to getCount.
			this.incrementMemcacheAtomic(counterName, amountIncrementedInTx.longValue());
		}
	}

	/**
	 * A private implementation of {@link Work} that increments the incrementAmount of a {@link CounterShardData} by a
	 * specified non-negative {@code incrementAmount}.
	 */
	@VisibleForTesting
	final class IncrementShardWork implements Work<Long>
	{
		// We begin this transactional unit of work with just the counter name so we can guarantee all data in question
		// is consistent, and allow a counter shard key to vary based upon the number of shards indicated in
		// CounterData, which won't be available in a consistent manner until we enter the transaction.
		private final String counterName;
		private final long incrementAmount;

		/**
		 * Required-Args Constructor.
		 *
		 * @param counterName
		 * @param incrementAmount
		 */
		IncrementShardWork(final String counterName, final long incrementAmount)
		{
			Preconditions.checkNotNull(counterName);
			Preconditions.checkArgument(!StringUtils.isBlank(counterName));
			this.counterName = counterName;

			Preconditions.checkArgument(incrementAmount > 0, "Counter increments must be positive numbers!");
			this.incrementAmount = incrementAmount;
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
				// Create a new CounterShardData in memory. No need to preemptively save to the Datastore until the very
				// end.
				counterShardData = new CounterShardData(counterName, shardNumber);
			}

			// Increment the count by {incrementAmount}
			final long newAmount = counterShardData.getCount() + incrementAmount;
			counterShardData.setCount(newAmount);

			if (getLogger().isLoggable(Level.FINE))
			{
				final String msg = String
					.format(
						"About to update CounterShardData (%s-->Shard-%s) with current incrementAmount %s and new incrementAmount %s",
						counterName, shardNumber, counterShardData.getCount(), newAmount);
				getLogger().log(Level.FINE, msg);
			}

			// Persist the updated value.
			ObjectifyService.ofy().save().entity(counterShardData).now();
			return new Long(incrementAmount);
		}
	}

	// /////////////////////////////
	// Decrementing Functions
	// /////////////////////////////

	@Override
	public long decrement(final String counterName, final long amount)
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
			// Try to decrement an incrementAmount from one shard at a time in a serial fashion so that two shards
			// aren't
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

		// Don't return #getCount because it should be accessed outside of the decrement context.
		return totalAmountDecremented;
	}

	/**
	 * An implementation of {@link Work} that decrements a specific {@link CounterShardData} by a specified
	 * incrementAmount. {@link CounterShardData} entities may not go below zero, so the incrementAmount returned by this
	 * callable may be less than the requested incrementAmount.
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

			Preconditions.checkArgument(requestedDecrementAmount > 0,
				"Counter decrement amounts must be positive numbers!");
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

			final CounterData counterData = getOrCreateCounterData(counterName);

			// Increments/Decrements can only occur on Counters with a counterStatus of AVAIALBLE.
			assertCounterAmountMutatable(counterData.getCounterName(), counterData.getCounterStatus());

			// Load the appropriate Shard
			final CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardKey).now();
			if (counterShardData == null)
			{
				// Nothing was decremented! We don't create shards on a
				// decrement operation!
				return new Long(0L);
			}

			// This is the incrementAmount to decrement by. It may be reduced if the shard doesn't have enough count!
			final long decrementAmount = computeLargestDecrementAmountForShard(counterShardData.getCount(),
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

		/**
		 * Returns a decrement incrementAmount that is either zero, or a positive long incrementAmount that a particular
		 * CounterShard can be reduced by.
		 * 
		 * @param counterShardCount
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
				// count, and the requested decrement.
				final long delta = counterShardCount - decrementAmount;

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
				// Load in a TX so that two threads don't mark the counter as deleted at the same time.
				final Key<CounterData> counterDataKey = CounterData.key(counterName);
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

				// The TaskQueue will delete the counter once all shards are deleted.
				counterData.setCounterStatus(CounterData.CounterStatus.DELETING);
				// Call this Async so that the rest of the thread can
				// continue. Everything will block till commit is called.
				ObjectifyService.ofy().save().entity(counterData);

				// Transactionally enqueue this task to the path specified in the constructor (if this is null, then the
				// default queue will be used).
				TaskOptions taskOptions = TaskOptions.Builder.withParam(COUNTER_NAME, counterName);
				if (config.getRelativeUrlPathForDeleteTaskQueue() != null)
				{
					taskOptions = taskOptions.url(config.getRelativeUrlPathForDeleteTaskQueue());
				}

				// Kick off a Task to delete the Shards for this CounterData and the CounterData itself, but only if the
				// overall TX commit succeeds
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
			// Nothing to delete...perhaps another task already did the deletion?
			final String msg = String.format(
				"No CounterData was found in the Datastore while attempting to delete CounterData named '%s",
				counterName);
			getLogger().severe(msg);

			// Clear this counter from Memcache, just in case.
			this.memcacheSafeDelete(counterName);
			return;
		}
		else if (counterData.getCounterStatus() != CounterData.CounterStatus.DELETING)
		{
			final String msg = String.format(
				"Can't delete counter '%s' because it is currently not in the DELETING state!", counterName);
			throw new RuntimeException(msg);
		}
		else
		{
			// Assemble a list of CounterShard keys, and delete them all in a batch!
			final Collection<Key<CounterShardData>> counterShardDataKeys = Lists.newArrayList();
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterName, i);
				counterShardDataKeys.add(counterShardDataKey);
			}

			// No TX allowed since there may be many shards.
			ObjectifyService.ofy().transactionless().delete().keys(counterShardDataKeys).now();

			// Delete the CounterData itself...No TX needed.
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
		return ObjectifyService.ofy().transact(new Work<CounterData>()
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

	private static final int NUM_RETRIES_LIMIT = 10;

	/**
	 * <p>
	 * Increment the memcache version of the named-counter by {@code incrementAmount} (positive or negative) in an
	 * atomic fashion using {@link MemcacheService#increment(Object, long, Long)}.
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
	protected Optional<Long> incrementMemcacheAtomic(final String counterName, final long amount)
	{
		Preconditions.checkNotNull(counterName);
		// amount may be positive, negative, or zero depending on the source of the caller

		if (amount == 0)
		{
			return Optional.of(0L);
		}

		// Get the cache counter at a current point in time.
		final String memCacheKey = this.assembleCounterKeyforMemcache(counterName);

		for (int currentRetry = 0; currentRetry < NUM_RETRIES_LIMIT; currentRetry++)
		{
			try
			{
				// Atomically increment the memcache counter by "incrementAmount". If nothing exists in the cache, then
				// either this is an existing counter in which the value has been evicted or this is a new counter.
				//
				// In either case, we can't tell the difference since we don't have information about the true
				// count of this counter due to potential transactional limitations of the Datastore. Thus, if there is
				// no value in memcache, we must simply return {@link Optional#absent} and
				// allow a subsequent calls to #getCount to populate the cache.
				final Long postIncrementValue = memcacheService.increment(memCacheKey, amount);
				if (postIncrementValue == null)
				{
					if (getLogger().isLoggable(Level.FINE))
					{
						final String msg = String
							.format(
								"While trying to increment Memcache, no value was found for counter '%s'.  Memcache will be populated on the next called to getCounter()!",
								counterName);
						getLogger().fine(msg);
					}

					// Note: Only #getCounter should "put" a value to memcache.
					break;
				}
				else
				{
					// If we get here, the count existed in memcache and we have a valid postIncrementValue
					if (getLogger().isLoggable(Level.FINE))
					{
						final String msg = String.format("Memcache: Increment SUCCESS! Post-Increment counter is %s",
							postIncrementValue);
						getLogger().fine(msg);
					}

					// If we get here, the increment succeeded...
					return Optional.of(postIncrementValue);
				}
			}
			catch (InvalidValueException | MemcacheServiceException memcacheException)
			{
				// Guard against too many retries!
				if ((currentRetry + 1) < NUM_RETRIES_LIMIT)
				{
					if (getLogger().isLoggable(Level.WARNING))
					{
						final String msg = String.format(
							"Memcache: Unable to atomically increment counter '%s'.  Retrying %s more times...",
							counterName, (NUM_RETRIES_LIMIT - currentRetry));
						getLogger().log(Level.WARNING, msg, memcacheException);
					}
					// Keep trying...
					continue;
				}
				else
				{
					// Evict the counter here, and let the next call to #getCounter populate memcache
					final String msg = String
						.format(
							"Memcache: Unable to atomically increment counter '%s', with no more retries.   Evicting counter from the cache!",
							counterName);
					getLogger().log(Level.SEVERE, msg, memcacheException);
					memcacheService.delete(memCacheKey);
					break;
				}
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
	 * Helper method to determine if a counter's incrementAmount can be mutated (incremented or decremented). In order
	 * for that to happen, the counter's status must be {@link CounterStatus#AVAILABLE}.
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
			final String msg = String
				.format(
					"Can't mutate the incrementAmount of counter '%s' because it's currently in the %s state but must be in in the %s state!",
					counterName, counterStatus.name(), CounterStatus.AVAILABLE);
			throw new RuntimeException(msg);
		}
	}

	/**
	 * Helper method to determine if a counter's incrementAmount can be mutated (incremented or decremented). In order
	 * for that to happen, the counter's status must be {@link CounterStatus#AVAILABLE}.
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
			final String msg = String
				.format(
					"Can't mutate the details of counter '%s' because it's currently in the %s state but must be in in the %s or %s state!",
					counterName, counterStatus, CounterStatus.AVAILABLE, CounterStatus.READ_ONLY_COUNT);
			throw new RuntimeException(msg);
		}
	}

	@VisibleForTesting
	protected boolean isParentTransactionActive()
	{
		return ObjectifyService.ofy().getTransaction() == null ? false : ObjectifyService.ofy().getTransaction()
			.isActive();
	}
}
