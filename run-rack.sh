#!/usr/bin/env bash
# One-command launcher for the Rack debug app on the local stack + rackdemo emulator.
# Idempotent: starts only what is not already up, then builds, installs and launches.
# Usage:  ./run-rack.sh
set -euo pipefail

REPO="/home/developer/CascadeProjects/rack"
SDK="${ANDROID_HOME:-/home/developer/android-sdk}"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD="rackdemo"
APP="de.rack.app/.MainActivity"
cd "$REPO"

echo "[1/5] Supabase local stack ..."
if curl -sf -m 4 "http://127.0.0.1:55321/auth/v1/health" >/dev/null 2>&1; then
  echo "      already up."
else
  echo "      starting (supabase start) ..."
  supabase start >/dev/null
fi

echo "[2/5] rack-MCP (port 8787) ..."
if ss -ltn 2>/dev/null | grep -q ':8787'; then
  echo "      already running."
else
  [ -f mcp/dist/index.js ] || ( echo "      building MCP ..."; cd mcp && npm run build )
  ( cd mcp && nohup node dist/index.js >/tmp/rack-mcp.log 2>&1 & )
  for _ in $(seq 1 12); do ss -ltn 2>/dev/null | grep -q ':8787' && break; sleep 1; done
  if ss -ltn 2>/dev/null | grep -q ':8787'; then
    echo "      started (http://localhost:8787/mcp)."
  else
    echo "ERROR: MCP did not start (see /tmp/rack-mcp.log):"; tail -5 /tmp/rack-mcp.log; exit 1
  fi
fi

echo "[3/5] Emulator ($AVD) ..."
if "$ADB" devices | grep -qE 'emulator-[0-9]+[[:space:]]+device$'; then
  echo "      already running."
else
  if ! { [ -r /dev/kvm ] && [ -w /dev/kvm ]; }; then
    cat <<'EOF'
ERROR: no access to /dev/kvm -- the x86_64 emulator needs hardware acceleration.
Fix once (persistent), then restart WSL:
  sudo usermod -aG kvm $USER
  # then in Windows PowerShell:  wsl --shutdown   (and reopen the terminal)
Or apply immediately (works until the next Windows reboot):
  sudo chmod 666 /dev/kvm
EOF
    exit 1
  fi
  nohup "$EMU" -avd "$AVD" -no-boot-anim -netdelay none -netspeed full \
    >/tmp/rack-emulator.log 2>&1 &
  emu_pid=$!
  printf "      booting"
  booted=0
  for _ in $(seq 1 150); do
    if ! kill -0 "$emu_pid" 2>/dev/null; then
      printf "\n"; echo "ERROR: emulator exited early -- last log lines:"; tail -6 /tmp/rack-emulator.log; exit 1
    fi
    if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      booted=1; break
    fi
    printf "."; sleep 2
  done
  printf "\n"
  if [ "$booted" != "1" ]; then
    echo "ERROR: emulator did not finish booting in time (see /tmp/rack-emulator.log)"; exit 1
  fi
  "$ADB" shell input keyevent 82 >/dev/null 2>&1 || true   # dismiss lockscreen
fi

echo "[4/5] Build + install debug APK ..."
( cd android && ./gradlew -q installDebug )

echo "[5/5] Launch ..."
"$ADB" shell am start -n "$APP" >/dev/null
echo "OK - Rack is running in the emulator.  (logs: $ADB logcat --pid=\$($ADB shell pidof de.rack.app))"
