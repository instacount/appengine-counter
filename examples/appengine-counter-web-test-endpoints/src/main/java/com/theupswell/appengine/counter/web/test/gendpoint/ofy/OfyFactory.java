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
package com.theupswell.appengine.counter.web.test.gendpoint.ofy;

import java.util.logging.Logger;

import com.googlecode.objectify.ObjectifyFactory;
import com.sappenin.objectify.translate.BigDecimalEmbeddedEntityTranslatorFactory;
import com.sappenin.objectify.translate.JodaMoneyEmbeddedEntityTranslatorFactory;
import com.sappenin.objectify.translate.UTCReadableInstantDateTranslatorFactory;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterShardData;

/**
 * A Wrapper for the ObjectifyFactory that allows for the return of an
 * {@link Ofy} for convenience. This Factory is returned by
 * {@link ObjectifyFactoryBean}.
 * 
 * @author David Fuelling
 * 
 */
public class OfyFactory extends ObjectifyFactory
{
	protected final static Logger logger = Logger.getLogger(OfyFactory.class.getName());

	/** Register our entity types */
	public OfyFactory()
	{
		long time = System.currentTimeMillis();

		// ///////////////////
		// Translation Classes
		// ///////////////////

		final BigDecimalEmbeddedEntityTranslatorFactory bigDecimalStringTranslatorFactory = new BigDecimalEmbeddedEntityTranslatorFactory();
		getTranslators().add(bigDecimalStringTranslatorFactory);

		final JodaMoneyEmbeddedEntityTranslatorFactory jodaMoneyTranslatorFactory = new JodaMoneyEmbeddedEntityTranslatorFactory();
		getTranslators().add(jodaMoneyTranslatorFactory);

		final UTCReadableInstantDateTranslatorFactory utcReadableInstantTranslatorFactory = new UTCReadableInstantDateTranslatorFactory();
		getTranslators().add(utcReadableInstantTranslatorFactory);

		// ///////////////////
		// Register Classes
		// ///////////////////

		// ShardedCounter Entities
		register(CounterData.class);
		register(CounterShardData.class);

		long millis = System.currentTimeMillis() - time;
		logger.info("Registration took " + millis + " millis");
	}

	@Override
	public Ofy begin()
	{
		return new Ofy(this);
	}

}
