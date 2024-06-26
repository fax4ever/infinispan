package org.infinispan.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.eviction.impl.PassivationManager;
import org.infinispan.filter.CacheFilters;
import org.infinispan.marshall.TestObjectStreamMarshaller;
import org.infinispan.marshall.persistence.impl.MarshalledEntryUtil;
import org.infinispan.persistence.dummy.DummyInMemoryStore;
import org.infinispan.persistence.manager.PersistenceManager;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.CheckPoint;
import org.infinispan.commons.util.concurrent.CompletionStages;
import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

/**
 * Test to verify distributed entry behavior when a loader with passivation is present in local mode
 *
 * @author wburns
 * @since 8.0
 */
@Test(groups = "functional", testName = "stream.LocalStreamIteratorWithPassivationTest")
public class LocalStreamIteratorWithPassivationTest extends DistributedStreamIteratorWithPassivationTest {
   protected ConfigurationBuilder builderUsed;

   public LocalStreamIteratorWithPassivationTest() {
      super(false, CacheMode.LOCAL);
   }

   @Test(enabled = false, description = "This requires supporting concurrent activation in cache loader interceptor")
   public void testConcurrentActivation() throws InterruptedException, ExecutionException, TimeoutException {
      final Cache<String, String> cache = cache(0, CACHE_NAME);

      Map<String, String> originalValues = new HashMap<>();
      originalValues.put(cache.toString() + 1, "cache0");
      originalValues.put(cache.toString() + 2, "cache1");
      originalValues.put(cache.toString() + 3, "cache2");

      final String loaderKey = cache.toString() + " in loader";
      final String loaderValue = "loader0";

      cache.putAll(originalValues);

      // Put this in after the cache has been updated
      originalValues.put(loaderKey, loaderValue);

      PersistenceManager persistenceManager = TestingUtil.extractComponent(cache, PersistenceManager.class);
      DummyInMemoryStore store = persistenceManager.getStores(DummyInMemoryStore.class).iterator().next();

      TestObjectStreamMarshaller sm = new TestObjectStreamMarshaller();
      PersistenceManager pm = null;
      try {
         store.write(MarshalledEntryUtil.create(loaderKey, loaderValue, sm));

         final CheckPoint checkPoint = new CheckPoint();
         pm = waitUntilAboutToProcessStoreTask(cache, checkPoint);

         Future<Void> future = fork(() -> {
            // Wait until loader is invoked
            checkPoint.awaitStrict("pre_process_on_all_stores_invoked", 10, TimeUnit.SECONDS);

            // Now force the entry to be moved to the in memory
            assertEquals(loaderValue, cache.get(loaderKey));

            checkPoint.triggerForever("pre_process_on_all_stores_released");
            return null;
         });

         Iterator<Map.Entry<String, String>> iterator = cache.entrySet().stream().iterator();

         // we need this count since the map will replace same key'd value
         int count = 0;
         Map<String, String> results = new HashMap<>();
         while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            results.put(entry.getKey(), entry.getValue());
            count++;
         }
         assertEquals(4, count);
         assertEquals(originalValues, results);

         future.get(10, TimeUnit.SECONDS);
      } finally {
         if (pm != null) {
            TestingUtil.replaceComponent(cache, PersistenceManager.class, pm, true, true);
         }
         sm.stop();
      }
   }

   @Test(enabled = false, description = "This requires supporting concurrent activation in cache loader interceptor")
   public void testConcurrentActivationWithFilter() throws InterruptedException, ExecutionException, TimeoutException {
      final Cache<String, String> cache = cache(0, CACHE_NAME);

      Map<String, String> originalValues = new HashMap<>();
      originalValues.put(cache.toString() + 1, "cache0");
      originalValues.put(cache.toString() + 2, "cache1");
      originalValues.put(cache.toString() + 3, "cache2");

      final String loaderKey = cache.toString() + " in loader";
      final String loaderValue = "loader0";

      final String filteredLoaderKey = cache.toString() + " in loader1";
      final String filteredLoaderValue = "loader1";

      cache.putAll(originalValues);

      // Put this in after the cache has been updated
      originalValues.put(loaderKey, loaderValue);

      PersistenceManager persistenceManager = TestingUtil.extractComponent(cache, PersistenceManager.class);
      DummyInMemoryStore store = persistenceManager.getStores(DummyInMemoryStore.class).iterator().next();

      TestObjectStreamMarshaller sm = new TestObjectStreamMarshaller();
      PersistenceManager pm = null;
      try {
         store.write(MarshalledEntryUtil.create(loaderKey, loaderValue, sm));
         store.write(MarshalledEntryUtil.create(filteredLoaderKey, filteredLoaderValue, sm));

         final CheckPoint checkPoint = new CheckPoint();
         pm = waitUntilAboutToProcessStoreTask(cache, checkPoint);

         Future<Void> future = fork(() -> {
            // Wait until loader is invoked
            checkPoint.awaitStrict("pre_process_on_all_stores_invoked", 10, TimeUnit.SECONDS);

            // Now force the entry to be moved to the in memory
            assertEquals(loaderValue, cache.get(loaderKey));

            checkPoint.triggerForever("pre_process_on_all_stores_released");
            return null;
         });

         Iterator<CacheEntry<String, String>> iterator = cache.getAdvancedCache().cacheEntrySet().stream()
               .filter(CacheFilters.predicate((k, v, m) -> originalValues.containsKey(k)))
               .iterator();

         // we need this count since the map will replace same key'd value
         int count = 0;
         Map<String, String> results = new HashMap<>();
         while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            results.put(entry.getKey(), entry.getValue());
            count++;
         }
         // We shouldn't have found the value in the loader
         assertEquals(4, count);
         assertEquals(originalValues, results);

         future.get(10, TimeUnit.SECONDS);
      } finally {
         if (pm != null) {
            TestingUtil.replaceComponent(cache, PersistenceManager.class, pm, true, true);
         }
         sm.stop();
      }
   }

   @Test(enabled = false, description = "This requires supporting concurrent activation in cache loader interceptor")
   public void testConcurrentActivationWithConverter() throws InterruptedException, ExecutionException, TimeoutException {
      final Cache<String, String> cache = cache(0, CACHE_NAME);

      Map<String, String> originalValues = new HashMap<>();
      originalValues.put(cache.toString() + 1, "cache0");
      originalValues.put(cache.toString() + 2, "cache1");
      originalValues.put(cache.toString() + 3, "cache2");

      final String loaderKey = cache.toString() + " in loader";
      final String loaderValue = "loader0";

      cache.putAll(originalValues);

      // Put this in after the cache has been updated
      originalValues.put(loaderKey, loaderValue);

      PersistenceManager persistenceManager = TestingUtil.extractComponent(cache, PersistenceManager.class);
      DummyInMemoryStore store = persistenceManager.getStores(DummyInMemoryStore.class).iterator().next();

      TestObjectStreamMarshaller sm = new TestObjectStreamMarshaller();
      PersistenceManager pm = null;
      try {
         store.write(MarshalledEntryUtil.create(loaderKey, loaderValue, sm));

         final CheckPoint checkPoint = new CheckPoint();
         pm = waitUntilAboutToProcessStoreTask(cache, checkPoint);

         Future<Void> future = fork(() -> {
            // Wait until loader is invoked
            checkPoint.awaitStrict("pre_process_on_all_stores_invoked", 10, TimeUnit.SECONDS);

            // Now force the entry to be moved to the in memory
            assertEquals(loaderValue, cache.get(loaderKey));

            checkPoint.triggerForever("pre_process_on_all_stores_released");
            return null;
         });

         Iterator<CacheEntry<String, String>> iterator = cache.getAdvancedCache().cacheEntrySet().stream().map(
                 CacheFilters.function(new StringTruncator(1, 3))).iterator();

         // we need this count since the map will replace same key'd value
         int count = 0;
         Map<String, String> results = new HashMap<>();
         while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            results.put(entry.getKey(), entry.getValue());
            count++;
         }
         // We shouldn't have found the value in the loader
         assertEquals(4, count);
         for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            assertEquals(entry.getValue().substring(1, 4), results.get(entry.getKey()));
         }

         future.get(10, TimeUnit.SECONDS);
      } finally {
         if (pm != null) {
            TestingUtil.replaceComponent(cache, PersistenceManager.class, pm, true, true);
         }
         sm.stop();
      }
   }


   protected PersistenceManager waitUntilAboutToProcessStoreTask(final Cache<?, ?> cache, final CheckPoint checkPoint) {
      PersistenceManager pm = TestingUtil.extractComponent(cache, PersistenceManager.class);
      final Answer<Object> forwardedAnswer = AdditionalAnswers.delegatesTo(pm);
      PersistenceManager mockManager = mock(PersistenceManager.class, withSettings().defaultAnswer(forwardedAnswer));
      doAnswer(invocation -> {
         // Wait for main thread to sync up
         checkPoint.trigger("pre_process_on_all_stores_invoked");
         // Now wait until main thread lets us through
         checkPoint.awaitStrict("pre_process_on_all_stores_released", 10, TimeUnit.SECONDS);

         return forwardedAnswer.answer(invocation);
      }).when(mockManager).publishEntries(any(), any(), anyBoolean(), anyBoolean(), any());
      TestingUtil.replaceComponent(cache, PersistenceManager.class, mockManager, true);
      return pm;
   }

   /**
    * This test is to verify that if a concurrent passivation occurs while switching between data container and loader(s)
    * that we don't return the same key/value twice
    */
   public void testConcurrentPassivation() throws InterruptedException, ExecutionException, TimeoutException {
      final Cache<String, String> cache = cache(0, CACHE_NAME);

      Map<String, String> originalValues = new HashMap<>();
      originalValues.put(cache.toString() + 1, "cache0");
      originalValues.put(cache.toString() + 2, "cache1");
      originalValues.put(cache.toString() + 3, "cache2");

      final String loaderKey = cache.toString() + " loader-value";
      final String loaderValue = "loader0";

      // Make sure this is in the cache to begin with
      originalValues.put(loaderKey, loaderValue);

      cache.putAll(originalValues);

      PersistenceManager pm = null;

      try {
         final CheckPoint checkPoint = new CheckPoint();
         pm = waitUntilAboutToProcessStoreTask(cache, checkPoint);

         Future<Void> future = fork(() -> {
            // Wait until loader is invoked
            checkPoint.awaitStrict("pre_process_on_all_stores_invoked", 10, TimeUnit.SECONDS);

            // Now force the entry to be moved to loader
            CompletionStages.join(TestingUtil.extractComponent(cache, PassivationManager.class).passivateAsync(new ImmortalCacheEntry(loaderKey, loaderValue)));

            checkPoint.triggerForever("pre_process_on_all_stores_released");
            return null;
         });

         Iterator<Map.Entry<String, String>> iterator = cache.entrySet().stream().iterator();

         // we need this count since the map will replace same key'd value
         Map<String, String> results = new HashMap<>();
         while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String prev = results.put(entry.getKey(), entry.getValue());
            assertNull("Entry " + entry + " replaced an existing value of " + prev, prev);
         }
         assertEquals(originalValues, results);

         future.get(10, TimeUnit.SECONDS);
      } finally {
         if (pm != null) {
            TestingUtil.replaceComponent(cache, PersistenceManager.class, pm, true, true);
         }
      }
   }
}
