package com.theupswell.appengine.counter.model.impl;

import java.util.UUID;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperationResult;

/**
 * An implementation of {@link CounterOperationResult} that extends {@link AbstractCounterOperationResult} to model an
 * increment to a counter.
 */
public class IncrementResult extends AbstractCounterOperationResult implements CounterOperationResult
{
	/**
	 * Required-args Constructor.
	 *
	 * @param counterShardDataKey A {@link Key} for the associated {@link CounterShardData} operated upon.
	 * @param amount The amount of this operation.
	 */
	public IncrementResult(final UUID operationUuid, final Key<CounterShardData> counterShardDataKey,
			final long amount)
	{
		super(operationUuid, counterShardDataKey, amount);
	}
}
