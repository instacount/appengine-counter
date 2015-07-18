package com.theupswell.appengine.counter.service;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.repackaged.com.google.common.math.LongMath;
import com.google.common.base.Optional;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.CounterOperation;
import com.theupswell.appengine.counter.CounterOperation.CounterOperationType;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterData.CounterIndexes;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.data.CounterShardData;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl.CounterDataGetCreateContainer;

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

	@Test(expected = NullPointerException.class)
	public void testGetCounter_NullName() throws Exception
	{
		impl.getCounter(null);
	}

	@Test
	public void testGetCounter_Deleting() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		final CounterData counterData = impl.getOrCreateCounterData(counterName).getCounterData();
		counterData.setCounterStatus(CounterStatus.DELETING);
		ObjectifyService.ofy().save().entity(counterData).now();

		final Counter counter = impl.getCounter(counterName).get();
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(BigInteger.ZERO));
		assertThat(counter.getIndexes(), is(NO_INDEXES));
	}

	@Test
	public void testGetCounter_CountIsCachedInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		impl.increment(counterName, 1L);
		this.memcache.put(counterName, BigInteger.TEN);

		final Counter counter = impl.getCounter(counterName).get();
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(BigInteger.TEN));
		assertThat(counter.getIndexes(), is(NO_INDEXES));
	}

	@Test
	public void testGetCounter_NoCountInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		shardedCounterService.createCounter(counterName);
		this.memcache.delete(counterName);

		Counter counter = impl.getCounter(counterName).get();
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(BigInteger.ZERO));

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
		shardedCounterService.createCounter(TEST_COUNTER1);
		Counter updatedCounter = new Counter(TEST_COUNTER1, NEW_DESCRIPTION, 22, CounterStatus.READ_ONLY_COUNT,
			ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		Counter dsCounter = impl.getCounter(TEST_COUNTER1).get();

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(BigInteger.ZERO));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));
	}

	@Test
	public void testUpdateCounterDetails_PreExistingCounterInDatastore() throws Exception
	{
		shardedCounterService.createCounter(TEST_COUNTER1);
		Counter dsCounter = impl.getCounter(TEST_COUNTER1).get();

		// counter. final String counterName, final String counterDescription, final int numShards,
		// final CounterData.CounterStatus counterStatus
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.READ_ONLY_COUNT, ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		dsCounter = impl.getCounter(TEST_COUNTER1).get();

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(BigInteger.ZERO));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));
	}

	@Test
	public void testUpdateCounterDetails_ProperCounterStatuses() throws Exception
	{
		shardedCounterService.createCounter(TEST_COUNTER1);
		Counter dsCounter = impl.getCounter(TEST_COUNTER1).get();

		// Set the counter be in the READ-ONLY state.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.READ_ONLY_COUNT, ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);
		dsCounter = impl.getCounter(TEST_COUNTER1).get();

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(BigInteger.ZERO));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.READ_ONLY_COUNT));
		assertThat(dsCounter.getIndexes(), is(ALL_INDEXES));

		// //////////////////
		// Try to update the state to be AVAILABLE and the CounterDataIndexes to none().
		// //////////////////
		updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22, CounterStatus.AVAILABLE,
			NO_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		dsCounter = impl.getCounter(TEST_COUNTER1).get();

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(BigInteger.ZERO));
		assertThat(dsCounter.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(dsCounter.getIndexes(), is(NO_INDEXES));
	}

	@Test(expected = RuntimeException.class)
	public void testUpdateCounterDetails_InvalidCounterStatuses() throws Exception
	{
		Counter dsCounter = impl.getCounter(TEST_COUNTER1).get();

		// Set the counter be in the READ-ONLY state.
		Counter updatedCounter = new Counter(dsCounter.getCounterName(), NEW_DESCRIPTION, 22,
			CounterStatus.CONTRACTING_SHARDS, ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);
		dsCounter = impl.getCounter(TEST_COUNTER1).get();

		assertThat(dsCounter.getCounterName(), is(TEST_COUNTER1));
		assertThat(dsCounter.getCounterDescription(), is(NEW_DESCRIPTION));
		assertThat(dsCounter.getNumShards(), is(22));
		assertThat(dsCounter.getCount(), is(BigInteger.ZERO));
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
		Counter dsCounter = impl.getCounter(TEST_COUNTER1).get();

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
		this.shardedCounterService.createCounter(TEST_COUNTER1);
		impl.increment(TEST_COUNTER1, 1L);
		Counter counter = impl.getCounter(TEST_COUNTER1).get();
		impl.increment(TEST_COUNTER1, 20);
		impl.increment(TEST_COUNTER1, 20);

		counter = new Counter(counter.getCounterName(), counter.getCounterDescription(), 5, CounterStatus.DELETING,
			ALL_INDEXES);
		impl.updateCounterDetails(counter);
		impl.onTaskQueueCounterDeletion(TEST_COUNTER1);

		assertThat(impl.getCounter(TEST_COUNTER1).isPresent(), is(false));
		assertThat(memcache.get(TEST_COUNTER1), is(nullValue()));
	}

	// /////////////////////////
	// getOrCreateCounterData
	// /////////////////////////

	@Test(expected = IllegalArgumentException.class)
	public void testGetOrCreateCounterData_NullInput() throws Exception
	{
		impl.getOrCreateCounterData(null);
	}

	@Test
	public void testGetOrCreateCounterData_NoPreExistingCounter() throws Exception
	{
		final CounterDataGetCreateContainer counterDataGetCreateContainer = impl.getOrCreateCounterData(TEST_COUNTER1);
		final CounterData counterData = counterDataGetCreateContainer.getCounterData();
		assertThat(counterData.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(counterData.getCounterName(), is(TEST_COUNTER1));
		assertThat(counterData.getCounterDescription(), is(nullValue()));
		assertThat(counterData.getNumShards(), is(3));
		assertThat(counterData.getIndexes(), is(NO_INDEXES));
		assertThat(counterDataGetCreateContainer.isNewCounterDataCreated(), is(true));
	}

	@Test
	public void testGetOrCreateCounterData_PreExistingCounter() throws Exception
	{
		shardedCounterService.createCounter(TEST_COUNTER1);
		final Counter counter = impl.getCounter(TEST_COUNTER1).get();

		final Counter updatedCounter = new Counter(counter.getCounterName(), counter.getCounterDescription(), 30,
			counter.getCounterStatus(), ALL_INDEXES);
		impl.updateCounterDetails(updatedCounter);

		final CounterDataGetCreateContainer counterDataGetCreateContainer = impl.getOrCreateCounterData(TEST_COUNTER1);
		final CounterData actual = counterDataGetCreateContainer.getCounterData();

		assertThat(actual.getCounterStatus(), is(CounterStatus.AVAILABLE));
		assertThat(actual.getCounterName(), is(TEST_COUNTER1));
		assertThat(actual.getCounterDescription(), is(nullValue()));
		assertThat(actual.getNumShards(), is(30));
		assertThat(actual.getIndexes(), is(ALL_INDEXES));
		assertThat(counterDataGetCreateContainer.isNewCounterDataCreated(), is(false));
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
		assertThat(optActual.isPresent(), is(false));
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
	public void testIncrementShardWork_NullCounterData() throws Exception
	{
		impl.new IncrementShardWork(null, UUID.randomUUID(), Optional.of(1), 1, false);
	}

	@Test(expected = NullPointerException.class)
	public void testIncrementShardWork_NullOperationUuid() throws Exception
	{
		impl.new IncrementShardWork(mock(CounterData.class), null, Optional.of(1), 1, false);
	}

	@Test(expected = NullPointerException.class)
	public void testIncrementShardWork_NullShardNumber() throws Exception
	{
		impl.new IncrementShardWork(mock(CounterData.class), UUID.randomUUID(), null, 1, false);
	}

	// Expect IllegalArgumentException because the CounterData doesn't exist.
	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_AbsentOptionalShardNumber_NoPreExistingCounterData() throws Exception
	{
		impl.new IncrementShardWork(mock(CounterData.class), UUID.randomUUID(), Optional.<Integer> absent(), 1, false)
			.run();
	}

	@Test
	public void testIncrementShardWork_AbsentOptionalShardNumber_PreExistingCounterData() throws Exception
	{
		impl.increment(TEST_COUNTER1, 1);

		final CounterDataGetCreateContainer counterDataGetCreateContainer = impl.getOrCreateCounterData(TEST_COUNTER1);
		final CounterData counterData = counterDataGetCreateContainer.getCounterData();

		CounterOperation actual = impl.new IncrementShardWork(counterData, UUID.randomUUID(),
			Optional.<Integer> absent(), 1, counterDataGetCreateContainer.isNewCounterDataCreated()).run();
		assertThat(actual.getAppliedAmount(), is(1L));

		impl.increment(TEST_COUNTER1, 1);

		assertThat(impl.getCounter(TEST_COUNTER1).get().getCount(), is(BigInteger.valueOf(3L)));
		assertThat(counterDataGetCreateContainer.isNewCounterDataCreated(), is(false));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementShardWork_ZeroCount() throws Exception
	{
		impl.new IncrementShardWork(mock(CounterData.class), UUID.randomUUID(), Optional.of(1), 0, false);
	}

	@Test
	public void testIncrementShardWork_PreExistingCounterData() throws Exception
	{
		impl.increment(TEST_COUNTER1, 1);

		final CounterDataGetCreateContainer counterDataGetCreateContainer = impl.getOrCreateCounterData(TEST_COUNTER1);
		final CounterData counterData = counterDataGetCreateContainer.getCounterData();

		final CounterOperation actual = impl.new IncrementShardWork(counterData, UUID.randomUUID(), Optional.of(1), 1,
			counterDataGetCreateContainer.isNewCounterDataCreated()).run();
		assertThat(actual.getAppliedAmount(), is(1L));

		impl.increment(TEST_COUNTER1, 1);

		assertThat(impl.getCounter(TEST_COUNTER1).get().getCount(), is(BigInteger.valueOf(3L)));
		assertThat(counterDataGetCreateContainer.isNewCounterDataCreated(), is(false));
	}

	// //////////////////////////////////
	// testIncrementShardWork with negative values.
	// //////////////////////////////////

	@Test
	public void testDecrementShardWork_PreExistingCounter_NegativeAmount() throws Exception
	{
		// Make sure there's only 1 shard in the counter so we don't have unpredictable results.
		final ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withNumInitialShards(1).withNegativeCountAllowed(ShardedCounterServiceConfiguration.ALLOW_NEGATIVE_COUNTS)
			.build();
		impl = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(), config);

		impl.increment(TEST_COUNTER1, 1);
		assertThat(impl.getCounter(TEST_COUNTER1).get().getCount(), is(BigInteger.valueOf(1L)));

		final CounterDataGetCreateContainer counterDataGetCreateContainer = impl.getOrCreateCounterData(TEST_COUNTER1);
		final CounterData counterData = counterDataGetCreateContainer.getCounterData();

		final CounterOperation decrementShardResult = impl.new IncrementShardWork(counterData, UUID.randomUUID(),
			Optional.of(1), -1, counterDataGetCreateContainer.isNewCounterDataCreated()).run();
		assertThat(decrementShardResult.getAppliedAmount(), is(1L));

		// The counter indicates a count of 1 still due to cache.
		assertThat(impl.getCounter(TEST_COUNTER1).get().getCount(), is(BigInteger.valueOf(1L)));
		MemcacheServiceFactory.getMemcacheService().clearAll();
		assertThat(impl.getCounter(TEST_COUNTER1).get().getCount(), is(BigInteger.ZERO));
		assertThat(counterDataGetCreateContainer.isNewCounterDataCreated(), is(false));
	}

	// //////////////////////////////////
	// testIncrementCounterOperationResult
	// //////////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testCounterOperationResult_NullCounterOperationUuid()
	{
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new CounterOperation.Impl(null, counterShardDataKey, CounterOperationType.INCREMENT, 0L,
			DateTime.now(DateTimeZone.UTC), false);
	}

	@Test(expected = NullPointerException.class)
	public void testCounterOperationResult_NullCounterShardDataKey()
	{
		final UUID operationUuid = UUID.randomUUID();
		new CounterOperation.Impl(operationUuid, null, CounterOperationType.INCREMENT, 0,
			DateTime.now(DateTimeZone.UTC), false);
	}

	@Test(expected = NullPointerException.class)
	public void testCounterOperationResult_NullCounterOperationType()
	{
		final UUID operationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new CounterOperation.Impl(operationUuid, counterShardDataKey, null, 0, DateTime.now(DateTimeZone.UTC), false);
	}

	@Test(expected = NullPointerException.class)
	public void testCounterOperationResult_NullCreationDateTime()
	{
		final UUID operationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new CounterOperation.Impl(operationUuid, counterShardDataKey, CounterOperationType.INCREMENT, 1, null, false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIncrementCounterOperationResult_NegativeAmount()
	{
		final UUID operationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new CounterOperation.Impl(operationUuid, counterShardDataKey, CounterOperationType.INCREMENT, -1, now, false);
	}

	@Test
	public void testIncrementCounterOperationResult_ValidAmounts()
	{
		final UUID operationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 1);
		final DateTime now = DateTime.now(DateTimeZone.UTC);

		final CounterOperation result = new CounterOperation.Impl(operationUuid, counterShardDataKey,
			CounterOperationType.INCREMENT, 10, now, false);

		assertThat(result.getOperationUuid(), is(operationUuid));
		assertThat(result.getAppliedAmount(), is(10L));
		assertThat(result.getCounterOperationType(), is(CounterOperationType.INCREMENT));
		assertThat(result.getCounterShardDataKey(), is(counterShardDataKey));
		assertThat(result.getCreationDateTime(), is(now));
	}

	// //////////////////////////////////
	// test CounterOperationResult (DecrementCounterOperationResult)
	// //////////////////////////////////

	@Test(expected = IllegalArgumentException.class)
	public void testDecrementCounterOperationResult_NegativeAmount()
	{
		final UUID operationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 0);
		new CounterOperation.Impl(operationUuid, counterShardDataKey, CounterOperationType.DECREMENT, -1,
			DateTime.now(DateTimeZone.UTC), false);
	}

	@Test
	public void testDecrementCounterOperationResult_ValidAmounts()
	{
		final UUID operationUuid = UUID.randomUUID();
		final Key<CounterData> counterDataKey = CounterData.key(TEST_COUNTER1);
		final Key<CounterShardData> counterShardDataKey = CounterShardData.key(counterDataKey, 10);
		final DateTime now = DateTime.now(DateTimeZone.UTC);
		CounterOperation result = new CounterOperation.Impl(operationUuid, counterShardDataKey,
			CounterOperationType.DECREMENT, 10, DateTime.now(DateTimeZone.UTC), false);

		assertThat(result.getOperationUuid(), is(operationUuid));
		assertThat(result.getCounterOperationType(), is(CounterOperationType.DECREMENT));
		assertThat(result.getAppliedAmount(), is(10L));
		assertThat(result.getCounterShardDataKey(), is(counterShardDataKey));
		assertTrue(result.getCreationDateTime().isEqual(now) || result.getCreationDateTime().isAfter(now));
	}

	// ///////////////////
	//
	// ///////////////////

	@Test(expected = NullPointerException.class)
	public void testDetermineRandomShardNumber_NullOptionalShardNumber()
	{
		impl.determineRandomShardNumber(null, 2);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDetermineRandomShardNumber_0Shards()
	{
		impl.determineRandomShardNumber(Optional.of(2), 0);
	}

	@Test
	public void testDetermineRandomShardNumber_AbsentShardNumber()
	{
		int actualShardNumber = impl.determineRandomShardNumber(Optional.<Integer> absent(), 3);
		assertThat(actualShardNumber >= 0 && actualShardNumber < 3, is(true));
	}

	@Test
	public void testDetermineRandomShardNumber_ManyShards()
	{
		int actualShardNumber = impl.determineRandomShardNumber(Optional.of(2), 27);
		assertThat(actualShardNumber, is(2));
	}

	@Test
	public void testDetermineRandomShardNumber_TooFewShards()
	{
		int actualShardNumber = impl.determineRandomShardNumber(Optional.of(27), 3);
		assertThat(actualShardNumber >= 0 && actualShardNumber < 3, is(true));
	}

	@Test
	public void testDetermineRandomShardNumber_TooFewShards2()
	{
		int actualShardNumber = impl.determineRandomShardNumber(Optional.of(2), 1);
		assertThat(actualShardNumber, is(0));
	}

	// ///////////////////
	// Guava Overflow/Underlfow
	// ///////////////////

	// Adding 1 to Long.MAX_VALUE should overflow.
	@Test(expected = ArithmeticException.class)
	public void testOverflow()
	{
		long left = Long.MAX_VALUE;
		long right = 1L;

		LongMath.checkedAdd(left, right);
		fail();
	}

	// Adding a negative 1 to Long.MIN_VALUE should underflow.
	@Test(expected = ArithmeticException.class)
	public void testUnderflow()
	{
		long left = Long.MIN_VALUE;
		long right = -1L;

		LongMath.checkedAdd(left, right);
		fail();
	}

}