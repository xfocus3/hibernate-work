package org.hibernate.orm.test.boot.models.xml.bad;

import org.hibernate.MappingException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.models.MemberResolutionException;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
public class BadMappingTests {
	@Test
	@ServiceRegistry
	void testBadEmbeddedName(ServiceRegistryScope registryScope) {
		final MetadataSources metadataSources = new MetadataSources( registryScope.getRegistry() )
				.addResource( "mappings/models/bad/bad-embedded.xml" );
		try {
			metadataSources.buildMetadata();
			fail( "Expecting failure" );
		}
		catch (MemberResolutionException expected){
			// expected outcome
		}

	}

	@Test
	@ServiceRegistry
	void testBadEmbeddedIdName(ServiceRegistryScope registryScope) {
		final MetadataSources metadataSources = new MetadataSources( registryScope.getRegistry() )
				.addResource( "mappings/models/bad/bad-embedded-id.xml" );
		try {
			metadataSources.buildMetadata();
			fail( "Expecting failure" );
		}
		catch (MemberResolutionException expected){
			// expected outcome
		}
	}

	@Test
	@ServiceRegistry
	void testDuplicateEntityClass(ServiceRegistryScope registryScope) {
		final MetadataSources metadataSources = new MetadataSources( registryScope.getRegistry() )
				.addResource( "mappings/models/bad/duplicate-entity-class.xml" );
		final MappingException exception = assertThrows( MappingException.class, metadataSources::buildMetadata );
		assertTrue( exception.getMessage().contains( "org.hibernate.orm.test.boot.models.xml.SimpleEntity" ) );
	}

	@Test
	@ServiceRegistry
	void testDuplicateEntityClassAcrossDocuments(ServiceRegistryScope registryScope) {
		final MetadataSources metadataSources = new MetadataSources( registryScope.getRegistry() )
				.addResource( "mappings/models/bad/duplicate-entity-first.xml" )
				.addResource( "mappings/models/bad/duplicate-entity-second.xml" );
		final MappingException exception = assertThrows( MappingException.class, metadataSources::buildMetadata );
		assertTrue( exception.getMessage().contains( "org.hibernate.orm.test.boot.models.xml.SimpleEntity" ) );
	}
}
