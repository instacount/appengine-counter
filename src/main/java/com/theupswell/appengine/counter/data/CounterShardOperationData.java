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

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.annotation.Unindex;
import com.theupswell.appengine.counter.CounterOperation.CounterOperationType;

/**
 * Represents a mutation to the amount field of a {@link CounterShardData}. This entity is the child of a
 * {@link CounterShardData} to increase performance of storing this type of increment/decrement tracking data, and is
 * used to indicate of a particular counter increment/decrement succeeded.
 * 
 * @author David Fuelling
 */
@Entity
// Not cached via @Cache because this information isn't required to be accessed often, and persisting it to memcache
// during the counter shard update adds potential memcache latency that isn't adding value.
@Getter
@Unindex
@ToString
@EqualsAndHashCode(of = "id")
public class CounterShardOperationData
{
	//static final String COUNTER_SHARD_KEY_SEPARATOR = "-";

	// It's ok if two shards share the same UUID since they will have different @Parent's.
	@Id
	private String id;

	@Parent
	private Key<CounterShardData> counterShardDataKey;

	@Unindex
	private CounterOperationType counterOperationType;

	// The total of this mutations amount (not the total counter count)
	@Unindex
	private long mutationAmount;

	@Unindex
	private DateTime creationDateTime;

	/**
	 * Default Constructor for Objectify
	 *
	 * @deprecated Exists only for Objectify. Use the param-based constructors instead.
	 */
	@Deprecated
	public CounterShardOperationData()
	{
		// Implemented for Objectify
	}

	/**
	 * Required args Constructor
	 *
	 * @param counterShardDataKey
	 * @param id
	 * @param counterOperationType
	 * @param mutationAmount
	 */
	public CounterShardOperationData(final Key<CounterShardData> counterShardDataKey, final UUID id,
			final CounterOperationType counterOperationType, final long mutationAmount)
	{
		this(counterShardDataKey, id, counterOperationType, mutationAmount, DateTime.now(DateTimeZone.UTC));
	}

	/**
	 * Required args Constructor
	 *
	 * @param counterShardDataKey
	 * @param id
	 * @param counterOperationType An instance of {@link CounterOperationType} that indicates if the operation was an
	 *            addition or a reduction.
	 * @param mutationAmount A long number (positive or negative) representing the amount that was incremented in the
	 *            associated {@link CounterShardData}.
	 * @param dateTime An instance of {@link DateTime}.
	 */
	public CounterShardOperationData(final Key<CounterShardData> counterShardDataKey, final UUID id,
			final CounterOperationType counterOperationType, final long mutationAmount, final DateTime dateTime)
	{
		Preconditions.checkNotNull(counterShardDataKey);
		this.counterShardDataKey = counterShardDataKey;

		Preconditions.checkNotNull(id);
		this.id = id.toString();

		Preconditions.checkNotNull(counterOperationType);
		this.counterOperationType = counterOperationType;

		// Amounts for this object must always be positive. Use counterOperationType to determine if the amount was
		// added or subtracted from the Shard.
		Preconditions.checkArgument(mutationAmount > 0);
		this.mutationAmount = mutationAmount;

		Preconditions.checkNotNull(dateTime);
		this.creationDateTime = dateTime;
	}

	// /////////////////////////
	// Getters/Setters
	// /////////////////////////

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that key will be included in the returned Key
	 * heirarchy.
	 *
	 * @return
	 */
	public Key<CounterShardOperationData> getTypedKey()
	{
		// CounterShardMutationData is parented by CounterShardData
		return Key.create(this.counterShardDataKey, CounterShardOperationData.class, this.getId());
	}

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that key will be included in the returned Key
	 * heirarchy.
	 */
	public com.google.appengine.api.datastore.Key getKey()
	{
		return this.getTypedKey().getRaw();
	}

	/**
	 * Create a {@link Key} of type {@link CounterShardOperationData}. Keys for this entity are "parented" by a
	 * {@link Key} of type {@link CounterShardOperationData}.
	 *
	 * @param counterShardDataKey
	 * @param id
	 * @return
	 */
	public static Key<CounterShardOperationData> key(final Key<CounterShardData> counterShardDataKey, final UUID id)
	{
		Preconditions.checkNotNull(counterShardDataKey);
		Preconditions.checkNotNull(id);

		return Key.create(counterShardDataKey, CounterShardOperationData.class, id.toString());
	}

}
