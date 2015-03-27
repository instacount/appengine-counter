package com.theupswell.appengine.counter.model.impl;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.model.CounterOperation.CounterOperationType;
import com.theupswell.appengine.counter.model.CounterShardOperation;

/**
 * A container class that is used to identify a discrete decrement for later identification.
 */
@Getter
@ToString
@EqualsAndHashCode(of = {
	"counterShardOperationUuid"
})
public abstract class AbstractCounterShardOperation implements CounterShardOperation
{
	private final UUID counterShardOperationUuid;

	private final UUID parentCounterOperationUuid;

	private final Key<CounterShardData> counterShardDataKey;

	private final CounterOperationType counterOperationType;

	private final long amount;

	private DateTime creationDateTime;

	/**
	 * Required-args Constructor.
	 *
	 * @param counterShardOperationUuid A {@link UUID} that uniquely identifies this counter shard operation.
	 * @param parentCounterOperationUuid A {@link UUID} that identifies the parent operation for this counter shard
	 *            operation. Multiple increments/decrements may occur as part of a single counter operation.
	 * @param counterShardDataKey A {@link Key} for the associated {@link CounterShardData} that this increment was
	 *            performed against.
	 * @param counterOperationType An instance of {@link CounterOperationType}.
	 * @param amount The amount of the applied increment or decrement.
	 * @param creationDateTime The {@link DateTime} that this operation was created.
	 */
	protected AbstractCounterShardOperation(UUID counterShardOperationUuid, final UUID parentCounterOperationUuid,
			final Key<CounterShardData> counterShardDataKey, final CounterOperationType counterOperationType,
			final long amount, final DateTime creationDateTime)
	{
		Preconditions.checkNotNull(counterShardOperationUuid);
		this.counterShardOperationUuid = counterShardOperationUuid;

		Preconditions.checkNotNull(parentCounterOperationUuid);
		this.parentCounterOperationUuid = parentCounterOperationUuid;

		Preconditions.checkNotNull(counterShardDataKey);
		this.counterShardDataKey = counterShardDataKey;

		Preconditions.checkNotNull(counterShardDataKey);
		this.counterOperationType = counterOperationType;

		Preconditions.checkArgument(amount > 0);
		this.amount = amount;

		Preconditions.checkNotNull(creationDateTime);
		this.creationDateTime = creationDateTime;
	}
}
