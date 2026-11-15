#!/usr/bin/env python3
"""Turn ordinary mod jars into ModHost-loadable ones.
usage: convert_mod.py <d8> <android.jar> <api.jar> <in-dir> <out-dir>
For every <in-dir>/*.jar: dex it with d8 and write <name>.json (id, name, version, entrypoints).
Reads fabric.mod.json (Fabric) and META-INF/neoforge.mods.toml + @Mod classes (NeoForge)."""
import json
import pathlib
import re
import subprocess
import sys
import zipfile

d8, android_jar, api_jar, src, out = sys.argv[1:6]
src, out = pathlib.Path(src), pathlib.Path(out)
out.mkdir(parents=True, exist_ok=True)
report = []

NEO_MOD = b"Lnet/neoforged/fml/common/Mod;"


def fabric_entrypoints(meta):
    names = []
    for key in ("main", "client"):
        for item in meta.get("entrypoints", {}).get(key, []):
            v = item if isinstance(item, str) else item.get("value", "")
            if v and "::" not in v:
                names.append(v)
    return names


def run_d8(jar, dest, no_desugar):
    cmd = [d8, "--release", "--min-api", "26", "--lib", android_jar, "--classpath", api_jar]
    gson = pathlib.Path(api_jar).with_name("gson.jar")
    if gson.exists():
        cmd += ["--classpath", str(gson)]
    cmd += ["--output", str(dest), str(jar)]
    if no_desugar:
        cmd.insert(1, "--no-desugaring")
    return subprocess.run(cmd, capture_output=True, text=True)


jars = sorted(src.glob("*.jar"))
if not jars:
    report.append("no jars found in mods-in/")

for jar in jars:
    name = jar.stem
    with zipfile.ZipFile(jar) as z:
        files = z.namelist()
        uses_mc = False
        neo_classes = []
        for f in files:
            if not f.endswith(".class"):
                continue
            data = z.read(f)
            if b"net/minecraft/" in data:
                uses_mc = True
            if NEO_MOD in data:
                neo_classes.append(f[:-6].replace("/", "."))
        fabric = json.loads(z.read("fabric.mod.json")) if "fabric.mod.json" in files else None
        toml = z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace") if "META-INF/neoforge.mods.toml" in files else ""

    mod_id, mod_name, version, eps = name, name, "", []
    if fabric:
        mod_id = fabric.get("id", name)
        mod_name = fabric.get("name", mod_id)
        version = str(fabric.get("version", ""))
        eps = fabric_entrypoints(fabric)
    elif toml:
        m = re.search(r'modId\s*=\s*"([^"]+)"', toml)
        mod_id = m.group(1) if m else name
        m = re.search(r'displayName\s*=\s*"([^"]+)"', toml)
        mod_name = m.group(1) if m else mod_id
        m = re.search(r'^\s*version\s*=\s*"([^"$]+)"', toml, re.M)
        version = m.group(1) if m else ""

    if not eps and not neo_classes:
        report.append(f"{name}: SKIPPED, no usable entrypoints "
                      f"(needs fabric.mod.json entrypoints, or a @Mod class for NeoForge). "
                      f"Method references like Class::method are not supported.")
        continue

    dest = out / f"{name}.jar"
    r = run_d8(jar, dest, no_desugar=False)
    if r.returncode != 0:  # missing Java Edition classes can break desugaring; retry without it
        r = run_d8(jar, dest, no_desugar=True)
    if r.returncode != 0:
        report.append(f"{name}: d8 FAILED\n{r.stderr[-1500:]}")
        continue

    meta = {"id": mod_id, "name": mod_name, "version": version}
    if eps:
        meta["entrypoints"] = eps
    if neo_classes:
        meta["neoforge"] = neo_classes
    (out / f"{name}.json").write_text(json.dumps(meta, indent=2))

    kind = "Fabric" if eps else "NeoForge"
    if uses_mc:
        report.append(f"{name}: converted ({kind}), but it uses Minecraft Java classes (net.minecraft.*). "
                      "It will show in the Mods menu as 'Java Edition only' and stay off; it cannot work on Bedrock unchanged.")
    else:
        report.append(f"{name}: converted OK ({kind}), entrypoints: {', '.join(eps + neo_classes)}")

(out / "REPORT.txt").write_text("\n".join(report) + "\n")
print("\n".join(report))
