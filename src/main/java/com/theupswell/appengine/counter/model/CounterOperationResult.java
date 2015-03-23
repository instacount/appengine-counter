package com.theupswell.appengine.counter.model;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * An interface for modeling the result of a mutation (increment or decrement) of a {@link CounterShardData} entity in
 * the Datastore.
 */
public interface CounterOperationResult
{
	/**
	 * Return the unique identifier for this {@link CounterOperationResult}.
	 * 
	 * @return
	 */
	public String getOperationId();

	/**
	 * Return the {@link Key} for the {@link CounterShardData} that a counter operation was effected upon.
	 *
	 * @return
	 */
	public Key<CounterShardData> getCounterShardDataKey();

	/**
	 * Return the amount of this mutation result, as a long.
	 *
	 * @return
	 */
	public long getAmount();
}
