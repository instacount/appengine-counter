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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.memcache.Expiration;
import com.google.common.base.Preconditions;
import com.theupswell.annotations.Immutable;

/**
 * A Configuration class for {@link ShardedCounterService}.
 *
 * @author David Fuelling
 */
@Getter
@ToString
@EqualsAndHashCode
@Immutable
public class ShardedCounterServiceConfiguration
{
	// The number of shards to begin with for this counter.
	public static final int DEFAULT_NUM_COUNTER_SHARDS = 3;

	static final int SIXTY_SECONDS = 60;

	// static final int FIVE_MINUTES_IN_SECONDS = SIXTY_SECONDS * 5;

	static final int SIXTY_MINUTES_IN_SECONDS = 60 * SIXTY_SECONDS;

	// The timeout for any entities annotated with Objectify's @Cache annotation, which will be stored in memcache via
	// Objectify's session cache. CounterShards do not have this annotation, but counter data and other infrequently
	// updated entities have this value. Per the objectify docs, "There is still, however, one circumstance in which the
	// cache could go out of synchronization with the datastore: If your requests are cut off by
	// DeadlineExceededException." Thus, we limit the caching of this information to 1 hour, by default, since we don't
	// expect this condition to occur very often, but just in case.
	public static final int OBJECTIFY_ENTITY_CACHE_TIMEOUT = SIXTY_MINUTES_IN_SECONDS;

	// Each Counter's count is cached in memcache upon a "get count". Additionally, increments and decrements mutate the
	// cache, so we expect the memcache version of the count to be highly accurate. If memcache is having problems or is
	// down, the counter will be loaded from the datastore, repopulating the cache accurately. Thus, there's a small
	// chance that the cache will go out of sync, but a 1 hour cache expiration is suitable for an
	// eventually-consistency model.
	static final Expiration DEFAULT_COUNTER_COUNT_CACHE_EXPIRATION = Expiration
		.byDeltaSeconds(SIXTY_MINUTES_IN_SECONDS);

	// The number of counter shards to create when a new counter is created. The default value is 3.
	private final int numInitialShards;

	// The default Memcache expiration for counter objects.
	private final Expiration defaultCounterCountExpiration;

	// // Set to true to store each increment/decrement with a unique identifier
	// private final boolean storeCounterShardOperations;

	// The name of the queue that will be used to delete shards in an async fashion
	private final String deleteCounterShardQueueName;

	// The optional value of {@link TaskBuilder#url} when interacting with the queue used to delete CounterShards.
	private final String relativeUrlPathForDeleteTaskQueue;

	// Set to true to allow counter counts to decrement below zero.
	// private final boolean negativeCountAllowed;

	/**
	 * The default constructor for building a ShardedCounterService configuration class. Private so that only the
	 * builder can build this class.
	 *
	 * @param builder
	 */
	private ShardedCounterServiceConfiguration(Builder builder)
	{
		Preconditions.checkNotNull(builder);
		this.numInitialShards = builder.numInitialShards;
		this.deleteCounterShardQueueName = builder.deleteCounterShardQueueName;
		this.relativeUrlPathForDeleteTaskQueue = builder.relativeUrlPathForDeleteTaskQueue;
		this.defaultCounterCountExpiration = builder.getDefaultCounterCountExpiration();
		// this.negativeCountAllowed = builder.isNegativeCountAllowed();
	}

	/**
	 * Constructs a {@link ShardedCounterServiceConfiguration} object with default values.
	 *
	 * @return
	 */
	public static ShardedCounterServiceConfiguration defaultConfiguration()
	{
		ShardedCounterServiceConfiguration.Builder builder = new ShardedCounterServiceConfiguration.Builder();
		return new ShardedCounterServiceConfiguration(builder);
	}

	/**
	 * A Builder for {@link ShardedCounterServiceConfiguration}.
	 *
	 * @author david
	 */
	public static final class Builder
	{
		@Getter
		@Setter
		private int numInitialShards;

		/**
		 * Note: {@code null} may be used here to specify no specific expiration.
		 */
		@Getter
		@Setter
		private Expiration defaultCounterCountExpiration;

		@Getter
		@Setter
		private String deleteCounterShardQueueName;

		@Getter
		@Setter
		private String relativeUrlPathForDeleteTaskQueue;

		/**
		 * Default Constructor. Sets up this buildr with 1 shard by default.
		 */
		public Builder()
		{
			this.numInitialShards = DEFAULT_NUM_COUNTER_SHARDS;
			// The default expiration for Counter counts in memcache. See comment with variable declaration.
			this.defaultCounterCountExpiration = DEFAULT_COUNTER_COUNT_CACHE_EXPIRATION;
		}

		public Builder withNumInitialShards(int numInitialShards)
		{
			Preconditions.checkArgument(numInitialShards > 0,
				"Number of Shards for a new CounterData must be greater than 0!");
			this.numInitialShards = numInitialShards;
			return this;
		}

		public Builder withDeleteCounterShardQueueName(String deleteCounterShardQueueName)
		{
			Preconditions.checkArgument(!StringUtils.isBlank(deleteCounterShardQueueName));
			this.deleteCounterShardQueueName = deleteCounterShardQueueName;
			return this;
		}

		public Builder withRelativeUrlPathForDeleteTaskQueue(String relativeUrlPathForDeleteTaskQueue)
		{
			Preconditions.checkArgument(!StringUtils.isBlank(relativeUrlPathForDeleteTaskQueue));
			this.relativeUrlPathForDeleteTaskQueue = relativeUrlPathForDeleteTaskQueue;
			return this;
		}

		/**
		 * Method to build a new {@link ShardedCounterServiceConfiguration}.
		 *
		 * @return
		 */
		public ShardedCounterServiceConfiguration build()
		{
			return new ShardedCounterServiceConfiguration(this);
		}

	}

}
