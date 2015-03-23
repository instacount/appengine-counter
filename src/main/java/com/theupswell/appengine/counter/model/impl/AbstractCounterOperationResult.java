package com.theupswell.appengine.counter.model.impl;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperationResult;

/**
 * A container class that is used to identify a discrete decrement for later identification.
 */
@Getter
@ToString
@EqualsAndHashCode(of = "operationId")
public abstract class AbstractCounterOperationResult implements CounterOperationResult
{
	// A unique identifier for this counter operation collection.
	private final String operationId;

	// The Key of the CounterShardData that this mutation occurred on.
	private final Key<CounterShardData> counterShardDataKey;

	private final long amount;

	private DateTime creationDateTime;

	/**
	 * Required-args Constructor.
	 *
	 * @param operationId A {@link String} that uniquely identifies this operation.
	 * @param counterShardDataKey A unique identifier that consists of a
	 * @param amount The amount of the applied increment or decrement.
	 * @param creationDateTime The {@link DateTime} that this operation was created.
	 */
	protected AbstractCounterOperationResult(final String operationId, final Key<CounterShardData> counterShardDataKey,
			final long amount, final DateTime creationDateTime)
	{
		Preconditions.checkNotNull(operationId);
		this.operationId = operationId;

		Preconditions.checkNotNull(counterShardDataKey);
		this.counterShardDataKey = counterShardDataKey;

		Preconditions.checkArgument(amount > 0);
		this.amount = amount;

		Preconditions.checkNotNull(creationDateTime);
		this.creationDateTime = creationDateTime;
	}
}
