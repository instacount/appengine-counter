/**
 * Copyright (C) 2016 Instacount Inc. (developers@instacount.io)
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
package io.instacount.appengine.counter;

import java.math.BigInteger;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Preconditions;
import io.instacount.appengine.counter.data.CounterData;
import io.instacount.appengine.counter.data.CounterData.CounterIndexes;
import io.instacount.appengine.counter.data.CounterData.CounterStatus;
import io.instacount.appengine.counter.service.ShardedCounterServiceConfiguration;

/**
 * An immutable object that stores count information.
 *
 * @author David Fuelling
 */
@Data
@EqualsAndHashCode
@ToString
public class Counter
{
	private final String name;
	private final String description;
	// The number of shards available for this counter.
	private final int numShards;
	private final CounterData.CounterStatus counterStatus;
	private final BigInteger count;
	private final CounterIndexes indexes;
	private final DateTime creationDateTime;

	/**
	 * Required-args Constructor. Sets the {@code counterStatus} to
	 * {@link CounterData.CounterStatus#AVAILABLE} and the {@code count} to zero.
	 *
	 * @param name
	 */
	public Counter(final String name)
	{
		this(name, null);
	}

	/**
	 * Required-args Constructor. Sets the {@code counterStatus} to
	 * {@link CounterData.CounterStatus#AVAILABLE} and the {@code count} to zero.
	 *
	 * @param name
	 * @param description
	 */
	public Counter(final String name, final String description)
	{
		this(name, description, ShardedCounterServiceConfiguration.DEFAULT_NUM_COUNTER_SHARDS,
			CounterData.CounterStatus.AVAILABLE, CounterIndexes.none());
	}

	/**
	 * Required-args Constructor. Sets the {@code count} to zero.
	 *
	 * @param name
	 * @param counterStatus
	 * @param numShards
	 * @param counterStatus
	 */
	public Counter(final String name, final String description, final int numShards,
			final CounterData.CounterStatus counterStatus, final CounterIndexes indexes)
	{
		this(name, description, numShards, counterStatus, BigInteger.ZERO, indexes, DateTime
			.now(DateTimeZone.UTC));
	}

	/**
	 * Required-args Constructor.
	 * 
	 * @param name
	 * @param description
	 * @param counterStatus
	 * @param count
	 * @param indexes
	 * @param creationDateTime The {@link DateTime} that this counter was created.
	 */
	public Counter(final String name, final String description, final int numShards,
			final CounterStatus counterStatus, final BigInteger count, final CounterIndexes indexes,
			final DateTime creationDateTime)
	{

		Preconditions.checkArgument(!StringUtils.isBlank(name), "CounterName may not be empty, blank, or null!");
		Preconditions.checkNotNull(counterStatus);

		this.name = name;
		this.description = description;
		this.numShards = numShards;
		this.counterStatus = counterStatus;
		this.count = count;
		this.creationDateTime = creationDateTime;

		// Set to none if not specified
		this.indexes = indexes == null ? CounterIndexes.none() : indexes;
	}
}
