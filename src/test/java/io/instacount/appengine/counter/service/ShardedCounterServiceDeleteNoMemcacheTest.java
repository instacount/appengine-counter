/**
 * Copyright (C) 2016 Instacount Inc. (developers@instacount.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.instacount.appengine.counter.service;

import org.junit.After;
import org.junit.Before;

import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.tools.development.testing.LocalCapabilitiesServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;

/**
 * Unit tests for deleting a counter via
 * {@link ShardedCounterServiceImpl}
 * when memcache is not available.
 * 
 * @author David Fuelling
 */
public class ShardedCounterServiceDeleteNoMemcacheTest extends ShardedCounterServiceDeleteTest
{
	@Before
	public void setUp() throws Exception
	{
		super.setUp();

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

	@After
	public void tearDown()
	{
		super.tearDown();
	}
}
