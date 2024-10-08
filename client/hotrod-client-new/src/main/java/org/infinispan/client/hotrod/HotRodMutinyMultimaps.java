package org.infinispan.client.hotrod;

import org.infinispan.api.Experimental;
import org.infinispan.api.configuration.MultimapConfiguration;
import org.infinispan.api.mutiny.MutinyMultimap;
import org.infinispan.api.mutiny.MutinyMultimaps;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * @since 14.0
 **/
@Experimental
final class HotRodMutinyMultimaps implements MutinyMultimaps {
   private final HotRod hotrod;

   HotRodMutinyMultimaps(HotRod hotrod) {
      this.hotrod = hotrod;
   }

   @Override
   public <K, V> Uni<MutinyMultimap<K, V>> create(String name, MultimapConfiguration cacheConfiguration) {
      throw new UnsupportedOperationException();
   }

   @Override
   public <K, V> Uni<MutinyMultimap<K, V>> create(String name, String template) {
      throw new UnsupportedOperationException();
   }

   @Override
   public <K, V> Uni<MutinyMultimap<K, V>> get(String name) {
      return Uni.createFrom().item(new HotRodMutinyMultimap<>(hotrod, name)); // PLACEHOLDER
   }

   @Override
   public Uni<Void> remove(String name) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Multi<String> names() {
      throw new UnsupportedOperationException();
   }

   @Override
   public Uni<Void> createTemplate(String name, MultimapConfiguration cacheConfiguration) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Uni<Void> removeTemplate(String name) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Multi<String> templateNames() {
      throw new UnsupportedOperationException();
   }
}
