package com.theupswell.appengine.counter.exceptions;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * An extension of {@link RuntimeException} that is used to indicate that a counter exists.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CounterNotMutableException extends IllegalArgumentException
{
	@NonNull
	private String counterName;

	/**
	 * Required-args constructor.
	 * 
	 * @param counterName The name of the counter that cannot be mutated.
	 * @param message The exception message.
	 */
	public CounterNotMutableException(final String counterName, final String message)
	{
		super(message);
		this.counterName = counterName;
	}
}
