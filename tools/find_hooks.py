#!/usr/bin/env python3
"""Find Bedrock functions in libminecraftpe.so (arm64) and write <event>.txt byte signatures.
usage: find_hooks.py <libminecraftpe.so> <out-dir>
Method: Bedrock embeds readable function names (profiler/assert strings). For each anchor string we find the
code that loads it (ADRP+ADD), take the function that contains that code (from .eh_frame_hdr), and turn the
function's first bytes into a pattern (pc-relative instructions become wildcards). A pattern is only written
when it matches exactly one place in .text."""
import mmap, re, struct, sys, pathlib
import numpy as np

ANCHORS = {  # event -> exact embedded string
    "world.join":   "void ClientInstance::_joinWorldInProgressSubclient(brstd::optional<ConnectionContextInfo>)",
    "world.leave":  "virtual void ClientInstance::requestLeaveGame(bool, bool)",
    "player.tick":  "virtual void Player::tickWorld(const Tick &)",
    "level.tick":   "virtual void Level::tick()",
    "game.update":  "void MinecraftGame::_update()",
    "player.login": "void ServerNetworkHandler::_onSubClientAuthenticated(const NetworkIdentifier &, std::shared_ptr<SubClientLoginPacket>, const PlayerAuthenticationInfo &)",
}

path, out = sys.argv[1], pathlib.Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
f = open(path, "rb"); mm = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)

# --- sections (via readelf-like parse)
shoff, = struct.unpack_from("<Q", mm, 0x28); shentsize, shnum, shstrndx = struct.unpack_from("<HHH", mm, 0x3A)
secs = {}
raw = [struct.unpack_from("<IIQQQQIIQQ", mm, shoff + i * shentsize) for i in range(shnum)]
stro = raw[shstrndx][4]
for r in raw:
    n = mm[stro + r[0]: mm.find(b"\0", stro + r[0])].decode()
    secs[n] = (r[3], r[4], r[5])  # addr, offset, size
t_addr, t_off, t_size = secs[".text"]
hdr_addr, hdr_off, hdr_size = secs[".eh_frame_hdr"]

# --- function table from .eh_frame_hdr (datarel sdata4)
ver, ptr_enc, cnt_enc, tab_enc = mm[hdr_off:hdr_off + 4]
assert tab_enc == 0x3b, hex(tab_enc)
cnt, = struct.unpack_from("<I", mm, hdr_off + 8)
tab = np.frombuffer(mm, dtype="<i4", count=cnt * 2, offset=hdr_off + 12).reshape(-1, 2)
starts = (tab[:, 0].astype(np.int64) + hdr_addr)
print("functions in eh_frame_hdr:", len(starts))

# --- ADRP+ADD xref scan over .text
code = np.frombuffer(mm, dtype="<u4", count=t_size // 4, offset=t_off)
isadrp = (code & 0x9F000000) == 0x90000000
idx = np.nonzero(isadrp[:-1])[0]
adrp = code[idx]
rd = adrp & 31
immlo = (adrp >> 29) & 3; immhi = (adrp >> 5) & 0x7FFFF
imm = ((immhi << 2) | immlo).astype(np.int64)
imm = np.where(imm & (1 << 20), imm - (1 << 21), imm)
pc = (t_addr + idx.astype(np.int64) * 4)
page = (pc & ~0xFFF) + (imm << 12)
nxt = code[idx + 1]
isadd = ((nxt & 0xFFC00000) == 0x91000000) & (((nxt >> 5) & 31) == rd)
off12 = ((nxt >> 10) & 0xFFF).astype(np.int64)
target = page + off12
good = isadd
xr_t = target[good]; xr_pc = pc[good]

def fn_start(addr):
    i = np.searchsorted(starts, addr, side="right") - 1
    return int(starts[i]) if i >= 0 else 0

# --- instruction-aware pattern
def wild(w):
    op = w & 0xFC000000
    return (op in (0x14000000, 0x94000000)            # B, BL
            or (w & 0x9F000000) in (0x90000000, 0x10000000)  # ADRP, ADR
            or (w & 0x7E000000) == 0x34000000         # CBZ/CBNZ
            or (w & 0x7E000000) == 0x36000000         # TBZ/TBNZ
            or (w & 0xFF000010) == 0x54000000         # B.cond
            or (w & 0x3B000000) == 0x18000000)        # LDR literal

text = mm[t_off:t_off + t_size]
def pattern(addr, n):
    o = addr - t_addr
    ws = struct.unpack_from("<%dI" % n, text, o)
    parts, rx = [], []
    for w in ws:
        if wild(w):
            parts += ["??"] * 4; rx.append(b".{4}")
        else:
            b = struct.pack("<I", w); parts += ["%02X" % x for x in b]; rx.append(re.escape(b))
    return " ".join(parts), b"".join(rx)

results = {}
for ev, s in ANCHORS.items():
    needle = b"\0" + s.encode() + b"\0"
    p = mm.find(needle)
    if p < 0: results[ev] = "string not found"; continue
    sva = p + 1  # rodata vaddr == file offset
    hits = xr_pc[xr_t == sva]
    fns = sorted({fn_start(int(h)) for h in hits})
    if len(fns) != 1:
        results[ev] = f"{len(hits)} xrefs in {len(fns)} functions (need exactly 1)"; continue
    fa = fns[0]
    for n in (8, 12, 16, 24, 32):
        pat, rx = pattern(fa, n)
        if len(list(re.finditer(rx, text, re.DOTALL))) == 1:
            (out / f"{ev}.txt").write_text(pat + "\n")
            results[ev] = f"ok  fn=0x{fa:x} len={n} insns"; break
    else:
        results[ev] = f"fn=0x{fa:x} but no unique pattern up to 32 insns"
for k, v in results.items(): print(f"{k:14} {v}")
