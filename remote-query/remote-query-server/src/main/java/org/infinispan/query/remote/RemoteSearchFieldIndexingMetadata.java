package org.infinispan.query.remote;

import static org.infinispan.query.remote.impl.indexing.IndexingMetadata.findProcessedAnnotation;

import java.util.Map;
import java.util.Optional;

import org.hibernate.search.engine.backend.metamodel.IndexDescriptor;
import org.hibernate.search.engine.backend.metamodel.IndexFieldDescriptor;
import org.hibernate.search.engine.backend.metamodel.IndexValueFieldTypeDescriptor;
import org.infinispan.objectfilter.impl.syntax.IndexedFieldProvider;
import org.infinispan.objectfilter.impl.util.StringHelper;
import org.infinispan.protostream.descriptors.Descriptor;
import org.infinispan.protostream.descriptors.FieldDescriptor;
import org.infinispan.protostream.descriptors.GenericDescriptor;
import org.infinispan.protostream.descriptors.JavaType;
import org.infinispan.query.remote.impl.indexing.IndexingMetadata;

public class RemoteSearchFieldIndexingMetadata implements IndexedFieldProvider.FieldIndexingMetadata<Descriptor> {

   private final Descriptor messageDescriptor;
   private final IndexDescriptor indexDescriptor;
   private final String keyProperty;
   private final Descriptor keyMessageDescriptor;

   public RemoteSearchFieldIndexingMetadata(IndexDescriptor indexDescriptor, Descriptor messageDescriptor, Map<String, GenericDescriptor> genericDescriptors) {
      if (messageDescriptor == null) {
         throw new IllegalArgumentException("argument cannot be null");
      }
      this.indexDescriptor = indexDescriptor;
      this.messageDescriptor = messageDescriptor;
      IndexingMetadata indexingMetadata = findProcessedAnnotation(messageDescriptor, IndexingMetadata.INDEXED_ANNOTATION);
      if (indexingMetadata != null && indexingMetadata.indexingKey() != null) {
         keyProperty = indexingMetadata.indexingKey().fieldName();
         keyMessageDescriptor = (Descriptor) genericDescriptors.get(indexingMetadata.indexingKey().typeFullName());
      } else {
         keyProperty = null;
         keyMessageDescriptor = null;
      }
   }

   public RemoteSearchFieldIndexingMetadata(Descriptor messageDescriptor, IndexDescriptor indexDescriptor,
                                            String keyProperty, Descriptor keyMessageDescriptor) {
      this.messageDescriptor = messageDescriptor;
      this.indexDescriptor = indexDescriptor;
      this.keyProperty = keyProperty;
      this.keyMessageDescriptor = keyMessageDescriptor;
   }

   @Override
   public boolean isSearchable(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);
      return field != null && field.searchable();
   }

   @Override
   public boolean isAnalyzed(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);
      return field != null && field.analyzerName().isPresent();
   }

   @Override
   public boolean isNormalized(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);
      return field != null && field.normalizerName().isPresent();
   }

   @Override
   public boolean isProjectable(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);
      return field != null && field.projectable();
   }

   @Override
   public boolean isAggregable(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);
      return field != null && field.aggregable();
   }

   @Override
   public boolean isSortable(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);
      return field != null && field.sortable();
   }

   @Override
   public boolean isVector(String[] propertyPath) {
      IndexValueFieldTypeDescriptor field = getField(propertyPath);

      // TODO https://hibernate.atlassian.net/browse/HSEARCH-3909 check it
      return true;
   }

   @Override
   public Object getNullMarker(String[] propertyPath) {
      Descriptor md = messageDescriptor;
      int i = 0;
      for (String p : propertyPath) {
         i++;
         FieldDescriptor field = md.findFieldByName(p);
         if (field == null) {
            break;
         }
         if (i == propertyPath.length) {
            IndexingMetadata indexingMetadata = findProcessedAnnotation(md, IndexingMetadata.INDEXED_ANNOTATION);
            return indexingMetadata == null ? null : indexingMetadata.getNullMarker(field.getName());
         }
         if (field.getJavaType() != JavaType.MESSAGE) {
            break;
         }
         md = field.getMessageType();
      }
      return null;
   }

   @Override
   public Descriptor keyType(String property) {
      return (property.equals(keyProperty)) ? keyMessageDescriptor : null;
   }

   private IndexValueFieldTypeDescriptor getField(String[] propertyPath) {
      Optional<IndexFieldDescriptor> field = indexDescriptor.field(StringHelper.join(propertyPath));
      if (!field.isPresent()) {
         return null;
      }

      IndexFieldDescriptor indexFieldDescriptor = field.get();
      if (!indexFieldDescriptor.isValueField()) {
         return null;
      }

      return indexFieldDescriptor.toValueField().type();
   }

}
