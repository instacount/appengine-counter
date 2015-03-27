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
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.annotation.Unindex;
import com.theupswell.appengine.counter.model.CounterOperation.CounterOperationType;

/**
 * Represents a mutation to the amount field of a {@link CounterShardData}. This entity is the child of a
 * {@link CounterShardData} to increase performance of storing this type of increment/decrement tracking data.
 * 
 * @author David Fuelling
 */
@Entity
@Cache
@Getter
@Unindex
@ToString
@EqualsAndHashCode(of = "operationId")
public class CounterShardOperationData
{
	// It's ok if two shards share the same identifier since they will have different @Parent's.
	// We prefer an id over a String so we don't have to @Index the creationDateTime (the index will order things for
	// us for later data processing).
	@Id
	private Long operationId;

	@Parent
	private Key<CounterShardData> counterShardDataKey;

	// Increments and Decrements occur in a "counter operation" which holds one or more CounterShard operations
	// (increment/decrement) so that multiple shard increments/decrements can be associated
	// under a single counter one operation. This key identifies that overarching operation.
	@Unindex
	private UUID parentCounterOperationUuid;

	// The total of this shard's count (not the total counter count)
	@Unindex
	private long amount;

	@Unindex
	private CounterOperationType type;

	// Indexed to allow for loading increment/decrement by creation date/time since the index on @Id will not be ordered
	// properly for this type of query.
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
	 * Param-based Constructor
	 *
	 * @param counterShardDataKey The parent Key for this entity.
	 * @param parentCounterOperationUuid A unique identifier for the parent Counter operation that this counter shard
	 *            operation is a part of.
	 * @param amount The amount of this operation.
	 */
	public CounterShardOperationData(final Key<CounterShardData> counterShardDataKey,
			final UUID parentCounterOperationUuid, final CounterOperationType counterOperationType, final long amount)
	{
		Preconditions.checkNotNull(counterShardDataKey);
		this.counterShardDataKey = counterShardDataKey;

		Preconditions.checkNotNull(parentCounterOperationUuid);
		this.parentCounterOperationUuid = parentCounterOperationUuid;

		Preconditions.checkNotNull(counterOperationType);
		this.type = counterOperationType;

		// In-general, increments/decrements will not be 0, but sometimes they may be.
		// Preconditions.checkNotNull(amount);
		this.amount = amount;

		this.creationDateTime = DateTime.now(DateTimeZone.UTC);
	}

	// /////////////////////////
	// Getters/Setters
	// /////////////////////////

	/**
	 * @return The last dateTime that an increment occurred.
	 */
	public long getId()
	{
		return this.operationId;
	}

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that key will be included in the returned Key
	 * heirarchy.
	 *
	 * @return
	 */
	public Key<CounterShardOperationData> getTypedKey()
	{
		// CounterShardMutationData is parented by CounterShardData
		return Key.create(this.getCounterShardDataKey(), CounterShardOperationData.class, this.getId());
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
	 * @param operationId
	 * @return
	 */
	public static Key<CounterShardOperationData> key(final Key<CounterShardData> counterShardDataKey,
			final long operationId)
	{
		// Preconditions checked by #constructCounterShardIdentifier
		return Key.create(counterShardDataKey, CounterShardOperationData.class, operationId);
	}

}
