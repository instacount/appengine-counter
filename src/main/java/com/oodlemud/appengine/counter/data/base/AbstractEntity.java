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
package com.oodlemud.appengine.counter.data.base;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Id;

/**
 * An abstract base class for appengine-counter entities (data stored to the
 * datastore).
 * 
 * @author David Fuelling <dfuelling@oodlemud.com>
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public abstract class AbstractEntity
{
	@Id
	private String id;

	private DateTime creationDateTime;
	private DateTime updatedDateTime;

	/**
	 * Default Constructor
	 */
	public AbstractEntity()
	{
		this(UUID.randomUUID().toString());
	}

	/**
	 * Required Params constructor
	 * 
	 * @param id A globally unique identifier (i.e., a {@link UUID} as a
	 *            String).
	 */
	public AbstractEntity(String id)
	{
		this.id = id;
		this.creationDateTime = DateTime.now(DateTimeZone.UTC);
		this.updatedDateTime = DateTime.now(DateTimeZone.UTC);
	}

	/**
	 * By default, Entities have a null parent Key. This is overridden by
	 * implementations if a Parent key exists.
	 */
	public Key<?> getParentKey()
	{
		return null;
	}

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that
	 * key will be included in the returned Key heirarchy.
	 * 
	 * @return
	 */
	public <T> Key<T> getTypedKey()
	{
		if (this.getId() == null)
		{
			return null;
		}
		else
		{
			com.google.appengine.api.datastore.Key rawKey = ObjectifyService.factory().getMetadataForEntity(this)
				.getKeyMetadata().getRawKey(this);
			return Key.create(rawKey);
		}
	}

	/**
	 * Assembles the Key for this entity. If an Entity has a Parent Key, that
	 * key will be included in the returned Key heirarchy.
	 */
	public com.google.appengine.api.datastore.Key getKey()
	{
		Key<?> typedKey = this.getTypedKey();
		return typedKey == null ? null : typedKey.getRaw();
	}

}
