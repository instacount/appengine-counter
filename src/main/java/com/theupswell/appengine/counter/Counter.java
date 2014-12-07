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
import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.data.CounterData;

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
	public static final int DEFAULT_OPS_PER_SECOND = 5;

	private final String counterName;
	private final String counterDescription;
	// The number of increment/decrements per second this counter can sustain. GAE entity groups can sustain ~5 updates
	// per-second, so a setting of 10 here would imply 2 shards.
	private final int opsPerSecond;
	private final CounterData.CounterStatus counterStatus;
	private final long count;

	/**
	 * Required-args Constructor. Sets the {@code counterStatus} to
	 * {@link com.theupswell.appengine.counter.data.CounterData.CounterStatus#AVAILABLE} and the {@code count} to zero.
	 * 
	 * @param counterName
	 */
	public Counter(final String counterName, final String counterDescription)
	{
		this(counterName, counterDescription, DEFAULT_OPS_PER_SECOND, CounterData.CounterStatus.AVAILABLE);
	}

	/**
	 * Required-args Constructor. Sets the {@code count} to zero.
	 * 
	 * @param counterName
	 * @param counterStatus
	 */
	public Counter(final String counterName, final String counterDescription, final int opsPerSecond,
			final CounterData.CounterStatus counterStatus)
	{
		this(counterName, counterDescription, opsPerSecond, counterStatus, 0);
	}

	/**
	 * Required-args Constructor.
	 * 
	 * @param counterName
	 * @param counterDescription
	 * @param counterStatus
	 * @param count
	 */
	public Counter(final String counterName, final String counterDescription, final int opsPerSecond,
			final CounterData.CounterStatus counterStatus, final long count)
	{
		Preconditions.checkArgument(!StringUtils.isBlank(counterName), "CounterName may not be empty, blank, or null!");
		Preconditions.checkNotNull(counterStatus);

		this.counterName = counterName;
		this.counterDescription = counterDescription;
		this.opsPerSecond = opsPerSecond;
		this.counterStatus = counterStatus;
		this.count = count;
	}
}
