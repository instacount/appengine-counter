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
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.annotation.Unindex;

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
@EqualsAndHashCode(of = "uuid")
public class CounterShardOperationData
{
	// It's ok if two shards share the same UUID since they will have different @Parent's.
	@Id
	private String uuid;

	@Parent
	private Key<CounterShardData> counterShardDataKey;

	// The total of this shard's count (not the total counter count)
	@Unindex
	private long amount;

	@Unindex
	private Type type;

	// Indexed to allow for loading increment/decrement by creation date/time since the index on @Id will not be ordered
	// properly for this type of query.
	@Index
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
	 * @param counterShardDataKey
	 * @param uuid
	 * @param amount
	 */
	public CounterShardOperationData(final Key<CounterShardData> counterShardDataKey, final UUID uuid, final Type type,
			final long amount)
	{
		Preconditions.checkNotNull(counterShardDataKey);
		this.counterShardDataKey = counterShardDataKey;

		Preconditions.checkNotNull(uuid);
		this.uuid = uuid.toString();

		Preconditions.checkNotNull(type);
		this.type = type;

		// In-general, increments/decrements will not be 0, but sometimes they may be (e.g., with a no-op decrement).
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
	public String getId()
	{
		return this.uuid;
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
	 * @param uuid
	 * @return
	 */
	public static Key<CounterShardOperationData> key(final Key<CounterShardData> counterShardDataKey, final UUID uuid)
	{
		// Preconditions checked by #constructCounterShardIdentifier
		return Key.create(counterShardDataKey, CounterShardOperationData.class, uuid.toString());
	}

	/**
	 * The type of mutation performed.
	 */
	public static enum Type
	{
		INCREMENT, DECREMENT
	}

}
