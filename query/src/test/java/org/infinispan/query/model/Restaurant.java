package org.infinispan.query.model;

import org.infinispan.api.annotations.indexing.Basic;
import org.infinispan.api.annotations.indexing.GeoCoordinates;
import org.infinispan.api.annotations.indexing.Indexed;
import org.infinispan.api.annotations.indexing.Keyword;
import org.infinispan.api.annotations.indexing.Latitude;
import org.infinispan.api.annotations.indexing.Longitude;
import org.infinispan.api.annotations.indexing.Text;
import org.infinispan.protostream.annotations.Proto;

@Proto
@Indexed
@GeoCoordinates(fieldName = "location", projectable = true, sortable = true)
public record Restaurant(
      @Keyword(normalizer = "lowercase") String name,
      @Text String description,
      @Text String address,
      @Longitude Double longitude,
      @Latitude Double latitude,
      @Basic Float score
) {
}
