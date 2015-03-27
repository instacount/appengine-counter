package com.theupswell.appengine.counter.model;

import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;

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
	Set<T> getCounterShardOperations();

	/**
	 * Return the total amount of this counter operation result across sub-operations, if any.
	 *
	 * @return
	 */
	long getTotalAmount();

	/**
	 * The date and time that this counter operation was applied.
	 * 
	 * @return
	 */
	DateTime getDateTimeApplied();

	/**
	 * An enumeration that identifies the type of a {@link CounterShardOperation}.
	 */
	enum CounterOperationType
	{
		INCREMENT, DECREMENT
	}
}
