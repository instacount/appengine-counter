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
package com.oodlemud.appengine.counter.web.test.guice;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.tools.appstats.AppstatsFilter;
import com.google.appengine.tools.appstats.AppstatsServlet;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import com.googlecode.objectify.ObjectifyFilter;
import com.oodlemud.appengine.counter.service.ShardedCounterService;
import com.oodlemud.appengine.counter.service.ShardedCounterServiceConfiguration;
import com.oodlemud.appengine.counter.service.ShardedCounterServiceImpl;
import com.oodlemud.appengine.counter.web.test.ofy.OfyFactory;
import com.oodlemud.appengine.counter.web.test.servlets.CounterServlet;
import com.oodlemud.appengine.counter.web.test.servlets.CounterServletSerial;

public class GuiceModule extends AbstractModule
{
	@Override
	protected void configure()
	{
		// Configure Guice as you normally would, then...
		install(new JerseyGuiceServletModule());
	}

	/**
	 * Initialize any Follie Servlets, such as for the HomePage or any other
	 * paths. This need not be public since only this class uses it.
	 */
	private static class JerseyGuiceServletModule extends ServletModule
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

			filter("/*").through(ObjectifyFilter.class);

			// Servlets
			serve("/test/increment.html").with(CounterServlet.class);
			serve("/test/incrementSerial.html").with(CounterServletSerial.class);

			// Jersey Params
			Map<String, String> params = Maps.newHashMap();

			// Configure Jersey Filters
			// params.put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES,
			// CacheControlResourceFilterFactory.class.getCanonicalName());
			// params.put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS,
			// com.sun.jersey.api.container.filter.GZIPContentEncodingFilter.class.getCanonicalName());
			// params.put(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS,
			// com.sun.jersey.api.container.filter.GZIPContentEncodingFilter.class.getCanonicalName());

			// Setup the Main Jersey Application so that it doesn't scan the
			// whole classpath for Providers, etc.
			// params.put("javax.ws.rs.Application",
			// "com.oodlemud.appengine.counter.web.test.AppengineCounterJerseyApp");

			// Disable Classpath Scanning
			// See here:
			// https://jersey.java.net/documentation/latest/deployment.html

			params.put("com.sun.jersey.api.json.POJOMappingFeature", "true");
			// params.put(ResourceConfig.FEATURE_TRACE, "false");
			// params.put(ResourceConfig.FEATURE_DISABLE_WADL, "true");

			// serve("/*").with(GuiceContainer.class, params);

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
			// bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);
			// hook Jackson into Jersey as the POJO <-> JSON mapper
			// bind(JacksonJsonProvider.class).toProvider(JacksonJsonProviderProvider.class).in(Singleton.class);

			// External things that don't have Guice annotations
			bind(AppstatsFilter.class).in(Singleton.class);
			bind(ObjectifyFilter.class).in(Singleton.class);

			// Servlets
			bind(AppstatsServlet.class).in(Singleton.class);
			bind(CounterServlet.class).in(Singleton.class);
			bind(CounterServletSerial.class).in(Singleton.class);

			long end = System.currentTimeMillis();
			logger.log(Level.INFO, "configureDataModule() took " + (end - start) + " millis");
		}

		@Provides
		public MemcacheService provideMemcacheService()
		{
			return MemcacheServiceFactory.getMemcacheService(NamespaceManager.get());
		}

		@Provides
		public CapabilitiesService provideCapabilitiesService()
		{
			return CapabilitiesServiceFactory.getCapabilitiesService();
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

			// //////////////////////
			// Providers
			// //////////////////////

			// //////////////////////
			// Bind Jersey Resources
			// bind(CounterResource.class).in(RequestScoped.class);

			// --------------
			// SingletonScope
			// --------------

			// The entire app has a single ShardedCounterServiceConfiguration.
			// In future, this could be request-scoped and configured per
			// AccountData.
			ShardedCounterServiceConfiguration shardedCounterServiceConfig = new ShardedCounterServiceConfiguration.Builder()
				.withNumInitialShards(2).build();
			bind(ShardedCounterServiceConfiguration.class).toInstance(shardedCounterServiceConfig);

			// --------------
			// RequestScoped
			// --------------
			// Each request gets a new ShardedCounterService since this is
			// namespace-aware.
			bind(ShardedCounterService.class).toProvider(ShardedCounterServiceProvider.class).in(Singleton.class);

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

		public static final class ShardedCounterServiceProvider implements Provider<ShardedCounterService>
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

	}
}