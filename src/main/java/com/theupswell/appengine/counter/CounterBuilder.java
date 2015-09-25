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

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;

@Data
@RequiredArgsConstructor
public class CounterBuilder
{
	@NonNull
	private final String name;

	private String description;
	private int numShards;
	private CounterStatus counterStatus = CounterStatus.AVAILABLE;
	private BigInteger count;
	private CounterIndexes indexes;
	private DateTime creationDateTime;

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @param counterData
	 * @return
	 */
	public CounterBuilder(final CounterData counterData)
	{
		Preconditions.checkNotNull(counterData);

		this.name = counterData.getName();

		this.setDescription(counterData.getDescription());
		this.setNumShards(counterData.getNumShards());
		this.setCounterStatus(counterData.getCounterStatus());
		this.setIndexes(counterData.getIndexes());
		this.setCreationDateTime(counterData.getCreationDateTime());
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

		this.name = counter.getName();

		// Use setters to enforce Preconditions...
		this.setDescription(counter.getDescription());
		this.setNumShards(counter.getNumShards());
		this.setCounterStatus(counter.getCounterStatus());
		this.setCount(counter.getCount());
		this.setIndexes(counter.getIndexes());
		this.setCreationDateTime(counter.getCreationDateTime());
	}

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @return
	 */
	public Counter build()
	{
		return new Counter(this.getName(), this.getDescription(), this.getNumShards(),
			this.getCounterStatus(), this.getCount(), this.getIndexes(), this.getCreationDateTime());
	}

	/**
	 *
	 * @param counterDescription
	 * @return
	 */
	public CounterBuilder withCounterDescription(final String counterDescription)
	{
		this.setDescription(counterDescription);
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
	 *
	 * @param creationDateTime
	 * @return
	 */
	public CounterBuilder withCreationDateTime(final DateTime creationDateTime)
	{
		Preconditions.checkNotNull(creationDateTime);
		this.setCreationDateTime(creationDateTime);
		return this;
	}

	public static final Counter zero(final String counterName)
	{
		return new CounterBuilder(counterName).build();
	}
}
