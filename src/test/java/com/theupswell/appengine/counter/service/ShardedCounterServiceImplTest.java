package com.theupswell.appengine.counter.service;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.repackaged.com.google.common.collect.ImmutableSet;
import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.data.CounterShardOperationData;
import com.theupswell.appengine.counter.data.CounterShardOperationData.Type;
import com.theupswell.appengine.counter.model.CounterOperationResult;
import com.theupswell.appengine.counter.model.CounterOperationResultSet;
import com.theupswell.appengine.counter.model.impl.DecrementResult;
import com.theupswell.appengine.counter.model.impl.DecrementResultSet;
import com.theupswell.appengine.counter.model.impl.IncrementResult;
import com.theupswell.appengine.counter.model.impl.IncrementResultSet;

public class ShardedCounterServiceImplTest extends AbstractShardedCounterServiceTest
{

	private static final String NEW_DESCRIPTION = "new Description";
	private static final CounterIndexes NO_INDEXES = CounterIndexes.none();
	private static final CounterIndexes ALL_INDEXES = CounterIndexes.all();

	private Key<CounterShardData> testCounter1CounterShard0Key;

	private ShardedCounterServiceImpl impl;

	@Before
	public void setUp() throws Exception
	{
		super.setUp();

		Key<CounterData> testCounter1Key = CounterData.key(TEST_COUNTER1);
		this.testCounter1CounterShard0Key = CounterShardData.key(testCounter1Key, 0);
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
		assertThat(counter.getIndexes(), is(NO_INDEXES));
	}

	@Test
	public void testGetCounter_CountIsCachedInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		this.memcache.put(counterName, 10L);

		Counter counter = impl.getCounter(counterName);
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(10L));
		assertThat(counter.getIndexes(), is(NO_INDEXES));
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
		Counter updatedCounter = new Counter(TEST_COUNTER1, NEW_DESCRIPTION, 22, CounterStatus.READ_ONLY_COUNT,
			ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));
	}

	@Test
	public void testUpdateCounterDetails_PreExistingCounterInDatastore() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// counter. final String counterName, final String counterDescription, final int numShards,
		// final CounterData.CounterStatus counterStatus
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.READ_ONLY_COUNT, ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));
	}

	@Test
	public void testUpdateCounterDetails_ProperCounterStatuses() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// Set the counter be in the READ-ONLY state.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.READ_ONLY_COUNT, ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);
		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));

		// //////////////////
		// Try to update the state to be AVAILABLE and the CounterDataIndexes to none().
		// //////////////////
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE,
			NO_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(dsCounter.getIndexes(), is(NO_INDEXES));
	}

	@Test(expected = RuntimeException.class)
	public void testUpdateCounterDetails_InvalidCounterStatuses() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1);

		// Set the counter be in the READ-ONLY state.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.CONTRACTING_SHARDS, ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);
		dsCounter = impl.getCounter(TEST_COUNTER1);

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(0L));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.CONTRACTING_SHARDS));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));

		// //////////////////
		// Try to update the state to be AVAILABLE.
		// //////////////////
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE,
			ALL_INDEXES);
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
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE,
			ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		// Reduce the number of counter shards
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 20, CounterStatus.AVAILABLE,
			ALL_INDEXES);

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

		counter = new Counter(counter.getCounterName(), counter.getCounterDescription(), 5, CounterStatus.DELETING,
			ALL_INDEXES);
		impl.updateCounterDetails(counter);
		impl.onTaskQueueCounterDeletion(TEST_COUNTER1);

		counter = impl.getCounter(TEST_COUNTER1);
		assertThat(counter.getCount(), is(0L));
		assertThat(counter.getIndexes(), is(NO_INDEXES));

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
		assertThat(counterData.getIndexes(), is(NO_INDEXES));
	}

	@Test
	public void testGetOrCreateCounterData_PreExistingCounter() throws Exception
	{
		final Counter counter = impl.getCounter(TEST_COUNTER1);

		final Counter updatedCounter = new Counter(counter.getCounterName(), counter.getCounterDescription(), 30,
			counter.getCounterStatus(), ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		final CounterData actual = impl.getOrCreateCounterData(TEST_COUNTER1);

		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getCounterName(), is(TEST_COUNTER1));
		assertThat(actual.getCounterDescription(), is(nullValue()));
		assertThat(actual.getNumShards(), is(30));
		assertThat(actual.getIndexes(), is(ALL_INDEXES));
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
		impl.new IncrementShardWork(null, 1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_EmptyCounterName() throws Exception
	{
		impl.new IncrementShardWork("", 1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_BlankCounterName() throws Exception
	{
		impl.new IncrementShardWork(" ", 1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_NegativeCount() throws Exception
	{
		impl.new IncrementShardWork(TEST_COUNTER1, -1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_ZeroCount() throws Exception
	{
		impl.new IncrementShardWork(TEST_COUNTER1, 0, UUID.randomUUID());
	}

	@Test(expected = NullPointerException.class)
	public void testIncrementShardWork_NullIncrementUuid() throws Exception
	{
		impl.new IncrementShardWork(TEST_COUNTER1, 1, null);
	}

	@Test
	public void testIncrementShardWork_NoPreExistingCounter() throws Exception
	{
		IncrementResult actual = impl.new IncrementShardWork(TEST_COUNTER1, 1, UUID.randomUUID()).run();
		assertThat(actual.getAmount(), is(1L));

		impl.increment(TEST_COUNTER1, 1);

		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(2L));
	}

	@Test
	public void testIncrementShardWork_PreExistingCounter() throws Exception
	{
		impl.increment(TEST_COUNTER1, 1);

		final IncrementResult actual = impl.new IncrementShardWork(TEST_COUNTER1, 1, UUID.randomUUID())
			.run();
		assertThat(actual.getAmount(), is(1L));

		impl.increment(TEST_COUNTER1, 1);

		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(3L));
	}

	@Test
	public void testIncrementShardWork_PreExistingCounterShardMutationData() throws Exception
	{
		// Use a ShardedCounterService with only a single Shard per-counter.
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withNumInitialShards(1).build();
		final ShardedCounterServiceImpl shardedCounterServiceImpl1 = new ShardedCounterServiceImpl(memcache, config);

		// Counter will exist, and be 1.
		final IncrementResultSet result = shardedCounterServiceImpl1.increment(TEST_COUNTER1, 1);

		final Key<CounterShardData> counterShardDataKey = result.getCounterOperationResults().toArray(
			new CounterOperationResult[0])[0].getCounterShardDataKey();
		final UUID uuid = UUID.randomUUID();

		// Create and save the counterShard mutation data...
		final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(counterShardDataKey,
			uuid, Type.INCREMENT, 1L);
		ObjectifyService.ofy().save().entity(counterShardOperationData).now();

		// This will increment the same counter shard, and try to persist the same counter mutation object.
		final IncrementResult actual = shardedCounterServiceImpl1.new IncrementShardWork(TEST_COUNTER1,
			1, uuid).run();
		assertThat(actual.getAmount(), is(1L));

		// Load the mutation for the increment
		final CounterShardOperationData dsCounterShardOperationData = ObjectifyService.ofy().load()
			.key(CounterShardOperationData.key(counterShardDataKey, uuid)).now();

		// Even though we're updating the same CounterShardMutationData object, we're merely "saving" it, which will
		// overwrite the existing one in the datastore. This won't cause an error, but we don't expect the dates to
		// match. In reality, we don't check for this scenario because our UUIDs are expected to be unique, so we
		// shouldn't ever have a collision in increments. Despite all this, this test can sometimes run so quickly
		// that the dates actually do match, and the test fails. Thus, we don't assert on the date.
		// assertThat(dsCounterShardMutationData.getCreationDateTime(),
		// is(not(counterShardMutationData.getCreationDateTime())));
		assertThat(dsCounterShardOperationData.getAmount(), is(1L));
		assertThat(dsCounterShardOperationData.getId(), is(uuid.toString()));
		assertThat(dsCounterShardOperationData.getCounterShardDataKey(), is(counterShardDataKey));
		assertThat(dsCounterShardOperationData.getType(), is(Type.INCREMENT));

		impl.increment(TEST_COUNTER1, 1);
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(3L));
	}

	// //////////////////////////////////
	// testDecrementShardWork
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testDecrementShardWork_NullCounterName() throws Exception
	{
		impl.new DecrementShardWork(null, testCounter1CounterShard0Key, 1, UUID.randomUUID());
	}

	@Test(expected = NullPointerException.class)
	public void testDecrementShardWork_NullCounterShardKey() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, null, 1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_EmptyCounterName() throws Exception
	{
		impl.new DecrementShardWork("", testCounter1CounterShard0Key, 1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_BlankCounterName() throws Exception
	{
		impl.new DecrementShardWork(" ", testCounter1CounterShard0Key, 1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_NegativeCount() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, testCounter1CounterShard0Key, -1, UUID.randomUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementShardWork_ZeroCount() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, testCounter1CounterShard0Key, 0, UUID.randomUUID());
	}

	@Test(expected = NullPointerException.class)
	public void testDecrementShardWork_NullDecrementId() throws Exception
	{
		impl.new DecrementShardWork(TEST_COUNTER1, testCounter1CounterShard0Key, 1, null);
	}

	@Test
	public void testDecrementShardWork_NoPreExistingCounter() throws Exception
	{
		final Optional<DecrementResult> actual = impl.new DecrementShardWork(TEST_COUNTER1,
			testCounter1CounterShard0Key, 1, UUID.randomUUID()).run();

		assertThat(actual.isPresent(), is(false));
	}

	@Test
	public void testDecrementShardWork_PreExistingCounter() throws Exception
	{
		// Make sure there's only 1 shard in the counter so we don't have unpredictable results.
		final ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withNumInitialShards(1).build();
		impl = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(), config);

		impl.increment(TEST_COUNTER1, 1);
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(1L));

		final Optional<DecrementResult> decrementShardResult = impl.new DecrementShardWork(
			TEST_COUNTER1, testCounter1CounterShard0Key, 1, UUID.randomUUID()).run();
		assertThat(decrementShardResult.get().getAmount(), is(1L));

		// The counter indicates a count of 1 still due to cache.
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(1L));
		MemcacheServiceFactory.getMemcacheService().clearAll();
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(0L));
	}

	@Test
	public void testDecrementShardWork_PreExistingCounterShardMutationData() throws Exception
	{
		// Use a ShardedCounterService with only a single Shard per-counter.
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withNumInitialShards(1).build();
		final ShardedCounterServiceImpl shardedCounterServiceImpl1 = new ShardedCounterServiceImpl(memcache, config);

		// Counter will exist, and be 2.
		IncrementResultSet result = shardedCounterServiceImpl1.increment(TEST_COUNTER1, 2);

		final Key<CounterShardData> counterShardDataKey = result.getCounterOperationResults().toArray(
			new CounterOperationResult[0])[0].getCounterShardDataKey();
		final UUID uuid = UUID.randomUUID();

		// Create and save the counterShard mutation data...
		final CounterShardOperationData counterShardOperationData = new CounterShardOperationData(counterShardDataKey,
			uuid, Type.DECREMENT, 1L);
		ObjectifyService.ofy().save().entity(counterShardOperationData).now();

		// This will increment the same counter shard, and try to persist the same counter mutation object.
		final Optional<DecrementResult> actual = shardedCounterServiceImpl1.new DecrementShardWork(
			TEST_COUNTER1, counterShardDataKey, 1, uuid).run();
		assertThat(actual.get().getAmount(), is(1L));

		// Load the mutation for the increment
		final CounterShardOperationData dsCounterShardOperationData = ObjectifyService.ofy().load()
			.key(CounterShardOperationData.key(counterShardDataKey, uuid)).now();

		// Even though we're updating the same CounterShardMutationData object, we're merely "saving" it, which will
		// overwrite the existing one in the datastore. This won't cause an error, but we don't expect the dates to
		// match. In reality, we don't check for this scenario because our UUIDs are expected to be unique, so we
		// shouldn't ever have a collision in increments. Despite all this, this test can sometimes run so quickly
		// that the dates actually do match, and the test fails. Thus, we don't assert on the date.
		// assertThat(dsCounterShardMutationData.getCreationDateTime(),
		// is(not(counterShardMutationData.getCreationDateTime())));
		assertThat(dsCounterShardOperationData.getAmount(), is(1L));
		assertThat(dsCounterShardOperationData.getId(), is(uuid.toString()));
		assertThat(dsCounterShardOperationData.getCounterShardDataKey(), is(counterShardDataKey));
		assertThat(dsCounterShardOperationData.getType(), is(Type.DECREMENT));

		impl.decrement(TEST_COUNTER1, 1);
		assertThat(impl.getCounter(TEST_COUNTER1).getCount(), is(0L));
	}

	// //////////////////////////////////
	// testIncrementCounterOperationResult
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testIncrementCounterOperationResult_NullOperationUuid()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new IncrementResult(null, counterShardDataKey, 0);
	}

	@Test(expected = NullPointerException.class)
	public void testIncrementCounterOperationResult_NullCounterShardDataKey()
	{
		new IncrementResult(UUID.randomUUID(), null, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementCounterOperationResult_NegativeAmount()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new IncrementResult(UUID.randomUUID(), counterShardDataKey, -1);
	}

	@Test
	public void testIncrementCounterOperationResult_ValidAmounts()
	{
		final UUID mutationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 1);
		IncrementResult result = new IncrementResult(mutationUuid, counterShardDataKey,
			1);

		assertThat(result.getAmount(), is(1L));
		assertThat(result.getCounterShardDataKey(), is(counterShardDataKey));
	}

	// //////////////////////////////////
	// test CounterOperationResult (DecrementCounterOperationResult)
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testCounterOperationResult_NullUuid()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new DecrementResult(null, counterShardDataKey, 0);
	}

	@Test(expected = NullPointerException.class)
	public void testCounterOperationResult_NullCounterShardDataKey()
	{
		new DecrementResult(UUID.randomUUID(), null, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCounterOperationResult_NegativeAmount()
	{
		final UUID mutationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new DecrementResult(mutationUuid, counterShardDataKey, -1);
	}

	@Test
	public void testCounterOperationResult_ValidAmounts()
	{
		final UUID mutationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 1);
		CounterOperationResult result = new DecrementResult(mutationUuid, counterShardDataKey, 1);

		assertThat(result.getAmount(), is(1L));
		assertThat(result.getCounterShardDataKey(), is(counterShardDataKey));
	}

	@Test
	public void testCounterOperationResultCollection_NonEmptySet()
	{
		final UUID mutationUuid1 = UUID.randomUUID();
		final Key<CounterData> counterDataKey1 = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey1 = CounterShardData.key(counterDataKey1, 0);
		final DecrementResult documentShardResult1 = new DecrementResult(mutationUuid1,
			counterShardDataKey1, 1);

		final UUID mutationUuid2 = UUID.randomUUID();
		final Key<CounterData> counterDataKey2 = CounterData.key(TEST_COUNTER2);
		final Key<CounterShardData> counterShardDataKey2 = CounterShardData.key(counterDataKey2, 0);
		final DecrementResult documentShardResult2 = new DecrementResult(mutationUuid2,
			counterShardDataKey2, 1);

		// Add the a single CounterOperationResult and assert that only 1 exists.
		final UUID overallMutationUuid = UUID.randomUUID();
		Set<DecrementResult> resultSet = ImmutableSet.of(documentShardResult1);
		CounterOperationResultSet counterOperationResultSet = new DecrementResultSet.Builder(
			overallMutationUuid).withCounterOperationResults(resultSet).build();
		assertThat(counterOperationResultSet.getOperationUuid(), is(overallMutationUuid));
		assertThat(counterOperationResultSet.getTotalAmount(), is(1L));
		assertThat(counterOperationResultSet.getCounterOperationResults().size(), is(1));
		assertThat((Set<DecrementResult>) counterOperationResultSet.getCounterOperationResults(),
			is(resultSet));

		// Add the same CounterOperationResult Twice and assert that only 1 exists.
		resultSet = ImmutableSet.of(documentShardResult1, documentShardResult1);
		counterOperationResultSet = new DecrementResultSet.Builder(overallMutationUuid)
			.withCounterOperationResults(resultSet).build();
		assertThat(counterOperationResultSet.getOperationUuid(), is(overallMutationUuid));
		assertThat(counterOperationResultSet.getTotalAmount(), is(1L));
		assertThat(counterOperationResultSet.getCounterOperationResults().size(), is(1));
		assertThat((Set<DecrementResult>) counterOperationResultSet.getCounterOperationResults(),
			is(resultSet));

		// Add two different CounterOperationResult objects and assert that 2 exist.
		resultSet = ImmutableSet.of(documentShardResult1, documentShardResult2);
		counterOperationResultSet = new DecrementResultSet.Builder(overallMutationUuid)
			.withCounterOperationResults(resultSet).build();
		assertThat(counterOperationResultSet.getOperationUuid(), is(overallMutationUuid));
		assertThat(counterOperationResultSet.getTotalAmount(), is(2L));
		assertThat(counterOperationResultSet.getCounterOperationResults().size(), is(2));
		assertThat((Set<DecrementResult>) counterOperationResultSet.getCounterOperationResults(),
			is(resultSet));
	}
}