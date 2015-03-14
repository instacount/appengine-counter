package com.theupswell.appengine.counter.data.ofy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;

/**
 * Unit test for constructors of {@link CounterIndexes}.
 */
public class CounterDataIndexTest
{

	@Test
	public void testAll()
	{
		final CounterIndexes counterIndexes = CounterIndexes.all();
		assertThat(counterIndexes.isCounterStatusIndexable(), is(true));
		assertThat(counterIndexes.isNumShardsIndexable(), is(true));
		assertThat(counterIndexes.isDescriptionIndexable(), is(true));
	}

	@Test
	public void testNone()
	{
		final CounterIndexes counterIndexes = CounterIndexes.none();
		assertThat(counterIndexes.isCounterStatusIndexable(), is(false));
		assertThat(counterIndexes.isNumShardsIndexable(), is(false));
		assertThat(counterIndexes.isDescriptionIndexable(), is(false));
	}

	@Test
	public void testDefaultIndexed()
	{
		final CounterIndexes counterIndexes = CounterIndexes.sensibleDefaults();
		assertThat(counterIndexes.isCounterStatusIndexable(), is(true));
		assertThat(counterIndexes.isNumShardsIndexable(), is(true));
		assertThat(counterIndexes.isDescriptionIndexable(), is(false));
	}

}
