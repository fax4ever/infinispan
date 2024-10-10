package org.infinispan.server.resp.commands.hash;

import static org.infinispan.server.resp.commands.hash.HEXISTS.CONVERTER;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.infinispan.multimap.impl.EmbeddedMultimapPairCache;
import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.commands.Resp3Command;
import org.infinispan.server.resp.serialization.Resp3Response;

import io.netty.channel.ChannelHandlerContext;

public class HSETNX extends RespCommand implements Resp3Command {

   public HSETNX() {
      super(4, 1, 1, 1);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler, ChannelHandlerContext ctx, List<byte[]> arguments) {
      EmbeddedMultimapPairCache<byte[], byte[], byte[]> mmap = handler.getHashMapMultimap();
      byte[] key = arguments.get(0);
      byte[] propKey = arguments.get(1);
      byte[] propVal = arguments.get(2);
      CompletionStage<Long> cs = mmap.setIfAbsent(key, propKey, propVal).thenApply(CONVERTER);
      // Yes, use integers as booleans.
      return handler.stageToReturn(cs, ctx, Resp3Response.INTEGER);
   }
}