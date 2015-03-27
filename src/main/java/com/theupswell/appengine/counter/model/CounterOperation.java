package com.theupswell.appengine.counter.model;

import java.util.Set;
import java.util.UUID;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * An interface for modeling the result of a mutation (increment or decrement) of a {@link CounterShardData} entity in
 * the Datastore.
 */
public interface CounterOperation<T extends CounterShardOperation>
{
	/**
	 * A {@link UUID} that uniquely identifies this decrement operation.
	 * 
	 * @return
	 */
	public UUID getOperationUuid();

	/**
	 * Get the type of counter operation that was performed.
	 * 
	 * @return
	 */
	public CounterOperationType getCounterOperationType();

	/**
	 * Return the optionally present {@link Key} for the {@link CounterShardData} that a mutation was effected upon.
	 *
	 * @return
	 */
	public Set<T> getCounterShardOperations();

	/**
	 * Return the total amount of this counter operation result across sub-operations, if any.
	 *
	 * @return
	 */
	public long getTotalAmount();

	/**
	 * An enumeration that identifies the type of a {@link CounterShardOperation}.
	 */
	public static enum CounterOperationType
	{
		INCREMENT, DECREMENT
	}
}
