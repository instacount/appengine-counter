package com.theupswell.appengine.counter.model.impl;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import com.google.appengine.repackaged.com.google.common.collect.ImmutableSet;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.model.CounterOperation;
import com.theupswell.appengine.counter.model.CounterShardOperation;

/**
 * An implementation of {@link CounterOperation} to model a counter operation that consists of 0 or more operations upon
 * counter shards.
 */
@Getter
@ToString
@EqualsAndHashCode(of = {
	"operationUuid"
})
public abstract class AbstractCounterOperation<T extends CounterShardOperation> implements CounterOperation<T>
{
	// A unique identifier for this counter operation collection.
	private final UUID operationUuid;
	private final CounterOperationType counterOperationType;
	private final Set<T> counterShardOperations;

	/**
	 * Required-args Constructor.
	 *
	 * @param operationUuid A {@link UUID} that uniquely identifies this counter operation result set.
	 * @param counterOperationType The {@link CounterOperationType} of this operation.
	 * @param counterShardOperations A {@link Set} of instances of {@link CounterShardOperation} that represent the
	 *            shards operated upon and any associated data such as the amount.
	 */
	public AbstractCounterOperation(final UUID operationUuid, final CounterOperationType counterOperationType,
			final Set<T> counterShardOperations)
	{
		Preconditions.checkNotNull(operationUuid);
		this.operationUuid = operationUuid;

		Preconditions.checkNotNull(counterOperationType);
		this.counterOperationType = counterOperationType;

		Preconditions.checkNotNull(counterShardOperations);
		this.counterShardOperations = ImmutableSet.copyOf(counterShardOperations);
	}

	/**
	 * Helper method to retrieve the first {@link CounterShardOperation} in the Set of results accessed via
	 * {@link #getCounterShardOperations()}.
	 *
	 * @return
	 */
	public Optional<T> getFirstCounterOperationResult()
	{
		Set<T> results = this.getCounterShardOperations();
		if (results != null)
		{
			Iterator<T> iterator = results.iterator();
			if (iterator.hasNext())
			{
				return Optional.of(iterator.next());
			}
		}
		return Optional.absent();
	}

	/**
	 * Get the total amount of all counter operations. This method assumes a uniformity of CounterOperationResult.
	 *
	 * @return
	 */
	@Override
	public long getTotalAmount()
	{
		long amount = 0;

		for (CounterShardOperation operation : counterShardOperations)
		{
			amount += operation.getAmount();
		}

		return amount;
	}
}
