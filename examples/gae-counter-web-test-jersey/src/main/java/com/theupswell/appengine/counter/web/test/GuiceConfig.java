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
package com.theupswell.appengine.counter.web.test;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.tools.appstats.AppstatsFilter;
import com.google.appengine.tools.appstats.AppstatsServlet;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.RequestScoped;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.theupswell.appengine.counter.service.ShardedCounterService;
import com.theupswell.appengine.counter.service.ShardedCounterServiceConfiguration;
import com.theupswell.appengine.counter.service.ShardedCounterServiceImpl;
import com.theupswell.appengine.counter.web.test.guice.providers.JacksonJsonProviderProvider;
import com.theupswell.appengine.counter.web.test.guice.providers.ObjectMapperProvider;
import com.theupswell.appengine.counter.web.test.ofy.OfyFactory;
import com.theupswell.appengine.counter.web.test.ofy.OfyFilter;

/**
 * An implementation of {@link GuiceServletContextListener}.
 */
public class GuiceConfig extends GuiceServletContextListener
{
	private Logger logger = Logger.getLogger(GuiceConfig.class.getName());

	// See https://code.google.com/p/google-guice/issues/detail?id=488
	// static
	// {
	//
	// ServiceFinder.setIteratorProvider(new QuickServiceIterator());
	//
	// }

	/**
	 * Initialize any Follie Servlets, such as for the HomePage or any other paths. This need not be public since only
	 * this class uses it.
	 */
	private static class JerseyGuiceServletModule extends JerseyServletModule
	{
		private Logger logger = Logger.getLogger(JerseyGuiceServletModule.class.getName());

		@Override
		protected void configureServlets()
		{
			long start = System.currentTimeMillis();

			// AppStats
			Map<String, String> appstatsParams = Maps.newHashMap();
			appstatsParams.put("logMessage", "Appstats: /admin/appstats/details?time={ID}");
			appstatsParams.put("calculateRpcCosts", "true");
			filter("/*").through(AppstatsFilter.class, appstatsParams);
			serve("/appstats/*").with(AppstatsServlet.class);

			// Objectify
			filter("/*").through(OfyFilter.class);

			// Jersey Params
			Map<String, String> params = Maps.newHashMap();

			// Setup the Main Jersey Application so that it doesn't scan the
			// whole classpath for Providers, etc.
			params
				.put("javax.ws.rs.Application", "com.theupswell.appengine.counter.web.test.AppengineCounterJerseyApp");

			params.put("com.sun.jersey.api.json.POJOMappingFeature", "true");
			params.put(ResourceConfig.FEATURE_TRACE, "false");
			params.put(ResourceConfig.FEATURE_DISABLE_WADL, "true");

			serve("/*").with(GuiceContainer.class, params);

			// //////////////
			// Data Module
			configureDataModule();

			// //////////////
			// Data Module
			configureServicesModule();

			long end = System.currentTimeMillis();
			logger.log(Level.INFO, "configureServlets() took " + (end - start) + " millis");
		}

		// ##########################
		// ##########################
		// Data Module
		// ##########################
		// ##########################

		private void configureDataModule()
		{
			logger.entering(this.getClass().getName(), "configureDataModule");
			long start = System.currentTimeMillis();

			requestStaticInjection(OfyFactory.class);

			// Use Jackson for jaxrs
			bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);
			// hook Jackson into Jersey as the POJO <-> JSON mapper
			bind(JacksonJsonProvider.class).toProvider(JacksonJsonProviderProvider.class).in(Singleton.class);

			// External things that don't have Guice annotations
			bind(AppstatsFilter.class).in(Singleton.class);
			bind(OfyFilter.class).in(Singleton.class);

			// Servlets
			bind(AppstatsServlet.class).in(Singleton.class);

			long end = System.currentTimeMillis();
			logger.log(Level.INFO, "configureDataModule() took " + (end - start) + " millis");
		}

		// ##########################
		// ##########################
		// Services Module
		// ##########################
		// ##########################

		protected void configureServicesModule()
		{
			logger.entering(this.getClass().getName(), "configureServicesModule");
			long start = System.currentTimeMillis();

			// If the object is stateless and inexpensive to create, scoping is
			// unnecessary. Leave the binding unscoped and Guice will create new
			// instances as they're required.
			// bind(XYZService.class).to(XYZServiceImpl.class);

			// --------------
			// SingletonScope
			// --------------
			bind(CapabilitiesService.class).toProvider(CapabilitiesServiceProvider.class).in(Singleton.class);

			// The entire app has a single ShardedCounterServiceConfiguration.
			// In future, this could be request-scoped and configured per
			// AccountData.
			ShardedCounterServiceConfiguration shardedCounterServiceConfig = new ShardedCounterServiceConfiguration.Builder()
				.withNumInitialShards(2).build();
			bind(ShardedCounterServiceConfiguration.class).toInstance(shardedCounterServiceConfig);

			// --------------
			// RequestScoped
			// --------------

			// Memcache is namespace aware
			bind(MemcacheService.class).toProvider(MemcacheServiceProvider.class).in(RequestScoped.class);

			// Each request gets a new ShardedCounterService since this is
			// namespace-aware.
			bind(ShardedCounterService.class).toProvider(ShardedCounterServiceProvider.class).in(RequestScoped.class);

			// //////////////////
			// Request Scoped

			long end = System.currentTimeMillis();

			logger.log(Level.INFO, "configureServicesModule() took " + (end - start) + " millis");
		}
		// //////////////////////////////////
		// Providers
		// //////////////////////////////////

		// If the object is stateless and inexpensive to create, scoping is
		// unnecessary. Leave the binding unscoped and Guice will create new
		// instances as they're required.

		// //////////////////////
		// Providers
		// //////////////////////

		private static final class ShardedCounterServiceProvider implements Provider<ShardedCounterService>
		{
			private final ShardedCounterServiceConfiguration config;
			private final MemcacheService memcacheService;
			private final CapabilitiesService capabilitiesService;

			/**
			 * Required-args Constructor.
			 * 
			 * @param config
			 * @param memcacheService
			 */
			@Inject
			public ShardedCounterServiceProvider(final ShardedCounterServiceConfiguration config,
					final MemcacheService memcacheService, final CapabilitiesService capabilitiesService)
			{
				this.config = config;
				this.memcacheService = memcacheService;
				this.capabilitiesService = capabilitiesService;
			}

			@Override
			public ShardedCounterService get()
			{
				return new ShardedCounterServiceImpl(memcacheService, capabilitiesService, config);
			}
		}

		private static final class MemcacheServiceProvider implements Provider<MemcacheService>
		{
			@Override
			public MemcacheService get()
			{
				return MemcacheServiceFactory.getMemcacheService();
			}
		}

		private static final class CapabilitiesServiceProvider implements Provider<CapabilitiesService>
		{
			@Override
			public CapabilitiesService get()
			{
				return CapabilitiesServiceFactory.getCapabilitiesService();
			}
		}

	}

	/**
	 * Logs the time required to initialize Guice
	 */
	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent)
	{
		long time = System.currentTimeMillis();

		super.contextInitialized(servletContextEvent);

		long millis = System.currentTimeMillis() - time;
		logger.log(Level.INFO, "Guice initialization took " + millis + " millis");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.google.inject.servlet.GuiceServletContextListener#getInjector()
	 */
	@Override
	protected Injector getInjector()
	{
		return Guice.createInjector(new JerseyGuiceServletModule());
	}
}
