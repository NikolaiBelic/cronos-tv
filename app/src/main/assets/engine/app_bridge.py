# app_bridge.py — drop-in replacement for the socket-RPC variant in the
# original acestream engine repo. Same surface (`droid = Android()` /
# `droid.foo(args)`), backed by a built-in `app_bridge_native` C module that
# JNI-calls Kotlin EngineCallbacks.dispatch in the same process.
#
# This file is shipped as an asset and unpacked into filesDir/engine/, which
# is on PYTHONPATH ahead of stdlib so engine code resolves this module first.

import app_bridge_native


class Android:
    """Mirror the original socket-RPC `Android` API for engine compatibility."""

    def __init__(self, addr=None):
        # Original took addr=(host, port). We ignore it — every call goes
        # straight through JNI in-process.
        del addr

    def __getattr__(self, name):
        # Lazy method dispatch. EngineCallbacks.dispatch on the Kotlin side
        # decides which methods exist; unknown names log a warning and
        # return null.
        def call(*args):
            return app_bridge_native.call(name, *args)
        return call
