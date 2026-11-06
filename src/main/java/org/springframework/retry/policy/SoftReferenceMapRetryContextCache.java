/*
 * Copyright 2006-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.retry.policy;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.retry.RetryContext;

/**
 * Map-based implementation of {@link RetryContextCache}. The map backing the
 * cache of contexts is synchronized and its entries are soft-referenced, so may
 * be garbage collected under pressure. Entries are stored in memory, with a
 * configurable maximum capacity and LRU eviction policy. For a cache used in a
 * global state, any attempt to increase its capacity should result in an
 * exception. However, a regular cache should not have permanent entries, and
 * removing the eldest entries is sensible.
 * 
 * @see MapRetryContextCache for non-soft referenced version
 * 
 * @author Dave Syer
 */
public class SoftReferenceMapRetryContextCache implements RetryContextCache {

	/**
	 * Default value for maximum capacity of the cache. This is set to a
	 * reasonably low value (4096) to avoid users inadvertently filling the
	 * cache with item keys that are inconsistent.
	 */
	public static final int DEFAULT_CAPACITY = 4096;

	private static final Log logger = LogFactory.getLog(SoftReferenceMapRetryContextCache.class);

	private final Map<Object, SoftReference<RetryContext>> map;

	private final boolean failIfFull;

	private int capacity;

	/**
	 * Create an instance with {@link #DEFAULT_CAPACITY the default capacity}
	 */
	public SoftReferenceMapRetryContextCache() {
		this(DEFAULT_CAPACITY);
	}

	/**
	 * Create an instance with the given capacity, removing the eldest entries when the
	 * cache is full
	 * @param capacity the initial capacity of the cache
	 */
	public SoftReferenceMapRetryContextCache(int capacity) {
		this(capacity, true);
	}

	/**
	 * Create an instance with the given capacity and the policy to apply when the cache
	 * is full.
	 * @param capacity the size of the cache
	 * @param removeEldestEntries whether to remove the eldest entries when the cache is
	 * full
	 */
	public SoftReferenceMapRetryContextCache(int capacity, boolean removeEldestEntries) {
		this.capacity = capacity;
		this.map = Collections.synchronizedMap(removeEldestEntries
				? new LinkedHashMap<Object, SoftReference<RetryContext>>(capacity, 0.75f, true) {
					@Override
					protected boolean removeEldestEntry(Map.Entry<Object, SoftReference<RetryContext>> eldest) {
						boolean evict = size() > SoftReferenceMapRetryContextCache.this.capacity;
						if (evict && logger.isWarnEnabled()) {
							logger.warn("Retry cache capacity limit breached. "
									+ "Do you need to re-consider the implementation of the key generator, "
									+ "or the equals and hashCode of the items that failed?");
						}
						return evict;
					}
				} : new HashMap<Object, SoftReference<RetryContext>>());
		this.failIfFull = !removeEldestEntries;
	}

	/**
	 * Update the capacity of this cache. Prevent the cache from growing
	 * unboundedly if items that fail are misidentified and two references to an
	 * identical item actually do not have the same key. This can happen when
	 * users implement equals and hashCode based on mutable fields, for
	 * instance.
	 * 
	 * @param capacity the capacity to set
	 */
	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public boolean containsKey(Object key) {
		if (!map.containsKey(key)) {
			return false;
		}
		if (map.get(key).get() == null) {
			// our reference was garbage collected
			map.remove(key);
		}
		return map.containsKey(key);
	}

	public RetryContext get(Object key) {
		SoftReference<RetryContext> value = map.get(key);
		return (value != null) ? value.get() : null;
	}

	public void put(Object key, RetryContext context) {
		if (failIfFull && !map.containsKey(key) && map.size() >= capacity) {
			throw new RetryCacheCapacityExceededException("Cache capacity limit breached. "
					+ "Do you need to re-consider the implementation of the key generator, "
					+ "or the equals and hashCode of the items that failed?");
		}
		map.put(key, new SoftReference<RetryContext>(context));
	}

	public void remove(Object key) {
		map.remove(key);
	}
}
