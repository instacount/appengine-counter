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
package com.theupswell.appengine.counter.service;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.googlecode.objectify.Work;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl.DecrementShardResult;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl.DecrementShardResultCollection;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl.IncrementShardResult;

/**
 * A Counter Service that can retrieve, increment, decrement, and delete a named {@link Counter}.
 *
 * @author David Fuelling
 */
public interface CounterService
{
	/**
	 * Retrieve the value of the counter with the specified {@code counterName}. Counters always exist and are
	 * non-negative, so if the underlying implementation does not have a counter with the specified {@code counterName},
	 * then one will be created with a {@link Counter#getCount()} value of 0.
	 *
	 * @param counterName
	 *
	 * @return An {@link Counter} with an accurate count.
	 */
	public Counter getCounter(final String counterName);

	/**
	 * Update the non-count portions of a Counter with information found in {@code counter}. Note that changing a
	 * counter's "count" can not occur via this method. Instead, use {@link #increment} or {@link #decrement} instead.
	 * 
	 * @param counter
	 * 
	 * @throws DatastoreFailureException Thrown when any unknown error occurs while communicating with the data store.
	 *             Note that despite receiving this exception, it's possible that the datastore actually committed data
	 *             properly. Thus, clients should not attempt to retry after receiving this exception without checking
	 *             the state of the counter first.
	 * @throws DatastoreTimeoutException Thrown when a datastore operation times out. This can happen when you attempt
	 *             to put, get, or delete too many entities or an entity with too many properties, or if the datastore
	 *             is overloaded or having trouble. Note that despite receiving this exception, it's possible that the
	 *             datastore actually committed data properly. Thus, clients should not attempt to retry after receiving
	 *             this exception without checking the state of the counter first.
	 */
	public void updateCounterDetails(final Counter counter);

	/**
	 * Increment the value of a sharded counter by {@code amount} using an isolated TransactionContext. If you need to
	 * execute this operation in an existing parent transaction, then wrap this call in an Objectify {@link Work}
	 * anonymous class, like this:
	 *
	 * <pre>
	 * return ObjectifyService.ofy().transactNew(1, new Work&lt;Void&gt;()
	 * {
	 * 	&#064;Override
	 * 	public Void run()
	 * 	{
	 * 		counterService.increment(&quot;foo&quot;, 1);
	 * 	}
	 * });
	 * </pre>
	 * 
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} to decrement is negative (decrement amounts must always be
	 *             positive).
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             mutated (e.g., Fa {@link CounterStatus} of {@code CounterStatus#DELETING}). Only Counters with a
	 *             counterStatus of {@link CounterStatus#AVAILABLE} may be mutated, incremented or decremented.
	 * @throws DatastoreFailureException Thrown when any unknown error occurs while communicating with the data store.
	 *             Note that despite receiving this exception, it's possible that the datastore actually committed data
	 *             properly. Thus, clients should not attempt to retry after receiving this exception without checking
	 *             the state of the counter first.
	 * @throws DatastoreTimeoutException Thrown when a datastore operation times out. This can happen when you attempt
	 *             to put, get, or delete too many entities or an entity with too many properties, or if the datastore
	 *             is overloaded or having trouble. Note that despite receiving this exception, it's possible that the
	 *             datastore actually committed data properly. Thus, clients should not attempt to retry after receiving
	 *             this exception without checking the state of the counter first.
	 */
	public IncrementShardResult increment(final String counterName, final long requestedIncrementAmount);

	/**
	 * <p>
	 * Decrement the value of a sharded counter by {@code amount}.
	 * </p>
	 * <p>
	 * Note that this operation will always be performed with no transactional context. This is because a sharded
	 * counter will typically have at least 3 shards, and may have many more. Thus, in general, decrementing in a
	 * transaction would likely exceed exceed the limit of 5 entity groups in a single TX. As such, this operation
	 * should be considered eventually consistent.
	 * </p>
	 *
	 * @return The amount that was actually decremented from this counter. Depending on counter configuration, requests
	 *         to decrement a counter by more than its available count will succeed with a decrement amount that is
	 *         smaller than the requested decrement amount (e.g., if a counter may not decrement below zero). This
	 *         return value can be used to discern any difference between the requested and actual decrement amounts.
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} to decrement is negative (decrement amounts must always be
	 *             positive).
	 * @throws DatastoreFailureException Thrown when any unknown error occurs while communicating with the data store.
	 *             Note that despite receiving this exception, it's possible that the datastore actually committed data
	 *             properly. Thus, clients should not attempt to retry after receiving this exception without checking
	 *             the state of the counter first.
	 * @throws DatastoreTimeoutException Thrown when a datastore operation times out. This can happen when you attempt
	 *             to put, get, or delete too many entities or an entity with too many properties, or if the datastore
	 *             is overloaded or having trouble. Note that despite receiving this exception, it's possible that the
	 *             datastore actually committed data properly. Thus, clients should not attempt to retry after receiving
	 *             this exception without checking the state of the counter first.
	 * 
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             mutated (e.g., Fa {@link CounterStatus} of {@code CounterStatus#DELETING}). Only Counters with a
	 *             counterStatus of {@link CounterStatus#AVAILABLE} may be mutated, incremented or decremented.
	 */
	public DecrementShardResultCollection decrement(final String counterName, final long amount);

	/**
	 * Removes a {@link CounterData} from the Datastore and attempts to remove it's corresponding
	 * {@link CounterShardData} entities via a Task Queue. This operation may take some time to complete since it is
	 * task queue based, so constructing or incrementing a {@link CounterData} while the same one is being deleted will
	 * fail.
	 * 
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws DatastoreFailureException Thrown when any unknown error occurs while communicating with the data store.
	 *             Note that despite receiving this exception, it's possible that the datastore actually committed data
	 *             properly. Thus, clients should not attempt to retry after receiving this exception without checking
	 *             the state of the counter first.
	 * 
	 * @throws DatastoreTimeoutException Thrown when a datastore operation times out. This can happen when you attempt
	 *             to put, get, or delete too many entities or an entity with too many properties, or if the datastore
	 *             is overloaded or having trouble. Note that despite receiving this exception, it's possible that the
	 *             datastore actually committed data properly. Thus, clients should not attempt to retry after receiving
	 *             this exception without checking the state of the counter first.
	 */
	public void delete(final String counterName);
}
