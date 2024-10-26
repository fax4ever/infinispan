package org.infinispan.server.resp.commands.connection;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.commands.Resp3Command;
import org.infinispan.server.resp.serialization.Resp3Response;

import io.netty.channel.ChannelHandlerContext;

/**
 * READWRITE
 *
 * @see <a href="https://redis.io/commands/readwrite/">READWRITE</a>
 * @since 14.0
 */
public class READWRITE extends RespCommand implements Resp3Command {

   public READWRITE() {
      super(1, 0, 0, 0);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler,
                                                      ChannelHandlerContext ctx,
                                                      List<byte[]> arguments) {
      // We are always in read write allowing read from backups
      Resp3Response.ok(handler.allocator());
      return handler.myStage();
   }
}
