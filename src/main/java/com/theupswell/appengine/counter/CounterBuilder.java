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

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.theupswell.appengine.counter.data.CounterData;

@Data
@RequiredArgsConstructor
public class CounterBuilder
{
	@NonNull
	private final String counterName;
	private String counterDescription;
	private int numShards;
	private CounterData.CounterStatus counterStatus = CounterData.CounterStatus.AVAILABLE;
	private long count;

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @param counterData
	 * @return
	 */
	public CounterBuilder(final CounterData counterData)
	{
		this.counterName = counterData.getCounterName();
		this.counterDescription = counterData.getCounterDescription();
		this.numShards = counterData.getNumShards();
		this.counterStatus = counterData.getCounterStatus();
	}

	/**
	 * Build method for constructing a new Counter.
	 *
	 * @param counter
	 * @return
	 */
	public CounterBuilder(final Counter counter)
	{
		this.counterName = counter.getCounterName();
		this.counterDescription = counter.getCounterDescription();
		this.numShards = counter.getNumShards();
		this.counterStatus = counter.getCounterStatus();
		this.count = counter.getCount();
	}

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @return
	 */
	public Counter build()
	{
		return new Counter(this.getCounterName(), this.getCounterDescription(), this.getNumShards(),
			this.getCounterStatus(), this.getCount());
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
		this.setCounterStatus(counterStatus);
		return this;
	}

	/**
	 * 
	 * @param count
	 * @return
	 */
	public CounterBuilder withCount(final long count)
	{
		this.setCount(count);
		return this;
	}

	public static final Counter zero(String counterName)
	{
		return new CounterBuilder(counterName).build();
	}
}
