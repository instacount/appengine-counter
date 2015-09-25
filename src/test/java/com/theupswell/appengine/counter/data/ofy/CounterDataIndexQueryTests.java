package com.theupswell.appengine.counter.data.ofy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.googlecode.objectify.ObjectifyService;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterGroupData;
import com.theupswell.appengine.counter.service.AbstractShardedCounterServiceTest;

/**
 * Unit tests to check that the {@link IfCounterDataIndexable} annotation is functioning properly.
 */
public class CounterDataIndexQueryTests extends AbstractShardedCounterServiceTest
{
	private static final String TEST_COUNTER_NAME = "test-counter1";

	private CounterData counterData1;
	private CounterData counterData2;
	private CounterData counterData3;

	@Before
	public void before()
	{
		counterData1 = new CounterData("Counter1", 1);
		counterData1.setCounterStatus(CounterStatus.AVAILABLE);
		counterData1.setDescription("Abc Description");
		counterData1.setIndexes(CounterIndexes.all());
		counterData1.setCounterGroupData(new CounterGroupData());
		counterData1.getCounterGroupData().setEventuallyConsistentCount(1);
		ObjectifyService.ofy().save().entity(counterData1).now();

		counterData2 = new CounterData("Counter2", 2);
		counterData2.setCounterStatus(CounterStatus.CONTRACTING_SHARDS);
		counterData2.setDescription("Bcd Description");
		counterData2.setIndexes(CounterIndexes.all());
		counterData2.setCounterGroupData(new CounterGroupData());
		counterData2.getCounterGroupData().setEventuallyConsistentCount(2);
		ObjectifyService.ofy().save().entity(counterData2).now();

		counterData3 = new CounterData("Counter3", 3);
		counterData3.setCounterStatus(CounterStatus.READ_ONLY_COUNT);
		counterData3.setDescription("Cde Description");
		counterData3.setIndexes(CounterIndexes.all());
		counterData3.setCounterGroupData(new CounterGroupData());
		counterData3.getCounterGroupData().setEventuallyConsistentCount(3);
		ObjectifyService.ofy().save().entity(counterData3).now();
	}

	// //////////////////////
	// Indexing Tests
	// //////////////////////

	@Test
	public void testQueryByEachProperty()
	{
		// NumShards Ascending
		List<CounterData> countersList = ObjectifyService.ofy().load().type(CounterData.class).order("numShards")
			.list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData1));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData3));

		// NumShards Descending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("-numShards").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData3));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData1));

		// ///////////////////////
		// ///////////////////////

		// Description Descending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("description").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData1));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData3));

		// Description Ascending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("-description").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData3));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData1));

		// ///////////////////////
		// ///////////////////////

		// Counter Status Descending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("counterStatus").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData1));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData3));

		// Counter Status Ascending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("-counterStatus").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData3));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData1));

		// ///////////////////////
		// ///////////////////////

		// Count Descending
		countersList = ObjectifyService.ofy().load().type(CounterData.class)
			.order("counterGroupData.eventuallyConsistentCount").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData1));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData3));

		// Count Ascending
		countersList = ObjectifyService.ofy().load().type(CounterData.class)
			.order("-counterGroupData.eventuallyConsistentCount").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData3));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData1));

		// ///////////////////////
		// ///////////////////////

		// CreationDateTime Descending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("creationDateTime").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData1));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData3));

		// CreationDateTime Ascending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("-creationDateTime").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData3));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData1	));

		// ///////////////////////
		// ///////////////////////

		// UpdatedDateTime Descending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("updatedDateTime").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData1));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData3));

		// UpdatedDateTime Ascending
		countersList = ObjectifyService.ofy().load().type(CounterData.class).order("-updatedDateTime").list();
		assertThat(countersList.size(), is(3));
		assertThat(countersList.get(0), is(counterData3));
		assertThat(countersList.get(1), is(counterData2));
		assertThat(countersList.get(2), is(counterData1));
	}

}
