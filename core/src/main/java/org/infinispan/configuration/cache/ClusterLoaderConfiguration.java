package org.infinispan.configuration.cache;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.Attribute;
import org.infinispan.commons.configuration.attributes.AttributeDefinition;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.commons.util.TimeQuantity;
import org.infinispan.configuration.parsing.Element;
import org.infinispan.persistence.cluster.ClusterLoader;

/**
 * ClusterLoaderConfiguration.
 *
 * @author Tristan Tarrant
 * @since 5.2
 * @deprecated since 11.0. To be removed in 14.0 ISPN-11864 with no direct replacement.
 */
@BuiltBy(ClusterLoaderConfigurationBuilder.class)
@ConfigurationFor(ClusterLoader.class)
@Deprecated(forRemoval=true, since = "11.0")
public class ClusterLoaderConfiguration extends AbstractStoreConfiguration<ClusterLoaderConfiguration> {

   static final AttributeDefinition<TimeQuantity> REMOTE_CALL_TIMEOUT = AttributeDefinition.builder(org.infinispan.configuration.parsing.Attribute.REMOTE_TIMEOUT, TimeQuantity.valueOf("15s")).immutable().build();

   public static AttributeSet attributeDefinitionSet() {
      return new AttributeSet(ClusterLoaderConfiguration.class, AbstractStoreConfiguration.attributeDefinitionSet(), REMOTE_CALL_TIMEOUT);
   }

   private final Attribute<TimeQuantity> remoteCallTimeout;

   ClusterLoaderConfiguration(AttributeSet attributes, AsyncStoreConfiguration async) {
      super(Element.CLUSTER_LOADER, attributes, async);
      remoteCallTimeout = attributes.attribute(REMOTE_CALL_TIMEOUT);
   }

   public long remoteCallTimeout() {
      return remoteCallTimeout.get().longValue();
   }
}
