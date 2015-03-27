package com.theupswell.appengine.counter.model;

import org.joda.time.DateTime;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.data.CounterShardOperationData;

/**
 * An interface for modeling the result of the mutation (increment or decrement) of a {@link CounterShardData} entity in
 * the Datastore. Instances of this interface can be pooled together to create a single {@link CounterOperation}.
 */
public interface CounterShardOperation
{
	/**
	 * Return a long number that when coupled with {@link #getCounterShardOperationDataKey()} uniquely identifies this
	 * counter shard operation.
	 * 
	 * @return
	 */
	public long getId();

	/**
	 * Return the {@link Key} for the {@link CounterShardData} that this counter operation result was effected upon.
	 * 
	 * @return
	 */
	public Key<CounterShardOperationData> getCounterShardOperationDataKey();

	/**
	 * Return the amount of this mutation result, as a long.
	 *
	 * @return
	 */
	public long getAmount();

	/**
	 * Return the {@link DateTime} that this instance was created.
	 * 
	 * @return
	 */
	public DateTime getCreationDateTime();
}
