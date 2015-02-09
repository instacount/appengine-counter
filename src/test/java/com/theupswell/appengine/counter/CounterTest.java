package com.theupswell.appengine.counter;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Before;
import org.junit.Test;

import com.theupswell.appengine.counter.data.CounterData.CounterStatus;

public class CounterTest
{

	private static final String TEST_COUNTER_NAME = "test-counter";
	private static final String TEST_COUNTER_DESCRIPTION = "test-counter-description";
	private static final int NUM_SHARDS = 3;
	private static final long COUNT = 10L;

	@Before
	public void setUp() throws Exception
	{

	}

	@Test
	public void testConstructors() throws Exception
	{
		Counter actual = new Counter(TEST_COUNTER_NAME);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getCounterName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getCounterDescription(), is(nullValue()));
		assertThat(actual.getNumShards(), is(3));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual, is(actual));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getCounterName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getCounterDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION, NUM_SHARDS, CounterStatus.AVAILABLE);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getCounterName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getCounterDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));

		actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION, NUM_SHARDS, CounterStatus.AVAILABLE, COUNT);
		assertThat(actual, is(not(nullValue())));
		assertThat(actual.getCounterName(), is(TEST_COUNTER_NAME));
		assertThat(actual.getCounterDescription(), is(TEST_COUNTER_DESCRIPTION));
		assertThat(actual.getNumShards(), is(NUM_SHARDS));
		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));

	}
}