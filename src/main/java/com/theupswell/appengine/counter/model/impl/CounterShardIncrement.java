package com.theupswell.appengine.counter.model.impl;

import java.util.UUID;

import org.joda.time.DateTime;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
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
	 * @param counterShardOperationUuid A {@link UUID} that uniquely identifies this counter shard operation.
	 * @param parentCounterOperationUuid A {@link UUID} that identifies the parent operation for this counter shard
	 *            operation. Multiple increments/decrements may occur as part of a single counter operation.
	 * @param counterShardDataKey A {@link Key} for the associated {@link CounterShardData} that this increment was
	 *            performed against.
	 * @param amount The amount of this operation.
	 * @param creationDateTime The {@link DateTime} that this operation was created.
	 */
	public CounterShardIncrement(final UUID counterShardOperationUuid, final UUID parentCounterOperationUuid,
			final Key<CounterShardData> counterShardDataKey, final long amount, final DateTime creationDateTime)
	{
		super(counterShardOperationUuid, parentCounterOperationUuid, counterShardDataKey, amount, creationDateTime);
	}
}
