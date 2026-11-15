# Native hooks: what was found, how, and what is still open

Analysed: the `libminecraftpe.so` you supplied (arm64-v8a, stripped, ~342 MB, the build whose strings mention
format version 1.20.30+ and the template's `1.26.*` filter). Patterns are for **that build**; on another build a
missing pattern just shows `name=missing` and the hook is skipped.

## Method (`tools/find_hooks.py`)
The library exports no game symbols, but it embeds readable function names (profiler / assert strings). For each
name the script finds the code that loads the string (ADRP+ADD), takes the enclosing function from
`.eh_frame_hdr`, and writes the first 12 instructions as a pattern (calls, branches and ADRP become `??`). A pattern is
kept only if it matches exactly **one** place in `.text`. Run it again on a new build:

    python3 tools/find_hooks.py libminecraftpe.so signatures/<version>   # then copy to <dataDir>/signatures or Signatures.inc

## Result
| event | function | status |
|---|---|---|
| `world.join` | `ClientInstance::_joinWorldInProgressSubclient` | located, unique, detour installed |
| `world.leave` | `ClientInstance::requestLeaveGame(bool,bool)` | located, unique, detour installed |
| `player.login` | `ServerNetworkHandler::_onSubClientAuthenticated` | located, unique, detour installed |
| `player.tick`, `level.tick`, `game.update` | `Player::tickWorld`, `Level::tick`, `MinecraftGame::_update` | string found, but no direct code reference (reached through a data table): not located |
| `player.attack`, `chat.send`, `block.break`, `block.place` | - | the binary has no readable name for these; needs vtable/packet analysis in a disassembler |

## Not verified on a device
* Located and unique in the file, and the start of each function is a real prologue. Whether
  `_joinWorldInProgressSubclient` runs for normal single-player joins (its name suggests joining a world already running)
  must be checked in game: the log prints `hook.first` the first time each hook runs.
* The detours pass the arguments through unchanged; argument layout was taken from the demangled names, not tested.
