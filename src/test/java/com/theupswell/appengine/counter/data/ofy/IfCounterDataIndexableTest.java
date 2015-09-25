package com.theupswell.appengine.counter.data.ofy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;

import com.googlecode.objectify.ObjectifyFactory;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterGroupData;

/**
 * Unit test for {@link IfCounterDataIndexable}.
 */
public class IfCounterDataIndexableTest
{
	private ObjectifyFactory fact;
	private Field numShardsField;
	private Field counterStatusField;
	private Field counterDescriptionField;
	private Field counterCountField;

	private CounterData counterData;

	@Before
	public void before() throws NoSuchFieldException
	{
		this.fact = new ObjectifyFactory();

		this.numShardsField = CounterData.class.getDeclaredField("numShards");
		this.counterStatusField = CounterData.class.getDeclaredField("counterStatus");
		this.counterDescriptionField = CounterData.class.getDeclaredField("description");
		this.counterCountField = CounterGroupData.class.getDeclaredField("eventuallyConsistentCount");

		this.counterData = new CounterData("testCounterName", 3);
	}

	// ///////////////////////////////
	// Null Value
	// ///////////////////////////////

	@Test
	public void testMatchesPojo_Null() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(numShardsField);
		assertThat(ifCounterDataIndexable.matchesPojo(null), is(false));
	}

	// ///////////////////////////////
	// Matches Value
	// ///////////////////////////////

	@Test
	public void testMatchesValue() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(numShardsField);
		assertThat(ifCounterDataIndexable.matchesValue(this.counterData), is(false));
	}

	// ///////////////////////////////
	// Wrong Class
	// ///////////////////////////////

	@Test
	public void testWrongClassForField() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(Counter.class
			.getDeclaredField("count"));
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(false));
	}

	// ///////////////////////////////
	// NumShards Field
	// ///////////////////////////////

	@Test
	public void testMatchesPojo_NumShardsField_NotIndexable_None() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(numShardsField);
		this.counterData.setIndexes(CounterIndexes.none());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(false));
	}

	@Test
	public void testMatchesPojo_NumShardsField_Indexable_All() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(numShardsField);
		this.counterData.setIndexes(CounterIndexes.all());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_NumShardsField_Indexable_SensibleDefaults() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(numShardsField);
		this.counterData.setIndexes(CounterIndexes.sensibleDefaults());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_NumShardsField_Indexable_Only() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(numShardsField);
		this.counterData.setIndexes(new CounterIndexes().withCounterStatusIndexable(false)
			.withNumShardsIndexable(true).withDescriptionIndexable(false).withCountIndexable(false));
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	// ///////////////////////////////
	// CounterStatus Field
	// ///////////////////////////////

	@Test
	public void testMatchesPojo_CounterStatus_NotIndexable_All() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterStatusField);
		this.counterData.setIndexes(CounterIndexes.all());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_CounterStatus_Indexable_None() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterStatusField);
		this.counterData.setIndexes(CounterIndexes.none());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(false));
	}

	@Test
	public void testMatchesPojo_CounterStatus_NotIndexable_SensibleDefaults() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterStatusField);
		this.counterData.setIndexes(CounterIndexes.sensibleDefaults());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_CounterStatus_Indexable_Only() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterStatusField);
		this.counterData.setIndexes(new CounterIndexes().withCounterStatusIndexable(true)
			.withNumShardsIndexable(false).withDescriptionIndexable(false).withCountIndexable(false));
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	// ///////////////////////////////
	// CounterDescription Field
	// ///////////////////////////////

	@Test
	public void testMatchesPojo_Description_NotIndexable_All() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterDescriptionField);
		this.counterData.setIndexes(CounterIndexes.all());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_Description_Indexable_None() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterDescriptionField);
		this.counterData.setIndexes(CounterIndexes.none());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(false));
	}

	@Test
	public void testMatchesPojo_Description_Indexable_SensibleDefaults() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterDescriptionField);
		this.counterData.setIndexes(CounterIndexes.sensibleDefaults());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(false));
	}

	@Test
	public void testMatchesPojo_Description_Indexable_Only() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterDescriptionField);
		this.counterData.setIndexes(new CounterIndexes().withCounterStatusIndexable(false)
			.withNumShardsIndexable(false).withDescriptionIndexable(true).withCountIndexable(false));
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	// ///////////////////////////////
	// Count Field
	// ///////////////////////////////

	@Test
	public void testMatchesPojo_Count_NotIndexable_All() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterCountField);
		this.counterData.setIndexes(CounterIndexes.all());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_Count_Indexable_None() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterCountField);
		this.counterData.setIndexes(CounterIndexes.none());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(false));
	}

	@Test
	public void testMatchesPojo_Count_Indexable_SensibleDefaults() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterCountField);
		this.counterData.setIndexes(CounterIndexes.sensibleDefaults());
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	@Test
	public void testMatchesPojo_Count_Indexable_Only() throws Exception
	{
		final IfCounterDataIndexable ifCounterDataIndexable = constructIfCounterData(counterCountField);
		this.counterData.setIndexes(new CounterIndexes().withCounterStatusIndexable(false)
			.withNumShardsIndexable(false).withDescriptionIndexable(true).withCountIndexable(true));
		assertThat(ifCounterDataIndexable.matchesPojo(this.counterData), is(true));
	}

	// /////////////////////////
	// Private Helpers
	// /////////////////////////

	/**
	 * Helper to construct an instance of {@link IfCounterDataIndexable} with the proper {@link Field} for testing.
	 * 
	 * @param field
	 * @return
	 */
	private IfCounterDataIndexable constructIfCounterData(final Field field)
	{
		final IfCounterDataIndexable ifCounterDataIndexable = new IfCounterDataIndexable();
		ifCounterDataIndexable.init(fact, field);
		return ifCounterDataIndexable;
	}
}
