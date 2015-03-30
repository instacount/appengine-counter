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
package com.theupswell.appengine.counter;

import java.math.BigInteger;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.service.ShardedCounterServiceConfiguration;

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
	private final String counterName;
	private final String counterDescription;
	// The number of shards available for this counter.
	private final int numShards;
	private final CounterData.CounterStatus counterStatus;
	private final BigInteger count;
	private final CounterIndexes indexes;

	/**
	 * Required-args Constructor. Sets the {@code counterStatus} to
	 * {@link com.theupswell.appengine.counter.data.CounterData.CounterStatus#AVAILABLE} and the {@code count} to zero.
	 *
	 * @param counterName
	 */
	public Counter(final String counterName)
	{
		this(counterName, null);
	}

	/**
	 * Required-args Constructor. Sets the {@code counterStatus} to
	 * {@link com.theupswell.appengine.counter.data.CounterData.CounterStatus#AVAILABLE} and the {@code count} to zero.
	 * 
	 * @param counterName
	 * @param counterDescription
	 */
	public Counter(final String counterName, final String counterDescription)
	{
		this(counterName, counterDescription, ShardedCounterServiceConfiguration.DEFAULT_NUM_COUNTER_SHARDS,
			CounterData.CounterStatus.AVAILABLE, CounterIndexes.none());
	}

	/**
	 * Required-args Constructor. Sets the {@code count} to zero.
	 * 
	 * @param counterName
	 * @param counterStatus
	 * @param numShards
	 * @param counterStatus
	 */
	public Counter(final String counterName, final String counterDescription, final int numShards,
			final CounterData.CounterStatus counterStatus, final CounterIndexes indexes)
	{
		this(counterName, counterDescription, numShards, counterStatus, BigInteger.ZERO, indexes);
	}

	/**
	 * Required-args Constructor.
	 * 
	 * @param counterName
	 * @param counterDescription
	 * @param counterStatus
	 * @param count
	 * @param indexes
	 */
	public Counter(final String counterName, final String counterDescription, final int numShards,
			final CounterStatus counterStatus, final BigInteger count, final CounterIndexes indexes)
	{
		Preconditions.checkArgument(!StringUtils.isBlank(counterName), "CounterName may not be empty, blank, or null!");
		Preconditions.checkNotNull(counterStatus);

		this.counterName = counterName;
		this.counterDescription = counterDescription;
		this.numShards = numShards;
		this.counterStatus = counterStatus;
		this.count = count;

		// Set to none if not specified
		this.indexes = indexes == null ? CounterIndexes.none() : indexes;
	}
}
