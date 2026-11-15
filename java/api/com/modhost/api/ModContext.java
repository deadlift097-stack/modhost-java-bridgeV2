package com.modhost.api;

import java.io.File;

public final class ModContext {
    public final String id;
    public final String dataDir;
    public final ModLogger logger;
    public final ModConfig config;

    public ModContext(String id, String dataDir) {
        this.id = id;
        this.dataDir = dataDir;
        this.logger = new ModLogger(id);
        this.config = new ModConfig(new File(dataDir));
    }
}
