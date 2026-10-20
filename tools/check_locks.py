#!/usr/bin/env python3
"""Hash des modules. Un id listé dans locks.json ne doit plus changer.

--update recalcule les hash des modules ouverts.
Un module listé dans "locked" n'est pas recalculé, sauf avec --force.
"""
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCKS = Path(__file__).with_name("locks.json")

# Un élément est un dossier, un fichier, ou une fonction (début inclus, fin exclue).
MODULES = {
    "auth": ["core/plex/src/main/kotlin/tv/plexiplay/plex/auth"],
    "library": ["core/plex/src/main/kotlin/tv/plexiplay/plex/PlexClient.kt"],
    "preroll": ["core/plex/src/main/kotlin/tv/plexiplay/plex/preroll"],
    "player": ["core/player/src/main/kotlin/tv/plexiplay/player"],
    "settings": ["app/src/main/kotlin/tv/plexiplay/ui/settings"],
    "shell": ["app/src/main/kotlin/tv/plexiplay/ui/shell"],
    "playback": [
        "core/plex/src/main/kotlin/tv/plexiplay/plex/preroll",
        ("core/plex/src/main/kotlin/tv/plexiplay/plex/PlexClient.kt", "fun cinemaQueue(", "fun timeline("),
        ("app/src/main/kotlin/tv/plexiplay/MainActivity.kt", "private fun buildQueue(", "private fun trailerQueue("),
        ("app/src/main/kotlin/tv/plexiplay/MainActivity.kt", "private fun trailerQueue(", "private fun runOnUi("),
    ],
    "players": [
        "app/src/main/kotlin/tv/plexiplay/ui/player/PlayerScreen.kt",
        "app/src/main/kotlin/tv/plexiplay/ui/player/MpvPlayerScreen.kt",
        "core/player/src/main/kotlin/tv/plexiplay/player",
        "app/src/main/kotlin/tv/plexiplay/player/DeviceCodecs.kt",
    ],
}


def piece(spec) -> bytes:
    if isinstance(spec, str):
        path = ROOT / spec
        h = hashlib.sha256()
        files = [path] if path.is_file() else sorted(p for p in path.rglob("*") if p.is_file())
        for file in files:
            h.update(str(file.relative_to(ROOT)).encode())
            h.update(b"\0")
            h.update(file.read_bytes())
        return h.digest()
    path, start, end = spec
    text = (ROOT / path).read_text()
    begin = text.index(start)
    finish = text.index(end, begin + len(start))
    h = hashlib.sha256()
    h.update(path.encode())
    h.update(b"\0")
    h.update(start.encode())
    h.update(b"\0")
    h.update(text[begin:finish].encode())
    return h.digest()


def digest(specs) -> str:
    h = hashlib.sha256()
    for spec in specs:
        h.update(piece(spec))
    return h.hexdigest()


def main() -> int:
    update = "--update" in sys.argv
    force = "--force" in sys.argv
    data = json.loads(LOCKS.read_text())
    hashes = data.get("hashes", {})
    locked = set(data.get("locked", []))
    current = {name: digest(specs) for name, specs in MODULES.items()}
    if update:
        refused = [] if force else [name for name in sorted(locked) if hashes.get(name) != current.get(name)]
        if refused:
            print("refus: modules verrouillés modifiés:", ", ".join(refused))
            print("retirez l'id de locks.json, ou passez --force en connaissance de cause")
            return 1
        data["hashes"] = current
        LOCKS.write_text(json.dumps(data, indent=2) + "\n")
        print("hashes updated")
        return 0
    missing = [name for name in sorted(locked) if name not in current]
    failed = [name for name in sorted(locked) if hashes.get(name) != current.get(name)]
    if missing or failed:
        if missing:
            print("modules verrouillés inconnus:", ", ".join(missing))
        if failed:
            print("modules verrouillés modifiés:", ", ".join(failed))
        return 1
    print("locks ok:", ", ".join(sorted(locked)) or "aucun")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
