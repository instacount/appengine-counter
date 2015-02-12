package com.theupswell.appengine.counter.service;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;

public class ShardedCounterServiceImplTest extends AbstractShardedCounterServiceTest
{

	private static final String NEW_DESCRIPTION = "new Description";
	private ShardedCounterServiceImpl impl;

	@Before
	public void setUp() throws Exception
	{
		super.setUp();

		this.counterShardKey = Key.create(CounterShardData.class, "123");
		this.impl = (ShardedCounterServiceImpl) shardedCounterService;
	}

	@After
	public void tearDown()
	{
		super.tearDown();
	}

	// /////////////////////////
	// Unit Tests
	// /////////////////////////

	// #getCounter

	@Test(expected = IllegalArgumentException.class)
	public void testGetCounter_NullName() throws Exception
	{
		impl.getCounter(null);
	}

	@Test
	public void testGetCounter_Deleting() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		final CounterData counterData = impl.getOrCreateCounterData(counterName);
		counterData.setCounterStatus(CounterStatus.DELETING);
		ObjectifyService.ofy().save().entity(counterData).now();

		final Counter counter = impl.getCounter(counterName);
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(0L));
	}

	@Test
	public void testGetCounter_CountIsCachedInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		this.memcache.put(counterName, 10L);

		Counter counter = impl.getCounter(counterName);
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(10L));
	}

	@Test
	public void testGetCounter_NoCountInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		this.memcache.delete(counterName);

		Counter counter = impl.getCounter(counterName);
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(0L));

		// Assert that the value was added to memcache.
		assertThat(new Long(memcache.get(counterName).toString()), is(0L));
	}

	// #updateCounterDetails

	@Test(expected = NullPointerException.class)
	public void testUpdateCounterDetails() throws Exception
	{
		impl.updateCounterDetails(null);
	}

	@Test
	public void testUpdateCounterDetails_NoPreExistingCounterInDatastore() throws Exception
	{
		// counter. final String counterName, final String counterDescription, final int numShards,
		// final CounterData.CounterStatus counterStatus
		Counter updatedCounter = new Counter(TEST_COUNTER1, NEW_DESCRIPTION, 22, CounterStatus.READ_ONLY_COUNT);
		impl.updateCounterDetails(updatedCounter);

		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
	}

	@Test
	public void testUpdateCounterDetails_PreExistingCounterInDatastore() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// counter. final String counterName, final String counterDescription, final int numShards,
		// final CounterData.CounterStatus counterStatus
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.READ_ONLY_COUNT);
		impl.updateCounterDetails(updatedCounter);

		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
	}

	@Test
	public void testUpdateCounterDetails_ProperCounterStatuses() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// Set the counter be in the READ-ONLY state.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.READ_ONLY_COUNT);
		impl.updateCounterDetails(updatedCounter);
		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));

		// //////////////////
		// Try to update the state to be AVAILABLE.
		// //////////////////
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE);
		impl.updateCounterDetails(updatedCounter);

		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.AVAILABLE));
	}

	@Test(expected = RuntimeException.class)
	public void testUpdateCounterDetails_InvalidCounterStatuses() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// Set the counter be in the READ-ONLY state.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.CONTRACTING_SHARDS);
		impl.updateCounterDetails(updatedCounter);
		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.CONTRACTING_SHARDS));

		// //////////////////
		// Try to update the state to be AVAILABLE.
		// //////////////////
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE);
		try
		{
			impl.updateCounterDetails(updatedCounter);
			fail();
		}
		catch (RuntimeException re)
		{
			assertThat(
				re.getMessage(),
				is("Can't mutate the details of counter 'test-counter1' because it's currently in the CONTRACTING_SHARDS state but must be in in the AVAILABLE or READ_ONLY_COUNT state!"));
			throw re;
		}
	}

	@Test(expected = RuntimeException.class)
	public void testUpdateCounterDetails_ShardReductionNotAllowed() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// Set the counter shards to be something high.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE);
		impl.updateCounterDetails(updatedCounter);

		// Reduce the number of counter shards
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 20, CounterStatus.AVAILABLE);

		try
		{
			impl.updateCounterDetails(updatedCounter);
			fail();
		}
		catch (Exception e)
		{
			assertThat(
				e.getMessage(),
				is("Reducing the number of counter shards is not currently allowed!  See https://github.com/theupswell/appengine-counter/issues/4 for more details."));
			throw e;
		}

	}

	// /////////////////////////
	// #OnTaskQueueCounterDeletion
	// /////////////////////////

	@Test(expected = NullPointerException.class)
	public void testOnTaskQueueCounterDeletion_NullCounterName() throws Exception
	{
		impl.onTaskQueueCounterDeletion(null);
	}

	@Test
	public void testOnTaskQueueCounterDeletion_NothingInDatastore() throws Exception
	{
		impl.onTaskQueueCounterDeletion("foo");
	}

	@Test(expected = RuntimeException.class)
	public void testOnTaskQueueCounterDeletion_WrongStatus() throws Exception
	{
		impl.increment(TEST_COUNTER1, 1);

		try
		{
			impl.onTaskQueueCounterDeletion(TEST_COUNTER1);
			fail();
		}
		catch (RuntimeException re)
		{
			assertThat(re.getMessage(),
				is("Can't delete counter 'test-counter1' because it is currently not in the DELETING state!"));
			throw re;
		}
	}

	@Test
	public void testOnTaskQueueCounterDeletion() throws Exception
	{
		Counter counter = impl.getCounter(TEST_COUNTER1);
		impl.increment(TEST_COUNTER1, 20);
		impl.increment(TEST_COUNTER1, 20);

		counter = new Counter(counter.getCounterName(), counter.getCounterDescription(), 5, CounterStatus.DELETING);
		impl.updateCounterDetails(counter);
		impl.onTaskQueueCounterDeletion(TEST_COUNTER1);

		counter = impl.getCounter(TEST_COUNTER1);
		assertThat(counter.getCount(), is(0L));

		assertThat(new Long(memcache.get(counter.getCounterName()).toString()), is(0L));
	}

	// /////////////////////////
	// getOrCreateCounterData
	// /////////////////////////

	@Test(expected = NullPointerException.class)
	public void testGetOrCreateCounterData_NullInput() throws Exception
	{
		impl.getOrCreateCounterData(null);
	}

	@Test
	public void testGetOrCreateCounterData_NoPreExistingCounter() throws Exception
	{
		CounterData counterData = impl.getOrCreateCounterData(TEST_COUNTER1);
		assertThat(counterData.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(counterData.getCounterName(), is(TEST_COUNTER1));
		assertThat(counterData.getCounterDescription(), is(nullValue()));
		assertThat(counterData.getNumShards(), is(3));
	}

	@Test
	public void testGetOrCreateCounterData_PreExistingCounter() throws Exception
	{
		final Counter counter = impl.getCounter(TEST_COUNTER1);

		final Counter updatedCounter = new Counter(counter.getCounterName(), counter.getCounterDescription(), 30,
			counter.getCounterStatus());
		impl.updateCounterDetails(updatedCounter);

		final CounterData actual = impl.getOrCreateCounterData(TEST_COUNTER1);

		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getCounterName(), is(TEST_COUNTER1));
		assertThat(actual.getCounterDescription(), is(nullValue()));
		assertThat(actual.getNumShards(), is(30));
	}

	// /////////////////////////
	// incrementMemcacheAtomic
	// /////////////////////////

	@Test(expected = NullPointerException.class)
	public void testIncrementMemcacheAtomic_Null() throws Exception
	{
		this.impl.incrementMemcacheAtomic(null, 10);
	}

	@Test
	public void testIncrementMemcacheAtomic_NegativeOne() throws Exception
	{
		Optional<Long> optActual = this.impl.incrementMemcacheAtomic(TEST_COUNTER1, -1);
		assertThat(optActual.isPresent(), is(false));
	}

	@Test
	public void testIncrementMemcacheAtomic_Zero() throws Exception
	{
		this.impl.incrementMemcacheAtomic(TEST_COUNTER1, 0);
		Optional<Long> optActual = this.impl.incrementMemcacheAtomic(TEST_COUNTER1, 0);
		assertThat(optActual.isPresent(), is(true));
		assertThat(optActual.get(), is(0L));
	}

	@Test
	public void testIncrementMemcacheAtomic_NotInCache() throws Exception
	{
		Optional<Long> actual = this.impl.incrementMemcacheAtomic(TEST_COUNTER1, 1);
		assertThat(actual.isPresent(), is(false));
	}

	// /////////////////////////
	// assembleCounterKeyforMemcache
	// /////////////////////////

	@Test(expected = NullPointerException.class)
	public void testAssembleCounterKeyforMemcache_NullInput() throws Exception
	{
		impl.assembleCounterKeyforMemcache(null);
	}

	@Test
	public void testAssembleCounterKeyforMemcache() throws Exception
	{
		assertThat(impl.assembleCounterKeyforMemcache(TEST_COUNTER1), is(TEST_COUNTER1));
	}

	// /////////////////////////
	// assertCounterAmountMutatable
	// /////////////////////////

	@Test(expected = RuntimeException.class)
	public void testAssertCounterAmountMutatable_ContractingShards_CONTRACTING_SHARDS() throws Exception
	{
		impl.assertCounterAmountMutatable(TEST_COUNTER1, CounterStatus.CONTRACTING_SHARDS);
	}

	@Test(expected = RuntimeException.class)
	public void testAssertCounterAmountMutatable_ContractingShards_DELETING() throws Exception
	{
		impl.assertCounterAmountMutatable(TEST_COUNTER1, CounterStatus.DELETING);
	}

	@Test
	public void testAssertCounterAmountMutatable_ContractingShards_AVAILABLE() throws Exception
	{
		impl.assertCounterAmountMutatable(TEST_COUNTER1, CounterStatus.AVAILABLE);
	}

	@Test(expected = RuntimeException.class)
	public void testAssertCounterAmountMutatable_ContractingShards_EXPANDING_SHARDS() throws Exception
	{
		impl.assertCounterAmountMutatable(TEST_COUNTER1, CounterStatus.EXPANDING_SHARDS);
	}

	@Test(expected = RuntimeException.class)
	public void testAssertCounterAmountMutatable_ContractingShards_READ_ONLY_COUNT() throws Exception
	{
		impl.assertCounterAmountMutatable(TEST_COUNTER1, CounterStatus.READ_ONLY_COUNT);
	}

	// //////////////////////////////////
	// testMemcacheSafeDelete
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testMemcacheSafeDelete_NullInput() throws Exception
	{
		impl.memcacheSafeDelete(null);
	}

	@Test
	public void testMemcacheSafeDelete_MemcacheDisabled() throws Exception
	{
		this.disableMemcache();
		impl.memcacheSafeDelete(TEST_COUNTER1);
	}

	@Test
	public void testMemcacheSafeDelete_MemcacheEnabled() throws Exception
	{
		impl.memcacheSafeDelete(TEST_COUNTER1);
	}

	// //////////////////////////////////
	// testMemcacheSafeDelete
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testMemcacheSafeGet_NullInput() throws Exception
	{
		impl.memcacheSafeGet(null);
	}

	@Test
	public void testMemcacheSafeGet_MemcacheDisabled() throws Exception
	{
		this.disableMemcache();
		impl.memcacheSafeGet(TEST_COUNTER1);
	}

	@Test
	public void testMemcacheSafeGet_MemcacheEnabled() throws Exception
	{
		impl.memcacheSafeGet(TEST_COUNTER1);
	}

	// //////////////////////////////////
	// testIncrementShardWork
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testIncrementShardWork_NullCounterName() throws Exception
	{
		impl.new IncrementShardWork(null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_EmptyCounterName() throws Exception
	{
		impl.new IncrementShardWork("", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_BlankCounterName() throws Exception
	{
		impl.new IncrementShardWork(" ", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_NegativeCount() throws Exception
	{
		impl.new IncrementShardWork(TEST_COUNTER1, -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_ZeroCount() throws Exception
	{
		impl.new IncrementShardWork(TEST_COUNTER1, 0);
	}

	@Test
	public void testIncrementShardWork_NoPreExistingCounter() throws Exception
	{
		final Long actual = impl.new IncrementShardWork(TEST_COUNTER1, 1).run();
		assertThat(actual, is(1L));

		impl.increment(TEST_COUNTER1, 1);

		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(2L));
	}

	@Test
	public void testIncrementShardWork_PreExistingCounter() throws Exception
	{
		impl.increment(TEST_COUNTER1, 1);

		final Long actual = impl.new IncrementShardWork(TEST_COUNTER1, 1).run();
		assertThat(actual, is(1L));

		impl.increment(TEST_COUNTER1, 1);

		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(3L));
	}

	// //////////////////////////////////
	// testDecrementShardWork
	// //////////////////////////////////

	private Key<CounterShardData> counterShardKey;

	@Test(expected = NullPointerException.class)
	public void testDecrementShardWork_NullCounterName() throws Exception
	{
		impl.new DecrementShardWork(null, counterShardKey, 1);
	}

	@Test(expected = NullPointerException.class)
	public void testDecrementShardWork_NullCounterShardKey() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_EmptyCounterName() throws Exception
	{
		impl.new DecrementShardWork("", counterShardKey, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_BlankCounterName() throws Exception
	{
		impl.new DecrementShardWork(" ", counterShardKey, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_NegativeCount() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, counterShardKey, -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_ZeroCount() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, counterShardKey, 0);
	}

	@Test
	public void testDecrementShardWork_NoPreExistingCounter() throws Exception
	{
		final Long actual = impl.new DecrementShardWork(TEST_COUNTER1, counterShardKey, 1).run();
		assertThat(actual, is(0L));
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(0L));
	}

	@Test
	public void testDecrementShardWork_PreExistingCounter() throws Exception
	{
		impl.increment(TEST_COUNTER1, 1);

		final Long actual = impl.new DecrementShardWork(TEST_COUNTER1, counterShardKey, 1).run();
		assertThat(actual, is(0L));
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(1L));
	}
}