#pragma once
#include <cstdint>
#include <filesystem>
#include <string>

namespace modhost {

// Native hooks into Bedrock. Each hook is one text file in <dataDir>/signatures/<event>.txt holding a byte
// pattern ("48 8B ?? 10 ..." where ?? is a wildcard) for the function in libminecraftpe.so. The scanner finds
// the function; a detour made with the template's Hook API then calls bridge().fire(<event>, data).
class Hooks {
public:
    // Scans libminecraftpe.so for every signature file. Returns how many were located.
    int resolveAll(const std::filesystem::path& signatureDir);
    // Installs detours for every located hook (needs the preloader Hook API). Returns how many were installed.
    int install();
    void uninstall();
    // Address of a located function, or 0.
    uintptr_t address(const std::string& event) const;
    // "\nname=ok" / "\nname=missing" per signature, for Java (Hooks.report()).
    std::string status() const;
};

Hooks& hooks();

} // namespace modhost
