package org.neo4j.kernel.impl.storemigration.legacystore;

import java.io.IOException;

public class LegacyReaderFactory implements ReaderFactory
{
    @Override
    public NodeStoreReader newLegacyNodeStoreReader( String fileName ) throws IOException
    {
        return new LegacyNodeStoreReader( fileName );
    }
}
