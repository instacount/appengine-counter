package com.theupswell.appengine.counter.model;

import java.util.Set;
import java.util.UUID;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * An interface for modeling the result of a mutation (increment or decrement) of a {@link CounterShardData} entity in
 * the Datastore.
 */
public interface CounterOperationResultSet<T extends CounterOperationResult>
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
	public Set<T> getCounterOperationResults();

	/**
	 * Helper method to retrieve the first {@link CounterOperationResult} in the Set of results accessed via
	 * {@link #getCounterOperationResults()}.
	 * 
	 * @return
	 */
	// This is probably not appropriate for the interface, but is instead a helper method in the abstract class.
	// public Optional<T> getFirstCounterOperationResult();

	/**
	 * Return the total amount of this counter operation result across sub-operations, if any.
	 *
	 * @return
	 */
	public long getTotalAmount();

}
