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
package com.theupswell.appengine.counter.web.test.jersey;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.ResourceConfig;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.theupswell.appengine.counter.web.test.guice.GuiceModule;

/**
 * An extension class used to disable package scanning.
 */
public class AppengineCounterJerseyApp extends ResourceConfig
{
	private static final Logger logger = Logger.getLogger(AppengineCounterJerseyApp.class.getName());

	@Inject
	public AppengineCounterJerseyApp(ServiceLocator serviceLocator)
	{
		logger.info("Registering Resources...");

		// Register root resources, then...
		this.register(CounterResource.class);

		this.property("jersey.config.server.provider.packages",
			"com.fasterxml.jackson.jaxrs.json;com.theupswell.appengine.counter.web.test.jersey");

		logger.info("Registering Hk2/Guice Injectables...");

		// Instantiate Guice Bridge
		GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);

		GuiceIntoHK2Bridge guiceBridge = serviceLocator.getService(GuiceIntoHK2Bridge.class);
		Injector injector = Guice.createInjector(new GuiceModule());
		guiceBridge.bridgeGuiceInjector(injector);
	}
}
