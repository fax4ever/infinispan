package org.infinispan.commands.module;

import java.util.Map;

import org.infinispan.commands.ReplicableCommand;
import org.infinispan.commands.remote.CacheRpcCommand;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.util.ByteString;

/**
 * Modules which wish to implement their own commands and visitors must also provide an implementation of this
 * interface.
 * <p>
 * Note that this is a {@link Scopes#GLOBAL} component and as such cannot have {@link Inject} methods referring to
 * {@link Scopes#NAMED_CACHE} scoped components.
 *
 * @author Manik Surtani
 * @since 5.0
 * @deprecated since 15.1. This will class will be removed in 16.0 when commands will be marshalled via Protostream
 * <a href="https://github.com/infinispan/infinispan/issues/13247">#13247</a>
 */
@Deprecated(since = "15.1", forRemoval = true)
@Scope(Scopes.GLOBAL)
public interface ModuleCommandFactory {

   /**
    * Provides a map of command IDs to command types of all the commands handled by the command factory instance.
    * Unmarshalling requests for these command IDs will be dispatched to this implementation.
    *
    * @return map of command IDs to command types handled by this implementation.
    */
   Map<Byte, Class<? extends ReplicableCommand>> getModuleCommands();

   /**
    * Construct and initialize a {@link ReplicableCommand} based on the command id.
    *
    * @param commandId command id to construct
    * @return a ReplicableCommand
    */
   ReplicableCommand fromStream(byte commandId);

   /**
    * Construct and initialize a {@link CacheRpcCommand} based on the command id.
    *
    * @param commandId  command id to construct
    * @param cacheName  cache name at which command to be created is directed
    * @return           a {@link CacheRpcCommand}
    */
   CacheRpcCommand fromStream(byte commandId, ByteString cacheName);
}
