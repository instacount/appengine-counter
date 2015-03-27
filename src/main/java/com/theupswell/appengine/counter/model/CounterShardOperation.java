package com.theupswell.appengine.counter.model;

import java.util.UUID;

import org.joda.time.DateTime;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperation.CounterOperationType;

/**
 * An interface for modeling the result of the mutation (increment or decrement) of a {@link CounterShardData} entity in
 * the Datastore. Instances of this interface can be pooled together to create a single {@link CounterOperation}.
 */
public interface CounterShardOperation
{
	/**
	 * Return a {@link UUID} that identifies this counter shard operation.
	 *
	 * @return
	 */
	UUID getCounterShardOperationUuid();

	/**
	 * Return a {@link UUID} that identifies the parent operation for this counter shard operation. Multiple
	 * increments/decrements may occur as part of a single counter operation.
	 * 
	 * @return
	 */
	UUID getParentCounterOperationUuid();

	/**
	 * Return the {@link Key} for the {@link CounterShardData} that this counter operation result was effected upon.
	 * 
	 * @return
	 */
	Key<CounterShardData> getCounterShardDataKey();

	/**
	 * Get the type of counter operation that was performed.
	 *
	 * @return
	 */
	CounterOperationType getCounterOperationType();

	/**
	 * Return the amount of this mutation result, as a long.
	 *
	 * @return
	 */
	long getAmount();

	/**
	 * Return the {@link DateTime} that this instance was created.
	 * 
	 * @return
	 */
	DateTime getCreationDateTime();
}
