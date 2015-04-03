package com.theupswell.appengine.counter.data;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.CounterOperation.CounterOperationType;
import com.theupswell.appengine.counter.service.AbstractShardedCounterServiceTest;

/**
 * Unit tests for {@link CounterShardOperationData}.
 */
public class CounterShardOperationDataTest extends AbstractShardedCounterServiceTest
{

	@Test(expected = NullPointerException.class)
	public void testConstructor_NullCounterShardDataKey()
	{
		final UUID uuid = UUID.randomUUID();
		new CounterShardOperationData(null, uuid, CounterOperationType.INCREMENT, 1L);
	}

	@Test(expected = NullPointerException.class)
	public void testConstructor_NullId()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);

		new CounterShardOperationData(counterShardDataKey, null, CounterOperationType.INCREMENT, 1L);
	}

	@Test(expected = NullPointerException.class)
	public void testConstructor_NullCounterOperationType()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		final UUID uuid = UUID.randomUUID();

		new CounterShardOperationData(counterShardDataKey, uuid, null, 1L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_NegativeMutationAmount()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		final UUID uuid = UUID.randomUUID();

		new CounterShardOperationData(counterShardDataKey, uuid, CounterOperationType.INCREMENT, -1L);
	}

	@Test
	public void getterTest() throws Exception
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		final UUID uuid = UUID.randomUUID();
		final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(counterShardDataKey,
			uuid, CounterOperationType.INCREMENT, 1L);

		assertThat(counterShardOperationData.getMutationAmount(), is(1L));
		assertThat(counterShardOperationData.getCounterShardDataKey(), is(counterShardDataKey));
		assertThat(counterShardOperationData.getId(), is(uuid.toString()));
		assertThat(
			counterShardOperationData.getCreationDateTime().isBefore(DateTime.now(DateTimeZone.UTC).plusSeconds(10)),
			is(true));

		// TypedKey
		assertThat(counterShardOperationData.getTypedKey().getName(), is(uuid.toString()));
		assertThat(counterShardOperationData.getTypedKey().getParent().getName(), is(counterShardDataKey.getName()));

		// RawKey
		assertThat(counterShardOperationData.getKey().getName(), is(uuid.toString()));
	}

	@Test
	public void testEquals() throws Exception
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		final UUID uuid = UUID.randomUUID();

		final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(counterShardDataKey,
			uuid, CounterOperationType.INCREMENT, 1L);

		final CounterShardOperationData counterShardOperationData2 = new CounterShardOperationData(counterShardDataKey,
			uuid, CounterOperationType.INCREMENT, 1L);

		assertThat(counterShardOperationData, is(counterShardOperationData2));
	}

}