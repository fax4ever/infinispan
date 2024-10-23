package org.infinispan.query.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.configuration.cache.IndexStorage.LOCAL_HEAP;

import java.util.List;
import java.util.Map;

import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.model.Restaurant;
import org.infinispan.search.mapper.mapping.SearchMapping;
import org.infinispan.search.mapper.mapping.metamodel.IndexMetamodel;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "query.geo.GeoLocalQueryTest")
public class GeoLocalQueryTest extends SingleCacheManagerTest {

   private static final String ENTITY_NAME = Restaurant.class.getName();

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder config = new ConfigurationBuilder();
      config.statistics().enable();
      config.indexing().enable()
            .storage(LOCAL_HEAP)
            .addIndexedEntity(Restaurant.class);
      return TestCacheManagerFactory.createCacheManager(config);
   }

   @Test
   public void verifySpatialMapping() {
      SearchMapping searchMapping = TestingUtil.extractComponent(cache, SearchMapping.class);
      Map<String, IndexMetamodel> metamodel = searchMapping.metamodel();
      assertThat(metamodel).containsKeys(ENTITY_NAME);
      IndexMetamodel indexMetamodel = metamodel.get(ENTITY_NAME);
      assertThat(indexMetamodel.getValueFields().keySet())
            .containsExactlyInAnyOrder("name", "description", "address", "location", "score");
   }

   @Test
   public void indexingAndSearch() {
      // data taken from https://www.google.com/maps/
      cache.put("La Locanda di Pietro", new Restaurant("La Locanda di Pietro",
            "Roman-style pasta dishes & Lazio region wines at a cozy traditional trattoria with a shaded terrace.",
            "Via Sebastiano Veniero, 28/c, 00192 Roma RM", 41.907903484609356, 12.45540543756422, 4.6f));
      cache.put("Scialla The Original Street Food", new Restaurant("Scialla The Original Street Food",
            "Pastas & traditional pizza pies served in an unassuming eatery with vegetarian options.",
            "Vicolo del Farinone, 27, 00193 Roma RM", 41.90369455835456, 12.459566517195528, 4.7f));
      cache.put("Trattoria Pizzeria Gli Archi", new Restaurant("Trattoria Pizzeria Gli Archi",
            "Traditional trattoria with exposed brick walls, serving up antipasti, pizzas & pasta dishes.",
            "Via Sebastiano Veniero, 26, 00192 Roma RM", 41.907930453801285, 12.455204785977637, 4.0f));
      cache.put("Alla Bracioleria Gracchi Restaurant", new Restaurant("Alla Bracioleria Gracchi Restaurant",
            "", "Via dei Gracchi, 19, 00192 Roma RM", 41.907129402661795, 12.458927251586584, 4.7f ));
      cache.put("Magazzino Scipioni", new Restaurant("Magazzino Scipioni",
            "Contemporary venue with a focus on unique wines & seasonal Italian plates, plus a bottle shop.",
            "Via degli Scipioni, 30, 00192 Roma RM", 41.90817843995448, 12.457118458698043, 4.6f));
      cache.put("Dal Toscano Restaurant", new Restaurant("Dal Toscano Restaurant",
            "Rich pastas, signature steaks & classic Tuscan dishes, plus Chianti wines, at a venerable trattoria.",
            "Via Germanico, 58-60, 00192 Roma RM", 41.90785274056548, 12.45822050287784, 4.2f));
      cache.put("Il Ciociaro", new Restaurant("Il Ciociaro",
            "Long-running, old-school restaurant plating traditional staples, from carbonara to tiramisu.",
            "Via Barletta, 21, 00192 Roma RM", 41.91038657525997, 12.458851939120656, 4.2f));

      String ickle1 = String.format("from %s r " +
            "where r.location within circle(41.90847031512531, 12.455633288333539, :distance) ", ENTITY_NAME);
      Query<Restaurant> query = cache.query(ickle1);
      query.setParameter("distance", 4_339_500);
      List<Restaurant> list = query.list();
      assertThat(list).extracting(Restaurant::name)
            .containsExactlyInAnyOrder("Scialla The Original Street Food", "Alla Bracioleria Gracchi Restaurant",
                  "Dal Toscano Restaurant");
   }
}
