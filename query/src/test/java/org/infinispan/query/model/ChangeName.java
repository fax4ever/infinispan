package org.infinispan.query.model;

import org.infinispan.api.annotations.indexing.Basic;
import org.infinispan.api.annotations.indexing.Indexed;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoSchema;

@Indexed
public class ChangeName {

   public static final String DATA_FIELD_NAME = "dataProperty";
   public static final String INDEX_FIELD_NAME = "indexProperty";

   private String dataProperty;

   @ProtoFactory
   public ChangeName(String dataProperty) {
      this.dataProperty = dataProperty;
   }

   @ProtoField(number = 1)
   @Basic(name = INDEX_FIELD_NAME)
   public String getDataProperty() {
      return dataProperty;
   }

   @ProtoSchema(
         includeClasses = ChangeName.class,
         schemaFileName = "change-name.proto",
         schemaPackageName = "bla"
   )
   public interface ChangeNameSchema extends GeneratedSchema {
      ChangeNameSchema INSTANCE = new ChangeNameSchemaImpl();
   }
}
