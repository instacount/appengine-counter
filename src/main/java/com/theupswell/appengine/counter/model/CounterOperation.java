package com.theupswell.appengine.counter.model;

import java.util.Set;
import java.util.UUID;

import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * An interface for modeling the result of a mutation (increment or decrement) of a {@link CounterShardData} entity in
 * the Datastore.
 */
public interface CounterOperation
{
	/**
	 * A {@link UUID} that uniquely identifies this decrement operation.
	 * 
	 * @return
	 */
	UUID getOperationUuid();

	/**
	 * Get the type of counter operation that was performed.
	 * 
	 * @return
	 */
	CounterOperationType getCounterOperationType();

	/**
	 * Return the optionally present {@link Key} for the {@link CounterShardData} that a mutation was effected upon.
	 *
	 * @return
	 */
	Set<CounterShardOperation> getCounterShardOperations();

	/**
	 * Helper method to retrieve the first {@link CounterShardOperation} in the Set of results accessed via
	 * {@link #getCounterShardOperations()}.
	 *
	 * @return
	 */
	 Optional<CounterShardOperation> getFirstCounterOperationResult();

	/**
	 * Return the total amount of this counter operation result across sub-operations, if any.
	 *
	 * @return
	 */
	long getTotalAmount();

	/**
	 * An enumeration that identifies the type of a {@link CounterShardOperation}.
	 */
	enum CounterOperationType
	{
		INCREMENT, DECREMENT
	}
}
