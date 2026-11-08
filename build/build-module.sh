#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT_DIR="${1:-$ROOT/dist}"
STAGE=$(mktemp -d "${TMPDIR:-/tmp}/xclipse-tuner.XXXXXX")
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM

mkdir -p "$OUT_DIR" "$STAGE/bin"
cp "$ROOT/module.prop" "$STAGE/module.prop"
cp "$ROOT/service.sh" "$STAGE/service.sh"
cp "$ROOT/tools/xclipse-diagnose.sh" "$STAGE/bin/xclipse-diagnose.sh"
chmod 0644 "$STAGE/module.prop"
chmod 0755 "$STAGE/service.sh" "$STAGE/bin/xclipse-diagnose.sh"

if ! command -v zip >/dev/null 2>&1; then
    echo "error: zip is required to build the KernelSU package" >&2
    exit 1
fi

VERSION=$(sed -n 's/^version=//p' "$ROOT/module.prop" | head -n 1)
PACKAGE="$OUT_DIR/xclipse-tuner-$VERSION.zip"
rm -f "$PACKAGE"
(
    cd "$STAGE"
    zip -qr "$PACKAGE" .
)
echo "$PACKAGE"
