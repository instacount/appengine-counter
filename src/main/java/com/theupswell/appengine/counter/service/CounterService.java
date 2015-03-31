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

import java.util.Set;
import java.util.UUID;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.googlecode.objectify.Work;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperation;

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
	 * @param skipCache {@code true} if the cache should be skipped and all shards should be counted in order to get the
	 *            count; {@code false} if the cache should be consulted first in order to save computing resources while
	 *            trying to determine the count.
	 *
	 * @return An {@link Counter} with an accurate count.
	 */
	Counter getCounter(final String counterName, final boolean skipCache);

	/**
	 * Retrieve the value of the counter with the specified {@code counterName}. Counters always exist and are
	 * non-negative, so if the underlying implementation does not have a counter with the specified {@code counterName},
	 * then one will be created with a {@link Counter#getCount()} value of 0.
	 *
	 * @param counterName
	 * 
	 * @return An {@link Counter} with an accurate count.
	 */
	Counter getCounter(final String counterName);

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
	void updateCounterDetails(final Counter counter);

	/**
	 * Increment the value of a sharded counter by {@code amount} using a random increment {@link UUID}, which is used
	 * to uniquely identify each increment operation. See the javadoc of {@link #increment(String, long, UUID)} for more
	 * details.
	 * 
	 * @param counterName The name of the counter to increment.
	 * @param amount The amount to increment the counter with.
	 *
	 * @return An instance of {@link CounterOperation} that holds a {@link Set} decrements, as well as the amount that
	 *         was actually added to the counter named {@code counterName}. This return value can be used to discern any
	 *         difference between the requested and actual decrement amounts.
	 * 
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} to increment is negative (increment amounts must always be
	 *             positive).
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             mutated (e.g., a {@link CounterStatus} of {@code CounterStatus#DELETING}). Only Counters with a
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
	CounterOperation increment(final String counterName, final long amount);

	/**
	 * Increment the value of a sharded counter by {@code amount} using a specified increment {@link UUID}, which is
	 * used to uniquely identify the increment operation for later idempotent retry.
	 * 
	 * This operation is idempotent from the perspective of a ConcurrentModificationException, in which case the
	 * requested increment operation will have failed and will not have been applied. However, be aware that per the
	 * AppEngine docs, in certain rare cases "If your application receives an exception when committing a transaction,
	 * it does not always mean that the transaction failed. You can receive DatastoreTimeoutException or
	 * DatastoreFailureException in cases where transactions have been committed and eventually will be applied
	 * successfully." In these cases, clients of this counter service can retrieve the current count of a counter using
	 * {@link #getCounter(String)} to help determine if an increment was actually applied. Additionally, for a more
	 * accurate determination, clients can call {@link #isIncrementApplied} to determine if the increment succeeded or
	 * failed, or is still in processing.
	 * 
	 * If you need to execute this operation in an existing parent transaction, then wrap this call in an Objectify
	 * {@link Work} anonymous class, like this:
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
	 * @param counterName The name of the counter to increment.
	 * @param amount The amount to increment the counter with.
	 * @param incrementUuid A {@link UUID} for the increment that will be performed.
	 * 
	 * @return An instance of {@link CounterOperation} that holds a {@link Set} decrements, as well as the amount that
	 *         was actually added to the counter named {@code counterName}. This return value can be used to discern any
	 *         difference between the requested and actual decrement amounts.
	 * 
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} to increment is negative (increment amounts must always be
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
	CounterOperation increment(final String counterName, final long amount, final UUID incrementUuid);

	/**
	 * <p>
	 * Decrement the value of a sharded counter by {@code amount} and a random decrement {@link UUID}, which is used to
	 * uniquely identify each decrement operation.
	 * </p>
	 * <p>
	 * Note that this operation will always be performed with no transactional context. This is because a sharded
	 * counter will typically have at least 3 shards, and may have many more. Thus, in general, decrementing in a
	 * transaction would likely exceed exceed the limit of 5 entity groups in a single TX. As such, this operation
	 * should be considered eventually consistent.
	 * </p>
	 *
	 * @param counterName The name of the counter to decrement.
	 * @param requestedDecrementAmount The amount to decrement the counter with.
	 * 
	 * @return An instance of {@link CounterOperation} that holds a {@link Set} decrements, as well as the amount that
	 *         was actually decremented from this counter. Depending on counter configuration, requests to decrement a
	 *         counter by more than its available count will succeed with a decrement amount that is smaller than the
	 *         requested decrement amount (e.g., if a counter may not decrement below zero). This return value can be
	 *         used to discern any difference between the requested and actual decrement amounts.
	 * 
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
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             mutated (e.g., Fa {@link CounterStatus} of {@code CounterStatus#DELETING}). Only Counters with a
	 *             counterStatus of {@link CounterStatus#AVAILABLE} may be mutated, incremented or decremented.
	 */
	CounterOperation decrement(final String counterName, final long requestedDecrementAmount);

	/**
	 * <p>
	 * Decrement the value of a sharded counter by {@code amount} and a specified decrement {@link UUID}, which is used
	 * to uniquely identify each decrement operation.
	 * </p>
	 * <p>
	 * Note that this operation will always be performed with no transactional context. This is because a sharded
	 * counter will typically have at least 3 shards, and may have many more. Thus, in general, decrementing in a
	 * transaction would likely exceed exceed the limit of 5 entity groups in a single TX. As such, this operation
	 * should be considered eventually consistent.
	 * </p>
	 *
	 * @param counterName The name of the counter to decrement.
	 * @param requestedDecrementAmount The amount to decrement the counter with.
	 * @param decrementUuid A {@link UUID} for the increment that will be performed.
	 * 
	 * @return An instance of {@link CounterOperation} that holds a {@link Set} decrements, as well as the amount that
	 *         was actually decremented from this counter. Depending on counter configuration, requests to decrement a
	 *         counter by more than its available count will succeed with a decrement amount that is smaller than the
	 *         requested decrement amount (e.g., if a counter may not decrement below zero). This return value can be
	 *         used to discern any difference between the requested and actual decrement amounts.
	 * 
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
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             mutated (e.g., Fa {@link CounterStatus} of {@code CounterStatus#DELETING}). Only Counters with a
	 *             counterStatus of {@link CounterStatus#AVAILABLE} may be mutated, incremented or decremented.
	 */
	CounterOperation decrement(final String counterName, final long requestedDecrementAmount, final UUID decrementUuid);

	/**
	 * Resets a {@link Counter} to have a count of zero by disabling the counter, incrementing or decrementing as
	 * necessary, and then enabling the counter.
	 *
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
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
	void reset(final String counterName);

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
	void delete(final String counterName);

}
