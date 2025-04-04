package org.infinispan.server.resp.commands.scripting.eval;

import java.util.concurrent.CompletionStage;

import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespRequestHandler;

import io.netty.channel.ChannelHandlerContext;

/**
 * EVAL_SHA
 *
 * @see <a href="https://redis.io/docs/latest/commands/evalsha/">EVALSHA</a>
 * @since 15.1
 */
public class EVALSHA extends EVAL {

   protected CompletionStage<RespRequestHandler> performEval(Resp3Handler handler, ChannelHandlerContext ctx, String script, String[] keys, String[] argv) {
      try {
         return handler
               .stageToReturn(handler.respServer().luaEngine().evalSha(handler, ctx, script, keys, argv, 0)
                     .thenApply(__ -> handler), ctx);
      } catch (Exception e) {
         handler.writer().customError(e.getMessage());
         return handler.myStage();
      }
   }
}
