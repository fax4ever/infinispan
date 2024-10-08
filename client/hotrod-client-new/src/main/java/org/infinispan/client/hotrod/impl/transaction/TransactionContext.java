package org.infinispan.client.hotrod.impl.transaction;

import static org.infinispan.client.hotrod.logging.Log.HOTROD;
import static org.infinispan.commons.util.Util.toStr;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.impl.operations.CacheOperationsFactory;
import org.infinispan.client.hotrod.impl.transaction.entry.Modification;
import org.infinispan.client.hotrod.impl.transaction.entry.TransactionEntry;
import org.infinispan.client.hotrod.impl.transaction.operations.PrepareTransactionOperation;
import org.infinispan.client.hotrod.impl.transport.netty.OperationDispatcher;
import org.infinispan.client.hotrod.logging.Log;
import org.infinispan.client.hotrod.logging.LogFactory;
import org.infinispan.commons.util.ByRef;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.commons.util.CloseableIteratorSet;

import jakarta.transaction.Transaction;

/**
 * A context with the keys involved in a {@link Transaction}.
 * <p>
 * There is a single context for each ({@link TransactionalRemoteCacheImpl}, {@link Transaction}) pair.
 * <p>
 * It keeps the keys read and written in order to maintain the transactions isolated.
 *
 * @author Pedro Ruivo
 * @since 9.3
 */
public class TransactionContext<K, V> {

   private static final Log log = LogFactory.getLog(TransactionContext.class, Log.class);

   private final Map<WrappedKey<K>, TransactionEntry<K, V>> entries;
   private final Function<K, byte[]> keyMarshaller;
   private final Function<V, byte[]> valueMarshaller;
   private final CacheOperationsFactory operationsFactory;
   private final OperationDispatcher dispatcher;
   private final String cacheName;
   private final boolean recoverable;

   TransactionContext(Function<K, byte[]> keyMarshaller, Function<V, byte[]> valueMarshaller,
                      CacheOperationsFactory operationsFactory, OperationDispatcher dispatcher,
                      String cacheName, boolean recoveryEnabled) {
      this.keyMarshaller = keyMarshaller;
      this.valueMarshaller = valueMarshaller;
      this.operationsFactory = operationsFactory;
      this.dispatcher = dispatcher;
      this.cacheName = cacheName;
      this.recoverable = recoveryEnabled;
      entries = new ConcurrentHashMap<>();
   }

   @Override
   public String toString() {
      return "TransactionContext{" +
             "cacheName='" + cacheName + '\'' +
             ", context-size=" + entries.size() + " (entries)" +
             '}';
   }

   CompletableFuture<Boolean> containsKey(Object key, Function<K, MetadataValue<V>> remoteValueSupplier) {
      CompletableFuture<Boolean> result = new CompletableFuture<>();
      //noinspection unchecked
      entries.compute(wrap((K) key), (wKey, entry) -> {
         if (entry == null) {
            entry = createEntryFromRemote(wKey.key, remoteValueSupplier);
         }
         result.complete(!entry.isNonExists());
         return entry;
      });
      return result;
   }

   boolean containsValue(Object value, Supplier<CloseableIteratorSet<Map.Entry<K, V>>> iteratorSupplier,
         Function<K, MetadataValue<V>> remoteValueSupplier) {
      boolean found = entries.values().stream()
            .map(TransactionEntry::getValue)
            .filter(Objects::nonNull)
            .anyMatch(v -> Objects.deepEquals(v, value));
      return found || searchValue(value, iteratorSupplier.get(), remoteValueSupplier);
   }

   <T> CompletableFuture<T> compute(K key, Function<TransactionEntry<K, V>, T> function) {
      CompletableFuture<T> future = new CompletableFuture<>();
      entries.compute(wrap(key), (wKey, entry) -> {
         if (entry == null) {
            entry = TransactionEntry.notReadEntry(wKey.key);
         }
         if (log.isTraceEnabled()) {
            log.tracef("Compute key (%s). Before=%s", wKey, entry);
         }
         T result = function.apply(entry);
         future.complete(result);
         if (log.isTraceEnabled()) {
            log.tracef("Compute key (%s). After=%s (result=%s)", wKey, entry, result);
         }
         return entry;
      });
      return future;
   }

   <T> CompletableFuture<T> compute(K key, Function<TransactionEntry<K, V>, T> function,
         Function<K, MetadataValue<V>> remoteValueSupplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      entries.compute(wrap(key), (wKey, entry) -> {
         if (entry == null) {
            entry = createEntryFromRemote(wKey.key, remoteValueSupplier);
            if (log.isTraceEnabled()) {
               log.tracef("Fetched key (%s) from remote. Entry=%s", wKey, entry);
            }
         }
         if (log.isTraceEnabled()) {
            log.tracef("Compute key (%s). Before=%s", wKey, entry);
         }
         T result = function.apply(entry);
         future.complete(result);
         if (log.isTraceEnabled()) {
            log.tracef("Compute key (%s). After=%s (result=%s)", wKey, entry, result);
         }
         return entry;
      });
      return future;
   }

   boolean isReadWrite() {
      return entries.values().stream().anyMatch(TransactionEntry::isModified);
   }

   <T> T computeSync(K key, Function<TransactionEntry<K, V>, T> function,
         Function<K, MetadataValue<V>> remoteValueSupplier) {
      ByRef<T> ref = new ByRef<>(null);
      entries.compute(wrap(key), (wKey, entry) -> {
         if (entry == null) {
            entry = createEntryFromRemote(wKey.key, remoteValueSupplier);
            if (log.isTraceEnabled()) {
               log.tracef("Fetched key (%s) from remote. Entry=%s", wKey, entry);
            }
         }
         if (log.isTraceEnabled()) {
            log.tracef("Compute key (%s). Before=%s", wKey, entry);
         }
         T result = function.apply(entry);
         ref.set(result);
         if (log.isTraceEnabled()) {
            log.tracef("Compute key (%s). After=%s (result=%s)", wKey, entry, result);
         }
         return entry;
      });
      return ref.get();
   }

   /**
    * Prepares the {@link Transaction} in the server and returns the {@link XAResource} code.
    * <p>
    * A special value {@link Integer#MIN_VALUE} is used to signal an error before contacting the server (for example, it
    * wasn't able to marshall the key/value)
    */
   int prepareContext(Xid xid, boolean onePhaseCommit, long timeout) {
      PrepareTransactionOperation operation;
      List<Modification> modifications;
      try {
         modifications = toModification();
         if (log.isTraceEnabled()) {
            log.tracef("Preparing transaction xid=%s, remote-cache=%s, modification-size=%d", xid, cacheName,
                  modifications.size());
         }
         if (modifications.isEmpty()) {
            return XAResource.XA_RDONLY;
         }
      } catch (Exception e) {
         return Integer.MIN_VALUE;
      }
      try {
         int xaReturnCode;
         do {
            operation = operationsFactory
                  .newPrepareTransactionOperation(xid, onePhaseCommit, modifications, recoverable, timeout);
            xaReturnCode = dispatcher.execute(operation).toCompletableFuture().get();
         } while (operation.shouldRetry());
         return xaReturnCode;
      } catch (Exception e) {
         HOTROD.exceptionDuringPrepare(xid, e);
         return XAException.XA_RBROLLBACK;
      }
   }

   void cleanupEntries() {
      entries.clear();
   }

   private List<Modification> toModification() {
      return entries.values().stream()
            .filter(TransactionEntry::isModified)
            .map(entry -> entry.toModification(keyMarshaller, valueMarshaller))
            .collect(Collectors.toList());
   }

   private TransactionEntry<K, V> createEntryFromRemote(K key, Function<K, MetadataValue<V>> remoteValueSupplier) {
      MetadataValue<V> remoteValue = remoteValueSupplier.apply(key);
      return remoteValue == null ? TransactionEntry.nonExistingEntry(key) : TransactionEntry.read(key, remoteValue);
   }

   private boolean searchValue(Object value, CloseableIteratorSet<Map.Entry<K, V>> iterator,
         Function<K, MetadataValue<V>> remoteValueSupplier) {
      try (CloseableIterator<Map.Entry<K, V>> it = iterator.iterator()) {
         while (it.hasNext()) {
            Map.Entry<K, V> entry = it.next();
            if (!entries.containsKey(wrap(entry.getKey())) && Objects.deepEquals(entry.getValue(), value)) {
               ByRef.Boolean result = new ByRef.Boolean(false);
               entries.computeIfAbsent(wrap(entry.getKey()), wrappedKey -> {
                  MetadataValue<V> remoteValue = remoteValueSupplier.apply(wrappedKey.key);
                  if (Objects.deepEquals(remoteValue.getValue(), value)) {
                     //value didn't change. store it locally.
                     result.set(true);
                     return TransactionEntry.read(wrappedKey.key, remoteValue);
                  } else {
                     return null;
                  }
               });
               if (result.get()) {
                  return true;
               }
            }
         }
      }
      //we iterated over all keys.
      return false;
   }

   private WrappedKey<K> wrap(K key) {
      return new WrappedKey<>(key);
   }

   private static class WrappedKey<K> {
      private final K key;

      private WrappedKey(K key) {
         this.key = key;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         }
         if (o == null || getClass() != o.getClass()) {
            return false;
         }

         WrappedKey<?> that = (WrappedKey<?>) o;

         return Objects.deepEquals(key, that.key);
      }

      @Override
      public int hashCode() {
         if (key instanceof Object[]) {
            return Arrays.deepHashCode((Object[]) key);
         } else if (key instanceof byte[]) {
            return Arrays.hashCode((byte[]) key);
         } else if (key instanceof short[]) {
            return Arrays.hashCode((short[]) key);
         } else if (key instanceof int[]) {
            return Arrays.hashCode((int[]) key);
         } else if (key instanceof long[]) {
            return Arrays.hashCode((long[]) key);
         } else if (key instanceof char[]) {
            return Arrays.hashCode((char[]) key);
         } else if (key instanceof float[]) {
            return Arrays.hashCode((float[]) key);
         } else if (key instanceof double[]) {
            return Arrays.hashCode((double[]) key);
         } else if (key instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) key);
         } else {
            return Objects.hashCode(key);
         }
      }

      @Override
      public String toString() {
         return "WrappedKey{" +
                "key=" + toStr(key) +
                '}';
      }
   }

}
