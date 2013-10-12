/**
 * Copyright (C) ${project.inceptionYear} Oodlemud Inc. (developers@oodlemud.com)
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
package com.oodlemud.appengine.counter.web.test.gendpoint.ofy;

import com.googlecode.objectify.ObjectifyService;

/**
 * Gives us our custom version rather than the standard Objectify one. Also
 * responsible for setting up the static OfyFactory instead of the standard
 * ObjectifyFactory.
 */
public class OfyService
{
	static
	{
		ObjectifyService.setFactory(new OfyFactory());
	}

	/**
	 * @return our extension to Objectify
	 */
	public static Ofy ofy()
	{
		return (Ofy) ObjectifyService.ofy();
	}

	/**
	 * @return our extension to ObjectifyFactory
	 */
	public static OfyFactory factory()
	{
		return (OfyFactory) ObjectifyService.factory();
	}
}
