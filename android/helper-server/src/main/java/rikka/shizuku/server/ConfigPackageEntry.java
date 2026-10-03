// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: server-shared/src/main/java/rikka/shizuku/server/ConfigPackageEntry.java
package rikka.shizuku.server;

import rikka.shizuku.server.util.Logger;

public abstract class ConfigPackageEntry {

    protected static final Logger LOGGER = new Logger("ConfigPackageEntry");

    public ConfigPackageEntry() {
    }

    public abstract boolean isAllowed();

    public abstract boolean isDenied();
}
