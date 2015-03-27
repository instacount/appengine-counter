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
public class CounterOperationImpl implements CounterOperation
{
	// A unique identifier for this counter operation collection.
	private final UUID operationUuid;
	private final CounterOperationType counterOperationType;
	private final Set<CounterShardOperation> counterShardOperations;

	/**
	 * Required-args Constructor.
	 *
	 * @param counterOperation A {@link CounterOperation} to copy from.
	 */
	public CounterOperationImpl(final CounterOperation counterOperation)
	{
		this(counterOperation.getOperationUuid(), counterOperation.getCounterOperationType(), counterOperation
			.getCounterShardOperations());
	}

	/**
	 * Required-args Constructor.
	 *
	 * @param operationUuid A {@link UUID} that uniquely identifies this counter operation result set.
	 * @param counterOperationType The {@link CounterOperationType} of this operation.
	 * @param counterShardOperations A {@link Set} of instances of {@link CounterShardOperation} that represent the
	 *            shards operated upon and any associated data such as the amount.
	 */
	public CounterOperationImpl(final UUID operationUuid, final CounterOperationType counterOperationType,
			final Set<CounterShardOperation> counterShardOperations)
	{
		Preconditions.checkNotNull(operationUuid);
		this.operationUuid = operationUuid;

		Preconditions.checkNotNull(counterOperationType);
		this.counterOperationType = counterOperationType;

		Preconditions.checkNotNull(counterShardOperations);
		this.counterShardOperations = ImmutableSet.copyOf(counterShardOperations);
	}

	public CounterOperationImpl(final Builder builder)
	{
		Preconditions.checkNotNull(builder);

		this.operationUuid = builder.getOperationUuid();
		this.counterOperationType = builder.getCounterOperationType();
		this.counterShardOperations = builder.getCounterShardOperations();
	}

	@Override
	public Optional<CounterShardOperation> getFirstCounterOperationResult()
	{
		Set<CounterShardOperation> results = this.getCounterShardOperations();
		if (results != null)
		{
			Iterator<CounterShardOperation> iterator = results.iterator();
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

	/**
	 * A builder for instances of {@link CounterOperationImpl}.
	 */
	@Getter
	@EqualsAndHashCode
	@ToString
	public static class Builder
	{
		// A unique identifier for this counter operation collection.
		private final UUID operationUuid;

		private final CounterOperationType counterOperationType;

		private Set<CounterShardOperation> counterShardOperations;

		/**
		 * Required-args Constructor.
		 *
		 * @param operationUuid A {@link UUID} that uniquely identifies an increment.
		 */
		public Builder(final UUID operationUuid, final CounterOperationType counterOperationType)
		{
			Preconditions.checkNotNull(operationUuid);
			this.operationUuid = operationUuid;

			Preconditions.checkNotNull(counterOperationType);
			this.counterOperationType = counterOperationType;

			counterShardOperations = ImmutableSet.of();
		}

		/**
		 * Builder method for constructing instances of {@link CounterOperation}.
		 *
		 * @return
		 */
		public CounterOperationImpl build()
		{
			return new CounterOperationImpl(this);
		}

		/**
		 * Wither...
		 *
		 * @param shardIncrementResult An instance of {@link CounterShardOperation}.
		 * @return
		 */
		public Builder withCounterOperationResult(final CounterShardOperation shardIncrementResult)
		{
			this.counterShardOperations = ImmutableSet.of(shardIncrementResult);
			return this;
		}

		/**
		 * Wither...
		 *
		 * @param counterShardOperations
		 * @return
		 */
		public Builder withCounterOperationResults(final Set<CounterShardOperation> counterShardOperations)
		{
			Preconditions.checkNotNull(counterShardOperations);
			this.counterShardOperations = counterShardOperations;
			return this;
		}
	}
}
