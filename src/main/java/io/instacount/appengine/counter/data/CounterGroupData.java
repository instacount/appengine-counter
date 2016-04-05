/**
 * Copyright (C) 2016 Instacount Inc. (developers@instacount.io)
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
package io.instacount.appengine.counter.data;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import io.instacount.appengine.counter.service.ShardedCounterServiceConfiguration;

/**
 * An entity parented by {@link CounterData} that represents an eventually consistent view of that counter's count and
 * provides unique indexing for sorting counters by their approximate value. Use of this entity is optional, but users
 * should be aware that it requires a multi-value index to be able to sort a group of counters by their
 * {@code eventuallyConsistentCount} value.
 *
 * @author David Fuelling
 */
@Entity
// Cached via @Cache because this information isn't mutated frequently like a CounterShardData, so it's actually helpful
// to have this accessed via memcache instead of hitting the Datastore. Per the objectify docs, "There is still,
// however, one circumstance in which the cache could go out of synchronization with the datastore: If your requests are
// cut off by DeadlineExceededException." Thus, we expire the global cache every OBJECTIFY_ENTITY_CACHE_TIMEOUT minutes.
@Cache(expirationSeconds = ShardedCounterServiceConfiguration.OBJECTIFY_ENTITY_CACHE_TIMEOUT)
@Getter
@Setter
@Index
@ToString
@EqualsAndHashCode
public class CounterGroupData
{
	private static final long COUNTER_GROUP_DATA_IDENTIFIER = 1L;

	@Id
	private long id = COUNTER_GROUP_DATA_IDENTIFIER;

	// See https://github.com/instacount/appengine-counter/issues/6. Each value in this list represents a group name
	// that this counter belongs to. For example, if we have various counters that track "favorites",
	// then we can add the group name "favorite" to this list. This will be indexed along with the "count" field,
	// and we can then perform a query on all counters in the "favorites" group ordred by count.
	@Index
	private List<String> counterGroups;

	// An eventually consistent view of this counter's count. This is not the actual count for this entity,
	// but is instead just an eventually consistent view of the count that is populated asynchronously by some other
	// process. Because of this assumption, this value should not be adjusted manually.
	@Index
	private long eventuallyConsistentCount;

	/**
	 * Default Constructor
	 */
	public CounterGroupData()
	{
		this.id = COUNTER_GROUP_DATA_IDENTIFIER;
	}

	// //////////////////////////////
	// Getters/Setters
	// //////////////////////////////

	/**
	 * Create a {@link Key} of type {@link CounterGroupData}. This entity only exists in the context of a
	 * {@link CounterData}, and is always parented by that entity so the id of this entity is always
	 * {@code CounterGroupData#COUNTER_GROUP_DATA_IDENTIFIER}.
	 *
	 * @return A {@link Key}
	 */
	public static Key<CounterGroupData> key()
	{
		return Key.create(CounterGroupData.class, COUNTER_GROUP_DATA_IDENTIFIER);
	}

}
