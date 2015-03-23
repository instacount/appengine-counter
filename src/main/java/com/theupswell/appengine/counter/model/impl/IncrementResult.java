package com.theupswell.appengine.counter.model.impl;

import org.joda.time.DateTime;

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
	 * @param operationId A {@link String} that uniquely identifies this operation.
	 * @param counterShardDataKey A {@link Key} for the associated {@link CounterShardData} operated upon.
	 * @param amount The amount of this operation.
	 * @param creationDateTime The {@link DateTime} that this operation was created.
	 */
	public IncrementResult(final String operationId, final Key<CounterShardData> counterShardDataKey,
			final long amount, final DateTime creationDateTime)
	{
		super(operationId, counterShardDataKey, amount, creationDateTime);
	}
}
