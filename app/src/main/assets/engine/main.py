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

def inspect_coreapp():
    """Diagnóstico dirigido de las clases relevantes de CoreApp."""

    try:
        sys.stdout.reconfigure(line_buffering=True)
    except Exception:
        pass

    print("\n[CoreAppInspect] ===== START =====", flush=True)

    try:
        import inspect
        import acestreamengine.CoreApp as CA

        targets = [
            "CoreApp",
            "AttestationManager",
        ]

        keywords = (
            "attest",
            "integrity",
            "license",
            "premium",
            "mod",
            "auth",
            "token",
            "play",
            "ad",
            "start",
            "init",
        )

        for class_name in targets:
            print(
                "\n[CoreAppInspect] ===== CLASS:",
                class_name,
                "====="
            )

            obj = getattr(CA, class_name, None)

            if obj is None:
                print("[CoreAppInspect] NOT FOUND")
                continue

            print("[CoreAppInspect] repr:", repr(obj))

            try:
                print(
                    "[CoreAppInspect] signature:",
                    inspect.signature(obj)
                )
            except Exception as e:
                print(
                    "[CoreAppInspect] signature ERROR:",
                    repr(e)
                )

            try:
                attrs = sorted(dir(obj))
            except Exception as e:
                print(
                    "[CoreAppInspect] dir ERROR:",
                    repr(e)
                )
                continue

            interesting = [
                name for name in attrs
                if any(k in name.lower() for k in keywords)
            ]

            print(
                "[CoreAppInspect] interesting:",
                interesting
            )

            for name in interesting:
                try:
                    member = getattr(obj, name)

                    print(
                        "[CoreAppInspect] MEMBER:",
                        class_name + "." + name,
                        "| type:",
                        type(member),
                        "| repr:",
                        repr(member)[:400],
                        )

                    try:
                        print(
                            "[CoreAppInspect] SIG:",
                            class_name + "." + name,
                            inspect.signature(member)
                        )
                    except Exception:
                        pass

                except Exception as e:
                    print(
                        "[CoreAppInspect] MEMBER ERROR:",
                        class_name + "." + name,
                        repr(e)
                    )

        print("\n[CoreAppInspect] ===== CoreApp CLASS =====")

        try:
            cls = CA.CoreApp

            for name in sorted(dir(cls)):
                if any(k in name.lower() for k in (
                        "license",
                        "attest",
                        "android",
                        "play",
                        "auth",
                        "request",
                        "premium",
                        "app",
                )):
                    try:
                        obj = getattr(cls, name)

                        print(
                            "[CoreAppInspect] CORE METHOD:",
                            name,
                            "| type:",
                            type(obj),
                            "| repr:",
                            repr(obj)[:300]
                        )

                        try:
                            print(
                                "[CoreAppInspect] CORE SIG:",
                                name,
                                inspect.signature(obj)
                            )
                        except Exception as e:
                            print(
                                "[CoreAppInspect] CORE SIG ERROR:",
                                name,
                                repr(e)
                            )

                    except Exception as e:
                        print(
                            "[CoreAppInspect] CORE ERROR:",
                            name,
                            repr(e)
                        )

        except Exception:
            print("[CoreAppInspect] CoreApp class inspection FAILED:")
            traceback.print_exc()


        print("\n[CoreAppInspect] ===== DASHRequestBroker =====", flush=True)

        try:
            cls = CA.DASHRequestBroker

            print(
                "[CoreAppInspect] DASH CLASS:",
                repr(cls),
                flush=True,
            )

            try:
                print(
                    "[CoreAppInspect] DASH CLASS SIG:",
                    inspect.signature(cls),
                    flush=True,
                )
            except Exception as e:
                print(
                    "[CoreAppInspect] DASH CLASS SIG ERROR:",
                    repr(e),
                    flush=True,
                )

            for name in sorted(dir(cls)):
                try:
                    member = getattr(cls, name)

                    if not callable(member):
                        continue

                    print(
                        "[CoreAppInspect] DASH METHOD:",
                        name,
                        "| type:",
                        type(member),
                        "| repr:",
                        repr(member)[:500],
                        flush=True,
                    )

                    try:
                        print(
                            "[CoreAppInspect] DASH SIG:",
                            name,
                            inspect.signature(member),
                            flush=True,
                        )
                    except Exception:
                        pass

                except Exception as e:
                    print(
                        "[CoreAppInspect] DASH MEMBER ERROR:",
                        name,
                        repr(e),
                        flush=True,
                    )

        except Exception:
            print(
                "[CoreAppInspect] DASHRequestBroker inspection FAILED:",
                flush=True,
            )
            traceback.print_exc()

        print("\n[CoreAppInspect] ===== END =====")

    except Exception:
        print("[CoreAppInspect] FAILED:")
        traceback.print_exc()

def install_socket_audit():
    """Diagnóstico temporal: registra conexiones iniciadas desde Python/Cython."""
    import sys
    import traceback

    def audit_hook(event, args):
        if event not in ("socket.connect", "socket.getaddrinfo"):
            return

        try:
            print(
                "\n[SocketAudit] EVENT:",
                event,
                "ARGS:",
                repr(args),
                flush=True,
            )

            print("[SocketAudit] STACK:", flush=True)

            for line in traceback.format_stack(limit=30):
                print(
                    "[SocketAudit]",
                    line.rstrip(),
                    flush=True,
                )

        except Exception as e:
            print(
                "[SocketAudit] ERROR:",
                repr(e),
                flush=True,
            )

    sys.addaudithook(audit_hook)

    print(
        "[SocketAudit] hook installed",
        flush=True,
    )

def install_http_audit():
    """Diagnóstico temporal: registra HTTP sin alterar peticiones/respuestas."""
    import urllib.request
    import http.client
    import traceback

    # Guardar implementaciones originales ANTES de instalar los wrappers.
    original_open = urllib.request.OpenerDirector.open
    original_read = http.client.HTTPResponse.read
    original_http_request = http.client.HTTPConnection.request
    original_putrequest = http.client.HTTPConnection.putrequest

    def safe_headers(headers):
        result = {}
        try:
            items = headers.items()
        except Exception:
            return repr(headers)

        for key, value in items:
            if key.lower() in (
                    "authorization",
                    "cookie",
                    "proxy-authorization",
            ):
                value = "<REDACTED>"
            result[key] = value
        return result

    def audited_http_request(
            self,
            method,
            url,
            body=None,
            headers={},
            *,
            encode_chunked=False,
    ):
        try:
            print("\n[HttpWire] ===== HTTPConnection.request =====", flush=True)
            print("[HttpWire] CONNECTION:", type(self).__name__, flush=True)
            print("[HttpWire] HOST:", getattr(self, "host", None), flush=True)
            print("[HttpWire] PORT:", getattr(self, "port", None), flush=True)
            print("[HttpWire] METHOD:", method, flush=True)
            print("[HttpWire] URL/PATH:", url, flush=True)
            print("[HttpWire] HEADERS:", safe_headers(headers), flush=True)

            if body is None:
                print("[HttpWire] BODY: <none>", flush=True)
            else:
                try:
                    print("[HttpWire] BODY LENGTH:", len(body), flush=True)

                    preview = body

                    if isinstance(preview, bytes):
                        try:
                            preview = preview.decode(
                                "utf-8",
                                errors="replace",
                            )
                        except Exception:
                            preview = repr(preview)

                    if isinstance(preview, str):
                        if len(preview) > 3000:
                            preview = preview[:3000] + "...<truncated>"

                        print(
                            "[HttpWire] BODY PREVIEW:",
                            repr(preview),
                            flush=True,
                        )
                    else:
                        print(
                            "[HttpWire] BODY TYPE:",
                            type(body),
                            flush=True,
                        )

                except Exception as e:
                    print(
                        "[HttpWire] BODY LOG ERROR:",
                        repr(e),
                        flush=True,
                    )

            print("[HttpWire] STACK:", flush=True)
            for line in traceback.format_stack(limit=20):
                print("[HttpWire]", line.rstrip(), flush=True)
        except Exception as e:
            print("[HttpWire] LOG ERROR:", repr(e), flush=True)

        return original_http_request(
            self,
            method,
            url,
            body=body,
            headers=headers,
            encode_chunked=encode_chunked,
        )

    def audited_putrequest(self, method, url, *args, **kwargs):
        try:
            print("\n[HttpWire] ===== PUTREQUEST =====", flush=True)
            print("[HttpWire] HOST:", getattr(self, "host", None), flush=True)
            print("[HttpWire] PORT:", getattr(self, "port", None), flush=True)
            print("[HttpWire] METHOD:", method, flush=True)
            print("[HttpWire] TARGET:", url, flush=True)
        except Exception as e:
            print("[HttpWire] PUTREQUEST LOG ERROR:", repr(e), flush=True)

        return original_putrequest(self, method, url, *args, **kwargs)

    def audited_open(self, fullurl, data=None, timeout=None):
        try:
            if isinstance(fullurl, urllib.request.Request):
                req = fullurl
                print("\n[HttpAudit] REQUEST URL:", req.full_url, flush=True)
                inspect_q_blob(req.full_url)
                print("[HttpAudit] METHOD:", req.get_method(), flush=True)
                print("[HttpAudit] HEADERS:", safe_headers(req.headers), flush=True)
                req_data = req.data
            else:
                print("\n[HttpAudit] REQUEST URL:", repr(fullurl), flush=True)
                inspect_q_blob(str(fullurl))
                req_data = data

            if req_data is None:
                print("[HttpAudit] BODY: <none>", flush=True)
            else:
                try:
                    print("[HttpAudit] BODY LENGTH:", len(req_data), flush=True)
                except Exception:
                    pass

            print("[HttpAudit] STACK:", flush=True)
            for line in traceback.format_stack(limit=20):
                print("[HttpAudit]", line.rstrip(), flush=True)
        except Exception as e:
            print("[HttpAudit] REQUEST LOG ERROR:", repr(e), flush=True)

        response = original_open(self, fullurl, data=data, timeout=timeout)

        try:
            print("[HttpAudit] RESPONSE URL:", response.geturl(), flush=True)
            print("[HttpAudit] STATUS:", getattr(response, "status", None), flush=True)
            print(
                "[HttpAudit] RESPONSE HEADERS:",
                safe_headers(response.headers),
                flush=True,
            )
        except Exception as e:
            print("[HttpAudit] RESPONSE LOG ERROR:", repr(e), flush=True)

        return response

    def audited_read(self, amt=None):
        if amt is None:
            data = original_read(self)
        else:
            data = original_read(self, amt)

        try:
            preview = data[:2048]
            if isinstance(preview, bytes):
                try:
                    preview = preview.decode("utf-8", errors="replace")
                except Exception:
                    preview = repr(preview)

            print("[HttpAudit] RESPONSE BODY:", repr(preview), flush=True)
        except Exception as e:
            print("[HttpAudit] BODY LOG ERROR:", repr(e), flush=True)

        return data

    urllib.request.OpenerDirector.open = audited_open
    http.client.HTTPResponse.read = audited_read
    http.client.HTTPConnection.request = audited_http_request
    http.client.HTTPConnection.putrequest = audited_putrequest

    print("[HttpAudit] hooks installed + HttpWire installed", flush=True)

def inspect_q_blob(url):
    import urllib.parse
    import base64
    import hashlib

    try:
        parsed = urllib.parse.urlsplit(url)
        qs = urllib.parse.parse_qs(parsed.query)

        values = qs.get("_q")
        if not values:
            return

        q_text = values[0]

        print(
            "[QInspect] TEXT LENGTH:",
            len(q_text),
            flush=True,
        )

        print(
            "[QInspect] TEXT PREFIX:",
            repr(q_text[:40]),
            flush=True,
        )

        # parse_qs ya ha hecho URL-decode (%2B, %2F, etc.)
        raw_text = q_text.encode("ascii")

        # Completar padding Base64 si falta.
        padding = (-len(raw_text)) % 4
        padded = raw_text + (b"=" * padding)

        try:
            blob = base64.b64decode(
                padded,
                validate=False,
            )
        except Exception as e:
            print(
                "[QInspect] BASE64 ERROR:",
                repr(e),
                flush=True,
            )
            return

        print(
            "[QInspect] BLOB LENGTH:",
            len(blob),
            flush=True,
        )

        print(
            "[QInspect] SHA256:",
            hashlib.sha256(blob).hexdigest(),
            flush=True,
        )

        print(
            "[QInspect] FIRST 32:",
            blob[:32].hex(),
            flush=True,
        )

        print(
            "[QInspect] LAST 32:",
            blob[-32:].hex(),
            flush=True,
        )

    except Exception as e:
        print(
            "[QInspect] ERROR:",
            repr(e),
            flush=True,
        )

def install_mapper_audit():
    """
    Diagnóstico: identifica los mappers registrados por VideoHTTPServer.
    No modifica las peticiones ni sus resultados.
    """
    try:
        import ACEStream.Video.VideoServer as VS

        original = VS.VideoHTTPServer.acquire_inputstream

        def audited_acquire(self, *args, **kwargs):
            try:
                print(
                    "\n[MapperAudit] ===== acquire_inputstream =====",
                    flush=True,
                )

                print(
                    "[MapperAudit] self:",
                    repr(self),
                    flush=True,
                )

                print(
                    "[MapperAudit] mappers count:",
                    len(self.mappers),
                    flush=True,
                )

                for i, mapper in enumerate(self.mappers):
                    print(
                        "[MapperAudit] MAPPER",
                        i,
                        "| type:",
                        type(mapper),
                        "| module:",
                        type(mapper).__module__,
                        "| class:",
                        type(mapper).__name__,
                        "| repr:",
                        repr(mapper),
                        flush=True,
                    )

                    try:
                        method = getattr(mapper, "get", None)

                        print(
                            "[MapperAudit] GET:",
                            repr(method),
                            "| module:",
                            getattr(method, "__module__", None),
                            "| qualname:",
                            getattr(method, "__qualname__", None),
                            flush=True,
                        )

                    except Exception as e:
                        print(
                            "[MapperAudit] GET ERROR:",
                            repr(e),
                            flush=True,
                        )

            except Exception as e:
                print(
                    "[MapperAudit] ERROR:",
                    repr(e),
                    flush=True,
                )

            # Fundamental: ejecutamos exactamente la implementación original.
            return original(self, *args, **kwargs)

        VS.VideoHTTPServer.acquire_inputstream = audited_acquire

        print(
            "[MapperAudit] hook installed",
            flush=True,
        )

    except Exception:
        print("[MapperAudit] INSTALL FAILED:", flush=True)
        traceback.print_exc()

def install_dash_broker_audit():
    """Audita DASHRequestBroker.get y process_bt_request sin alterar resultados."""
    try:
        import ACEStream.Video.VideoServer as VS
        import traceback

        original_acquire = VS.VideoHTTPServer.acquire_inputstream

        def dump_call(tag, method_name, args, kwargs):
            print("\n[%s] ===== DASHRequestBroker.%s =====" % (tag, method_name), flush=True)
            print("[%s] ARGC:" % tag, len(args), flush=True)
            for i, arg in enumerate(args):
                try:
                    value = repr(arg)
                    if len(value) > 1000:
                        value = value[:1000] + "...<truncated>"
                    print("[%s] ARG %d:" % (tag, i), type(arg), value, flush=True)
                except Exception as e:
                    print("[%s] ARG %d ERROR:" % (tag, i), repr(e), flush=True)
            try:
                value = repr(kwargs)
                if len(value) > 2000:
                    value = value[:2000] + "...<truncated>"
                print("[%s] KWARGS:" % tag, value, flush=True)
            except Exception:
                pass
            print("[%s] STACK:" % tag, flush=True)
            for line in traceback.format_stack(limit=20):
                print("[%s]" % tag, line.rstrip(), flush=True)

        def dump_result(tag, result):
            try:
                value = repr(result)
                if len(value) > 4000:
                    value = value[:4000] + "...<truncated>"
                print("[%s] RESULT TYPE:" % tag, type(result), flush=True)
                print("[%s] RESULT:" % tag, value, flush=True)
            except Exception as e:
                print("[%s] RESULT LOG ERROR:" % tag, repr(e), flush=True)

        class BrokerProxy:
            def __init__(self, target):
                object.__setattr__(self, "_target", target)

            def __getattr__(self, name):
                return getattr(self._target, name)

            def __setattr__(self, name, value):
                if name == "_target":
                    object.__setattr__(self, name, value)
                else:
                    setattr(self._target, name, value)

            def get(self, *args, **kwargs):
                tag = "BrokerAudit"
                dump_call(tag, "get", args, kwargs)
                try:
                    result = self._target.get(*args, **kwargs)
                except Exception as e:
                    print("[%s] EXCEPTION:" % tag, type(e), repr(e), flush=True)
                    raise
                dump_result(tag, result)
                return result

            def _trace(self, method_name, *args, **kwargs):
                tag = "BrokerTrace"
                dump_call(tag, method_name, args, kwargs)
                try:
                    method = getattr(self._target, method_name)
                    result = method(*args, **kwargs)
                except Exception as e:
                    print(
                        "[%s] %s EXCEPTION:" % (tag, method_name),
                        type(e),
                        repr(e),
                        flush=True,
                        )
                    raise
                print(
                    "[%s] %s RETURN" % (tag, method_name),
                    flush=True,
                    )
                dump_result(tag, result)
                return result

            def process_bt_request(self, *args, **kwargs):
                return self._trace("process_bt_request", *args, **kwargs)

            def process_dash_request(self, *args, **kwargs):
                return self._trace("process_dash_request", *args, **kwargs)

            def process_dash_r_request(self, *args, **kwargs):
                return self._trace("process_dash_r_request", *args, **kwargs)

            def request_denied_error(self, *args, **kwargs):
                return self._trace("request_denied_error", *args, **kwargs)

        def audited_acquire(self, *args, **kwargs):
            try:
                for i, mapper in enumerate(self.mappers):
                    if isinstance(mapper, BrokerProxy):
                        continue
                    if (type(mapper).__module__ == "CoreApp"
                            and type(mapper).__name__ == "DASHRequestBroker"):
                        print("\n[BrokerAudit] wrapping DASHRequestBroker at index", i, flush=True)
                        self.mappers[i] = BrokerProxy(mapper)
            except Exception as e:
                print("[BrokerAudit] WRAP ERROR:", repr(e), flush=True)
                traceback.print_exc()

            return original_acquire(self, *args, **kwargs)

        VS.VideoHTTPServer.acquire_inputstream = audited_acquire
        print("[BrokerAudit] BrokerTrace hooks installed", flush=True)

    except Exception:
        print("[BrokerAudit] INSTALL FAILED:", flush=True)
        traceback.print_exc()

def install_bridge_audit():
    """
    Registra las llamadas del Engine al bridge Android.
    No modifica argumentos ni valores devueltos.
    """
    import traceback

    cls = app_bridge.Android

    skip = {
        "__init__",
        "__class__",
    }

    for name in dir(cls):
        if name.startswith("_") or name in skip:
            continue

        try:
            original = getattr(cls, name)
        except Exception:
            continue

        if not callable(original):
            continue

        # Evitar envolver dos veces.
        if getattr(original, "_cronos_audited", False):
            continue

        def make_wrapper(method_name, method):
            def wrapper(self, *args, **kwargs):
                try:
                    print(
                        "\n[BridgeAudit] CALL:",
                        method_name,
                        flush=True,
                    )

                    print(
                        "[BridgeAudit] ARGS:",
                        repr(args),
                        repr(kwargs),
                        flush=True,
                    )
                except Exception:
                    pass

                try:
                    result = method(self, *args, **kwargs)
                except Exception as e:
                    print(
                        "[BridgeAudit] EXCEPTION:",
                        method_name,
                        repr(e),
                        flush=True,
                    )
                    raise

                try:
                    text = repr(result)

                    if len(text) > 1500:
                        text = text[:1500] + "...<truncated>"

                    print(
                        "[BridgeAudit] RESULT:",
                        method_name,
                        "|",
                        text,
                        flush=True,
                    )

                    # Stack corto para intentar ver desde qué zona llega.
                    for line in traceback.format_stack(limit=12):
                        print(
                            "[BridgeAudit] STACK:",
                            line.rstrip(),
                            flush=True,
                        )

                except Exception:
                    pass

                return result

            wrapper._cronos_audited = True
            return wrapper

        try:
            setattr(
                cls,
                name,
                make_wrapper(name, original),
            )
        except Exception:
            pass

    print(
        "[BridgeAudit] hooks installed",
        flush=True,
    )

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

    install_socket_audit()
    install_http_audit()
    install_mapper_audit()
    install_dash_broker_audit()
    install_bridge_audit()

    print("[main.py] sys.path:")

    # Diagnóstico temporal de CoreApp.so.
    # No modifica licencia, attestation ni validaciones.
    inspect_coreapp()

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

