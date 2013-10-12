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
package com.oodlemud.appengine.counter.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Unindex;
import com.oodlemud.appengine.counter.data.base.AbstractEntity;

/**
 * Represents a discrete shard belonging to a named counter.<br/>
 * <br/>
 * An individual shard is written to infrequently to allow the counter in
 * aggregate to be incremented rapidly.
 * 
 * @author David Fuelling <dfuelling@oodlemud.com>
 */
@Entity
@Getter
@Setter
@Unindex
@ToString(callSuper = true)
public class CounterShardData extends AbstractEntity
{
	static final String COUNTER_SHARD_KEY_SEPARATOR = "-";

	// The id of a CounterShardData is the name of the counter (for easy lookup
	// via a starts-with query) combined with the shardNumber. E.g.,
	// "CounterName-2" would be the counter with name "CounterName" and Shard
	// number 2.

	// The total of this shard's counter (not the total counter count)
	private long count;

	/**
	 * Default Constructor for Objectify
	 * 
	 * @deprecated Use the param-based constructors instead.
	 */
	@Deprecated
	public CounterShardData()
	{
		// Implemented for Objectify
	}

	/**
	 * Param-based Constructor
	 * 
	 * @param counterName
	 * @param shardNumber
	 */
	public CounterShardData(final String counterName, final int shardNumber)
	{
		// Preconditions checked by #constructCounterShardIdentifier
		setId(constructCounterShardIdentifier(counterName, shardNumber));
	}

	// /////////////////////////
	// Getters/Setters
	// /////////////////////////

	/**
	 * @return The last dateTime that an increment occurred.
	 */
	public DateTime getLastIncrement()
	{
		return getUpdatedDateTime();
	}

	/**
	 * Set the amount of this shard with a new {@code count}.
	 * 
	 * @param count
	 */
	public void setCount(long count)
	{
		this.count = count;
		this.setUpdatedDateTime(DateTime.now(DateTimeZone.UTC));
	}

	/**
	 * Helper method to set the internal identifier for this entity.
	 * 
	 * @param counterName
	 * @param shardNumber A unique identifier to distinguish shards for the same
	 *            {@code counterName} from each other.
	 */
	private static String constructCounterShardIdentifier(final String counterName, final int shardNumber)
	{
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");
		Preconditions.checkArgument(shardNumber >= 0, "shardNumber must be greater than or equal to 0!");

		return counterName + COUNTER_SHARD_KEY_SEPARATOR + shardNumber;
	}

	/**
	 * Create a {@link Key Key<CounterShardData>}. Keys for this entity are not
	 * "parented" so that they can be added under high volume load in a given
	 * application. Note that CounterData will be in a namespace specific.
	 * 
	 * @param actorId
	 * @return
	 */
	public static Key<CounterShardData> key(final String counterName, final int shardNumber)
	{
		// Preconditions checked by #constructCounterShardIdentifier
		return Key.create(CounterShardData.class, constructCounterShardIdentifier(counterName, shardNumber));
	}

}