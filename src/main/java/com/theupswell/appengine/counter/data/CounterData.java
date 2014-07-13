/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
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
package com.theupswell.appengine.counter.data;

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
import com.theupswell.appengine.counter.data.base.AbstractEntity;
import com.theupswell.appengine.counter.service.ShardedCounterService;

/**
 * Represents a named counter in the datastore and allows for
 * shard-initialization and shard-association. Note that this entity does not
 * have a {@code count} property since counts are stored in
 * {@link CounterShardData} entities and aggregated by
 * {@link ShardedCounterService} implementations.
 * 
 * @author David Fuelling
 */
@Entity
@Getter
@Setter
@Unindex
@ToString(callSuper = true)
public class CounterData extends AbstractEntity
{

	// Used by the Get methods to indicate the state of a CounterData while it
	// is deleting.
	public static enum CounterStatus
	{
		// This Counter is available to be incremented, decremented, or deleted.
		AVAILABLE,
		// This Counter is expanding the number of shards it holds internally,
		// and may not be incremented, decremented, or deleted.
		EXPANDING_SHARDS,
		// // This Counter is contracting the number of shards it holds
		// internally, and may not be incremented, decremented, or deleted.
		CONTRACTING_SHARDS,
		// This Counter is in the process of being deleted, and may not be
		// incremented or decremented.
		DELETING
	}

	// ////////////////
	// @Id -- The counterName is the @Id of this entity, found in AbstractEntity
	// ////////////////

	// This is necessary to know in order to be able to evenly distribute
	// amongst all shards for a given counterName
	private int numShards;

	// This is AVAILABLE by default, which means it can be incremented and
	// decremented
	private CounterStatus counterStatus = CounterStatus.AVAILABLE;

	/**
	 * Default Constructor for Objectify
	 * 
	 * @deprecated Use the param-based constructors instead.
	 */
	@Deprecated
	public CounterData()
	{
		// Implement for Objectify
	}

	/**
	 * The param-based constructor
	 * 
	 * @param counterName The name of this CounterData. May not be null, blank,
	 *            or empty.
	 * @param numShards The number of shards this counter will contain.
	 */
	public CounterData(String counterName, int numShards)
	{
		super(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");
		this.numShards = numShards;
	}

	// //////////////////////////////
	// Getters/Setters
	// //////////////////////////////

	/**
	 * @return The name of this counter
	 */
	public String getCounterName()
	{
		return this.getId();
	}

	public void setNumShards(int numShards)
	{
		this.numShards = numShards;
		this.setUpdatedDateTime(new DateTime(DateTimeZone.UTC));
	}

	/**
	 * Create a {@link Key Key<CounterData>}. Keys for this entity are not
	 * "parented" so that they can be added under high volume load in a given
	 * application. Note that CounterData will be in a namespace specific.
	 * 
	 * @param counterName The name of the Counter to create a Key for.
	 * @return A {@link Key}
	 */
	public static Key<CounterData> key(String counterName)
	{
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");
		return Key.create(CounterData.class, counterName);
	}

}
