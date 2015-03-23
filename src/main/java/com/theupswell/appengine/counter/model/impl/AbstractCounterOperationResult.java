package com.theupswell.appengine.counter.model.impl;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperationResult;

/**
 * A container class that is used to identify a discrete decrement for later identification.
 */
@Getter
@ToString
@EqualsAndHashCode(of = "operationUuid")
public abstract class AbstractCounterOperationResult implements CounterOperationResult
{
	// A unique identifier for this counter operation collection.
	private final UUID operationUuid;

	// The Key of the CounterShardData that this mutation occurred on.
	private final Key<CounterShardData> counterShardDataKey;

	private final long amount;

	/**
	 * Required-args Constructor.
	 *
	 * @param operationUuid A {@link UUID} that uniquely identifies this operation.
	 * @param counterShardDataKey A unique identifier that consists of a
	 * @param amount The amount of the applied increment or decrement.
	 */
	protected AbstractCounterOperationResult(final UUID operationUuid, final Key<CounterShardData> counterShardDataKey,
			final long amount)
	{
		Preconditions.checkNotNull(operationUuid);
		this.operationUuid = operationUuid;

		Preconditions.checkNotNull(counterShardDataKey);
		this.counterShardDataKey = counterShardDataKey;

		Preconditions.checkArgument(amount > 0);
		this.amount = amount;
	}
}
