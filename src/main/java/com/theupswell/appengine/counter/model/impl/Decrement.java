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
public class Decrement extends AbstractCounterOperation<CounterShardDecrement> implements
		CounterOperation<CounterShardDecrement>
{
	/**
	 * Required-args Constructor.
	 *
	 * @param builder An instance of {@link Builder} to construct this instance from.
	 */
	private Decrement(final Builder builder)
	{
		super(builder.getOperationUuid(), CounterOperationType.DECREMENT, ImmutableSet.copyOf(builder
			.getCounterOperationResults()));
	}

	/**
	 * A builder for instances of {@link Decrement}.
	 */
	@Getter
	@EqualsAndHashCode
	@ToString
	public static class Builder
	{
		// A unique identifier for this counter operation collection.
		private final UUID operationUuid;

		private Set<CounterShardDecrement> counterOperationResults;

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
		 * Builder method for constructing instances of {@link Decrement}.
		 *
		 * @return
		 */
		public Decrement build()
		{
			return new Decrement(this);
		}

		/**
		 * Wither...
		 *
		 * @param counterShardDecrementResults
		 * @return
		 */
		public Builder withCounterOperationResults(final Set<CounterShardDecrement> counterShardDecrementResults)
		{
			this.counterOperationResults = counterShardDecrementResults;
			return this;
		}
	}

}
