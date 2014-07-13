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
package com.theupswell.appengine.counter.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.CounterBuilder;
import com.theupswell.appengine.counter.data.CounterData;
import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
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
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * A durable implementation of a {@link ShardedCounterService} that provides
 * counter increment, decrement, and delete functionality. This implementation
 * is is backed by one or more Datastore "shard" entities which each hold a
 * discrete count in order to provide for high throughput. When aggregated, the
 * sum total of all CounterShard entity counts is the value of the counter. See
 * the google link below for more details on Sharded Counters in appengine. Note
 * that CounterShards cannot go negative, and there is no difference between a
 * counter being "zero" and a counter not existing.<br/>
 * <br/>
 * Note that this implementation is capable of incrementing/decrementing various
 * counter shard counts but does not automatically increase or reduce the
 * <b>number</b> of shards for a given counter in response to load.<br/>
 * <br/>
 * All datastore operations are performed using Objectify.<br/>
 * <br/>
 * <br/>
 * <b>Incrementing a Counter</b><br/>
 * When incrementing, a random shard is selected to prevent a single shard from
 * being written to too frequently.<br/>
 * <br/>
 * <b>Decrementing a Counter</b><br/>
 * This implementation does not support negative counts, so CounterShard counts
 * can not go below zero. Thus, when decrementing, a random shard is selected to
 * prevent a single shard from being written to too frequently. However, if a
 * particular shard cannot be decremented then other shards are tried until all
 * shards have been tried. If no shard can be decremented, then the decrement
 * function is considered complete, even though nothing was decremented. Because
 * of this, it is possible that a request to reduce a counter by more than its
 * available count will succeed with a lesser count having been reduced.
 * <b>Getting the Count</b><br/>
 * Aggregate Counter counter lookups are first attempted using Memcache. If the
 * counter value is not in the cache, then the shards are read from the
 * datastore and accumulated to reconstruct the current count. This operation
 * has a cost of O(numShards), or O(N). Increase the number of shards to improve
 * counter increment throughput, but beware that this has a cost - it makes
 * counter lookups from the Datastore more expensive.<br/>
 * <br/>
 * <b>Throughput</b><br/>
 * As an upper-bound calculation of throughput and shard-count, the Psy
 * "Gangham Style" youtube video (arguably one of the most viral videos of all
 * time) reached 750m views in approximately 60 days. If that video's
 * 'hit-counter' was using appengine-counter as its underlying implementation,
 * then the counter would have needed to sustain an increment rate of 145
 * updates per second for 60 days. Since each CounterShard could have provided
 * up to 5 updates per second (this seems to be the average indicated by the
 * appengine team and in various documentation), then the counter would have
 * required at least 29 CounterShard entities, which in the grand scheme of the
 * Appengine Datastore seems prett small. In reality, a counter with this much
 * traffic would not need to be highly consistent, but it could have been using
 * appengine-counter.<br/>
 * <br/>
 * <b>Future Improvements</b><br/>
 * <ul>
 * <li><b>CounterShard Expansion</b>: A shard-expansion mechanism can be
 * envisioned to increase the number of CounterShard entities for a particular
 * Counter when load increases to a specified amount for a given Counter.</li>
 * <li><b>CounterShard Contraction</b>: A shard-reduction mechanism can be
 * envisioned to aggregate multiple shards (and their counts) into fewer shards
 * to improve datastore counter lookup performance when Counter load falls below
 * some threshold.</li>
 * <li><b>Counter Reset</b>: Reset a counter to zero by resetting all counter
 * shards 'counts' to zero. This would need to be, by nature of this
 * implementation, async.</li>
 * </ul>
 * 
 * @see "https://developers.google.com/appengine/articles/sharding_counters"
 * @author David Fuelling
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
	 * Default Constructor for Dependency-Injection that uses
	 * {@link MemcacheServiceFactory} to construct the {@link MemcacheService}
	 * and {@link CapabilitiesServiceFactory} to construct the
	 * {@link CapabilitiesService}. dependency for this service.
	 */
	public ShardedCounterServiceImpl()
	{
		this(MemcacheServiceFactory.getMemcacheService(), CapabilitiesServiceFactory.getCapabilitiesService());
	}

	/**
	 * Default Constructor for Dependency-Injection that uses a default number
	 * of counter shards (set to 1) and a default configuration per
	 * {@link ShardedCounterServiceConfiguration#defaultConfiguration}.
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
			// The relativeUrlPathForDeleteTaskQueue may be null, but if
			// it's non-null, then it must not be blank.
			Preconditions.checkArgument(!StringUtils.isBlank(config.getRelativeUrlPathForDeleteTaskQueue()),
				"Must be null (for the Default Queue) or a non-blank String!");
		}
	}

	// /////////////////////////////
	// Retreival Functions
	// /////////////////////////////

	/**
	 * The cache will expire after {@code defeaultExpiration} seconds, so the
	 * counter will be accurate after a minute because it performs a load from
	 * the datastore.
	 * 
	 * @param counterData
	 * @return
	 */
	@Override
	public Counter getCounter(final String counterName)
	{
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");

		// We always load the CounterData from the Datastore (or its Objectify
		// cache), but we sometimes return the cached count value.
		CounterData counterData = this.getOrCreateCounterData(counterName);
		// If the counter is DELETING, then its count is always 0!
		if (CounterData.CounterStatus.DELETING == counterData.getCounterStatus())
		{
			return new CounterBuilder(counterData).withCount(0L).build();
		}

		String memCacheKey = this.assembleCounterKeyforMemcache(counterName);
		Long cachedCounterCount = null;
		if (this.isMemcacheAvailable())
		{
			cachedCounterCount = (Long) memcacheService.get(memCacheKey);
		}

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
			List<Key<CounterShardData>> keysToLoad = Lists.newArrayList();
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				Key<CounterShardData> counterShardKey = CounterShardData.key(counterData.getCounterName(), i);
				keysToLoad.add(counterShardKey);
			}

			long sum = 0;

			// For added performance, we could spawn multiple threads to wait
			// for each value to be returned from the DataStore, and then
			// aggregate that way. However, the simple summation below is not
			// very expensive, so creating multiple threads to get each value
			// would probably be overkill. Just let objectify do this for us,
			// even though we have to wait for all entities to return before
			// summation begins.

			// No TX - get is Strongly consistent by default, and we will exceed
			// the TX limit for high-shard-count counters if we try to do this
			// in a TX.
			Map<Key<CounterShardData>, CounterShardData> counterShardDatasMap = ObjectifyService.ofy()
				.transactionless().load().keys(keysToLoad);
			Collection<CounterShardData> counterShardDatas = counterShardDatasMap.values();
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

			if (this.isMemcacheAvailable())
			{
				memcacheService.put(memCacheKey, new Long(sum), config.getDefaultExpiration(), SetPolicy.SET_ALWAYS);
			}

			return new CounterBuilder(counterName).withCount(sum).build();
		}
	}

	// /////////////////////////////
	// Increment Functions
	// /////////////////////////////

	@Override
	public Counter increment(String counterName)
	{
		return this.increment(counterName, 1L);
	}

	@Override
	public Counter increment(String counterName, long amount)
	{
		return this.increment(counterName, amount, true);
	}

	@Override
	public Counter increment(final String counterName, final long amount, boolean isolated)
	{
		// ///////////
		// Precondition Checks
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "CounterData increments must be positive numbers!");

		// Create the Work to be done for this increment, which will be done
		// inside of a TX. See
		// https://developers.google.com/appengine/docs/java/datastore/transactions#Java_Isolation_and_consistency
		final Work<Long> atomicIncrementShardWork = new Work<Long>()
		{
			// NOTE: In order for this to work properly, the CounterShardData
			// must be gotten, created, and updated all in the same transaction
			// in order to remain consistent (in other words, it must be
			// atomic).

			@Override
			public Long run()
			{
				CounterData counterData = getOrCreateCounterData(counterName);
				if (counterData.getCounterStatus() == CounterData.CounterStatus.DELETING)
				{
					throw new RuntimeException("Can't increment counter \"" + counterName
						+ "\" because it is currently being deleted!");
				}

				// Find how many shards are in this counter.
				final int currentNumShards = counterData.getNumShards();

				// Choose the shard randomly from the available shards.
				final int shardNumber = generator.nextInt(currentNumShards);

				Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterName, shardNumber);

				// Load the Shard from the DS.
				CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardDataKey).now();
				if (counterShardData == null)
				{
					// Create it in the Datastore
					counterShardData = new CounterShardData(counterName, shardNumber);
					ObjectifyService.ofy().save().entity(counterShardData).now();
				}

				// Increment the count by {amount}
				counterShardData.setCount(counterShardData.getCount() + amount);

				if (getLogger().isLoggable(Level.FINE))
				{
					getLogger().log(
						Level.FINE,
						"Saving CounterShardData" + shardNumber + " for CounterData \"" + counterName
							+ "\" with count " + counterShardData.getCount());
				}

				// Persist the updated value.
				ObjectifyService.ofy().save().entity(counterShardData).now();
				return new Long(amount);
			}
		};

		// ///////////
		// Take Off!

		Long amountIncrementedInTx = new Long(0L);
		// Perform the increment inside of its own, isolated transaction.
		if (isolated)
		{
			amountIncrementedInTx = ObjectifyService.ofy().transactNew(atomicIncrementShardWork);
		}
		// Perform the increment inside of the existing transaction, if any.
		else
		{
			amountIncrementedInTx = ObjectifyService.ofy().transact(atomicIncrementShardWork);
		}

		// We use the "amountIncrementedInTx" to pause this thread until the
		// work inside of "atomicIncrementShardWork" completes. This is because
		// we don't want to increment memcache (below) until after that point.

		// /////////////////
		// Increment this counter in memcache atomically, with retry until it
		// succeeds (with some governor). If this fails, it's ok
		// because memcache is merely a cache of the actual count data, and will
		// eventually become accurate when the cache is reloaded.
		// /////////////////
		incrementMemcacheAtomic(counterName, amountIncrementedInTx.longValue());

		// return #getCount because this will either return the memcache value
		// or get the actual count from the Datastore, which will do the same
		// thing.
		return getCounter(counterName);

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
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));

		// ///////////
		// CounterData Status Checks
		CounterData counterData = getOrCreateCounterData(counterName);

		// Find how many shards are in this counter.
		final int currentNumShards = counterData.getNumShards();

		long totalAmountDecremented = 0L;

		// Try a random shard at first -- this will generally work, but if it
		// fails, then the code below it will kick-in, which is less
		// efficient since it scans through all of the shards and will generally
		// bias towards the lower-numbered shards since the counter starts at 0.
		// An improvement to reduce this bias would be to pick a random shard,
		// then scan up and down from there.

		// Choose the shard randomly from the available shards.
		final int randomShardNum = generator.nextInt(currentNumShards);
		Key<CounterShardData> randomCounterShardDataKey = CounterShardData.key(counterName, randomShardNum);
		DecrementShardWork decrementShardTask = new DecrementShardWork(counterName, randomCounterShardDataKey, amount);

		Long lAmountDecrementedInTx = ObjectifyService.ofy().transactNew(decrementShardTask);
		long amountDecrementedInTx = lAmountDecrementedInTx == null ? 0L : lAmountDecrementedInTx.longValue();
		totalAmountDecremented = amountDecrementedInTx;
		long amountLeftToDecrement = amount - amountDecrementedInTx;
		if (amountLeftToDecrement > 0)
		{
			// Try to decrement an amount from one shard at a time in a serial
			// fashion so that two shards aren't decremented at the
			// same time.
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				final Key<CounterShardData> sequentialCounterShardDataKey = CounterShardData.key(counterName, i);
				if (sequentialCounterShardDataKey.equals(randomCounterShardDataKey))
				{
					// This shard has already been decremented, so don't try
					// again, but keep trying other shards.
					continue;
				}

				// Try to decrement amountLeftToDecrement
				decrementShardTask = new DecrementShardWork(counterName, sequentialCounterShardDataKey,
					amountLeftToDecrement);
				lAmountDecrementedInTx = ObjectifyService.ofy().transactNew(decrementShardTask);
				amountDecrementedInTx = lAmountDecrementedInTx == null ? 0L : lAmountDecrementedInTx.longValue();
				totalAmountDecremented += amountDecrementedInTx;
				amountLeftToDecrement -= amountDecrementedInTx;
			}
		}

		// /////////////////
		// Increment this counter in memcache atomically, with retry until it
		// succeeds (with some governor). If this fails, it's ok
		// because memcache is merely a cache of the actual count data, and will
		// eventually become accurate when the cache is reloaded.
		// /////////////////

		incrementMemcacheAtomic(counterName, (totalAmountDecremented * -1L));

		// return #getCount because this will either return the memcache value
		// or get the actual count from the Datastore, which will do the same
		// thing.
		return getCounter(counterName);

	}
	/**
	 * An implementation of {@link Work} that decrements a specific
	 * {@link CounterShardData} by a specified amount. {@link CounterShardData}
	 * entities may not go below zero, so the amount returned by this callable
	 * may be less than the requested amount.
	 */
	final class DecrementShardWork implements Work<Long>
	{
		private final String counterName;
		private final Key<CounterShardData> counterShardKey;
		private final long requestedDecrementAmount;

		/**
		 * Required args Constructor
		 * 
		 * @param counterShardKey
		 * @param isolated
		 * @param allowNegativeShardCounts
		 */
		public DecrementShardWork(final String counterName, final Key<CounterShardData> counterShardKey,
				final long requestedDecrementAmount)
		{
			Preconditions.checkArgument(!StringUtils.isBlank(counterName));
			Preconditions.checkNotNull(counterShardKey);
			Preconditions.checkArgument(requestedDecrementAmount >= 0, "Cannot decrement with a negative number!");

			this.counterName = counterName;
			this.counterShardKey = counterShardKey;
			this.requestedDecrementAmount = requestedDecrementAmount;
		}

		/**
		 * Attempt to decrement a particular CounterShardData by the
		 * {@code decrementAmount}, or something less if the shard does not have
		 * enough count to fulfill the entire decrement request. Note that
		 * CounterShardData counts are not permitted to go negative!
		 */
		@Override
		public Long run()
		{
			CounterData counterData = getOrCreateCounterData(counterName);
			if (counterData.getCounterStatus() == CounterData.CounterStatus.DELETING)
			{
				throw new RuntimeException("Can't increment counter \"" + counterName
					+ "\" because it is currently being deleted!");
			}

			// Load the appropriate Shard
			CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardKey).now();
			if (counterShardData == null)
			{
				// Nothing was decremented! We don't create shards on a
				// decrement operation!
				return new Long(0L);
			}

			// This is the amount to decrement by. It may be reduced if
			// the shard doesn't have enough count!
			long decrementAmount = computeLargestDecrementAmountForShard(counterShardData.getCount(),
				requestedDecrementAmount);

			// ///////////////////////////////
			// We have adjusted the decrementAmount, but it's possible
			// it's still 0, in which case we should short-circuit the
			// datastore update and just return 0.
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
		 * Returns a decrement amount that is either zero, or a positive long
		 * amount that a particular CounterShard can be reduced by.
		 * 
		 * @param counterShardData
		 * @param decrementAmount
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
	};

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
		// Load in a TX so that two threads don't mark the counter as
		// deleted at the same time.
		Key<CounterData> counterDataKey = CounterData.key(counterName);
		final CounterData counterData = ObjectifyService.ofy().load().key(counterDataKey).now();
		if (counterData == null)
		{
			getLogger().severe(
				"While attempting to delete CounterData named \"" + counterName
					+ "\", no CounterData was found in the Datastore!");
			// Nothing to delete...perhaps another task already did the
			// deletion?
			return;
		}
		else if (counterData.getCounterStatus() != CounterData.CounterStatus.DELETING)
		{
			throw new RuntimeException("Can't delete a counter \"" + counterName
				+ "\" because it is currently no in the DELETING state!");
		}

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

		// Clear Memcache
		if (isMemcacheAvailable())
		{
			memcacheService.delete(counterName);
		}
	}

	// //////////////////////////////////
	// Protected Helpers
	// //////////////////////////////////

	/**
	 * Helper method to get (or create and then get) a {@link CounterData} from
	 * the Datastore with a given name. The result of this function is
	 * guaranteed to be non-null if no exception is thrown.
	 * 
	 * @param counterName
	 * @return
	 * @throws NullPointerException in the case where no CounterData could be
	 *             loaded from the Datastore.
	 */
	@VisibleForTesting
	protected CounterData getOrCreateCounterData(final String counterName)
	{
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
	 * Increment the memcache version of the named-counter by {@code amount}
	 * (positive or negative) in an atomic fashion. Use memcache as a
	 * Semaphore/Mutex, and retry up to 10 times if other threads are attempting
	 * to update memcache at the same time. If nothing is in Memcache when this
	 * function is called, then do nothing because only #getCounter should "put"
	 * a value to memcache.
	 * 
	 * @param counterName
	 * @param amount
	 * @return The new count of this counter as reflected by memcache
	 */
	@VisibleForTesting
	protected Optional<Long> incrementMemcacheAtomic(final String counterName, final long amount)
	{
		// Memcache update did not succeed!
		if (!isMemcacheAvailable())
		{
			return Optional.absent();
		}

		// Get the cache counter at a current point in time.
		String memCacheKey = this.assembleCounterKeyforMemcache(counterName);

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
	 * Assembles a CounterKey for Memcache
	 * 
	 * @param counterName
	 * @return
	 */
	@VisibleForTesting
	protected String assembleCounterKeyforMemcache(String counterName)
	{
		return counterName;
	}

	/**
	 * @return {@code true} if Memcache is usable; {@code false} otherwise.
	 */
	@VisibleForTesting
	protected boolean isMemcacheAvailable()
	{
		CapabilityStatus capabilityStatus = this.capabilitiesService.getStatus(Capability.MEMCACHE).getStatus();
		return capabilityStatus == CapabilityStatus.ENABLED;
	}

	/**
	 * @return
	 */
	protected Logger getLogger()
	{
		return logger;
	}

}
