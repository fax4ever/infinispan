package org.infinispan.server.resp.commands.connection;

import static org.infinispan.server.resp.Util.utf8;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

import org.infinispan.security.AuthorizationPermission;
import org.infinispan.security.Security;
import org.infinispan.server.core.transport.ConnectionMetadata;
import org.infinispan.server.core.transport.NettyTransport;
import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespErrorUtil;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.commands.Resp3Command;
import org.infinispan.server.resp.serialization.Resp3Response;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelMatcher;

/**
 * CLIENT
 *
 * @see <a href="https://redis.io/commands/client/">CLIENT</a> *
 * @since 15.0
 */
public class CLIENT extends RespCommand implements Resp3Command {

   // We accept a String that matches from the start to end, only contains ASCII characters that are not blank.
   private static final Pattern CLIENT_SETINFO_PATTERN = Pattern.compile("^(?:(?=\\p{ASCII})[^\\s\\t\\n\\x0B\\f\\r])*$");

   public CLIENT() {
      super(-2, 0, 0, 0);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler, ChannelHandlerContext ctx,
                                                      List<byte[]> arguments) {
      String subcommand = utf8(arguments.get(0)).toUpperCase();
      ConnectionMetadata metadata = ConnectionMetadata.getInstance(ctx.channel());
      switch (subcommand) {
         case "CACHING":
         case "UNPAUSE":
         case "PAUSE":
         case "NO-EVICT":
         case "KILL":
         case "NO-TOUCH":
         case "GETREDIR":
         case "UNBLOCK":
         case "REPLY":
            RespErrorUtil.customError("unsupported command", handler.allocator());
            break;
         case "SETINFO":
            for (int i = 1; i < arguments.size(); i++) {
               String name = utf8(arguments.get(i));
               switch (name.toUpperCase()) {
                  case "LIB-NAME":
                     String libName = utf8(arguments.get(++i));
                     if (!CLIENT_SETINFO_PATTERN.matcher(libName).matches()) {
                        RespErrorUtil.customError("lib-name cannot contain spaces, newlines or special characters.", handler.allocator());
                        return handler.myStage();
                     }
                     metadata.clientLibraryName(libName);
                     break;
                  case "LIB-VER":
                     String libVer = utf8(arguments.get(++i));
                     if (!CLIENT_SETINFO_PATTERN.matcher(libVer).matches()) {
                        RespErrorUtil.customError("lib-ver cannot contain spaces, newlines or special characters.", handler.allocator());
                        return handler.myStage();
                     }
                     metadata.clientLibraryVersion(libVer);
                     break;
                  default:
                     RespErrorUtil.customError("unsupported attribute " + name, handler.allocator());
                     return handler.myStage();
               }
            }
            Resp3Response.ok(handler.allocator());
            break;
         case "SETNAME":
            metadata.clientName(utf8(arguments.get(1)));
            Resp3Response.ok(handler.allocator());
            break;
         case "GETNAME":
            // This could be UTF-8
            Resp3Response.string(metadata.clientName(), handler.allocator());
            break;
         case "ID":
            Resp3Response.integers(metadata.id(), handler.allocator());
            break;
         case "INFO": {
            StringBuilder sb = new StringBuilder();
            addInfo(sb, metadata);
            // This could be UTF-8
            Resp3Response.string(sb, handler.allocator());
            break;
         }
         case "LIST": {
            handler.checkPermission(AuthorizationPermission.ADMIN);
            StringBuilder sb = new StringBuilder();
            ChannelMatcher matcher = handler.respServer().getChannelMatcher();
            NettyTransport transport = handler.respServer().getTransport();
            if (transport == null) {
               transport = (NettyTransport) handler.respServer().getEnclosingProtocolServer().getTransport();
            }
            ChannelGroup channels = transport.getAcceptedChannels();
            channels.forEach(ch -> {
               if (matcher.matches(ch)) {
                  addInfo(sb, ConnectionMetadata.getInstance(ch));
               }
            });
            Resp3Response.string(sb, handler.allocator());
            break;
         }
         case "TRACKING":
            RespErrorUtil.customError("client tracking not supported", handler.allocator());
            break;
         case "TRACKINGINFO":
            Resp3Response.map(Collections.emptyMap(), handler.allocator());
            break;
      }
      return handler.myStage();
   }

   private void addInfo(StringBuilder sb, ConnectionMetadata metadata) {
      sb.append("id=");
      sb.append(metadata.id());
      sb.append(" addr=");
      sb.append(metadata.remoteAddress());
      sb.append(" laddr=");
      sb.append(metadata.localAddress());
      sb.append(" name=");
      String name = metadata.clientName();
      if (name != null) {
         sb.append(name);
      }
      String libName = metadata.clientLibraryName();
      sb.append(" lib-name=").append(libName != null ? libName : "");

      String libVer = metadata.clientLibraryVersion();
      sb.append(" lib-ver=").append(libVer != null ? libVer : "");
      sb.append(" age=");
      sb.append(Duration.between(metadata.created(), Instant.now()).getSeconds());
      sb.append(" db=");
      sb.append(metadata.cache());
      sb.append(" user=");
      sb.append(Security.getSubjectUserPrincipalName(metadata.subject()));
      sb.append(" resp=3");
      sb.append("\n");
   }
}
