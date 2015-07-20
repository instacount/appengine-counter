/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.theupswell.appengine.counter.service;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
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
import com.google.common.math.LongMath;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.CounterBuilder;
import com.theupswell.appengine.counter.CounterOperation;
import com.theupswell.appengine.counter.CounterOperation.CounterOperationType;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.data.CounterShardOperationData;
import com.theupswell.appengine.counter.exceptions.CounterExistsException;
import com.theupswell.appengine.counter.exceptions.CounterNotMutableException;
import com.theupswell.appengine.counter.exceptions.InvalidCounterNameException;
import com.theupswell.appengine.counter.exceptions.NoCounterExistsException;

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

	// This indicates that cache should be used, and is equivalent to skipCache=false
	private static final boolean USE_CACHE = false;
	private static final boolean SKIP_CACHE = true;

	/**
	 * A random number generating, for distributing writes across shards.
	 */
	protected final Random generator = new Random();

	protected final MemcacheService memcacheService;
	protected final ShardedCounterServiceConfiguration config;
	protected final CounterNameValidator counterNameValidator;

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
		this(memcacheService, config, new DefaultCounterNameValidator());
	}

	/**
	 * Default Constructor for Dependency-Injection.
	 *
	 * @param memcacheService
	 * @param config The configuration for this service
	 */
	public ShardedCounterServiceImpl(final MemcacheService memcacheService,
			final ShardedCounterServiceConfiguration config, final CounterNameValidator counterNameValidator)
	{
		Preconditions.checkNotNull(memcacheService, "Invalid memcacheService!");
		this.memcacheService = memcacheService;

		Preconditions.checkNotNull(config);
		this.config = config;

		Preconditions.checkArgument(config.getNumInitialShards() > 0,
			"Number of Shards for a new CounterData must be greater than 0!");
		if (config.getRelativeUrlPathForDeleteTaskQueue() != null)
		{
			// The relativeUrlPathForDeleteTaskQueue may be null, but if it's non-null, then it must not be blank.
			Preconditions.checkArgument(!StringUtils.isBlank(config.getRelativeUrlPathForDeleteTaskQueue()),
				"Must be null (for the Default Queue) or a non-blank String!");
		}

		Preconditions.checkNotNull(counterNameValidator);
		this.counterNameValidator = counterNameValidator;
	}

	// /////////////////////////////
	// Retrieval Functions
	// /////////////////////////////

	/**
	 * The cache will expire after {@code defaultCounterCountExpiration} seconds, so the counter will be accurate after
	 * a minute because it performs a load from the datastore.
	 *
	 * @param counterName
	 * @return
	 */
	@Override
	public Optional<Counter> getCounter(final String counterName)
	{
		return this.getCounter(counterName, USE_CACHE);
	}

	@Override
	public Optional<CounterOperation> getCounterOperation(String counterName, int shardNumber, UUID counterOperationId)
	{
		// Load the counterOperationData
		final Key<CounterData> counterDataKey = CounterData.key(counterName);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, shardNumber);
		final Key<CounterShardOperationData> counterShardOperationDataKey = CounterShardOperationData.key(
			counterShardDataKey, counterOperationId);

		final CounterShardOperationData counterShardOperationData = ObjectifyService.ofy().load()
			.key(counterShardOperationDataKey).now();

		if (counterShardOperationData == null)
		{
			return Optional.absent();
		}
		else
		{
			return Optional.<CounterOperation> of(new CounterOperation.Impl(counterShardOperationData));
		}
	}

	@Override
	public Counter createCounter(String counterName)
	{
		final CounterData counterData = this.createCounterData(counterName);
		return new CounterBuilder(counterData).withCount(BigInteger.ZERO).build();
	}

	/**
	 * The cache will expire after {@code defaultCounterCountExpiration} seconds, so the counter will be accurate after
	 * a minute because it performs a load from the datastore.
	 *
	 * @param counterName
	 * @param skipCache A boolean that allows a caller to skip memcache when retrieving a counter. Set to {@code true}
	 *            to load the counter and all of its shards directly from the Datastore. Set to {@code false} to attempt
	 *            to load the count from memcache, with fallback to the datastore.
	 * @return
	 */
	@Override
	public Optional<Counter> getCounter(final String counterName, final boolean skipCache)
	{
		Preconditions.checkNotNull(counterName);

		// This method always load the CounterData from the Datastore (or its Objectify cache), but sometimes returns
		// the
		// cached count value.

		// //////////////
		// ShortCircuit: If nothing is present in the datastore.
		// //////////////
		final Optional<CounterData> optCounterData = this.getCounterData(counterName);
		if (!optCounterData.isPresent())
		{
			logger.log(Level.FINEST, String.format("Counter '%s' was not found in hte Datastore!", counterName));
			return Optional.absent();
		}

		final CounterData counterData = optCounterData.get();

		// //////////////
		// ShortCircuit: If the counter is in an indeterminate state, then return its count as 0.
		// //////////////
		if (this.counterStatusYieldsIndeterminateCount(counterData.getCounterStatus()))
		{
			logger.log(Level.FINEST,
				String.format("Counter '%s' was in an indeterminate state.  Returning 0!", counterName));
			return Optional.of(new CounterBuilder(counterData).withCount(BigInteger.ZERO).build());
		}

		// //////////////
		// ShortCircuit: If the counter was found in memcache.
		// //////////////
		final String memCacheKey = this.assembleCounterKeyforMemcache(counterName);
		if (!skipCache)
		{
			final BigInteger cachedCounterCount = this.memcacheSafeGet(memCacheKey);
			if (cachedCounterCount != null)
			{
				// /////////////////////////////////////
				// The count was found in memcache, so return it.
				// /////////////////////////////////////
				logger.log(Level.FINEST,
					String.format("Cache Hit for Counter Named '%s': value=%s", counterName, cachedCounterCount));
				return Optional.of(new CounterBuilder(counterData).withCount(cachedCounterCount).build());
			}
			else
			{
				logger.log(Level.FINE, String.format(
					"Cache Miss for CounterData Named '%s': value='%s'.  Checking Datastore instead!", counterName,
					cachedCounterCount));
			}
		}

		// /////////////////////////////////////
		// skipCache was true or the count was NOT found in memcache!
		// /////////////////////////////////////

		// Note: No Need to clear the Objectify session cache here because it will be cleared automatically and
		// repopulated upon every request.

		logger.log(
			Level.FINE,
			String.format("Aggregating counts from '%s' CounterDataShards for CounterData named '%s'!",
				counterData.getNumShards(), counterData.getCounterName()));

		// ///////////////////
		// Assemble a List of CounterShardData Keys to retrieve in parallel!
		final List<Key<CounterShardData>> keysToLoad = Lists.newArrayList();
		for (int i = 0; i < counterData.getNumShards(); i++)
		{
			final Key<CounterShardData> counterShardKey = CounterShardData.key(counterData.getTypedKey(), i);
			keysToLoad.add(counterShardKey);
		}

		long sum = 0;

		// For added performance, we could spawn multiple threads to wait for each value to be returned from the
		// DataStore, and then aggregate that way. However, the simple summation below is not very expensive, so
		// creating multiple threads to get each value would probably be overkill. Just let objectify do this for
		// us. Even though we have to wait for all entities to return before summation begins, the summation is a quick
		// in-memory operation with a relatively small number of shards, so parallelizing it would likely not increase
		// performance.

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

		logger
			.log(
				Level.FINE,
				String
					.format(
						"The Datastore is reporting a count of %s for CounterData '%s' count.  Resetting memcache count to %s for this counter name.",
						sum, counterData.getCounterName(), sum));

		final BigInteger bdSum = BigInteger.valueOf(sum);
		try
		{
			// This method will only get here if there was nothing in Memcache, or if the caller requested to skip
			// reading the Counter count from memcache. In these cases, the value in memcache should always be replaced.
			memcacheService.put(memCacheKey, bdSum, config.getDefaultCounterCountExpiration(), SetPolicy.SET_ALWAYS);
		}
		catch (MemcacheServiceException mse)
		{
			// Do nothing. The method will still return even though memcache is not available.
		}

		return Optional.of(new CounterBuilder(counterData).withCount(bdSum).build());
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
				// First, load the incomingCounter from the datastore via a transactional get to ensure it has the
				// proper state.
				final Optional<CounterData> optCounterData = getCounterData(incomingCounter.getCounterName());

				// //////////////
				// ShortCircuit: Can't update a counter that doesn't exist!
				// //////////////
				if (!optCounterData.isPresent())
				{
					throw new NoCounterExistsException(incomingCounter.getCounterName());
				}

				final CounterData counterDataInDatastore = optCounterData.get();
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
					throw new IllegalArgumentException(
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

				// return null to satisfy Java...
				return null;
			}
		});
	}

	/**
	 * Helper method to determine the random shard number for the mutation operation. If {@code optShardNumber} is
	 * present, then this value will be used so long as it is greater-than or equal to zero. Otherwise, a PRNG will be
	 * used to select the next shard number based upon the current number of shards that are allowed for the current
	 * counter as specified by {@code numShards}.
	 *
	 * @param optShardNumber An {@link Optional} instance of {@link Integer} that specifies the shard number to use, if
	 *            present.
	 * @param numShards The number of shards that the mutating counter has available to it for increment/decrement
	 *            operations.
	 * @return
	 */
	@Override
	public int determineRandomShardNumber(final Optional<Integer> optShardNumber, final int numShards)
	{
		Preconditions.checkArgument(numShards > 0);
		Preconditions.checkNotNull(optShardNumber);

		if (optShardNumber.isPresent())
		{
			final int shardNumber = optShardNumber.get();
			if (shardNumber >= 0 && shardNumber < numShards)
			{
				return optShardNumber.get();
			}
		}

		// Otherwise...
		return generator.nextInt(numShards);

	}

	// /////////////////////////////
	// Increment Functions
	// /////////////////////////////

	@Override
	public CounterOperation increment(final String counterName, final long amount)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "Increment amounts must be positive!");

		return this.mutateCounterShard(counterName, amount, Optional.<Integer> absent(), UUID.randomUUID());
	}

	@Override
	public CounterOperation increment(final String counterName, final long amount, final int shardNumber,
			final UUID incrementOperationId)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "Increment amounts must be positive!");
		Preconditions.checkArgument(shardNumber >= 0);
		Preconditions.checkNotNull(incrementOperationId);

		return this.mutateCounterShard(counterName, amount, Optional.of(shardNumber), incrementOperationId);
	}

	// /////////////////////////////
	// Decrementing Functions
	// /////////////////////////////

	@Override
	public CounterOperation decrement(final String counterName, final long amount)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "Decrement amounts must be positive!");

		// Perform the Decrement
		final long decrementAmount = amount * -1L;
		return this.mutateCounterShard(counterName, decrementAmount, Optional.<Integer> absent(), UUID.randomUUID());
	}

	@Override
	public CounterOperation decrement(final String counterName, final long amount, final int shardNumber,
			final UUID decrementOperationUuid)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName));
		Preconditions.checkArgument(amount > 0, "Decrement amounts must be positive!");
		Preconditions.checkArgument(shardNumber >= 0);
		Preconditions.checkNotNull(decrementOperationUuid);

		final long decrementAmount = amount * -1L;
		return this.mutateCounterShard(counterName, decrementAmount, Optional.of(shardNumber), decrementOperationUuid);
	}

	// The following two methods exists for posterity to see one manner in which non-negative counter enforcement might
	// be accomplished (though imperfectly). These are not used because it's not possible to perfectly (nor easily)
	// guarantee non-negative semantics in a sharded-counter because all shards need to be retrieved in order to
	// accurately gauge the count to determine if it will be negative. Since multiple threads may be operating on a
	// given CounterShardData object, we would need to lock the entire counter to make this check, which would defeat
	// the purpose of sharding. After some thought, it's not clear that the "can't go negative" requirement exists for
	// a sharded counter. Additionally, with the #reset method, clients of appengine-counter could enforce this
	// requirement in their own ways.

	// @Override
	// public CounterOperation decrement(final String counterName, final long amount)
	// {
	// Preconditions.checkNotNull(counterName);
	// Preconditions.checkArgument(!StringUtils.isBlank(counterName));
	// Preconditions.checkArgument(amount > 0, "Decrement amounts must be positive!");
	//
	// // A Note About Count-Below-Zero Limiting
	// // --
	// // The mutateCounterShard operation does not have the ability to determine the count of a counter while it's
	// // performing it's work. Additionally, since multiple shards might be operating upon the same counter at the
	// // same time, getting the count of a counter and passing it into the mutateCounterShard operation would not
	// // accurately stop the counter from going below zero. Instead, we perform a preemptive check here to see if
	// // the counter is currently at zero, and throw an exception if that's the case and this request is attempting
	// // to decrement below zero. However, this check may occur while other decrement threads have already made their
	// // way into the mutateCounterShard method (but not completed). Thus, we perform an additional "negative check"
	// // after the decrement is applied, and optionally reset the counter if it has become errantly negative.
	//
	// // Preemptive Negative Check
	// this.assertCounterCanDecrementBelowZero(counterName, amount);
	//
	// // Perform the Decrement
	// final long decrementAmount = amount * -1L;
	// final CounterOperation counterOperation = this.mutateCounterShard(counterName, decrementAmount,
	// Optional.<Integer> absent(), UUID.randomUUID());
	//
	// // Post-Decrement Negative Check
	// this.resetCounterIfErrantlyNegative(counterName, amount);
	//
	// return counterOperation;
	// }

	// /**
	// * Helper method to assert that a counter can decrement below zero.
	// *
	// * @param counterName The name of a counter being decremented.
	// * @param amount The amount of the decrement that is about to be applied to a counter.
	// * @throws CounterNotMutableException If a counter cannot be decremented by the amount specified by {@code
	// amount}.
	// */
	// @VisibleForTesting
	// protected void assertCounterCanDecrementBelowZero(final String counterName, final long amount)
	// {
	// Preconditions.checkNotNull(counterName);
	//
	// final Optional<Counter> optCounter = this.getCounter(counterName);
	// Preconditions.checkArgument(optCounter.isPresent(), "This counter should have existed but didn't!");
	// final Counter counter = optCounter.get();
	// if (!counter.isNegativeCountAllowed()
	// && (counter.getCount().subtract(BigInteger.valueOf(amount))).compareTo(BigInteger.ZERO) < 0)
	// {
	// throw new CounterNotMutableException(counterName, String.format("Cannot decrement counter below zero!"));
	// }
	// }

	// /**
	// * Helper method to reset a counter to zero if it has a negative count but is configured to not allow the counter
	// to
	// * be negative.
	// *
	// * @param counterName The name of a counter being decremented.
	// * @param amount The amount of the decrement that is about to be applied to a counter.
	// * @throws CounterNotMutableException If a counter was negative but was reset to zero. This exception is chosen
	// * because it is assumed that a counter became negative as a part of this request (or at least during
	// * this request).
	// */
	// @VisibleForTesting
	// protected void resetCounterIfErrantlyNegative(final String counterName, final long amount)
	// {
	// Preconditions.checkNotNull(counterName);
	//
	// final Optional<Counter> optCounter = this.getCounter(counterName);
	// Preconditions.checkArgument(optCounter.isPresent(), "This counter should have existed but didn't!");
	// final Counter counter = optCounter.get();
	// if (!counter.isNegativeCountAllowed() && counter.getCount().compareTo(BigInteger.ZERO) < 0)
	// {
	// this.reset(counterName);
	// // This exception is chosen because it is assumed that a counter became negative as a part of this request
	// // (or at least during this request). The above call will set the counter to zero.
	// throw new CounterNotMutableException(counterName, String.format("Cannot decrement counter below zero!"));
	// }
	// }

	@Override
	public void reset(final String counterName)
	{
		Preconditions.checkNotNull(counterName);

		// Use the cache here if possible, because the first two short-circuits don't really need accurate counts to
		// work.
		final Optional<Counter> optCounter = this.getCounter(counterName, USE_CACHE);

		// /////////////
		// ShortCircuit: Do nothing - there's nothing to return.
		// /////////////
		if (!optCounter.isPresent())
		{
			return;
		}

		final Counter counter = optCounter.get();

		// Update the Counter in a New TX to the RESETTING_STATE and apply the TX. The #updateCounterDetails will
		// not succeed if the counter is not in the available state, so no need to double-check that preemptively.
		// The "new" TX ensures that no other thread nor parent transaction is performing this operation at the
		// same time, since if that were the case the updateCounter would fail.
		ObjectifyService.ofy().transactNew(new VoidWork()
		{
			@Override
			public void vrun()
			{
				// Update the status via updateCounterDetails so that we don't accidentally update a counter that's not
				// in the Available status.
				final Counter readOnlyCounter = new CounterBuilder(counter).withCounterStatus(CounterStatus.RESETTING)
					.build();

				// Will throw an exception if the counter status cannot be updated
				updateCounterDetails(readOnlyCounter);
			}
		});

		// Start the try here because if the above fails, we don't want to perform another update the counter, since
		// that would have been the failing method.
		try
		{
			// Perform all reset operations in same TX to provide atomic rollback. Since Appengine now support up to 25
			// entities in a transaction, this will work for all counters up to 25 shards.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				@Override
				public void vrun()
				{
					// For each shard, reset it. Shard Index starts at 0.
					for (int shardNumber = 0; shardNumber < counter.getNumShards(); shardNumber++)
					{
						final ResetCounterShardWork resetWork = new ResetCounterShardWork(counterName, shardNumber);
						ObjectifyService.ofy().transact(resetWork);
					}
				}
			});
		}
		finally
		{
			// Clear the cache to ensure that future callers get the right count.
			this.memcacheSafeDelete(counterName);

			// If the reset-shards loop above completes properly, OR if an exception is thrown above, the counter status
			// needs to be reset to AVAILABLE. In the first case (an exception is thrown) then the reset TX will be
			// rolled-back, and the counter status needs to become AVAILABLE. In the second case (the counter was
			// successfully reset) the same counter status update to AVAILABLE needs to occur.
			ObjectifyService.ofy().transactNew(new VoidWork()
			{
				/**
				 * Updates the {@link CounterData} status to be {@link CounterStatus#AVAILABLE}, but only if the current
				 * status is {@link CounterStatus#RESETTING}.
				 */
				@Override
				public void vrun()
				{
					final Optional<CounterData> optCounterData = getCounterData(counterName);
					if (optCounter.isPresent())
					{
						final CounterData counterData = optCounterData.get();
						if (counterData.getCounterStatus() == CounterStatus.RESETTING)
						{
							counterData.setCounterStatus(CounterStatus.AVAILABLE);
							ObjectifyService.ofy().save().entity(counterData).now();
						}
					}
				}
			});
		}
	}

	/**
	 * Does the work of incrementing or decrementing the value of a single shard for the counter named
	 * {@code counterName}.
	 *
	 * @param counterName
	 * @param amount The amount to mutate a counter shard by. This value will be negative for a decrement, and positive
	 *            for an increment.
	 * @param incrementOperationId
	 * @param optShardNumber An optionally specified shard number to increment. If not specified, a random shard number
	 *            will be chosen.
	 * @return An instance of {@link CounterOperation} with information about the increment/decrement.
	 */
	private CounterOperation mutateCounterShard(final String counterName, final long amount,
			final Optional<Integer> optShardNumber, final UUID incrementOperationId)
	{
		// ///////////
		// Precondition Checks are performed by calling methods.

		// This get is transactional and strongly consistent, but we perform it here so that the increment doesn't have
		// to be part of an XG transaction. Since the CounterData isn't expected to mutate that often, we want
		// increment/decrement operations to be as speedy as possibly. In this way, the IncrementWork can take place in
		// a non-XG transaction.
		final CounterDataGetCreateContainer counterDataGetCreateContainer = this.getOrCreateCounterData(counterName);
		final CounterData counterData = counterDataGetCreateContainer.getCounterData();

		// Create the Work to be done for this increment, which will be done inside of a TX. See
		// "https://developers.google.com/appengine/docs/java/datastore/transactions#Java_Isolation_and_consistency"
		final Work<CounterOperation> atomicIncrementShardWork = new IncrementShardWork(counterData,
			incrementOperationId, optShardNumber, amount, false);

		// Note that this operation is idempotent from the perspective of a ConcurrentModificationException. In that
		// case, the increment operation will fail and will not have been applied. An Objectify retry of the
		// increment/decrement will occur, however and a successful increment/decrement will only ever happen once (if
		// the Appengine datastore is functioning properly). See the Javadoc in the API about DatastoreTimeoutException
		// or
		// DatastoreFailureException in cases where transactions have been committed and eventually will be applied
		// successfully."

		// We use the "counterShardOperationInTx" to force this thread to wait until the work inside of
		// "atomicIncrementShardWork" completes. This is because we don't want to increment memcache (below) until after
		// that operation's transaction successfully commits.
		final CounterOperation counterShardOperationInTx = ObjectifyService.ofy().transact(atomicIncrementShardWork);

		// /////////////////
		// Try to increment this counter in memcache atomically, but only if we're not inside of a parent caller's
		// transaction. If that's the case, then we can't know if the parent TX will fail upon commit, which would
		// happen after our call to memcache.
		// /////////////////
		if (isParentTransactionActive())
		{
			// If a parent-transaction is active, then don't update memcache. Instead, clear it out since we can't know
			// if the parent commit will actually stick.
			this.memcacheSafeDelete(counterName);
		}
		else
		{
			// Otherwise, try to increment memcache. If the memcache operation fails, it's ok because memcache is merely
			// a cache of the actual count data, and will eventually become accurate when the cache is reloaded via a
			// call to #getCount.
			long amountToMutateCache = counterShardOperationInTx.getAppliedAmount();
			if (amount < 0)
			{
				amountToMutateCache *= -1L;
			}
			this.incrementMemcacheAtomic(counterName, amountToMutateCache);
		}

		return counterShardOperationInTx;
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
			throw new IllegalArgumentException(msg);
		}
		else
		{
			// Assemble a list of CounterShard keys, and delete them all in a batch!
			final Collection<Key<CounterShardData>> counterShardDataKeys = Lists.newArrayList();
			for (int i = 0; i < counterData.getNumShards(); i++)
			{
				final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, i);
				counterShardDataKeys.add(counterShardDataKey);

				// Delete all CounterShardOperationData entries for this Shard. If the taskqueue times out, this
				// operation will be retried, which will continue deleting the counter operations for this counter.
				ObjectifyService.ofy().transactionless().delete().type(CounterShardOperationData.class)
					.parent(counterShardDataKey);
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
	 * A private implementation of {@link Work} that increments the {@code incrementAmount} of a
	 * {@link CounterShardData} by a specified non-negative {@code incrementAmount}.
	 */
	@VisibleForTesting
	final class IncrementShardWork implements Work<CounterOperation>
	{
		private final CounterData counterData;
		private final UUID counterShardOperationUuid;
		private final Optional<Integer> optShardNumber;
		private final long incrementAmount;
		private final boolean newCounterCreated;

		/**
		 * Required-Args Constructor.
		 *
		 * @param counterData A {@link CounterData} that contains information about the counter being incremented.
		 * @param counterShardOperationUuid The unique identifier of the job performing the increment.
		 * @param optShardNumber An optionally supplied shard number that should an increment/decrement should be
		 *            applied to. If specified as {@link Optional#absent()} , then a random shard will be selected.
		 * @param incrementAmount A long representing the amount of the increment to be applied.
		 * @param newCounterCreated {@code true} if a new counter was created to perform this operation; {@code false}
		 *            otherwise.
		 */
		IncrementShardWork(final CounterData counterData, final UUID counterShardOperationUuid,
				final Optional<Integer> optShardNumber, final long incrementAmount, boolean newCounterCreated)
		{
			Preconditions.checkNotNull(counterData);
			this.counterData = counterData;

			Preconditions.checkNotNull(counterShardOperationUuid);
			this.counterShardOperationUuid = counterShardOperationUuid;

			Preconditions.checkArgument(incrementAmount != 0,
				"Counter increment amounts must be positive or negative numbers!");
			this.incrementAmount = incrementAmount;

			Preconditions.checkNotNull(optShardNumber);
			this.optShardNumber = optShardNumber;

			this.newCounterCreated = newCounterCreated;
		}

		/**
		 * NOTE: In order for this to work properly, the CounterShardData must be gotten, created, and updated all in
		 * the same transaction in order to remain consistent (in other words, it must be atomic).
		 *
		 * @return An instance of {@link CounterOperation} containing the results of this increment.
		 * @throws ArithmeticException if after adding {@code incrementAmount} to the current shard's amount, an
		 *             Overflow or Underflow would occur.
		 */
		@Override
		public CounterOperation run()
		{
			final String counterName = counterData.getCounterName();

			// Callers may specify a random shard number. If not specified, then choose the shard randomly from the
			// available shards.
			final int shardNumber = determineRandomShardNumber(optShardNumber, counterData.getNumShards());

			// If this operation exists in the datastore, then don't apply it again. This is not expensive (i.e., not
			// XG) because this lookup will be for an entity in the same entity group as the ultimate shard increment
			// operation. In fact, they're the exact same entity.
			if (counterShardOperationAlreadyExists(counterData.getTypedKey(), shardNumber))
			{
				final String msg = String
					.format(
						"CounterShardOperation '%s' for CounterShard %s on Counter '%s' already exists and will not be applied again!  CounterData was: %s",
						counterShardOperationUuid, shardNumber, counterName, counterData);
				throw new IllegalArgumentException(msg);
			}

			// Increments/Decrements can only occur on Counters with a counterStatus of AVAILABLE.
			assertCounterAmountMutatable(counterData.getCounterName(), counterData.getCounterStatus());

			final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterData.getTypedKey(),
				shardNumber);

			// Load the Shard from the DS.
			CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardDataKey).now();
			if (counterShardData == null)
			{
				// Create a new CounterShardData in memory. No need to preemptively save to the Datastore until the very
				// end.
				counterShardData = new CounterShardData(counterName, shardNumber);
			}

			// Increment the count by {incrementAmount}, but only if there will be no overflow/underflow, since If it
			// overflows, it goes back to the minimum value and continues from there. If it underflows, it goes back to
			// the maximum value and continues from there..
			final long newAmount = LongMath.checkedAdd(counterShardData.getCount(), incrementAmount);
			counterShardData.setCount(newAmount);
			counterShardData.setUpdatedDateTime(DateTime.now(DateTimeZone.UTC));

			final String msg = String
				.format(
					"About to update CounterShardData (%s-->Shard-%s) with current incrementAmount %s and new incrementAmount %s",
					counterName, shardNumber, counterShardData.getCount(), newAmount);
			getLogger().log(Level.FINE, msg);

			// Save Both CounterShardData and CounterShardOperationData together in the same TX.
			final CounterOperationType counterOperationType = incrementAmount < 0 ? CounterOperationType.DECREMENT
				: CounterOperationType.INCREMENT;
			final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(
				counterShardData.getTypedKey(), this.counterShardOperationUuid, counterOperationType,
				Math.abs(incrementAmount), counterShardData.getUpdatedDateTime());
			final CounterShardData savableCounterShardData = counterShardData;

			// We run these in a Transaction because they are in the same entity group and should be stored together.
			// Additionally, this is compatible with a parent-tx.

			try
			{
				ObjectifyService.ofy().transact(new VoidWork()
				{
					@Override
					public void vrun()
					{
						ObjectifyService.ofy().save().entities(savableCounterShardData);
						ObjectifyService.ofy().save().entities(counterShardOperationData);
					}
				});
			}
			catch (DatastoreFailureException | DatastoreTimeoutException dse)
			{
				logger
					.log(
						Level.SEVERE,
						String
							.format(
								"Counter Shard Mutation '%s' for Shard Number %s on Counter '%s' may or may not have completed!  Error: %s",
								counterShardOperationUuid, shardNumber, counterData.getCounterName(), dse.getMessage()),
						dse);
				// Throw this for now. In the future, we may implement retry logic.
				throw dse;
			}

			return new CounterOperation.Impl(counterShardOperationUuid, counterShardDataKey, counterOperationType,
				Math.abs(incrementAmount), counterShardData.getUpdatedDateTime(), newCounterCreated);
		}

		@VisibleForTesting
		boolean counterShardOperationAlreadyExists(final Key<CounterData> counterDataKey, final int shardNumber)
		{
			Preconditions.checkNotNull(counterDataKey);
			Preconditions.checkArgument(shardNumber >= 0);

			final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, shardNumber);
			final Key<CounterShardOperationData> counterShardOperationDataKey = CounterShardOperationData.key(
				counterShardDataKey, counterShardOperationUuid);

			// See "https://groups.google.com/forum/#!searchin/objectify-appengine/exist/objectify-appengine/zFI2YWP5DTI
			// /BpwFNlVQo1UJ". This methodolody will be less expensive, and strongly-consistent, but will be slightly
			// slower than a simple index query since it loads the entire entity.
			return ObjectifyService.ofy().load().key(counterShardOperationDataKey).now() != null;
		}
	}

	/**
	 * A private implementation of {@link Work} that sets a CounterShard's amount to zero.
	 */
	@VisibleForTesting
	final static class ResetCounterShardWork extends VoidWork
	{
		private final String counterName;
		private final int shardNumber;

		/**
		 * Required-Args Constructor.
		 *
		 * @param counterName A {@link String} that identifies the name of the counter being incremented.
		 * @param shardNumber The index of the shard number to reset to zero.
		 */
		ResetCounterShardWork(final String counterName, int shardNumber)
		{
			Preconditions.checkNotNull(counterName);
			Preconditions.checkArgument(!StringUtils.isBlank(counterName));
			this.counterName = counterName;

			Preconditions.checkArgument(shardNumber >= 0);
			this.shardNumber = shardNumber;
		}

		/**
		 * Reset the indicated CounterShardData 'count' to zero.
		 *
		 * @return
		 */
		@Override
		public void vrun()
		{
			final Key<CounterData> counterDataKey = CounterData.key(counterName);
			final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, shardNumber);
			final CounterShardData counterShardData = ObjectifyService.ofy().load().key(counterShardDataKey).now();
			if (counterShardData == null)
			{
				return;
			}
			else
			{
				counterShardData.setCount(0L);
				counterShardData.setUpdatedDateTime(DateTime.now(DateTimeZone.UTC));
				logger.log(Level.FINE, String.format("Resetting CounterShardData (%s-->ShardNum: %s) to zero!",
					counterShardDataKey, shardNumber, counterShardData.getCount()));
				ObjectifyService.ofy().save().entities(counterShardData).now();
			}
		}
	}

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
	BigInteger memcacheSafeGet(final String memcacheKey)
	{
		Preconditions.checkNotNull(memcacheKey);

		BigInteger cachedCounterCount;
		try
		{
			cachedCounterCount = (BigInteger) memcacheService.get(memcacheKey);
		}
		catch (MemcacheServiceException mse)
		{
			// Do nothing. This merely indicates that memcache was unreachable, which is fine. If it's
			// unreachable, there's likely nothing in the cache anyway, but in any case there's nothing we can do here.
			cachedCounterCount = null;
		}
		return cachedCounterCount;
	}

	/**
	 * Helper method to get the {@link CounterData} associated with the supplied {@code counterName}.
	 *
	 * @param counterName
	 * @return
	 */
	@VisibleForTesting
	protected Optional<CounterData> getCounterData(final String counterName)
	{
		Preconditions.checkNotNull(counterName);

		final Key<CounterData> counterKey = CounterData.key(counterName);
		final CounterData counterData = ObjectifyService.ofy().load().key(counterKey).now();

		return Optional.fromNullable(counterData);
	}

	/**
	 * Helper method to create the {@link CounterData} associated with the supplied counter information.
	 *
	 * @param counterName
	 * @return
	 * @throws IllegalArgumentException If the {@code counterName} is invalid.
	 * @throws CounterExistsException If the counter with {@code counterName} already exists in the Datastore.
	 */
	@VisibleForTesting
	protected CounterData createCounterData(final String counterName)
	{
		this.counterNameValidator.validateCounterName(counterName);

		final Key<CounterData> counterKey = CounterData.key(counterName);

		// Perform a transactional GET to see if the counter exists. If it does, throw an exception. Otherwise, create
		// the counter in the same TX.
		return ObjectifyService.ofy().transact(new Work<CounterData>()
		{
			@Override
			public CounterData run()
			{
				final CounterData loadedCounterData = ObjectifyService.ofy().load().key(counterKey).now();
				if (loadedCounterData == null)
				{
					final CounterData counterData = new CounterData(counterName, config.getNumInitialShards());
					// counterData.setNegativeCountAllowed(config.isNegativeCountAllowed());
					ObjectifyService.ofy().save().entity(counterData).now();
					return counterData;
				}
				else
				{
					throw new CounterExistsException(counterName);
				}
			}
		});
	}

	// Used to indicate that, for a given operation, a CounterData already existed, so no new counter was created.
	private static final boolean COUNTER_EXISTED = false;
	// Used to indicate that, for a given operation, a new CounterData was created.
	private static final boolean NEW_COUNTER_CREATED = true;

	/**
	 * Helper method to get (or create and then get) a {@link CounterData} from the Datastore with a given name. The
	 * result of this function is guaranteed to be non-null if no exception is thrown.
	 *
	 * @param counterName
	 * @return
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws InvalidCounterNameException If the counter name is too long or too short.
	 */
	@VisibleForTesting
	protected CounterDataGetCreateContainer getOrCreateCounterData(final String counterName)
	{
		// Validation failures will throw an exception. See JavaDoc for details.
		this.counterNameValidator.validateCounterName(counterName);

		final Key<CounterData> counterKey = CounterData.key(counterName);

		// This get/create is acceptable to perform outside of a transaction. In certain edge-cases, it's possible
		// that two threads might call this operation at the same time, and one or both threads might think (errantly)
		// that
		// no CounterData exists in the Datastore when in reality one of the threads (or some other thread) may have
		// created the CoutnerData already. However, in this case, the two create operations will be the same using
		// default
		// data, so even though the "winner" will be random, the result will be the same. In future, this assumption
		// may need to change.
		CounterData counterData = ObjectifyService.ofy().load().key(counterKey).now();
		if (counterData == null)
		{
			counterData = new CounterData(counterName, config.getNumInitialShards());
			// counterData.setNegativeCountAllowed(config.isNegativeCountAllowed());
			ObjectifyService.ofy().save().entity(counterData).now();
			return new CounterDataGetCreateContainer(counterData, NEW_COUNTER_CREATED);
		}
		else
		{
			return new CounterDataGetCreateContainer(counterData, COUNTER_EXISTED);
		}
	}

	/**
	 * A helper object to indicate if a new counter was created after calling {@link #getOrCreateCounterData}.
	 */
	@Getter
	@ToString
	@EqualsAndHashCode
	@VisibleForTesting
	protected static final class CounterDataGetCreateContainer
	{
		private final CounterData counterData;
		private final boolean newCounterDataCreated;

		/**
		 * Required-args Constructor.
		 *
		 * @param counterData
		 * @param newCounterDataCreated
		 */
		private CounterDataGetCreateContainer(final CounterData counterData, final boolean newCounterDataCreated)
		{
			Preconditions.checkNotNull(counterData);
			this.counterData = counterData;

			this.newCounterDataCreated = newCounterDataCreated;
		}
	}

	private static final int NUM_RETRIES_LIMIT = 10;

	/**
	 * Increment the memcache version of the named-counter by {@code amount} (positive or negative) in an atomic
	 * fashion. Use memcache as a Semaphore/Mutex, and retry up to 10 times if other threads are attempting to update
	 * memcache at the same time. If nothing is in Memcache when this function is called, then do nothing because only
	 * #getCounter should "put" a value to memcache. Additionally, if this operation fails, the cache will be
	 * re-populated after a configurable amount of time, so the count will eventually become correct.
	 *
	 * @param counterName
	 * @param amount
	 * @return The new count of this counter as reflected by memcache
	 */
	@VisibleForTesting
	protected Optional<Long> incrementMemcacheAtomic(final String counterName, final long amount)
	{
		Preconditions.checkNotNull(counterName);

		// Get the cache counter at a current point in time.
		String memCacheKey = this.assembleCounterKeyforMemcache(counterName);

		for (int currentRetry = 0; currentRetry < NUM_RETRIES_LIMIT; currentRetry++)
		{
			try
			{
				final IdentifiableValue identifiableCounter = memcacheService.getIdentifiable(memCacheKey);
				// See Javadoc about a null identifiableCounter. If it's null, then the named counter doesn't exist in
				// memcache.
				if (identifiableCounter == null
					|| (identifiableCounter != null && identifiableCounter.getValue() == null))
				{
					final String msg = "No identifiableCounter was found in Memcache.  Unable to Atomically increment for CounterName '%s'.  Memcache will be populated on the next called to getCounter()!";
					logger.log(Level.FINEST, String.format(msg, counterName));

					// This will return an absent value. Only #getCounter should "put" a value to memcache.
					break;
				}

				// If we get here, the count exists in memcache, so it can be atomically incremented/decremented.
				final BigInteger cachedCounterAmount = (BigInteger) identifiableCounter.getValue();
				final long newMemcacheAmount = cachedCounterAmount.longValue() + amount;

				logger.log(Level.FINEST, String.format("Just before Atomic Increment of %s, Memcache has value: %s",
					amount, identifiableCounter.getValue()));

				if (memcacheService.putIfUntouched(counterName, identifiableCounter,
					BigInteger.valueOf(newMemcacheAmount), config.getDefaultCounterCountExpiration()))
				{
					logger.log(Level.FINEST,
						String.format("MemcacheService.putIfUntouched SUCCESS! with value: %s", newMemcacheAmount));

					// If we get here, the put succeeded...
					return Optional.of(new Long(newMemcacheAmount));
				}
				else
				{
					logger.log(Level.WARNING, String.format(
						"Unable to update memcache counter atomically.  Retrying %s more times...",
						(NUM_RETRIES_LIMIT - currentRetry)));
					continue;
				}
			}
			catch (MemcacheServiceException mse)
			{
				// Check and post-decrement the numRetries counter in one step
				if ((currentRetry + 1) < NUM_RETRIES_LIMIT)
				{
					logger.log(Level.WARNING, String.format(
						"Unable to update memcache counter atomically.  Retrying %s more times...",
						(NUM_RETRIES_LIMIT - currentRetry)));

					// Keep trying...
					continue;
				}
				else
				{
					// Evict the counter here, and let the next call to getCounter populate memcache
					final String logMessage = "Unable to update memcache counter atomically, with no more allowed retries.  Evicting counter named '%s' from the cache!";
					logger.log(Level.SEVERE, String.format(logMessage, (NUM_RETRIES_LIMIT - currentRetry)), mse);

					this.memcacheSafeDelete(memCacheKey);
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
	 * @return
	 */
	@VisibleForTesting
	protected void assertCounterAmountMutatable(final String counterName, final CounterStatus counterStatus)
	{
		if (counterStatus != CounterStatus.AVAILABLE)
		{
			final String msg = String
				.format(
					"Can't mutate the amount of counter '%s' because it's currently in the %s state but must be in in the %s state!",
					counterName, counterStatus.name(), CounterStatus.AVAILABLE);
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * Helper method to determine if a counter's incrementAmount can be mutated (incremented or decremented). In order
	 * for that to happen, the counter's status must be {@link CounterStatus#AVAILABLE}.
	 *
	 * @param counterName
	 * @param counterStatus
	 * @return
	 */
	@VisibleForTesting
	protected void assertCounterDetailsMutatable(final String counterName, final CounterStatus counterStatus)
	{
		if (counterStatus != CounterStatus.AVAILABLE && counterStatus != CounterStatus.READ_ONLY_COUNT)
		{
			final String msg = String.format("Can't mutate with status %s.  Counter must be in in the %s or %s state!",
				counterStatus, CounterStatus.AVAILABLE, CounterStatus.READ_ONLY_COUNT);
			throw new CounterNotMutableException(counterName, msg);
		}
	}

	@VisibleForTesting
	protected boolean isParentTransactionActive()
	{
		return ObjectifyService.ofy().getTransaction() == null ? false : ObjectifyService.ofy().getTransaction()
			.isActive();
	}

	/**
	 * When a counter is in certain statuses, its count can not be determined for various reasons. For example, if the
	 * number of shards is contracting from 5 to 4, then the total summation of all shards will not be possible to
	 * determine since counts from shard 5 may have been applied to shards 5 and 4, whereas the actual "number of
	 * shards" value in the CoutnerData may not have been updated to indicate this change. Thus, for certain
	 * {@link CounterStatus} values, we cannot provide a valid count, and instead return {@link BigInteger#ZERO}.
	 *
	 * @param counterStatus
	 * @return
	 */
	protected boolean counterStatusYieldsIndeterminateCount(final CounterStatus counterStatus)
	{
		Preconditions.checkNotNull(counterStatus);
		return counterStatus == CounterStatus.DELETING || counterStatus == CounterStatus.CONTRACTING_SHARDS
			|| counterStatus == CounterStatus.RESETTING || counterStatus == CounterStatus.EXPANDING_SHARDS;
	}

	/**
	 * An implementation of {@link CounterNameValidator} that validates counter name existence and length.
	 */
	private static class DefaultCounterNameValidator implements CounterNameValidator
	{
		/**
		 * The main validation for counter names. Ensures that a counter name does not exceed 500 characters, but is not
		 * blank, empty, or null.
		 *
		 * @param counterName The name of a counter being operated upon.
		 * @throws NullPointerException if the {@code counterName} is null.
		 * @throws InvalidCounterNameException If the counter name is too long or too short.
		 */
		@Override
		public void validateCounterName(final String counterName)
		{
			final String preconditionMessage = "Counter names must not exceed 500 characters, and may not be null, blank, nor empty!";
			Preconditions.checkArgument(!StringUtils.isBlank(counterName), preconditionMessage);
			if (StringUtils.length(counterName) > 500)
			{
				throw new InvalidCounterNameException(preconditionMessage);
			}
		}
	}

}
