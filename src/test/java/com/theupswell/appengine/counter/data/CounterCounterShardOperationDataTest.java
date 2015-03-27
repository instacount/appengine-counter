package com.theupswell.appengine.counter.data;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.model.CounterOperation.CounterOperationType;
import com.theupswell.appengine.counter.service.AbstractShardedCounterServiceTest;

/**
 * Unit tests for {@link CounterShardOperationData}.
 */
public class CounterCounterShardOperationDataTest extends AbstractShardedCounterServiceTest
{

	@Test
	public void getterTest() throws Exception
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		final UUID parentOperationUuid = UUID.randomUUID();

		final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(counterShardDataKey,
			parentOperationUuid, CounterOperationType.INCREMENT, 1L);

		assertThat(counterShardOperationData.getId(), is(0L));
		assertThat(counterShardOperationData.getAmount(), is(1L));
		assertThat(counterShardOperationData.getCounterShardDataKey(), is(counterShardDataKey));
		assertThat(counterShardOperationData.getParentCounterOperationUuid(), is(parentOperationUuid));
		assertThat(
			counterShardOperationData.getCreationDateTime().isBefore(DateTime.now(DateTimeZone.UTC).plusSeconds(10)),
			is(true));

		// TypedKey
		assertThat(counterShardOperationData.getTypedKey().getName(), is(parentOperationUuid.toString()));
		assertThat(counterShardOperationData.getTypedKey().getParent().getName(), is(counterShardDataKey.getName()));

		// RawKey
		assertThat(counterShardOperationData.getKey().getName(), is(parentOperationUuid.toString()));
	}

	@Test
	public void testEquals() throws Exception
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		final UUID parentOperationUuid = UUID.randomUUID();

		final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(counterShardDataKey,
			parentOperationUuid, CounterOperationType.INCREMENT, 1L);

		final CounterShardOperationData counterShardOperationData2 = new CounterShardOperationData(counterShardDataKey,
			parentOperationUuid, CounterOperationType.INCREMENT, 1L);

		assertThat(counterShardOperationData, is(counterShardOperationData2));
	}
}