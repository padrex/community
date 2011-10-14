package org.neo4j.kernel.impl.storemigration.legacystore;

import java.io.IOException;

public interface ReaderFactory
{
    NodeStoreReader newLegacyNodeStoreReader( String fileName ) throws IOException;
}
