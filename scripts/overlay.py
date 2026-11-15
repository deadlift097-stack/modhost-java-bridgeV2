#!/usr/bin/env python3
"""Overlay the Java bridge onto a checkout of the LeviLauncher mod template.
usage: overlay.py <bridge-repo> <template-checkout>"""
import pathlib
import shutil
import sys

bridge, tpl = (pathlib.Path(a) for a in sys.argv[1:3])


def patch(path, edits, prepend=""):
    """edits: list of (anchor, replacement). Each anchor must occur exactly once."""
    s = path.read_text()
    for old, new in edits:
        n = s.count(old)
        if n != 1:
            sys.exit(f"{path.name}: anchor found {n} times (need 1): {old!r}")
        s = s.replace(old, new)
    path.write_text(prepend + s)


# 1) files
shutil.copytree(bridge / "src" / "bridge", tpl / "src" / "bridge", dirs_exist_ok=True)
shutil.copytree(bridge / "cmake", tpl / "cmake", dirs_exist_ok=True)
res = tpl / "resources"
res.mkdir(exist_ok=True)
dex = bridge / "build" / "java" / "modhost-api.dex.jar"
if not dex.exists():
    sys.exit(f"missing {dex} (java job artifact)")
shutil.copy(dex, res / dex.name)

# 2) CMake: compile the bridge in, and ship the dex jar inside the .levipack
cm = tpl / "CMakeLists.txt"
patch(cm, [(
    "${LEVI_PACKAGE_CONFIG_COMMAND}",
    '${LEVI_PACKAGE_CONFIG_COMMAND}\n'
    '  COMMAND "${CMAKE_COMMAND}" -E make_directory "${LEVI_PACKAGE_MOD_DIR}/resources"\n'
    '  COMMAND "${CMAKE_COMMAND}" -E copy "${CMAKE_CURRENT_SOURCE_DIR}/resources/modhost-api.dex.jar" "${LEVI_PACKAGE_MOD_DIR}/resources/modhost-api.dex.jar"\n'
    '  COMMAND "${CMAKE_COMMAND}" -E copy "${CMAKE_CURRENT_SOURCE_DIR}/resources/modhost-api.dex.jar" "${LEVI_PACKAGE_MOD_DIR}/modhost-api.dex.jar"',
)])
cm.write_text(cm.read_text() + "\ninclude(cmake/modhost.cmake)\nmodhost_attach(${MOD_LIBRARY_NAME})\n")

# 3) C++: drive the bridge from the mod lifecycle (anchors taken from the template's MyMod.cpp)
LOAD = '''mConfig = mConfigFile->value();

  {
    namespace fs = std::filesystem;
    fs::path dex;
    const std::vector<fs::path> candidates = {
        fs::path(self.getResourceDir()) / "modhost-api.dex.jar",
        fs::path(self.getModDir()) / "resources" / "modhost-api.dex.jar",
        fs::path(self.getModDir()) / "modhost-api.dex.jar",
    };
    for (const auto &c : candidates) {
      if (fs::exists(c, ec)) {
        dex = c;
        break;
      }
    }
    fs::create_directories(fs::path(self.getDataDir()) / "mods", ec);
    if (dex.empty()) {
      self.getLogger().warn("modhost-api.dex.jar not found; Java mods disabled");
    } else {
      modhost::bridge().init(dex, fs::path(self.getDataDir()) / "mods",
                             fs::path(self.getDataDir()) / "data", nullptr);
    }
  }'''

patch(tpl / "src" / "mod" / "MyMod.cpp", [
    ("#include <filesystem>", "#include <filesystem>\n#include <vector>\n\n#include \"bridge/JavaBridge.h\""),
    ("mConfig = mConfigFile->value();", LOAD),
    ('self.getLogger().debug("Enabling...");', 'self.getLogger().debug("Enabling...");\n  modhost::bridge().enable();'),
    ('getSelf().getLogger().debug("Disabling...");', 'getSelf().getLogger().debug("Disabling...");\n  modhost::bridge().disable();'),
    ("mConfigFile.reset();", "mConfigFile.reset();\n  modhost::bridge().shutdown();"),
])

# 4) rename the package: the template ships as "Clange Me Mod"; this project is modhost-java-bridge
NAME = "modhost-java-bridge"
# CMake cache defaults drive the manifest name, the .levipack folder and the .so name
cmk = tpl / "CMakeLists.txt"
t = cmk.read_text()
import re as _re
for var, val in (("MOD_ID", "modhost_java_bridge"), ("MOD_NAME", NAME), ("MOD_AUTHOR", "modhost"),
                 ("MOD_LIBRARY_NAME", "modhost_java_bridge")):
    t, n = _re.subn(r'(set\(%s ")[^"]*(")' % var, r"\g<1>%s\g<2>" % val, t)
    if n != 1:
        sys.exit(f"CMakeLists.txt: could not set {var}")
cmk.write_text(t)
print("renamed package to", NAME)
print("overlay ok")
