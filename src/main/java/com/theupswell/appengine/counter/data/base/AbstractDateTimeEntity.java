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
package com.theupswell.appengine.counter.data.base;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * An abstract base class for appengine-counter entities (data stored to the datastore).
 * 
 * @author David Fuelling
 */
@Getter
@Setter
@ToString(callSuper = true)
// No @EqualsAndHashCode since we identify by Id in the super-class
public abstract class AbstractDateTimeEntity extends AbstractEntity
{
	private DateTime creationDateTime;
	private DateTime updatedDateTime;

	/**
	 * Default Constructor
	 */
	public AbstractDateTimeEntity()
	{
	}

	/**
	 * Required Params constructor
	 *
	 * @param id A globally unique identifier (i.e., a {@link UUID} as a String).
	 */
	public AbstractDateTimeEntity(String id)
	{
		super(id);
		this.creationDateTime = DateTime.now(DateTimeZone.UTC);
		this.updatedDateTime = DateTime.now(DateTimeZone.UTC);
	}

}
