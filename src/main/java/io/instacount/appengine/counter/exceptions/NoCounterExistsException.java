package io.instacount.appengine.counter.exceptions;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * An extension of {@link RuntimeException} that is used to indicate that a counter exists.
 */
@Getter
@Setter
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class NoCounterExistsException extends RuntimeException
{
	@NonNull
	private String counterName;
}
