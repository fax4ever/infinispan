package org.infinispan.client.hotrod.impl.iteration;

import java.lang.invoke.MethodHandles;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.infinispan.client.hotrod.exceptions.RemoteIllegalLifecycleStateException;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.infinispan.client.hotrod.impl.operations.IterationNextResponse;
import org.infinispan.client.hotrod.impl.operations.IterationStartResponse;
import org.infinispan.client.hotrod.impl.protocol.HotRodConstants;
import org.infinispan.client.hotrod.logging.Log;
import org.infinispan.client.hotrod.logging.LogFactory;
import org.infinispan.commons.reactive.AbstractAsyncPublisherHandler;
import org.infinispan.commons.util.IntSet;
import org.infinispan.commons.util.logging.TraceException;

class RemoteInnerPublisherHandler<K, E> extends AbstractAsyncPublisherHandler<Map.Entry<SocketAddress, IntSet>,
      Map.Entry<K, E>, IterationStartResponse, IterationNextResponse<K, E>> {
   private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass());

   protected final RemotePublisher<K, E> publisher;

   // Need to be volatile since cancel can come on a different thread
   protected volatile SocketAddress socketAddress;
   private volatile byte[] iterationId;
   private final AtomicBoolean cancelled = new AtomicBoolean();

   protected RemoteInnerPublisherHandler(RemotePublisher<K, E> parent, int batchSize,
         Supplier<Map.Entry<SocketAddress, IntSet>> supplier, Map.Entry<SocketAddress, IntSet> firstTarget) {
      super(batchSize, supplier, firstTarget);
      this.publisher = parent;
   }

   private String iterationId() {
      return publisher.iterationId(iterationId);
   }

   @Override
   protected void sendCancel(Map.Entry<SocketAddress, IntSet> target) {
      if (!cancelled.getAndSet(true)) {
         actualCancel();
      }
   }

   private void actualCancel() {
      if (iterationId != null && socketAddress != null) {
         // Just let cancel complete asynchronously
         publisher.sendCancel(iterationId, socketAddress);
      }
   }

   @Override
   protected CompletionStage<IterationStartResponse> sendInitialCommand(
         Map.Entry<SocketAddress, IntSet> target, int batchSize) {
      SocketAddress address = target.getKey();
      IntSet segments = target.getValue();
      log.tracef("Starting iteration with segments %s", segments);
      return publisher.newIteratorStartOperation(address, segments, batchSize);
   }

   @Override
   protected CompletionStage<IterationNextResponse<K, E>> sendNextCommand(Map.Entry<SocketAddress, IntSet> target, int batchSize) {
      return publisher.newIteratorNextOperation(iterationId, socketAddress);
   }

   @Override
   protected long handleInitialResponse(IterationStartResponse startResponse, Map.Entry<SocketAddress, IntSet> target) {
      this.socketAddress = startResponse.getSocketAddress();
      this.iterationId = startResponse.getIterationId();
      if (log.isDebugEnabled()) {
         log.iterationTransportObtained(socketAddress, iterationId());
         log.startedIteration(iterationId());
      }

      // We could have been cancelled while the initial response was sent
      if (cancelled.get()) {
         actualCancel();
      }
      return 0;
   }

   @Override
   protected long handleNextResponse(IterationNextResponse<K, E> nextResponse, Map.Entry<SocketAddress, IntSet> target) {
      if (!nextResponse.hasMore()) {
         // server doesn't clean up when complete
         sendCancel(target);
         // Don't complete the segments if it was an invalid iteration
         if (nextResponse.getStatus() != HotRodConstants.INVALID_ITERATION) {
            log.tracef("No more entries retrieved, so completing segments %s from %s", target.getValue(), target.getKey());
            publisher.completeSegments(target.getValue());
         } else {
            log.tracef("Invalid iteration response, so must retry segments %s", target.getValue());
         }
         targetComplete();
      }
      IntSet completedSegments = nextResponse.getCompletedSegments();
      if (completedSegments != null && log.isTraceEnabled()) {
         IntSet targetSegments = target.getValue();
         if (targetSegments != null) {
            targetSegments.removeAll(completedSegments);
         }
         log.tracef("Completed segments: %s still have %s left for %s", completedSegments, targetSegments,
               target.getKey());
      }
      publisher.completeSegments(completedSegments);
      List<Map.Entry<K, E>> entries = nextResponse.getEntries();
      for (Map.Entry<K, E> entry : entries) {
         if (!onNext(entry)) {
            break;
         }
      }
      return entries.size();
   }

   @Override
   protected void handleThrowableInResponse(Throwable t, Map.Entry<SocketAddress, IntSet> target) {
      if ((t instanceof TransportException || t instanceof RemoteIllegalLifecycleStateException || t instanceof ConnectException)
            && target.getKey() != null) {
         log.throwableDuringPublisher(t);
         if (log.isTraceEnabled()) {
            IntSet targetSegments = target.getValue();
            if (targetSegments != null) {
               log.tracef("There are still outstanding segments %s that will need to be retried", targetSegments);
            }
         }
         publisher.erroredServer(target.getKey());
         // Try next target if possible
         targetComplete();

         accept(0);
      } else {
         t.addSuppressed(new TraceException());
         super.handleThrowableInResponse(t, target);
      }

   }
}
