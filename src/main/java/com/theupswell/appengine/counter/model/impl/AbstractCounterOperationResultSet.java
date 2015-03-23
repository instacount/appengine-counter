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
import com.theupswell.appengine.counter.model.CounterOperationResult;
import com.theupswell.appengine.counter.model.CounterOperationResultSet;
import com.theupswell.appengine.counter.model.CounterOperationType;

/**
 * An implementation of {@link CounterOperationResultSet} to model a counter operation that consists of 0 or more
 * operations upon counter shards.
 */
@Getter
@ToString
@EqualsAndHashCode(of = {
	"operationUuid"
})
public abstract class AbstractCounterOperationResultSet<T extends CounterOperationResult> implements
		CounterOperationResultSet<T>
{
	// A unique identifier for this counter operation collection.
	private final UUID operationUuid;
	private final CounterOperationType counterOperationType;

	// May be foolhearty, but this Set could theoretically hold both Increment and Decrements at the same time.
	// This is why this class is not generically typed to a single CounterOperationResult type, but is instead
	// more lenient.
	private final Set<T> counterOperationResults;

	/**
	 * Required-args Constructor.
	 *
	 * @param operationUuid A {@link UUID} that uniquely identifies this counter operation result set.
	 * @param counterOperationType The {@link CounterOperationType} of this operation.
	 * @param counterOperationResults A {@link Set} of instances of {@link CounterOperationResult} that represent the
	 *            shards operated upon and any associated data such as the amount.
	 */
	public AbstractCounterOperationResultSet(final UUID operationUuid, final CounterOperationType counterOperationType,
			final Set<T> counterOperationResults)
	{
		Preconditions.checkNotNull(operationUuid);
		this.operationUuid = operationUuid;

		Preconditions.checkNotNull(operationUuid);
		this.counterOperationType = counterOperationType;

		Preconditions.checkNotNull(counterOperationResults);
		this.counterOperationResults = ImmutableSet.copyOf(counterOperationResults);
	}

	/**
	 * Helper method to retrieve the first {@link CounterOperationResult} in the Set of results accessed via
	 * {@link #getCounterOperationResults()}.
	 *
	 * @return
	 */
	public Optional<T> getFirstCounterOperationResult()
	{
		Set<T> results = this.getCounterOperationResults();
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

		for (CounterOperationResult operation : counterOperationResults)
		{
			amount += operation.getAmount();
		}

		return amount;
	}
}
