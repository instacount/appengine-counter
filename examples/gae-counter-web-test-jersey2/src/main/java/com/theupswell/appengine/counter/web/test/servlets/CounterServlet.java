/**
 * Copyright (C) 2014 UpSwell LLC (developers@theupswell.com)
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
package com.theupswell.appengine.counter.web.test.servlets;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.google.appengine.api.ThreadManager;
import com.google.inject.Inject;
import com.theupswell.appengine.counter.Counter;
import com.theupswell.appengine.counter.service.CounterService;
import com.theupswell.appengine.counter.service.ShardedCounterService;

public class CounterServlet extends HttpServlet
{
	private static Logger logger = Logger.getLogger(CounterServlet.class.getName());

	private static final long serialVersionUID = 4314514444980866055L;

	private final ShardedCounterService shardedCounterService;

	@Inject
	public CounterServlet(final ShardedCounterService shardedCounterService)
	{
		this.shardedCounterService = shardedCounterService;
	}

	/**
	 * Spawn 5 Threads, each incrementing a counter some number between 1 and
	 * 10. Then, decrement some random amount, and report on the results.
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{

		ThreadFactory factory = ThreadManager.currentRequestThreadFactory();
		// UncaughtExceptionHandler handler = new UncaughtExceptionHandler()
		// {
		// @Override
		// public void uncaughtException(Thread t, Throwable e)
		// {
		// e.printStackTrace();
		// }
		// };
		// ForkJoinWorkerThreadFactory fjpFactory = Fork;
		// ForkJoinPool fjp = new ForkJoinPool(5, fjpFactory , handler, true);
		ExecutorService pool = Executors.newFixedThreadPool(5, factory);

		// Run these jobs in parallel
		// List<ForkJoinTask<Integer>> futures = new
		// ArrayList<ForkJoinTask<Integer>>();

		for (int i = 0; i < 1; i++)
		{
			Tuple tuple = Tuple.defaultTuple();
			CounterWorkCallable task = new CounterWorkCallable("counter" + i, shardedCounterService, tuple);
			pool.submit(task);

			// CounterWorkRecursiveTask counterWork = new
			// CounterWorkRecursiveTask("counter" + i, 5,
			// shardedCounterService);
			// futures.add(fjp.submit(counterWork));
		}

		int actualIncrements = 0;
		// for (ForkJoinTask<Integer> fjt : futures)
		// {
		// try
		// {
		// actualIncrements += fjt.get();
		// }
		// catch (Exception e)
		// {
		// e.printStackTrace();
		// }
		// }

		logger.info("Expected 25 Increments.  Actual Increments: " + actualIncrements);

	}

	// private static class CounterWorkRecursiveTask extends
	// RecursiveTask<Integer>
	// {
	// private static final long serialVersionUID = 6541639820295015661L;
	//
	// private Logger logger =
	// Logger.getLogger(CounterWorkRecursiveTask.class.getName());
	//
	// private final String counterName;
	// private final int incrementNumber;
	// private final ShardedCounterService shardedCounterService;
	//
	// public CounterWorkRecursiveTask(final String counterName, final int
	// incrementNumber,
	// final ShardedCounterService shardedCounterService)
	// {
	// this.counterName = counterName;
	// this.incrementNumber = incrementNumber;
	// this.shardedCounterService = shardedCounterService;
	// }
	//
	// @Override
	// protected Integer compute()
	// {
	// logger.info("Starting Increment for Counter (" + this.counterName +
	// incrementNumber + ")");
	//
	// if (incrementNumber <= 1)
	// {
	// this.shardedCounterService.increment(counterName);
	// return 1;
	// }
	// else
	// {
	// CounterWorkRecursiveTask cw1 = new CounterWorkRecursiveTask(counterName,
	// incrementNumber - 1,
	// shardedCounterService);
	// cw1.fork();
	// CounterWorkRecursiveTask cw2 = new CounterWorkRecursiveTask(counterName,
	// incrementNumber - 2,
	// shardedCounterService);
	//
	// return cw2.compute() + cw1.join();
	// }
	// }
	// }

	private static class CounterWorkCallable implements Callable<Integer>
	{
		private Logger logger = Logger.getLogger(CounterWorkCallable.class.getName());

		private final String counterName;
		private final Tuple counterWorkTuple;
		private final CounterService counterService;

		public CounterWorkCallable(final String counterName, final CounterService counterService,
				final Tuple counterWorkTuple)
		{
			this.counterName = counterName;
			this.counterWorkTuple = counterWorkTuple;
			this.counterService = counterService;
		}

		/**
		 * Increment the counter a specified number of times.
		 */
		public Integer call() throws Exception
		{
			logger.info("Starting Increment for Counter (" + this.counterName + ")");
			int succesfulIncrements = 0;
			for (int i = 0; i < this.counterWorkTuple.getExpectedAmount(); i++)
			{
				logger.info("LOOP: Counter loop " + i + " out of " + this.counterWorkTuple.getExpectedAmount()
					+ " runs...");

				try
				{
					Counter counter = this.counterService.increment(counterName);
					succesfulIncrements++;
					logger.info("Counter (" + this.counterName + ") [Increment(" + i + ")],  is was "
						+ counter.getCount());
				}
				catch (Exception e)
				{
					logger.severe(e.getMessage());
					logger.info("Trying again...");
					// e.printStackTrace();
					// try again.
					// i--;
				}
				catch (Throwable t)
				{
					logger.severe(t.getMessage());
					logger.info("Trying again...");
				}

			}
			logger.info("Ending Increment for Counter (" + this.counterName + ") with " + succesfulIncrements
				+ " successful increments.");
			return succesfulIncrements;
		}
	}

	@Getter
	@Setter
	@ToString
	@EqualsAndHashCode
	private static class Tuple
	{
		private final int expectedAmount;
		private final int actualAmount;

		public Tuple(int expectedAmount, int actualAmount)
		{
			this.expectedAmount = expectedAmount;
			this.actualAmount = actualAmount;
		}

		public static Tuple defaultTuple()
		{
			return new Tuple(1, 0);
		}
	}

}
