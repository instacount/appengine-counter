package com.theupswell.appengine.counter.model.impl;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.data.CounterShardOperationData;
import com.theupswell.appengine.counter.model.CounterShardOperation;

/**
 * A container class that is used to identify a discrete decrement for later identification.
 */
@Getter
@ToString
@EqualsAndHashCode(of = {
	"id", "counterShardOperationDataKey"
})
public abstract class AbstractCounterShardOperation implements CounterShardOperation
{
	private final long id;

	private final Key<CounterShardOperationData> counterShardOperationDataKey;

	private final long amount;

	private DateTime creationDateTime;

	/**
	 * Required-args Constructor.
	 *
	 * @param id A long number that when coupled with {@code counterShardOperationDataKey} uniquely identifies this
	 *            counter shard operation.
	 * @param counterShardOperationDataKey A {@link Key} for the associated {@link CounterShardData} that this increment
	 *            was performed against.
	 * @param amount The amount of the applied increment or decrement.
	 * @param creationDateTime The {@link DateTime} that this operation was created.
	 */
	protected AbstractCounterShardOperation(final long id,
			final Key<CounterShardOperationData> counterShardOperationDataKey, final long amount,
			final DateTime creationDateTime)
	{
		Preconditions.checkArgument(id > 0);
		this.id = id;

		Preconditions.checkNotNull(counterShardOperationDataKey);
		this.counterShardOperationDataKey = counterShardOperationDataKey;

		Preconditions.checkArgument(amount > 0);
		this.amount = amount;

		Preconditions.checkNotNull(creationDateTime);
		this.creationDateTime = creationDateTime;
	}
}
