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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;

@Data
@RequiredArgsConstructor
public class CounterBuilder
{
	@NonNull
	private final String counterName;

	private String counterDescription;
	private int numShards;
	private CounterStatus counterStatus = CounterStatus.AVAILABLE;
	private BigInteger count;
	private boolean negativeCountAllowed;
	private CounterIndexes indexes;

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @param counterData
	 * @return
	 */
	public CounterBuilder(final CounterData counterData)
	{
		Preconditions.checkNotNull(counterData);

		this.counterName = counterData.getCounterName();

		this.setCounterDescription(counterData.getCounterDescription());
		this.setNumShards(counterData.getNumShards());
		this.setCounterStatus(counterData.getCounterStatus());
		this.setIndexes(counterData.getIndexes());
		this.setNegativeCountAllowed(counterData.isNegativeCountAllowed());
	}

	/**
	 * Build method for constructing a new Counter.
	 *
	 * @param counter
	 * @return
	 */
	public CounterBuilder(final Counter counter)
	{
		Preconditions.checkNotNull(counter);

		this.counterName = counter.getCounterName();

		// Use setters to enforce Preconditions...
		this.setCounterDescription(counter.getCounterDescription());
		this.setNumShards(counter.getNumShards());
		this.setCounterStatus(counter.getCounterStatus());
		this.setCount(counter.getCount());
		this.setIndexes(counter.getIndexes());
		this.setNegativeCountAllowed(counter.isNegativeCountAllowed());
	}

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @return
	 */
	public Counter build()
	{
		return new Counter(this.getCounterName(), this.getCounterDescription(), this.getNumShards(),
			this.getCounterStatus(), this.getCount(), this.getIndexes(), this.isNegativeCountAllowed());
	}

	/**
	 *
	 * @param counterDescription
	 * @return
	 */
	public CounterBuilder withCounterDescription(final String counterDescription)
	{
		this.setCounterDescription(counterDescription);
		return this;
	}

	/**
	 *
	 * @param numShards
	 * @return
	 */
	public CounterBuilder withNumShards(final int numShards)
	{
		Preconditions.checkArgument(numShards > 0);
		this.setNumShards(numShards);
		return this;
	}

	/**
	 * 
	 * @param counterStatus
	 * @return
	 */
	public CounterBuilder withCounterStatus(final CounterData.CounterStatus counterStatus)
	{
		Preconditions.checkNotNull(counterStatus);
		this.setCounterStatus(counterStatus);
		return this;
	}

	/**
	 * 
	 * @param count
	 * @return
	 */
	public CounterBuilder withCount(final BigInteger count)
	{
		Preconditions.checkNotNull(count);

		this.setCount(count);
		return this;
	}

	/**
	 * Determines if a counter is allowed to decrement below zero.
	 * 
	 * @param negativeCountAllowed if this counter may decrement below zero; {@code false} if the counter's count may
	 *            not decrement below zero.
	 * @return
	 */
	public CounterBuilder withNegativeCountAllowed(final boolean negativeCountAllowed)
	{
		this.negativeCountAllowed = negativeCountAllowed;
		return this;
	}

	public static final Counter zero(final String counterName)
	{
		return new CounterBuilder(counterName).build();
	}
}
