# ---------------------------------------------------------------------
# Copyright (c) 2025 Qualcomm Technologies, Inc. and/or its subsidiaries.
# SPDX-License-Identifier: BSD-3-Clause
# ---------------------------------------------------------------------

"""Reclaim leaked GPU memory before a serial GPU test suite (issue #19607).

GPU suites run as separate, serial pytest processes sharing one GPU. A leftover
worker from a prior suite can keep memory pinned and OOM the next suite. This
logs GPU memory and kills leftover GPU processes owned by the current user.
Best-effort: always exits 0 so it never blocks the test run.
"""

from __future__ import annotations

import argparse
import os
import signal
import subprocess
import sys
import time

# A clean GPU should be ~0 used; tolerate driver/context overhead before warning.
DEFAULT_THRESHOLD_GIB = 1.0
_MIB_PER_GIB = 1024.0


def _target_device_index() -> str | None:
    """Physical GPU index to guard, taken from CUDA_VISIBLE_DEVICES.

    Returns None when the device cannot be resolved to a single integer index
    (e.g. unset, multiple devices, or UUID form), in which case the caller
    should act across all visible devices.
    """
    env = os.environ.get("CUDA_VISIBLE_DEVICES", "").strip()
    if not env:
        return None
    parts = [p.strip() for p in env.split(",") if p.strip()]
    if len(parts) == 1 and parts[0].isdigit():
        return parts[0]
    return None


def _nvidia_smi(args: list[str]) -> str | None:
    """Run nvidia-smi with the given args; return stdout or None on failure."""
    try:
        result = subprocess.run(
            ["nvidia-smi", *args],
            capture_output=True,
            text=True,
            check=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"[gpu-guard] nvidia-smi unavailable or failed: {e!r}")
        return None
    return result.stdout


def _log_memory(phase: str, device_index: str | None) -> None:
    args = [
        "--query-gpu=index,memory.used,memory.total",
        "--format=csv,noheader,nounits",
    ]
    if device_index is not None:
        args += ["-i", device_index]
    out = _nvidia_smi(args)
    if out is None:
        return
    for line in out.strip().splitlines():
        idx, used, total = (f.strip() for f in line.split(","))
        print(f"[gpu-guard] {phase} gpu={idx} used={used} MiB total={total} MiB")


def _proc_cmdline(pid: int) -> str:
    try:
        with open(f"/proc/{pid}/cmdline") as f:
            return f.read().replace("\0", " ").strip()
    except (FileNotFoundError, ProcessLookupError, PermissionError):
        return ""


def _gpu_holder_pids() -> list[int]:
    """PIDs (in our own PID namespace) that hold open NVIDIA device handles.

    We scan ``/proc/*/fd`` for symlinks into ``/dev/nvidia*`` rather than using
    ``nvidia-smi --query-compute-apps``: the GPU CI runs inside a container
    (``container: image: ...gpu_builder`` in gpu_weekly.yml), and nvidia-smi
    reports *host* PIDs that don't map to the PIDs we can see or kill in our
    namespace. The /proc scan stays entirely within our namespace, so the PIDs
    it returns are killable.
    """
    my_uid = os.getuid()
    holders: list[int] = []
    for entry in os.listdir("/proc"):
        if not entry.isdigit():
            continue
        pid = int(entry)
        try:
            if os.stat(f"/proc/{pid}").st_uid != my_uid:
                continue
            fd_dir = f"/proc/{pid}/fd"
            for fd in os.listdir(fd_dir):
                try:
                    target = os.readlink(os.path.join(fd_dir, fd))
                except OSError:
                    continue
                if target.startswith("/dev/nvidia"):
                    holders.append(pid)
                    break
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            # Process exited or is not introspectable; skip.
            continue
    return holders


def _reclaim() -> None:
    """Kill leftover GPU processes owned by the current user.

    Runs between serial GPU test suites, where no legitimate GPU process should
    be alive, so any current-user process still holding an NVIDIA handle is a
    leftover worker from a prior suite. We skip ourselves and our ancestors (the
    invoking shell chain) so the guard never kills its own process tree.
    """
    my_pid = os.getpid()
    ancestors = _ancestor_pids(my_pid)
    for pid in _gpu_holder_pids():
        if pid == my_pid or pid in ancestors:
            continue
        cmd = _proc_cmdline(pid)
        print(f"[gpu-guard] killing leftover GPU process pid={pid}: {cmd[:120]}")
        try:
            os.kill(pid, signal.SIGKILL)
        except (ProcessLookupError, PermissionError) as e:
            print(f"[gpu-guard] could not kill pid={pid}: {e!r}")


def _ancestor_pids(pid: int) -> set[int]:
    """Set of ancestor PIDs of ``pid`` (including itself), via /proc PPid."""
    ancestors: set[int] = set()
    current = pid
    while current and current not in ancestors:
        ancestors.add(current)
        try:
            with open(f"/proc/{current}/status") as f:
                ppid = next(
                    (int(line.split()[1]) for line in f if line.startswith("PPid:")),
                    0,
                )
        except (FileNotFoundError, ProcessLookupError, PermissionError, ValueError):
            break
        current = ppid
    return ancestors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--no-kill",
        action="store_true",
        help="Only log GPU memory; do not kill leftover processes.",
    )
    parser.add_argument(
        "--threshold-gib",
        type=float,
        default=DEFAULT_THRESHOLD_GIB,
        help="Warn if used memory exceeds this after reclamation.",
    )
    args = parser.parse_args()

    device_index = _target_device_index()
    target = f"gpu {device_index}" if device_index is not None else "all GPUs"
    print(f"[gpu-guard] target: {target}")

    _log_memory("pre", device_index)
    if not args.no_kill:
        _reclaim()
        # Give the driver a moment to reclaim memory from killed processes.
        time.sleep(2)
        _log_memory("post", device_index)

    # Surface (but never fail on) a still-dirty GPU after reclamation.
    out = _nvidia_smi(
        ["--query-gpu=memory.used", "--format=csv,noheader,nounits"]
        + (["-i", device_index] if device_index is not None else [])
    )
    if out:
        for line in out.strip().splitlines():
            used_mib = float(line.strip())
            if used_mib / _MIB_PER_GIB > args.threshold_gib:
                print(
                    f"[gpu-guard] WARNING: GPU still has {used_mib:.0f} MiB in "
                    f"use after reclamation (threshold "
                    f"{args.threshold_gib:.1f} GiB); a prior suite may have "
                    f"leaked memory that could not be reclaimed."
                )

    # Best-effort: never block the test run.
    return 0


if __name__ == "__main__":
    sys.exit(main())
