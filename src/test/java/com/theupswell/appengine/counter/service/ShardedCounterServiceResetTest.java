package com.theupswell.appengine.counter.service;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.math.BigInteger;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.CounterBuilder;
import com.theupswell.appengine.counter.data.CounterData.CounterStatus;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl.ResetCounterShardWork;

/**
 * Unit test that exercises the reset functionality of the CounterService.
 */
public class ShardedCounterServiceResetTest extends AbstractShardedCounterServiceTest
{

	@Before
	public void setUp() throws Exception
	{
		super.setUp();
	}

	@After
	public void tearDown()
	{
		super.tearDown();
	}

	// /////////////////////////
	// Unit Tests
	// /////////////////////////

	// #resetCounter

	@Test(expected = NullPointerException.class)
	public void testResetCounter_NullName() throws Exception
	{
		shardedCounterService.reset(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResetCounter_EmptyName() throws Exception
	{
		shardedCounterService.reset("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResetCounter_BlankName() throws Exception
	{
		shardedCounterService.reset("  ");
	}

	@Test(expected = RuntimeException.class)
	public void testResetCounter_WrongStatus_Deleting() throws Exception
	{
		final Counter counter = shardedCounterService.getCounter("123").get();

		Counter readOnlyCounter = new CounterBuilder(counter).withCounterStatus(CounterStatus.DELETING).build();
		shardedCounterService.updateCounterDetails(readOnlyCounter);

		shardedCounterService.reset("123");
		fail();
	}

	@Test
	public void testResetCounter_CountIsCachedInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		shardedCounterService.createCounter(counterName);
		this.memcache.put(counterName, BigInteger.TEN);

		Counter counter = shardedCounterService.getCounter(counterName).get();
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(BigInteger.TEN));
	}

	@Test
	public void testResetCounter_NoCountInMemcache() throws Exception
	{
		final String counterName = UUID.randomUUID().toString();
		shardedCounterService.createCounter(counterName);
		this.memcache.clearAll();

		Counter counter = shardedCounterService.getCounter(counterName).get();
		assertThat(counter, is(not(nullValue())));
		assertThat(counter.getCount(), is(BigInteger.ZERO));

		// Assert that the value was added to memcache.
		BigInteger memcacheValue = (BigInteger) memcache.get(counterName);
		assertThat(memcacheValue, is(BigInteger.ZERO));
	}

	@Test
	public void testResetCounter() throws Exception
	{
		this.shardedCounterService.increment("123", 200);
		this.shardedCounterService.reset("123");

		assertThat(shardedCounterService.getCounter("123").get().getCount(), is(BigInteger.ZERO));
	}

	// ////////////////////////////
	// Test ResetCounterWork
	// ////////////////////////////

	@Test(expected = NullPointerException.class)
	public void testResetCounterWork_NullCounterName()
	{
		new ResetCounterShardWork(null, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResetCounterWork_EmptyCounterName()
	{
		new ResetCounterShardWork("", 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResetCounterWork_BlankCounterName()
	{
		new ResetCounterShardWork("  ", 1);
	}

	@Test
	public void testResetCounterWork()
	{
		this.shardedCounterService.increment("123", 100L);
		assertThat(shardedCounterService.getCounter("123").get().getCount(), is(BigInteger.valueOf(100)));

		ResetCounterShardWork resetCounterShardWork0 = new ResetCounterShardWork("123", 0);
		resetCounterShardWork0.vrun();

		ResetCounterShardWork resetCounterShardWork1 = new ResetCounterShardWork("123", 1);
		resetCounterShardWork1.vrun();

		ResetCounterShardWork resetCounterShardWork2 = new ResetCounterShardWork("123", 2);
		resetCounterShardWork2.vrun();

		memcache.clearAll();
		assertThat(shardedCounterService.getCounter("123").get().getCount(), is(BigInteger.ZERO));
	}

}