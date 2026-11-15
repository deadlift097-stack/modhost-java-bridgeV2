#include "Hooks.h"
#include "JavaBridge.h"

#include <link.h>

#include <cctype>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <vector>

namespace modhost {
namespace {

struct Range { uintptr_t begin = 0, end = 0; };

std::map<std::string, uintptr_t> gFound; // event -> address (0 = not found)

// executable segments of libminecraftpe.so
std::vector<Range> gameRanges() {
    std::vector<Range> out;
    dl_iterate_phdr([](dl_phdr_info* info, size_t, void* data) -> int {
        if (!info->dlpi_name || !std::strstr(info->dlpi_name, "libminecraftpe.so")) return 0;
        auto* v = static_cast<std::vector<Range>*>(data);
        for (int i = 0; i < info->dlpi_phnum; ++i) {
            const ElfW(Phdr)& ph = info->dlpi_phdr[i];
            if (ph.p_type == PT_LOAD && (ph.p_flags & PF_X))
                v->push_back({info->dlpi_addr + ph.p_vaddr, info->dlpi_addr + ph.p_vaddr + ph.p_memsz});
        }
        return 1;
    }, &out);
    return out;
}

// "48 8B ?? 10" -> bytes + mask (false = wildcard)
bool parse(const std::string& text, std::vector<uint8_t>& bytes, std::vector<bool>& mask) {
    std::istringstream in(text);
    std::string tok;
    while (in >> tok) {
        if (tok == "?" || tok == "??") { bytes.push_back(0); mask.push_back(false); continue; }
        if (tok.size() != 2 || !std::isxdigit((unsigned char)tok[0]) || !std::isxdigit((unsigned char)tok[1])) return false;
        bytes.push_back(static_cast<uint8_t>(std::stoi(tok, nullptr, 16)));
        mask.push_back(true);
    }
    return !bytes.empty();
}

uintptr_t scan(const std::vector<Range>& ranges, const std::vector<uint8_t>& pat, const std::vector<bool>& mask) {
    for (const Range& r : ranges) {
        if (r.end - r.begin < pat.size()) continue;
        const uint8_t* base = reinterpret_cast<const uint8_t*>(r.begin);
        const size_t last = (r.end - r.begin) - pat.size();
        for (size_t i = 0; i <= last; ++i) {
            size_t j = 0;
            while (j < pat.size() && (!mask[j] || base[i + j] == pat[j])) ++j;
            if (j == pat.size()) return r.begin + i;
        }
    }
    return 0;
}

const std::pair<const char*, const char*> kBuiltIn[] = {
#include "Signatures.inc"
};

} // namespace

int Hooks::resolveAll(const std::filesystem::path& dir) {
    gFound.clear();
    std::error_code ec;
    std::vector<Range> ranges = gameRanges();
    std::map<std::string, std::string> sigs;
    for (auto& kv : kBuiltIn) sigs[kv.first] = kv.second;           // shipped patterns
    if (std::filesystem::exists(dir, ec))                           // files in <dataDir>/signatures override them
        for (auto& e : std::filesystem::directory_iterator(dir, ec)) {
            if (e.path().extension() != ".txt") continue;
            std::ifstream f(e.path());
            std::stringstream ss;
            ss << f.rdbuf();
            sigs[e.path().stem().string()] = ss.str();
        }
    int ok = 0;
    for (auto& kv : sigs) {
        std::vector<uint8_t> bytes;
        std::vector<bool> mask;
        uintptr_t addr = 0;
        if (!ranges.empty() && parse(kv.second, bytes, mask)) addr = scan(ranges, bytes, mask);
        gFound[kv.first] = addr;
        if (addr) ++ok;
    }
    return ok;
}

uintptr_t Hooks::address(const std::string& event) const {
    auto it = gFound.find(event);
    return it == gFound.end() ? 0 : it->second;
}

std::string Hooks::status() const {
    std::string s;
    for (auto& kv : gFound) s += "\n" + kv.first + (kv.second ? "=ok" : "=missing");
    if (gFound.empty()) s += "\n(no signatures)";
    return s;
}

Hooks& hooks() { static Hooks h; return h; }

} // namespace modhost

// ---------------------------------------------------------------------------------------------
// Detours. Installed with the preloader SDK (pl::memory::hook), so this part only builds inside the template.
#if __has_include(<pl/memory/Hook.hpp>)
#include <pl/memory/Hook.hpp>

namespace modhost {
namespace {

std::vector<std::pair<void*, void*>> gInstalled; // target, detour
bool gSeen[8];

void seen(int i, const char* name) {
    if (gSeen[i]) return;
    gSeen[i] = true;
    bridge().fire("hook.first", name); // lets mods and the log show that a hook really ran
}

// void ClientInstance::_joinWorldInProgressSubclient(optional<ConnectionContextInfo>)   (this, optional by pointer)
void (*oJoin)(void*, void*);
void dJoin(void* self, void* a1) { oJoin(self, a1); seen(0, "world.join"); bridge().fire("world.join", ""); }

// void ClientInstance::requestLeaveGame(bool, bool)
void (*oLeave)(void*, bool, bool);
void dLeave(void* self, bool a, bool b) { seen(1, "world.leave"); bridge().fire("world.leave", ""); oLeave(self, a, b); }

// void ServerNetworkHandler::_onSubClientAuthenticated(const NetworkIdentifier&, shared_ptr<SubClientLoginPacket>, const PlayerAuthenticationInfo&)
void (*oLogin)(void*, void*, void*, void*);
void dLogin(void* self, void* a1, void* a2, void* a3) { oLogin(self, a1, a2, a3); seen(2, "player.login"); bridge().fire("player.login", ""); }

bool put(const char* name, void* detour, void** orig) {
    uintptr_t a = hooks().address(name);
    if (!a) return false;
    if (pl::memory::hook(reinterpret_cast<void*>(a), detour, orig) != 0) return false;
    gInstalled.emplace_back(reinterpret_cast<void*>(a), detour);
    return true;
}

} // namespace

int Hooks::install() {
    if (!gInstalled.empty()) return static_cast<int>(gInstalled.size());
    put("world.join", reinterpret_cast<void*>(&dJoin), reinterpret_cast<void**>(&oJoin));
    put("world.leave", reinterpret_cast<void*>(&dLeave), reinterpret_cast<void**>(&oLeave));
    put("player.login", reinterpret_cast<void*>(&dLogin), reinterpret_cast<void**>(&oLogin));
    return static_cast<int>(gInstalled.size());
}

void Hooks::uninstall() {
    for (auto& p : gInstalled) pl::memory::unhook(p.first, p.second);
    gInstalled.clear();
}

} // namespace modhost
#else
namespace modhost {
int Hooks::install() { return 0; }
void Hooks::uninstall() {}
} // namespace modhost
#endif
