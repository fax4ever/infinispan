package org.infinispan.test.integration.thirdparty.remote;

import static org.infinispan.test.integration.GenericDeploymentHelper.addLibrary;
import static org.infinispan.test.integration.thirdparty.DeploymentHelper.createDeployment;

import java.io.IOException;

import org.infinispan.test.integration.data.Book;
import org.infinispan.test.integration.data.Person;
import org.infinispan.test.integration.remote.AbstractHotRodQueryIT;
import org.infinispan.test.integration.remote.proto.BookQuerySchema;
import org.infinispan.test.integration.remote.proto.PersonSchema;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

/**
 * Test remote query.
 *
 * @author anistor@redhat.com
 * @since 8.2
 */
@RunWith(Arquillian.class)
public class HotRodQueryIT extends AbstractHotRodQueryIT {

   @Deployment
   @TargetsContainer("server-1")
   public static Archive<?> deployment() throws IOException {
      WebArchive war = createDeployment();
      war.addClass(AbstractHotRodQueryIT.class);
      war.addClass(Person.class);
      war.addPackage(PersonSchema.class.getPackage().getName());
      war.addClass(Book.class);
      war.addPackage(BookQuerySchema.class.getPackage().getName());
      war.addAsResource("proto/book.proto");
      war.addAsResource("proto/person.proto");
      addLibrary(war, "org.infinispan:infinispan-query-dsl");
      addLibrary(war, "org.infinispan:infinispan-remote-query-client");
      addLibrary(war, "org.infinispan:infinispan-client-hotrod");
      return war;
   }
}
