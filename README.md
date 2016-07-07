appengine-counter (A Sharded Counter for Google Appengine)
===========================
[![Build Status](https://travis-ci.org/instacount/appengine-counter.png)](https://travis-ci.org/instacount/appengine-counter)
[![Coverage Status](https://coveralls.io/repos/github/instacount/appengine-counter/badge.svg?branch=master)](https://coveralls.io/github/instacount/appengine-counter?branch=master)

Appengine-counter is a ShardedCounter implementation for use in Google Appengine.  It offers strongly consistent increment/decrement functionality while maintaining high-throughput via on-the-fly shard configuration.  Appengine-counter uses memcache for fast counter retrieval, all the while being fully backed by the GAE Datastore for incredible durability and availability.<br/><br/>Appengine-counter is patterned off of the following <a href="https://developers.google.com/appengine/articles/sharding_counters">article</a> from developer.google.com, but uses Objectify for improved maintainability.<br/><br/>The rationale for a ShardedCounter is as follows (quoted from the above linked Google article):

> When developing an efficient application on Google App Engine, you need to pay attention to how often an entity is updated. While App Engine's datastore scales to support a huge number of entities, it is important to note that you can only expect to update any single entity or entity group about five times a second. That is an estimate and the actual update rate for an entity is dependent on several attributes of the entity, including how many properties it has, how large it is, and how many indexes need updating. While a single entity or entity group has a limit on how quickly it can be updated, App Engine excels at handling many parallel requests distributed across distinct entities, and we can take advantage of this by using sharding."

Thus, when a datastore-backed counter is required (i.e., for counter consistency, redundancy, and availability) we can increment random Counter shards in parallel and achieve a high-throughput counter without sacrificing consistency or availability.  For example, if a particular counter needs to support 100 increments per second, then the application supporting this counter could create the counter with approximately 20 shards, and the throughput could be sustained (this is because, per the above quote, any particular entity group in the appengine HRD can support ~5 updates/second).

Features
--------
+ <b>Durable</b><br/>
Counter values are stored in the Google Appengine <a href="https://developers.google.com/appengine/docs/python/datastore/structuring_for_strong_consistency">HRD Datastore</a> for data durability and redundancy.  Once an increment or decrement is recorded by the datastore, it's there for good.

+ <b>Available</b><br/>
Since counters are backed by the appengine datastore and appengine itself, counter counts are highly available.

+ <b>Atomic</b><br/>
Counter increment/decrement operations are atomic and will either succeed or fail as a single unit of work.

+ <b>High Performance</b><br/>
Appengine datastore entity groups are limited to ~5 writes/second, so in order to provide a high-throughput counter implementation, a particular counter's 'count' is distributed amongst various counter-shard entities.  Whenever an increment operation is peformed, one of the available shards is chosen to have its count incremented.  In this way, counter throughput is limited only by the number of shards configured for each counter.

+ <b>Smart Caching</b><br/>
Counter values are cached in memcache for high-performance counter reads, and increment/decrement operations update memache so you almost never have to worry about stale counts.

+ <b>Growable Shards --> Higher Throughput</b><br/>
Increasing the number of shards for a particular counter will increase the number of updates/second that the system can handle.  Using appengine-counter, any counter's shard-count can be adjusted in real-time using the appengine datastore viewer.  The more shards, the higher the throughput for any particular counter.

+ <b>Optional Transactionality</b><br/>
By default, counter increment/decrement operations do not happen in an existing datastore transaction.  Instead, a new transaction is always created, which allows the counter to be atomically incremented without having to worry about XG-transaction limits (currently 25 entity groups per Transaction).  However, sometimes it's necessary to increment a counter inside of an XG transaction, and appengine-counter allows for this.

+ <b>Async Counter Deletion</b><br/>
Because some counters may have a large number of counter shards, counter deletion is facilitated in an asynchronous manner using a TaskQueue.  Counter deletion is eventually consistent, although the counter-status will reflect the fact that a counter is being deleted.

<b><i><u>Note: The current release of this library is not compatible with Objectify versions prior to version 5.0.3, and it works best with Objectify version 5.1.x.  See the changelog for previous version support.</u></i></b>

## Getting Started
----------
To get started, please see the instructions and details in the [Getting Started](https://github.com/instacount/appengine-counter/wiki/Getting-Started) page.

## Usage
----------
To learn more about using appengine-counter, please see the [Usage](https://github.com/instacount/appengine-counter/wiki/Usage) page.

Change Log
----------

**Version 2.0.0**
+ Package naming change from com.theupswell to io.instacount.
+ Fix [#24](https://github.com/instacount/appengine-counter/issues/24) Invalid CounterStatus is allowed when creating or updating a counter.
+ Fix [#20](https://github.com/instacount/appengine-counter/issues/20) Introduced CounterService.create() to create a counter without having to increment it.
+ Adjusted CounterService.getCounter to return an Optional.absent() if the counter doesn't exist.
+ Introduced CounterService.reset() to reset all counter shards to 0.
+ Changes to sharding implementation to unify increment and decrement.
+ Counter.java now holds a BigInteger instead of a long since the aggregation of multiple shards may exceed (Long.MAX_VALUE - 1).
+ Better failure handling in the event of a memcache failure.
+ Default counter memcache settings reduced to 60 seconds.
+ Improvements around Objectify's session cache handling of CounterShards.

**Version 1.2.0**
+ Remove AbstractEntity, and more tightly enforce that CounterData may not have null ids.

**Version 1.1.2**
+ Separate Creation/Update DateTime attributes out of AbstractEntity and into AbstractDateTimeEntity.
+ Added Indexability to CounterData for creation/update date-times.
+ Improved unit test coverage.

**Version 1.1.1**
+ Fix [Issue #18](https://github.com/instacount/appengine-counter/issues/18) Add ability to specify indexing in CounterData Entity class
+ Remove unused Guava dependency
+ Increment Appengine dependency

**Version 1.1.0**
+ Improve Transaction semantics for parent-transactions
+ Simplify CounterService interface (no longer returns Counter count; must specify increment/decrement appliedAmount)
+ Fix [Issue #7](https://github.com/instacount/appengine-counter/issues/7) numRetries doesn't get decremented in ShardedCounterServiceImpl.incrementMemcacheAtomic
+ Fix [Issue #11](https://github.com/instacount/appengine-counter/issues/11) Default Delete Implementation (see [here](https://github.com/instacount/appengine-counter/tree/master/src/main/java/io/instacount/appengine/counter/ext/DefaultDeletionTaskHandler.java)).
+ Fix [Issue #16](https://github.com/instacount/appengine-counter/issues/16) Remove redundant counterShard datastore put in ShardedCounterServiceImpl#increment
+ Fix [Issue #17](https://github.com/instacount/appengine-counter/issues/17) Enhance the interface of CounterService to not return a count when incrementing/decrementing.
+ Improve unit tests for new functionality.
+ Update default Objectify to 5.1.x.
+ Remove dependency on objectify-utils

**Version 1.0.2**
+ Fix [Issue #17](https://github.com/instacount/appengine-counter/issues/17) Increments in an existing Transaction may populate Memcache incorrectly
+ Improved unit test coverage
+ Improved Javadoc in CounterService and its descendants.

**Version 1.0.1**
+ Javadoc and license updates
+ First release deploy to maven central

**Version 1.0.0**
+ Update Library for Objectify5
+ Name change from Oodlemud to UpSwell
+ Package naming change from com.oodlemud to com.theupswell.
+ Initial Commit of revised code.

Authors
-------

**Instacount Inc.**
**David Fuelling**

+ http://twitter.com/instacount_io
+ http://github.com/instacount


Copyright and License
---------------------

Copyright 2016 Instacount Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.