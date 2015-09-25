package com.theupswell.appengine.counter.data.ofy;

import java.lang.reflect.Field;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.condition.InitializeIf;
import com.googlecode.objectify.condition.PojoIf;
import com.theupswell.appengine.counter.data.CounterData;
import com.theupswell.appengine.counter.data.CounterGroupData;

/**
 * An extension of {@link PojoIf} that decides if a particular property of a {@link CounterData} should be indexed in
 * the Appengine datastore.
 */
public class IfCounterDataIndexable extends PojoIf<CounterData> implements InitializeIf
{
	private Field field;

	@Override
	public void init(final ObjectifyFactory fact, final Field field)
	{
		Preconditions.checkNotNull(fact);
		Preconditions.checkNotNull(field);

		// Store off the field for use in the matches method.
		this.field = field;
	}

	@Override
	public boolean matchesPojo(final CounterData counterData)
	{
		if (counterData == null)
		{
			return false;
		}
		else
		{
			final String fieldName = field.getName();
			if (CounterData.class.equals(field.getDeclaringClass())
				|| CounterGroupData.class.equals(field.getDeclaringClass()))
			{
				// 'counterData.indexes' object is guaranteed to be non-null via Precondition checks.

				// Field found in CounterData.class
				if (StringUtils.equals(fieldName, "numShards"))
				{
					return counterData.getIndexes().isNumShardsIndexable();
				}
				// Field found in CounterData.class
				else if (StringUtils.equals(fieldName, "counterStatus"))
				{
					return counterData.getIndexes().isCounterStatusIndexable();
				}
				// Field found in CounterData.class
				else if (StringUtils.equals(fieldName, "description"))
				{
					return counterData.getIndexes().isDescriptionIndexable();
				}
				// Field found in CounterGroupData.class
				else if (StringUtils.equals(fieldName, "eventuallyConsistentCount"))
				{
					return counterData.getIndexes().isCountIndexable();
				}
				else if (StringUtils.equals(fieldName, "creationDateTime"))
				{
					return counterData.getIndexes().isCreationDateTimeIndexable();
				}
				else if (StringUtils.equals(fieldName, "updatedDateTime"))
				{
					return counterData.getIndexes().isUpdateDateTimeIndexable();
				}
			}

			return false;
		}
	}
}
