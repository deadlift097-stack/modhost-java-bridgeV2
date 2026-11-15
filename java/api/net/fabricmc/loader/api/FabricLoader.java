package net.fabricmc.loader.api;

import com.modhost.api.FabricLoaderImpl;
import net.fabricmc.api.EnvType;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FabricLoader {
    static FabricLoader getInstance() { return FabricLoaderImpl.INSTANCE; }

    boolean isModLoaded(String id);
    Collection<ModContainer> getAllMods();
    Optional<ModContainer> getModContainer(String id);
    Path getConfigDir();
    Path getGameDir();
    boolean isDevelopmentEnvironment();
    EnvType getEnvironmentType();
    <T> List<T> getEntrypoints(String key, Class<T> type);
    String[] getLaunchArguments(boolean sanitize);
}
