package com.theupswell.appengine.counter;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.math.BigInteger;
import java.util.Map;

import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;

public class CounterTest
{

	private static final String TEST_COUNTER_NAME = "test-counter";
	private static final String TEST_COUNTER_DESCRIPTION = "test-counter-description";
	private static final int NUM_SHARDS = 3;
	private static final BigInteger COUNT = BigInteger.valueOf(10L);
	private static final CounterIndexes ALL_INDEXES = CounterIndexes.all();
	private static final DateTime CREATION_DATE_TIME = DateTime.now(DateTimeZone.UTC);
	private static final Map<String, EmbeddedEntity> ADDITIONAL_ATTRIBUTES = ImmutableMap.of();

	@Test
	public void testConstructors() throws Exception
	{
		Counter actual = new Counter(TEST_COUNTER_NAME);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getDescription(), is(nullValue()));
		assertThat(actual.getNumShards(), is(3));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getAdditionalAttributes(), is(ADDITIONAL_ATTRIBUTES));
		assertThat(actual, is(actual));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getAdditionalAttributes(), is(ADDITIONAL_ATTRIBUTES));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION, NUM_SHARDS, CounterStatus.AVAILABLE,
			ALL_INDEXES);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getAdditionalAttributes(), is(ADDITIONAL_ATTRIBUTES));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION, NUM_SHARDS, CounterStatus.AVAILABLE, COUNT,
			ALL_INDEXES, CREATION_DATE_TIME, ADDITIONAL_ATTRIBUTES);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getAdditionalAttributes(), is(ADDITIONAL_ATTRIBUTES));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION, NUM_SHARDS, CounterStatus.AVAILABLE, COUNT,
			ALL_INDEXES, CREATION_DATE_TIME, ADDITIONAL_ATTRIBUTES);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getAdditionalAttributes(), is(ADDITIONAL_ATTRIBUTES));
	}
}