package com.theupswell.appengine.counter.model.impl;

import java.util.UUID;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperationResult;

/**
 * An implementation of {@link CounterOperationResult} that extends {@link AbstractCounterOperationResult} to model an
 * decrement to a counter.
 */
public class DecrementResult extends AbstractCounterOperationResult implements CounterOperationResult
{
	/**
	 * Required-args Constructor.
	 *
	 * @param operationUuid A {@link UUID} that uniquely identifies this operation.
	 * @param counterShardDataKey A {@link Key} for the associated {@link CounterShardData} operated upon.
	 * @param amount The amount of this operation.
	 */
	public DecrementResult(final UUID operationUuid, final Key<CounterShardData> counterShardDataKey,
			final long amount)
	{
		super(operationUuid, counterShardDataKey, amount);
	}

}
