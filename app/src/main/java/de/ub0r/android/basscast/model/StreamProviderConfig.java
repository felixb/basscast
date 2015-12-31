package de.ub0r.android.basscast.model;

import ckm.simple.sql_provider.UpgradeScript;
import ckm.simple.sql_provider.annotation.ProviderConfig;
import ckm.simple.sql_provider.annotation.SimpleSQLConfig;

/**
 * @author flx
 */
@SimpleSQLConfig(
        name = "StreamProvider",
        authority = "de.ub0r.android.basscast.stream_provider.authority",
        database = "streams.db",
        version = 1)
public class StreamProviderConfig implements ProviderConfig {

    @Override
    public UpgradeScript[] getUpdateScripts() {
        return new UpgradeScript[0];
    }
}
