#!/usr/bin/env bash
# TASK-005 branding gate (DEV-04, DEV-16; G8). Fails the build when any
# user-visible surface (Kotlin/Java strings and prompt/notification bodies,
# resources, launcher label/icons) carries an upstream product name in ANY
# letter case (OpenDroid, opendroid, OPENDROID, ClosePaw, closepaw, ...).
#
# Upstream names are allowed ONLY in these documented contexts:
#   1. Code comment lines: "// Origin:" provenance headers and the adapter
#      "upstream class com.opendroid.ai.*" provenance comments. Comments are
#      not user-visible text, so they carry the provenance truth (review R2).
#   2. Legacy-identifier exception (review R1): the literal "opendroid_prefs"
#      is the on-device legacy SharedPreferences FILE NAME that upstream-era
#      builds wrote. The one-time import must read and erase exactly that file
#      (KeystoreSecretStorage.kt LEGACY_PREFERENCES_NAME;
#      LegacyPlaintextPreferencesSourceTest.kt), so this name is not rebranded.
#      Only that literal is exempt - the rest of any line is still checked.
#   3. NOTICE context: the in-app notices strings block (notices_body) in
#      res/values/strings.xml - the one screen that must name upstream projects
#      (the same attribution surface as the NOTICE file).
#   4. NOTICE + LICENSE files and the docs/evidence tree (factual attribution)
#      are not scanned at all.
#   5. Constraints/decision docs naming the excluded Leap SDK (ADR-0003 rule).
# Shizuku app id / manager-permission renames are TASK-007's scope, not here.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

fail=0
# Case-insensitive everywhere: bare "opendroid"/"OPENDROID" are as disallowed
# as "OpenDroid" (review R3: the old case-sensitive pattern missed the former).
name_pattern='opendroid|closepaw'

# Exit-code discipline (review R3): "no matches" (exit 1) is fine, but any real
# error (exit >= 2) must fail the gate instead of being swallowed - the old
# multi-line check hid a dead glob behind `2>/dev/null || true` and silently
# scanned 0 files.
run_scan() {
  # $1 = section label; rest = grep arguments. Result in $scan_out.
  local label="$1"
  shift
  set +e
  scan_out="$(grep "$@")"
  scan_rc=$?
  set -e
  if [ "$scan_rc" -ge 2 ]; then
    echo "FAIL: scan errored (grep exit $scan_rc): $label"
    exit 1
  fi
}

echo "== Branding gate: user-visible code strings/lines (android/*/src/main *.kt, *.java) =="
code_files="$(git ls-files 'android/*/src/main/*.kt' 'android/*/src/main/*.java')"
if [ -z "$code_files" ]; then
  echo "FAIL: no source files enumerated - the code scan would be vacuous"
  exit 1
fi
echo "scanning $(printf '%s\n' "$code_files" | wc -l) tracked source files"
# One awk pass: comment-only lines are provenance (allowed context 1); the R1
# legacy identifier is stripped before matching (allowed context 2), so only it
# survives on its own lines and anything else on those lines is still checked.
set +e
hits="$(awk -v pat="$name_pattern" '
  /^[[:space:]]*(\/\/|\/\*|\*)/ { next }
  { line = $0; gsub(/opendroid_prefs/, "", line)
    if (tolower(line) ~ pat) print FILENAME ": " FNR ": " $0 }
' $code_files)"
awk_rc=$?
set -e
if [ "$awk_rc" -ne 0 ]; then
  echo "FAIL: code scan errored (awk exit $awk_rc)"
  exit 1
fi
if [ -n "$hits" ]; then
  echo "FAIL: upstream product names found in user-visible code:"
  echo "$hits"
  fail=1
else
  echo "OK: user-visible code carries no upstream product names in any case"
fi

echo "== Branding gate: multi-line prompt/notification bodies (triple-quoted, android/*/src/main *.kt) =="
prompt_files="$(git ls-files 'android/*/src/main/*.kt')"
if [ -z "$prompt_files" ]; then
  echo "FAIL: no Kotlin files enumerated - the prompt-body scan would be vacuous"
  exit 1
fi
echo "scanning $(printf '%s\n' "$prompt_files" | wc -l) tracked Kotlin files"
# Triple-quote parity per line tracks block state, so strings that open and
# close on one line (or hold several """) do not corrupt it. Nothing inside a
# prompt/notification body is allowlisted: prompt text is user-visible.
set +e
ml="$(awk '
  { n = gsub(/"""/, "&")
    if (inblock && tolower($0) ~ /opendroid|closepaw/) print FILENAME ": " FNR ": " $0
    if (n % 2 == 1) inblock = !inblock }
' $prompt_files)"
awk_rc=$?
set -e
if [ "$awk_rc" -ne 0 ]; then
  echo "FAIL: prompt-body scan errored (awk exit $awk_rc)"
  exit 1
fi
if [ -n "$ml" ]; then
  echo "FAIL: upstream names in prompt/notification bodies:"
  echo "$ml"
  fail=1
else
  echo "OK: prompt/notification bodies clean"
fi

echo "== Branding gate: resources and manifest (android/app/src/main/res, AndroidManifest.xml) =="
res_files="$(git ls-files 'android/app/src/main/res' 'android/app/src/main/AndroidManifest.xml')"
if [ -z "$res_files" ]; then
  echo "FAIL: no resource files enumerated - the resource scan would be vacuous"
  exit 1
fi
echo "scanning $(printf '%s\n' "$res_files" | wc -l) tracked resource/manifest files"
# XML comment lines are skipped (not user-visible); the notices_body block is
# the documented NOTICE context (allowed context 3).
set +e
res="$(awk -v pat="$name_pattern" '
  /<!--|-->/ { next }
  /<string[ \t]+name="notices_body">/ { innotices = 1 }
  innotices { if (/<\/string>/) innotices = 0; next }
  { if (tolower($0) ~ pat) print FILENAME ": " FNR ": " $0 }
' $res_files)"
awk_rc=$?
set -e
if [ "$awk_rc" -ne 0 ]; then
  echo "FAIL: resource scan errored (awk exit $awk_rc)"
  exit 1
fi
if [ -n "$res" ]; then
  echo "FAIL: upstream names in resources/manifests:"
  echo "$res"
  fail=1
else
  echo "OK: resources/manifests clean"
fi

echo "== Branding gate: excluded Leap SDK (ADR-0003/D-007) =="
run_scan "Leap SDK scan" -rniE 'liquid|leap-sdk|ai\.liquid' android/ \
  --include='*.gradle' --include='*.kts' --include='*.kt' --include='*.toml'
if [ -n "$scan_out" ]; then
  echo "FAIL: LiquidAI Leap SDK referenced (ADR-0003 exclusion):"
  echo "$scan_out"
  fail=1
else
  echo "OK: no Leap SDK references"
fi

echo "== Branding gate: provenance map covers exactly the tracked Kotlin files =="
map_file=android/Phase-One/evidence/task-005-provenance-map.md
tracked_kt="$(git ls-files 'android/**/*.kt')"
row_paths="$(awk -F'`' '/^\| :/ { print $2 }' "$map_file")"
total="$(printf '%s\n' "$tracked_kt" | wc -l)"
covered="$(printf '%s\n' "$row_paths" | wc -l)"
echo "kt files: $total; provenance rows: $covered"
# Beyond the row count (review R2d): the two sets must be identical, so a map
# row naming a path git ls-files does not have (e.g. a stale pre-relocation
# path) fails here and cannot regress unnoticed.
set +e
map_diff="$(diff <(printf '%s\n' "$tracked_kt" | sort) <(printf '%s\n' "$row_paths" | sort))"
diff_rc=$?
set -e
if [ "$diff_rc" -ge 2 ]; then
  echo "FAIL: provenance comparison errored (diff exit $diff_rc)"
  exit 1
fi
if [ "$diff_rc" -eq 1 ]; then
  echo "FAIL: provenance map rows and tracked Kotlin files disagree:"
  echo "$map_diff"
  echo "(left = tracked file missing from the map; right = map row naming a path git ls-files does not have)"
  fail=1
else
  echo "OK: provenance map covers all Kotlin files and every row path exists in git ls-files"
fi

if [ "$fail" -ne 0 ]; then
  echo "BRANDING GATE FAILED"
  exit 1
fi
echo "BRANDING GATE PASSED"
