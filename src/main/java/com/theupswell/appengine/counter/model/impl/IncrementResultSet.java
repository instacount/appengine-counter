package com.theupswell.appengine.counter.model.impl;

import java.util.Set;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import com.google.appengine.repackaged.com.google.common.collect.ImmutableSet;
import com.google.common.base.Preconditions;
import com.theupswell.appengine.counter.model.CounterOperationResultSet;
import com.theupswell.appengine.counter.model.CounterOperationType;

/**
 * An implementation of {@link CounterOperationResultSet} to model a counter operation that consists of 0 or more
 * operations upon counter shards.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class IncrementResultSet extends
		AbstractCounterOperationResultSet<IncrementResult>
{
	/**
	 * Required-args Constructor.
	 *
	 * @param builder An instance of {@link Builder} to construct this instance from.
	 */
	private IncrementResultSet(final Builder builder)
	{
		super(builder.getOperationUuid(), CounterOperationType.INCREMENT, builder.getCounterOperationResults());
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
		private Set<IncrementResult> counterOperationResults;

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
		 * Builder method for constructing instances of {@link IncrementResultSet}.
		 * 
		 * @return
		 */
		public IncrementResultSet build()
		{
			return new IncrementResultSet(this);
		}

		/**
		 * Wither...
		 * 
		 * @param incrementResults
		 * @return
		 */
		public Builder withCounterOperationResults(
				final Set<IncrementResult> incrementResults)
		{
			this.counterOperationResults = incrementResults;
			return this;
		}

		/**
		 * Wither...
		 *
		 * @param incrementResult An instance of {@link IncrementResult}.
		 * @return
		 */
		public Builder withCounterOperationResult(final IncrementResult incrementResult)
		{
			this.counterOperationResults = ImmutableSet.of(incrementResult);
			return this;
		}
	}

}
