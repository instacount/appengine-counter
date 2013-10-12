/**
 * Copyright (C) 2013 Oodlemud Inc. (developers@oodlemud.com)
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
package com.oodlemud.appengine.counter;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import com.oodlemud.appengine.counter.data.CounterData;
import com.oodlemud.appengine.counter.data.CounterData.CounterStatus;

@Data
@RequiredArgsConstructor
public class CounterBuilder
{
	@NonNull
	private final String counterName;
	private CounterStatus counterStatus = CounterStatus.AVAILABLE;
	private long count;

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @param builder
	 * @return
	 */
	public CounterBuilder(CounterData counterData)
	{
		this.counterName = counterData.getCounterName();
		this.counterStatus = counterData.getCounterStatus();
	}

	/**
	 * Build method for constructing a new Counter.
	 * 
	 * @param builder
	 * @return
	 */
	public Counter build()
	{
		return new Counter(this.getCounterName(), this.getCounterStatus(), this.getCount());
	}

	/**
	 * 
	 * @param counterStatus
	 * @return
	 */
	public CounterBuilder withCounterStatus(CounterStatus counterStatus)
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
