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
package com.theupswell.appengine.counter.data;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Wither;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Unindex;
import com.theupswell.appengine.counter.data.base.AbstractEntity;
import com.theupswell.appengine.counter.data.ofy.IfCounterDataIndexable;
import com.theupswell.appengine.counter.service.ShardedCounterService;

/**
 * Represents a named counter in the datastore and allows for shard-initialization and shard-association. Note that this
 * entity does not have a {@code count} property since counts are stored in {@link CounterShardData} entities and
 * aggregated by {@link ShardedCounterService} implementations.
 * 
 * @author David Fuelling
 */
@Entity
@Cache
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
		// This Counter is not available to be incremented or decremented, though its details can be updated.
		READ_ONLY_COUNT,
		// This Counter is expanding the number of shards it holds internally, and may not be incremented, decremented,
		// or deleted, or mutated.
		EXPANDING_SHARDS,
		// This Counter is contracting the number of shards it holds internally, and may not be incremented,
		// decremented, or deleted, or mutated.
		CONTRACTING_SHARDS,
		// This Counter is in the process of being deleted, and may not be incremented or decremented and its details
		// may not be changed.
		DELETING
	}

	// Embedded class that holds information about which parts of a CounterData to index and which parts not to index.
	@Unindex
	private CounterIndexes indexes = CounterIndexes.none();

	// Embedded class that allows for eventually consistent count querying as well as counter-groups for tagging. Null
	// by default, set if counter group informaiton should be used.
	@Index
	private CounterGroupData counterGroupData;

	// ////////////////
	// @Id -- The counterName is the @Id of this entity, found in AbstractEntity
	// ////////////////

	// This is necessary to know in order to be able to evenly distribute amongst all shards for a given counterName
	@Index(IfCounterDataIndexable.class)
	private int numShards = 1;

	@Index(IfCounterDataIndexable.class)
	private String counterDescription;

	// This is AVAILABLE by default, which means it can be incremented and
	// decremented
	@Index(IfCounterDataIndexable.class)
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
		this.setNumShards(1);

		this.indexes = CounterIndexes.none();
	}

	/**
	 * The param-based constructor
	 * 
	 * @param counterName The name of this CounterData. May not be null, blank, or empty.
	 * @param numShards The number of shards this counter will contain.
	 */
	public CounterData(final String counterName, final int numShards)
	{
		super(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");

		Preconditions.checkArgument(numShards > 0);
		this.setNumShards(numShards);

		this.indexes = CounterIndexes.none();
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

	/**
	 * Setter for {@code numShards}.
	 * 
	 * @param numShards
	 * @throws IllegalArgumentException if {@code numShards} is less-than or equals to zero.
	 */
	public void setNumShards(final int numShards)
	{
		Preconditions.checkArgument(numShards > 0, "A Counter must have at least 1 CounterShard!");

		this.numShards = numShards;
		this.setUpdatedDateTime(new DateTime(DateTimeZone.UTC));
	}

	/**
	 * Setter for {@code indexes}.
	 * 
	 * @param indexes
	 * @throws NullPointerException if {@code indexes} is null.
	 */
	public void setIndexes(final CounterIndexes indexes)
	{
		Preconditions.checkNotNull(indexes);
		this.indexes = indexes;
	}

	/**
	 * Create a {@link Key Key<CounterData>}. Keys for this entity are not "parented" so that they can be added under
	 * high volume load in a given application. Note that CounterData will be in a namespace specific.
	 * 
	 * @param counterName The name of the Counter to create a Key for.
	 * @return A {@link Key}
	 */
	public static Key<CounterData> key(final String counterName)
	{
		Preconditions.checkNotNull(counterName);
		Preconditions.checkArgument(!StringUtils.isBlank(counterName),
			"CounterData Names may not be null, blank, or empty!");
		return Key.create(CounterData.class, counterName);
	}

	/**
	 * A container class that holds true/false values for each property of a {@link CounterData} to indicate if the
	 * property should be indexed or not.
	 */
	@Getter
	@Wither
	@NoArgsConstructor
	@AllArgsConstructor
	@ToString
	@EqualsAndHashCode
	public static class CounterIndexes
	{
		private boolean numShardsIndexable;
		private boolean counterStatusIndexable;
		private boolean countIndexable;
		private boolean descriptionIndexable;

		/**
		 * Helper method to return an instance of {@link CounterIndexes} that have all property indexes enabled.
		 * 
		 * @return
		 */
		public static CounterIndexes all()
		{
			return new CounterIndexes(true, true, true, true);
		}

		/**
		 * Helper method to return an instance of {@link CounterIndexes} that have no property indexes enabled.
		 *
		 * @return
		 */
		public static CounterIndexes none()
		{
			return new CounterIndexes(false, false, false, false);
		}

		/**
		 * Helper method to return an instance of {@link CounterIndexes} that has sensible default valuse chosen.
		 * This indexes all Counter information except for the Description, which is probably better off indexed via the
		 * Search service.
		 *
		 * @return
		 */
		public static CounterIndexes sensibleDefaults()
		{
			return new CounterIndexes(true, true, true, false);
		}

	}

}
