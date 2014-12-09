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

import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * A Counter Service that can retrieve, increment, decrement, and delete a named
 * {@link com.theupswell.appengine.counter.Counter}.
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
	 * @return
	 */
	public void updateCounterDetails(final Counter counter);

	/**
	 * Increment the value of a sharded counter by 1 with an isolated TransactionContext.
	 *
	 * @param counterName The name of the counter to increment.
	 *
	 * @return A Counter with the current count of the Counter.
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} is negative.
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             updated. For example, a {@link CounterStatus} of {@code CounterStatus#DELETING} .
	 * @see {@link CounterService#increment(String, long, boolean)}.
	 */
	public Counter increment(final String counterName);

	/**
	 * Increment the value of a sharded counter by {@code amount} with an isolated TransactionContext.
	 *
	 * @return A Counter with the current count of the Counter.
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} is negative.
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             updated. For example, a {@link CounterData.CounterStatus} of {@code CounterStatus#DELETING} .
	 * @see {@link CounterService#increment(String, long, boolean)}.
	 */
	public Counter increment(final String counterName, final long amount);

	/**
	 * Increment the value of the sharded counter with name {@code counterName} by {@code amount}.
	 *
	 * @param counterName The name of the counter to increment.
	 * @param amount A positive number to increment the counter's count by.
	 * @param isolatedTransactionContext {@code true} if the increment operation should be performed inside of a new
	 *            Datastore transaction that is different from the current transactional context, if any. {@code false}
	 *            if the increment should occur inside of the existing transactional context, if any, or a new
	 *            transaction if no pre-existing context exists.
	 *
	 * @return A Counter with the current count of the Counter.
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} is negative.
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             updated. For example, a {@link CounterStatus} of {@code CounterStatus#DELETING} .
	 */
	public Counter increment(final String counterName, final long amount, boolean isolatedTransactionContext);

	/**
	 * Decrement the value of a sharded counter by 1 with an isolated TransactionContext.
	 *
	 * @param counterName The name of the counter to decrement.
	 *
	 * @return A Counter with the current count of the Counter.
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} is negative.
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             updated. For example, a {@link CounterStatus} of {@code CounterStatus#DELETING} .
	 * @see {@link CounterService#decrement(String, long)}.
	 */
	public Counter decrement(final String counterName);

	/**
	 * Decrement the value of a sharded counter by {@code amount} with no isolation context because a sharded counter
	 * will typically have at least 3 shards, which means decrement operations would likely exceed the limit of 5 entity
	 * groups in a single TX.
	 *
	 * @return A Counter with the current count of the Counter.
	 * @throws NullPointerException if the {@code counterName} is null.
	 * @throws IllegalArgumentException if the {@code counterName} is "blank" (i.e., null, empty, or empty spaces).
	 * @throws IllegalArgumentException if the {@code amount} is negative.
	 * @throws RuntimeException if the counter exists in the Datastore but has a status that prevents it from being
	 *             updated. For example, a {@link CounterStatus} of {@code CounterStatus#DELETING} .
	 * @see {@link CounterService#decrement(String, long)}.
	 */
	public Counter decrement(final String counterName, final long amount);

	/**
	 * Removes a {@link CounterData} from the Datastore and attempts to remove it's corresponding
	 * {@link CounterShardData} entities via a Task Queue. This operation may take some time to complete since it is
	 * task queue based, so constructing or incrementing a {@link CounterData} while the same one is being deleted will
	 * fail.
	 */
	public void delete(final String counterName);
}
