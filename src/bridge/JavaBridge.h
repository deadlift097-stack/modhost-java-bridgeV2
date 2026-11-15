#pragma once
#include <filesystem>
#include <string>

namespace modhost {

using LogFn = void (*)(int level, const std::string& message); // 0 debug .. 3 error

class JavaBridge {
public:
    // apiDex: modhost-api.dex.jar   modsDir: *.jar + *.json (dexed with d8)   dataRoot: per-mod data root
    bool init(const std::filesystem::path& apiDex,
              const std::filesystem::path& modsDir,
              const std::filesystem::path& dataRoot,
              LogFn log);
    void enable();
    void disable();
    void tick();      // optional: native tick (Java already ticks 20/s on its own)
    void fire(const std::string& name, const std::string& data); // raise a game event in Java
    void shutdown();
    bool ready() const;
};

JavaBridge& bridge();

} // namespace modhost
