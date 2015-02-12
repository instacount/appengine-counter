/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.theupswell.appengine.counter.service;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.tools.development.testing.LocalCapabilitiesServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.impl.translate.opt.joda.JodaTimeTranslators;
import com.googlecode.objectify.util.Closeable;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * An abstract base class for testing {@link com.theupswell.appengine.counter.service.ShardedCounterServiceImpl}
 *
 * @author David Fuelling
 */
public abstract class AbstractShardedCounterServiceTest
{
	protected static final String DELETE_COUNTER_SHARD_QUEUE_NAME = "deleteCounterShardQueue";

	protected static final String TEST_COUNTER1 = "test-counter1";

	protected static final String TEST_COUNTER2 = "test-counter2";

	protected ShardedCounterService shardedCounterService;

	protected LocalTaskQueueTestConfig.TaskCountDownLatch countdownLatch;

	protected LocalServiceTestHelper helper = new LocalServiceTestHelper(
		// No Eventual Consistency, by default
		new LocalDatastoreServiceTestConfig().setDefaultHighRepJobPolicyUnappliedJobPercentage(0f),
		new LocalMemcacheServiceTestConfig(), new LocalTaskQueueTestConfig());

	protected MemcacheService memcache;

	protected CapabilitiesService capabilitiesService;

	public static class DeleteShardedCounterDeferredCallback extends LocalTaskQueueTestConfig.DeferredTaskCallback
	{
		private static final long serialVersionUID = -2113612286521272160L;

		@Override
		protected int executeNonDeferredRequest(URLFetchRequest req)
		{
			// Do Nothing in this callback. This callback is only here to
			// simulate a task-queue run.

			// See here:
			// http://stackoverflow.com/questions/6632809/gae-unit-testing-taskqueue-with-testbed
			// The dev app server is single-threaded, so it can't run tests in
			// the background properly. Thus, we test that the task was added to
			// the queue properly. Then, we manually run the shard-deletion code
			// and assert that it's working properly.
			return 200;
		}
	}

	@Before
	public void setUp() throws Exception
	{
		// Don't call super.setUp because we initialize slightly differently
		// here...

		countdownLatch = new LocalTaskQueueTestConfig.TaskCountDownLatch(1);

		// See
		// http://www.ensor.cc/2010/11/unit-testing-named-queues-spring.html
		// NOTE: THE QUEUE XML PATH RELATIVE TO WEB APP ROOT, More info
		// below
		// http://stackoverflow.com/questions/11197058/testing-non-default-app-engine-task-queues
		final LocalTaskQueueTestConfig localTaskQueueConfig = new LocalTaskQueueTestConfig()
			.setDisableAutoTaskExecution(false).setQueueXmlPath("src/test/resources/queue.xml")
			.setTaskExecutionLatch(countdownLatch).setCallbackClass(DeleteShardedCounterDeferredCallback.class);

		// Use a different queue.xml for testing purposes
		helper = new LocalServiceTestHelper(
			new LocalDatastoreServiceTestConfig().setDefaultHighRepJobPolicyUnappliedJobPercentage(0.01f),
			new LocalMemcacheServiceTestConfig(), new LocalCapabilitiesServiceTestConfig(), localTaskQueueConfig);
		helper.setUp();

		memcache = MemcacheServiceFactory.getMemcacheService();
		capabilitiesService = CapabilitiesServiceFactory.getCapabilitiesService();

		// New Objectify 5.1 Way. See https://groups.google.com/forum/#!topic/objectify-appengine/O4FHC_i7EGk
		this.session = ObjectifyService.begin();

		// Enable Joda Translators
		JodaTimeTranslators.add(ObjectifyService.factory());

		ObjectifyService.factory().register(CounterData.class);
		ObjectifyService.factory().register(CounterShardData.class);

		shardedCounterService = new ShardedCounterServiceImpl();
	}

	// New Objectify 5.1 Way. See https://groups.google.com/forum/#!topic/objectify-appengine/O4FHC_i7EGk
	protected Closeable session;

	@After
	public void tearDown()
	{
		// New Objectify 5.1 Way. See https://groups.google.com/forum/#!topic/objectify-appengine/O4FHC_i7EGk
		this.session.close();

		this.helper.tearDown();
	}

	// ///////////////////////
	// Helper Methods
	// ///////////////////////

	/**
	 * Helper method to perform assertions on a specified counter.
	 *
	 * @param counter
	 */
	protected void assertCounter(Counter counter, String expectedCounterName, long expectedCounterCount)
	{
		assertTrue(counter != null);
		assertEquals(expectedCounterCount, counter.getCount());
		assertEquals(expectedCounterName, counter.getCounterName());
	}

	/**
	 * Create a new {@link com.theupswell.appengine.counter.service.CounterService} with the specified "number of
	 * initial shards" as found in {@code numInitialShards} .
	 *
	 * @param numInitialShards
	 *
	 * @return
	 */
	protected ShardedCounterService initialShardedCounterService(int numInitialShards)
	{
		ShardedCounterServiceConfiguration config = new ShardedCounterServiceConfiguration.Builder()
			.withNumInitialShards(numInitialShards).build();

		ShardedCounterService service = new ShardedCounterServiceImpl(MemcacheServiceFactory.getMemcacheService(),
			config);
		return service;
	}

	/**
	 * @return {@code true} if Memcache is usable; {@code false} otherwise.
	 */
	protected boolean isMemcacheAvailable()
	{
		CapabilityStatus capabilityStatus = this.capabilitiesService.getStatus(Capability.MEMCACHE).getStatus();
		return capabilityStatus == CapabilityStatus.ENABLED;
	}

	protected void disableMemcache()
	{
		// See
		// http://www.ensor.cc/2010/11/unit-testing-named-queues-spring.html
		// NOTE: THE QUEUE XML PATH RELATIVE TO WEB APP ROOT, More info
		// below
		// http://stackoverflow.com/questions/11197058/testing-non-default-app-engine-task-queues
		final LocalTaskQueueTestConfig localTaskQueueConfig = new LocalTaskQueueTestConfig()
			.setDisableAutoTaskExecution(false).setQueueXmlPath("src/test/resources/queue.xml")
			.setTaskExecutionLatch(countdownLatch).setCallbackClass(DeleteShardedCounterDeferredCallback.class);

		Capability testOne = new Capability("memcache");
		CapabilityStatus testStatus = CapabilityStatus.DISABLED;
		// Initialize
		LocalCapabilitiesServiceTestConfig capabilityStatusConfig = new LocalCapabilitiesServiceTestConfig()
			.setCapabilityStatus(testOne, testStatus);

		// Use a different queue.xml for testing purposes
		helper = new LocalServiceTestHelper(
			new LocalDatastoreServiceTestConfig().setDefaultHighRepJobPolicyUnappliedJobPercentage(0.01f),
			new LocalMemcacheServiceTestConfig(), localTaskQueueConfig, capabilityStatusConfig);
		helper.setUp();
	}

}
