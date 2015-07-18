package com.theupswell.appengine.counter.exceptions;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An extension of {@link RuntimeException} that is used to indicate that a counter exists.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InvalidCounterNameException extends RuntimeException
{
	/**
	 * Required-args constructor.
	 *
	 * @param message The exception message.
	 */
	public InvalidCounterNameException(final String message)
	{
		super(message);
	}
}
