package com.theupswell.appengine.counter.data;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.service.AbstractShardedCounterServiceTest;

public class CounterDataTest extends AbstractShardedCounterServiceTest
{
	private static final String TEST_COUNTER_NAME = "test-counter1";

	@Test(expected = NullPointerException.class)
	public void testConstructor_NullCounterName()
	{
		new CounterData(null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_EmptyName()
	{
		new CounterData("", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_BlankName()
	{
		new CounterData(" ", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_NegativeShards()
	{
		new CounterData("", -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_0Shards()
	{
		new CounterData(TEST_COUNTER_NAME, 0);
	}

	@Test(expected = NullPointerException.class)
	public void testGetKey_NullCounterName()
	{
		CounterData.key(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetKey_EmptyCounterName()
	{
		CounterData.key("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetKey_BlankCounterName()
	{
		CounterData.key(" ");
	}

	@Test
	public void testGetKey()
	{
		Key<CounterData> actual = CounterData.key(TEST_COUNTER_NAME);
		assertThat(actual, is(Key.create(CounterData.class, TEST_COUNTER_NAME)));
	}
}