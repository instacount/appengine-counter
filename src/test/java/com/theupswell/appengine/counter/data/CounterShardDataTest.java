package com.theupswell.appengine.counter.data;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

import com.googlecode.objectify.Key;
import com.theupswell.appengine.counter.service.AbstractShardedCounterServiceTest;

public class CounterShardDataTest extends AbstractShardedCounterServiceTest
{
	private static final String TEST_COUNTER_NAME = "test-counter1";

	@Test(expected = NullPointerException.class)
	public void testConstructor_NullCounterName()
	{
		new CounterShardData(null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_EmptyName()
	{
		new CounterShardData("", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_BlankName()
	{
		new CounterShardData(" ", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_NegativeShard()
	{
		new CounterShardData("", -1);
	}

	@Test
	public void testConstructor_0Shards()
	{
		new CounterShardData(TEST_COUNTER_NAME, 0);
	}

	@Test(expected = NullPointerException.class)
	public void testGetKey_NullCounterName()
	{
		CounterShardData.key(null, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetKey_EmptyCounterName()
	{
		CounterShardData.key(CounterData.key(""), 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetKey_BlankCounterName()
	{
		CounterShardData.key(CounterData.key(" "), 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetKey_NegativeShard()
	{
		CounterShardData.key(CounterData.key(TEST_COUNTER_NAME), -1);
	}

	@Test
	public void testGetKey()
	{
		final Key<CounterShardData> actual = CounterShardData.key(CounterData.key(TEST_COUNTER_NAME), 1);
		assertThat(actual, is(Key.create(CounterShardData.class, TEST_COUNTER_NAME + "-1")));
	}

	// ///////////////////////////
	// constructCounterShardIdentifier
	// ///////////////////////////

}