# main.py — Step 3b entry. Sets up sys.path for the engine bundle, imports
# acestreamengine.Core, and calls Core.run(params).

import os
import sys
import traceback

# Disable subprocess.Popen's vfork() path. After vfork(), the child runs
# reset_signal_handlers() which iterates SIGRTMIN..SIGRTMAX calling
# sigaction(); on Android each call goes through libsigchain's interposer,
# which is not async-signal-safe — the child SIGSEGVs before exec(). The
# fork() path runs reset_signal_handlers() in a normal forked child where
# libsigchain state is its own copy, so no race. Triggered today by
# PyCryptodome's _IntegerGMP probing libgmp via ctypes.util.find_library →
# subprocess.Popen(['ld', '-t', '-lgmp']) at engine startup; failure was
# already being swallowed by PyCryptodome's GMP fallback, but the vfork
# child crash spammed logcat with libsigchain backtraces every start.
import subprocess
subprocess._USE_VFORK = False

import app_bridge

droid = app_bridge.Android()


def setup_paths():
    """Add modules.zip + eggs to sys.path so engine imports resolve.

    Layout (set up by EngineInstaller):
      <engine>/main.py            <- this script (already on sys.path)
      <engine>/app_bridge.py      <- ditto
      <engine>/acestreamengine/   <- cythonized .so package (already on sys.path)
      <engine>/modules.zip        <- zipped pure-python ACEStream/ tree
      <engine>/eggs/              <- third-party Python packages
      <engine>/data/              <- schema, cacert, acestream.conf
    """
    here = os.path.dirname(os.path.abspath(__file__))
    modules_zip = os.path.join(here, "modules.zip")
    eggs_dir = os.path.join(here, "eggs")
    arch_lib_dir = os.path.join(here, "lib")

    # Order matters: modules.zip must come BEFORE the stdlib so engine-shipped
    # ACEStream/* takes precedence over anything stdlib-shaped.
    if os.path.isfile(modules_zip) and modules_zip not in sys.path:
        sys.path.insert(0, modules_zip)
    if os.path.isdir(eggs_dir) and eggs_dir not in sys.path:
        sys.path.insert(0, eggs_dir)
    # Arch-specific libs (Crypto, nacl, lxml, apsw, psutil, ...).
    if os.path.isdir(arch_lib_dir) and arch_lib_dir not in sys.path:
        sys.path.insert(0, arch_lib_dir)


def setup_home():
    """Engine reads ACESTREAM_HOME from env. Use the path Java tells us."""
    home = droid.getAceStreamHome()
    if home:
        try:
            os.makedirs(home, exist_ok=True)
        except OSError:
            pass
        os.environ["ACESTREAM_HOME"] = home
    return home


def parse_conf(conf_path):
    """Mirror of build_android/main.py: read acestream.conf with argparse's
    `fromfile_prefix_chars` so engine flags can ride along."""
    if not os.path.isfile(conf_path):
        return []
    import argparse
    parser = argparse.ArgumentParser(prog="acestream", fromfile_prefix_chars="@")
    try:
        _, parsed = parser.parse_known_args(["@" + conf_path])
        return parsed
    except Exception as e:
        print("[main.py] failed to read", conf_path, "-", e)
        return []


def main():
    print("[main.py] python", sys.version.splitlines()[0])

    # Capture engine flags pushed from Java via runMain's sys.argv (set in
    # pyembedded.c before PyRun_SimpleFileEx). Must read this BEFORE the
    # sys.argv = [script] clobber below.
    java_args = list(sys.argv[1:])

    # Pin sys.argv[0] to this script's path. The engine's setup_dirs() does
    # `exec_dir = os.path.abspath(os.path.dirname(sys.argv[0]))`, so without
    # this `exec_dir` would resolve to "/" and the engine would look for
    # `/data/cacert.pem` instead of `<engineDir>/data/cacert.pem`.
    sys.argv = [os.path.abspath(__file__)]
    # The engine reaches into platform.platform() during sentry setup, which
    # opens sys.executable as a regular file. Empty str defaults to "/" and
    # PermissionError ensues. Point at our script — anything readable works.
    sys.executable = os.path.abspath(__file__)

    home = setup_home()
    print("[main.py] ACESTREAM_HOME =", home)

    setup_paths()
    print("[main.py] sys.path:")
    for p in sys.path:
        print("  -", p)

    here = os.path.dirname(os.path.abspath(__file__))
    conf_path = os.path.join(here, "data", "acestream.conf")
    extra_params = parse_conf(conf_path)
    print("[main.py] conf params:", extra_params)
    print("[main.py] java args:", java_args)

    print("[main.py] from acestreamengine import Core ...")
    try:
        from acestreamengine import Core
        print("[main.py] Core imported:", Core)
    except Exception:
        print("[main.py] Core import FAILED:")
        traceback.print_exc()
        return

    # --client-console is the only baseline flag specific to the inproc
    # bridge; everything else (api/http port, log file, TV cache options,
    # ...) is built in MainProcessEngineCallbacks.buildEngineArgs and
    # arrives via java_args. acestream.conf @file extras stay supported
    # for static overrides shipped in the bundle.
    params = ["acestream", "--client-console"] + java_args + extra_params
    print("[main.py] calling Core.run(", params, ")")

    try:
        Core.run(params)
    except SystemExit:
        print("[main.py] Core.run exited via SystemExit")
    except Exception:
        print("[main.py] Core.run raised:")
        traceback.print_exc()


if __name__ == "__main__":
    main()
