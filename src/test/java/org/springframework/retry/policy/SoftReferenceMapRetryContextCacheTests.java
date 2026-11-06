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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.springframework.retry.RetryContext;

public class SoftReferenceMapRetryContextCacheTests {

	protected SoftReferenceMapRetryContextCache createCache(int capacity, boolean removeEldestEntries) {
		return new SoftReferenceMapRetryContextCache(capacity, removeEldestEntries);
	}

	@Test
	public void testPutAndGet() {
		SoftReferenceMapRetryContextCache cache = createCache(5, false);
		RetryContext context = createRetryContext();
		cache.put("foo", context);
		assertEquals(context, cache.get("foo"));
	}

	@Test
	public void testLruEviction() {
		SoftReferenceMapRetryContextCache cache = createCache(2, true);

		RetryContext context1 = createRetryContext();
		RetryContext context2 = createRetryContext();
		RetryContext context3 = createRetryContext();

		cache.put("1", context1);
		cache.put("2", context2);

		// Access "1" to make it most recently used
		cache.get("1");

		cache.put("3", context3);

		// "2" should be evicted because it's the least recently used
		assertTrue(cache.containsKey("1"));
		assertFalse(cache.containsKey("2"));
		assertTrue(cache.containsKey("3"));
	}

	@Test
	public void testRemove() {
		SoftReferenceMapRetryContextCache cache = createCache(5, true);
		RetryContext context = createRetryContext();
		cache.put("foo", context);
		assertTrue(cache.containsKey("foo"));

		cache.remove("foo");
		assertFalse(cache.containsKey("foo"));
		assertNull(cache.get("foo"));
	}

	@Test
	public void testContainsKey() {
		SoftReferenceMapRetryContextCache cache = createCache(5, true);
		RetryContext context = createRetryContext();
		cache.put("foo", context);
		assertTrue(cache.containsKey("foo"));
		assertFalse(cache.containsKey("bar"));
	}

	@Test
	public void testCapacityLimitBreachedThrowsException() {
		SoftReferenceMapRetryContextCache cache = createCache(1, false);
		RetryContext context1 = createRetryContext();
		RetryContext context2 = createRetryContext();

		cache.put("foo", context1);
		try {
			cache.put("bar", context2);
			fail("Expected RetryCacheCapacityExceededException");
		}
		catch (RetryCacheCapacityExceededException e) {
			// expected
		}
	}

	@Test
	public void testCapacityLimitBreachedAllowsUpdateOfExistingEntry() {
		SoftReferenceMapRetryContextCache cache = createCache(1, false);
		RetryContext context1 = createRetryContext();
		RetryContext context2 = createRetryContext();

		cache.put("foo", context1);
		cache.put("foo", context2);
		assertEquals(context2, cache.get("foo"));
	}

	@Test
	public void testCapacityCanBeUpdated() {
		SoftReferenceMapRetryContextCache cache = createCache(1, false);
		cache.put("1", createRetryContext());
		RetryContext context2 = createRetryContext();
		try {
			cache.put("2", context2);
			fail("Expected RetryCacheCapacityExceededException");
		}
		catch (RetryCacheCapacityExceededException e) {
			// expected
		}
		cache.setCapacity(2);
		cache.put("2", context2);
		assertEquals(context2, cache.get("2"));
	}

	@Test
	public void testDefaultCacheEvictsInsteadOfRejectingWhenFull() {
		SoftReferenceMapRetryContextCache cache = new SoftReferenceMapRetryContextCache();
		// Simulate an attacker filling the cache with unique keys that are never
		// re-presented
		for (int i = 0; i < SoftReferenceMapRetryContextCache.DEFAULT_CAPACITY + 10; i++) {
			cache.put("attacker-" + i, createRetryContext());
		}
		// A legitimate entry can still be cached
		RetryContext context = createRetryContext();
		cache.put("legitimate", context);
		assertTrue(cache.containsKey("legitimate"));
		assertEquals(context, cache.get("legitimate"));
		// The eldest entries were evicted
		assertFalse(cache.containsKey("attacker-0"));
	}

	protected RetryContext createRetryContext() {
		return mock(RetryContext.class);
	}

}
