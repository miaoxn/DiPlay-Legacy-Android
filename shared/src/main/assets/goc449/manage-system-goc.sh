#!/system/bin/sh
# Authorized 449-only system replacement. Actions: check, install, rollback.
set -eu
ACTION=${1:-check}
BASE=${0%/*}
SYS=/system/bin/gocsdk
STAGE=/system/bin/.gocsdk-449-stage
BACKUP=/data/local/goc449-system-backup
ORIGINAL=$BACKUP/gocsdk.stock
CANDIDATE=$BASE/gocsdk-449-iap2-test1
STOCK=52596a2ba3e1be85dc09efd20290b2d6b36aef9e569acd1d86014bcb991fe15e
PATCH=882afd622fe0d7a154e4cf0531d122c3fc54ba87c93cf47f90db258131502c0f
LOCK=/data/local/goc449-system-change.lock
STOPPED=0
CHANGED=0
COMMITTED=0
MOUNTED=0
OWN_LOCK=0
OWN_STAGE=0
BEFORE=
SOURCE=
EXPECTED=
CONTEXT=
hash() { busybox sha256sum "$1" | busybox awk '{print $1}'; }
meta() { busybox stat -c '%u:%g:%a' "$1"; }
context() { ls -Z "$1" 2>/dev/null | busybox grep -o 'u:object_r:[^ ]*' | busybox head -n 1; }
fail() { echo "FAILED: $*" >&2; exit 1; }
processes() {
 for p in /proc/[0-9]*; do
  exe=$(busybox readlink "$p/exe" 2>/dev/null || true)
  case "$exe" in "$SYS"|"$SYS (deleted)") echo "${p##*/}" ;; esac
 done
}
remount_rw() { mount -o remount,rw /system; MOUNTED=1; }
remount_original() {
 if [ "$MOUNTED" = 1 ] && [ "$WAS_RO" = 1 ]; then mount -o remount,ro /system || return 1; fi
}
prepare_stage() {
 [ ! -e "$STAGE" ] || fail stage-already-exists
 OWN_STAGE=1
 busybox cp -p "$SYS" "$STAGE"
 cat "$SOURCE" > "$STAGE"
 [ -z "$CONTEXT" ] || chcon "$CONTEXT" "$STAGE"
 [ "$(hash "$STAGE")" = "$EXPECTED" ] || fail stage-hash-mismatch
 [ "$(meta "$STAGE")" = "$METADATA" ] || fail stage-metadata-mismatch
 [ -z "$CONTEXT" ] || [ "$(context "$STAGE")" = "$CONTEXT" ] || fail stage-context-mismatch
 sync
}
wait_running() {
 n=0
 while [ "$n" -lt 25 ]; do
  if [ "$(getprop init.svc.gocsdk)" = running ] && [ -n "$(processes)" ]; then
   if [ "$EXPECTED" = "$STOCK" ] || busybox grep -q ' /dev/socket/goc_spp$' /proc/net/unix; then return 0; fi
  fi
  n=$((n+1)); sleep 1
 done
 return 1
}
finish() {
 rc=$?
 trap - 0 1 2 15
 if [ "$CHANGED" = 1 ] && [ "$COMMITTED" = 0 ]; then
  echo 'Installation did not commit; restoring pre-change bytes.'
  /system/bin/stop gocsdk || true
  # Only this fixed system executable; never terminate other Bluetooth processes.
  for p in $(processes); do kill "$p" 2>/dev/null || true; done
  sleep 1
  for p in $(processes); do kill -9 "$p" 2>/dev/null || true; done
  if [ -z "$(processes)" ] && [ "$(hash "$BACKUP/pre-change")" = "$BEFORE" ]; then
   rm -f "$STAGE"
   if busybox cp -p "$BACKUP/pre-change" "$STAGE" && { [ -z "$CONTEXT" ] || chcon "$CONTEXT" "$STAGE"; } && [ "$(hash "$STAGE")" = "$BEFORE" ]; then
    mv -f "$STAGE" "$SYS"; sync
   else
    echo 'AUTOMATIC RESTORE FAILED: use saved rollback package.' >&2
   fi
  else echo 'AUTOMATIC RESTORE BLOCKED: live process or invalid backup.' >&2; fi
 fi
 if [ "$STOPPED" = 1 ] && [ "$COMMITTED" = 0 ]; then
  /system/bin/start gocsdk || true
  EXPECTED=$BEFORE
  if wait_running && [ "$(hash "$SYS")" = "$BEFORE" ]; then echo 'Pre-change service restored.'; else echo 'RESTORE HEALTH CHECK FAILED' >&2; fi
 fi
 if [ "$CHANGED" = 0 ] && [ "$OWN_STAGE" = 1 ]; then rm -f "$STAGE"; fi
 if ! remount_original; then echo 'WARNING: /system remount ro failed' >&2; rc=1; fi
 if [ "$OWN_LOCK" = 1 ]; then rmdir "$LOCK" 2>/dev/null || true; fi
 exit "$rc"
}
case "$ACTION" in check|install|rollback) ;; *) fail 'usage: check|install|rollback' ;; esac
[ "$(busybox id -u)" = 0 ] || fail root-required
[ "$(getprop ro.build.version.sdk)" = 22 ] || fail wrong-sdk
[ "$(getprop ro.product.device)" = xea406m ] || fail wrong-device
[ -f "$SYS" ] && [ ! -L "$SYS" ] || fail unexpected-system-file
BEFORE=$(hash "$SYS")
case "$BEFORE" in "$STOCK"|"$PATCH") ;; *) fail unknown-system-hash ;; esac
METADATA=$(meta "$SYS")
CONTEXT=$(context "$SYS" || true)
MODE=$(busybox awk '$2=="/system" {print $4}' /proc/mounts)
[ -n "$MODE" ] || fail system-mount-not-found
WAS_RO=0
case ",$MODE," in *,ro,*) WAS_RO=1 ;; esac
if [ "$(getenforce 2>/dev/null || true)" = Enforcing ] && [ -z "$CONTEXT" ]; then fail cannot-read-selinux-context; fi
[ -z "$CONTEXT" ] || command -v chcon >/dev/null || fail chcon-required
if [ "$ACTION" != rollback ]; then
 [ "$(getprop init.svc.gocsdk)" = running ] && [ -n "$(processes)" ] || fail system-service-not-running
fi
for p in /proc/[0-9]*; do
 exe=$(busybox readlink "$p/exe" 2>/dev/null || true)
 case "$exe" in "$SYS") ;; */gocsdk*) fail foreign-goc-process ;; esac
done
for d in /data/data/com.freeauto.car.goc449test/files/goc449/temporary-lock /data/local/tmp/goc449-r2/temporary-lock; do
 [ ! -d "$d" ] || fail temporary-supervisor-lock-present
done
busybox printf 'action=%s\ncurrent_sha256=%s\nmetadata=%s\ncontext=%s\nmount=%s\n' "$ACTION" "$BEFORE" "$METADATA" "$CONTEXT" "$MODE"
# Do not write a verified system image; firmware hash alone does not identify boot policy.
if [ "$ACTION" != rollback ]; then
 [ "$(getprop ro.boot.veritymode)" != enforcing ] || fail verified-boot-not-supported
 for f in /fstab.* /vendor/etc/fstab.*; do
  if [ -f "$f" ] && busybox awk '$2=="/system" && $5 ~ /(^|,)verify(=|,|$)/ {found=1} END {exit !found}' "$f"; then fail verified-system-fstab-not-supported; fi
 done
fi
[ "$ACTION" != check ] || exit 0
mkdir "$LOCK" || fail another-system-change-in-progress
OWN_LOCK=1
trap finish 0
trap 'exit 1' 1 2 15
[ ! -L "$BACKUP" ] || fail unexpected-backup-link
mkdir -p "$BACKUP"
[ ! -L "$ORIGINAL" ] && [ ! -L "$BACKUP/pre-change" ] || fail unexpected-backup-file-link
chown 0:0 "$BACKUP"
chmod 700 "$BACKUP"
for f in original-metadata original-context result manage-system-goc.sh; do
 [ ! -L "$BACKUP/$f" ] || fail unexpected-backup-file-link
done
if [ -e "$ORIGINAL" ]; then
 [ "$(hash "$ORIGINAL")" = "$STOCK" ] || fail invalid-original-backup
else
 [ "$BEFORE" = "$STOCK" ] || fail original-backup-missing
 busybox cp -p "$SYS" "$ORIGINAL"
 [ "$(hash "$ORIGINAL")" = "$STOCK" ] || fail backup-failed
fi
if [ "$ACTION" = install ]; then SOURCE=$CANDIDATE; EXPECTED=$PATCH; else SOURCE=$ORIGINAL; EXPECTED=$STOCK; fi
[ "$(hash "$SOURCE")" = "$EXPECTED" ] || fail input-hash-mismatch
if [ "$BEFORE" = "$EXPECTED" ]; then
 if [ "$ACTION" = rollback ]; then /system/bin/start gocsdk; fi
 wait_running || fail installed-service-not-ready
 echo 'Already installed; unchanged.'; COMMITTED=1; exit 0
fi
[ ! -e "$STAGE" ] || fail stale-stage-requires-inspection
busybox cp -p "$SYS" "$BACKUP/pre-change"
[ "$(hash "$BACKUP/pre-change")" = "$BEFORE" ] || fail pre-change-backup-failed
busybox printf '%s\n' "$METADATA" > "$BACKUP/original-metadata"
busybox printf '%s\n' "$CONTEXT" > "$BACKUP/original-context"
busybox cp "$0" "$BACKUP/manage-system-goc.sh"
chmod 700 "$BACKUP/manage-system-goc.sh"
sync
remount_rw
prepare_stage
STOPPED=1
/system/bin/stop gocsdk
n=0
while [ -n "$(processes)" ] && [ "$n" -lt 12 ]; do n=$((n+1)); sleep 1; done
[ -z "$(processes)" ] || fail original-service-would-not-stop
# Mark before rename, so a signal immediately after rename still restores.
CHANGED=1
mv -f "$STAGE" "$SYS"
sync
[ "$(hash "$SYS")" = "$EXPECTED" ] || fail replaced-hash-mismatch
# Recovery must never put the patched executable back if the original service
# itself fails to start. Retain verified stock bytes for the next reboot.
if [ "$ACTION" = rollback ]; then
 COMMITTED=1
 busybox printf 'action=rollback\nsha256=%s\nstate=restored-awaiting-service\n' "$EXPECTED" > "$BACKUP/result"
fi
/system/bin/start gocsdk
wait_running || fail service-health-check-failed
sleep 5
[ "$(getprop init.svc.gocsdk)" = running ] && [ -n "$(processes)" ] || fail service-not-stable
[ "$(hash "$SYS")" = "$EXPECTED" ] || fail final-hash-mismatch
COMMITTED=1
busybox printf 'action=%s\nsha256=%s\nstate=committed\n' "$ACTION" "$EXPECTED" > "$BACKUP/result"
sync
echo 'COMMITTED: system GOC service running; original backup retained.'
