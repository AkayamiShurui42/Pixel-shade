#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SKILL_NAME="pixel-shade"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
installed=0

log() { printf '\n==> %s\n' "$*"; }
ok()  { printf '[OK] %s\n' "$*"; }
warn(){ printf '[WARN] %s\n' "$*" >&2; }

install_native() {
  local dest="${CODEX_HOME:-$HOME/.codex}/skills/$SKILL_NAME"
  mkdir -p "$(dirname "$dest")"
  rm -rf "$dest"
  cp -a "$SKILL_DIR" "$dest"
  ok "Installed native Termux skill to $dest"
  installed=1
}

install_proot() {
  local repo_root
  repo_root="$(git -C "$SKILL_DIR" rev-parse --show-toplevel 2>/dev/null)"
  local rel="${SKILL_DIR#"$repo_root"/}"

  proot-distro login codex-debian \
    --bind "$repo_root:/workspace" \
    -- bash -lc "
      set -e
      dest=\"\${CODEX_HOME:-\$HOME/.codex}/skills/$SKILL_NAME\"
      mkdir -p \"\$(dirname \"\$dest\")\"
      rm -rf \"\$dest\"
      cp -a \"/workspace/$rel\" \"\$dest\"
      printf '[OK] Installed Debian Codex skill to %s\\n' \"\$dest\"
    "
  installed=1
}

if command -v codex >/dev/null 2>&1 && codex --version >/dev/null 2>&1; then
  log "Installing for native Termux Codex"
  install_native
fi

if command -v proot-distro >/dev/null 2>&1 && proot-distro login codex-debian -- true >/dev/null 2>&1; then
  log "Installing for codex-debian"
  install_proot
fi

if [[ "$installed" -eq 0 ]]; then
  warn "No working native Codex or codex-debian environment was detected."
  warn "The repo-scoped skill is still available at: $SKILL_DIR/SKILL.md"
  exit 1
fi

cat <<'TXT'

Pixel Shade skill installed.

Start from the repository:
  cd ~/path/to/Pixel-shade
  codex-termux
TXT
