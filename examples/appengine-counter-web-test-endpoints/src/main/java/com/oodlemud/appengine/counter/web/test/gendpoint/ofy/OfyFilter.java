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

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import com.googlecode.objectify.ObjectifyFilter;

/**
 * Filter to warmup Objectify with our Ofy classes. Without this, the counter
 * will call the root Objectify classes before we have initialized them.
 */
public class OfyFilter extends ObjectifyFilter
{

	@Override
	public void init(FilterConfig config) throws ServletException
	{
		super.init(config);

		// Warmup OfyService so that it can initialize ObjectifyService.
		OfyService.ofy().factory();

	}

}
