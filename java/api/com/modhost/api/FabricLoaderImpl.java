package com.modhost.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

    private FabricLoaderImpl() {}

    public boolean isModLoaded(String id) {
        for (Bootstrap.Entry e : Bootstrap.list()) {
            if (e.id.equals(id) && e.status == Bootstrap.OK) return true;
        }
        return false;
    }

    public Collection<ModContainer> getAllMods() {
        List<ModContainer> out = new ArrayList<ModContainer>();
        for (Bootstrap.Entry e : Bootstrap.list()) if (e.status == Bootstrap.OK) out.add(new Container(e));
        return out;
    }

    public Optional<ModContainer> getModContainer(String id) {
        for (Bootstrap.Entry e : Bootstrap.list()) {
            if (e.id.equals(id) && e.status == Bootstrap.OK) return Optional.<ModContainer>of(new Container(e));
        }
        return Optional.empty();
    }

    public Path getConfigDir() {
        File d = new File(Bootstrap.dataRoot(), "config");
        d.mkdirs();
        return d.toPath();
    }

    public Path getGameDir() { return new File(Bootstrap.dataRoot()).toPath(); }
    public boolean isDevelopmentEnvironment() { return false; }
    public EnvType getEnvironmentType() { return EnvType.CLIENT; }
    public <T> List<T> getEntrypoints(String key, Class<T> type) { return Collections.<T>emptyList(); }
    public String[] getLaunchArguments(boolean sanitize) { return new String[0]; }

    private static final class Container implements ModContainer, ModMetadata {
        private final Bootstrap.Entry e;
        Container(Bootstrap.Entry e) { this.e = e; }

        public ModMetadata getMetadata() { return this; }
        public Path getRootPath() { return new File(e.ctx.dataDir).toPath(); }
        public List<Path> getRootPaths() { return Collections.singletonList(getRootPath()); }
        public Optional<Path> findPath(String file) {
            File f = new File(e.ctx.dataDir, file);
            return f.exists() ? Optional.of(f.toPath()) : Optional.<Path>empty();
        }
        public Optional<ModContainer> getContainingMod() { return Optional.empty(); }
        public Collection<ModContainer> getContainedMods() { return Collections.<ModContainer>emptyList(); }

        public String getType() { return "fabric"; }
        public String getId() { return e.id; }
        public Collection<String> getProvides() { return Collections.<String>emptyList(); }
        public Version getVersion() { return Version.parse(e.version); }
        public String getName() { return e.name; }
        public String getDescription() { return ""; }
        public Collection<Person> getAuthors() { return Collections.<Person>emptyList(); }
        public Collection<Person> getContributors() { return Collections.<Person>emptyList(); }
        public Collection<String> getLicense() { return Collections.<String>emptyList(); }
        public boolean containsCustomValue(String key) { return false; }
    }
}
