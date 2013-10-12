/**
 * Copyright (C) 2013 Oodlemud Inc. (developers@oodlemud.com)
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
package com.oodlemud.appengine.counter.web.test.servlets;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.google.inject.Inject;
import com.oodlemud.appengine.counter.Counter;
import com.oodlemud.appengine.counter.service.CounterService;
import com.oodlemud.appengine.counter.service.ShardedCounterService;

public class CounterServletSerial extends HttpServlet
{
	private static Logger logger = Logger.getLogger(CounterServletSerial.class.getName());

	private static final long serialVersionUID = 4314514444980866055L;

	private final ShardedCounterService shardedCounterService;

	private final static int NUM_LOOPS = 1;

	private final static int NUM_INCREMENTS_PER_LOOPS = 1;

	@Inject
	public CounterServletSerial(final ShardedCounterService shardedCounterService)
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
		for (int i = 0; i < NUM_LOOPS; i++)
		{
			Tuple tuple = Tuple.defaultTuple();
			CounterWorkCallable task = new CounterWorkCallable("counter" + i, shardedCounterService, tuple);
			try
			{
				task.call();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		int actualIncrements = 0;
		logger.info("Expected " + (NUM_INCREMENTS_PER_LOOPS * NUM_LOOPS) + " Increments.  Actual Increments: "
			+ actualIncrements);

	}

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
					e.printStackTrace();
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
			return new Tuple(NUM_INCREMENTS_PER_LOOPS, 0);
		}
	}

}
