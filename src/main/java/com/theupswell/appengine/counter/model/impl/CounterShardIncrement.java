package com.theupswell.appengine.counter.model.impl;

import org.joda.time.DateTime;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.data.CounterShardOperationData;
import com.theupswell.appengine.counter.model.CounterShardOperation;

/**
 * An implementation of {@link CounterShardOperation} that extends {@link AbstractCounterShardOperation} to model an
 * increment to a counter.
 */
public class CounterShardIncrement extends AbstractCounterShardOperation implements CounterShardOperation
{
	/**
	 * Required-args Constructor.
	 *
	 * @param id A long number that when coupled with {@code counterShardOperationDataKey} uniquely identifies this
	 *            counter shard operation.
	 * @param counterShardOperationDataKey A {@link Key} for the associated {@link CounterShardData} that this increment
	 *            was performed against.
	 * @param amount The amount of this operation.
	 * @param creationDateTime The {@link DateTime} that this operation was created.
	 */
	public CounterShardIncrement(final long id, final Key<CounterShardOperationData> counterShardOperationDataKey,
			final long amount, final DateTime creationDateTime)
	{
		super(id, counterShardOperationDataKey, amount, creationDateTime);
	}
}
