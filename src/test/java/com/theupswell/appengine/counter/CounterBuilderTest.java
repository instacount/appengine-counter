package com.theupswell.appengine.counter;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.math.BigInteger;
import java.util.Map;

import com.google.appengine.api.datastore.EmbeddedEntity;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;

public class CounterBuilderTest
{

	private static final String TEST_COUNTER_NAME = "test-counter";
	private static final String TEST_COUNTER_DESCRIPTION = "test-counter-description";
	private static final int NUM_SHARDS = 3;
	private static final BigInteger COUNT = BigInteger.valueOf(10L);
	private static final CounterIndexes NO_INDEXES = CounterIndexes.none();
	private static final DateTime CREATION_DATE_TIME = DateTime.now(DateTimeZone.UTC);
	private static final Map<String, EmbeddedEntity> ADDITIONAL_ATTRIBUTES = ImmutableMap.of();

	@Test
	public void testBuildWithCounter() throws Exception
	{
		Counter actual = new Counter(TEST_COUNTER_NAME, TEST_COUNTER_DESCRIPTION, NUM_SHARDS, CounterStatus.AVAILABLE,
			COUNT, NO_INDEXES, CREATION_DATE_TIME, ADDITIONAL_ATTRIBUTES);
		Counter copy = new CounterBuilder(actual).build();
		assertThat(actual, is(copy));
	}

	@Test
	public void testBuildWithCounterData() throws Exception
	{
		CounterData actualCounterData = new CounterData(TEST_COUNTER_NAME, NUM_SHARDS);
		Counter copy = new CounterBuilder(actualCounterData).build();
		assertThat(copy.getName(), is(TEST_COUNTER_NAME));
		assertThat(copy.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(copy.getDescription(), is(nullValue()));
		assertThat(copy.getNumShards(), is(NUM_SHARDS));
		assertThat(copy.getIndexes(), is(NO_INDEXES));
		assertThat(copy.getAdditionalAttributes(), is(ADDITIONAL_ATTRIBUTES));
		assertThat(copy.getCreationDateTime(), is(actualCounterData.getCreationDateTime()));
	}

}