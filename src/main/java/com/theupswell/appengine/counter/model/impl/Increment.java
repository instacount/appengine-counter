package com.theupswell.appengine.counter.model.impl;

import java.util.Set;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import com.google.appengine.repackaged.com.google.common.collect.ImmutableSet;
import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.model.CounterOperation;

/**
 * An implementation of {@link CounterOperation} to model a counter operation that consists of 0 or more operations upon
 * counter shards.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Increment extends AbstractCounterOperation<CounterShardIncrement> implements
		CounterOperation<CounterShardIncrement>
{
	/**
	 * Required-args Constructor.
	 *
	 * @param builder An instance of {@link Builder} to construct this instance from.
	 */
	private Increment(final Builder builder)
	{
		super(builder.getOperationUuid(), CounterOperationType.INCREMENT, ImmutableSet.copyOf(builder
			.getCounterOperationResults()));
	}

	/**
	 * 
	 */
	@Getter
	@EqualsAndHashCode
	@ToString
	public static class Builder
	{
		// A unique identifier for this counter operation collection.
		private final UUID operationUuid;
		private Set<CounterShardIncrement> counterOperationResults;

		/**
		 * Required-args Constructor.
		 * 
		 * @param operationUuid A {@link UUID} that uniquely identifies an increment.
		 */
		public Builder(final UUID operationUuid)
		{
			Preconditions.checkNotNull(operationUuid);
			this.operationUuid = operationUuid;

			counterOperationResults = ImmutableSet.of();
		}

		/**
		 * Builder method for constructing instances of {@link Increment}.
		 * 
		 * @return
		 */
		public Increment build()
		{
			return new Increment(this);
		}

		/**
		 * Wither...
		 * 
		 * @param shardIncrementResults
		 * @return
		 */
		public Builder withCounterOperationResults(final Set<CounterShardIncrement> shardIncrementResults)
		{
			this.counterOperationResults = shardIncrementResults;
			return this;
		}

		/**
		 * Wither...
		 *
		 * @param shardIncrementResult An instance of {@link CounterShardIncrement}.
		 * @return
		 */
		public Builder withCounterOperationResult(final CounterShardIncrement shardIncrementResult)
		{
			this.counterOperationResults = ImmutableSet.of(shardIncrementResult);
			return this;
		}
	}

}
