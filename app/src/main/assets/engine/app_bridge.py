import app_bridge_native


class Android:
    """Mirror the original socket-RPC `Android` API."""

    def __init__(self, addr=None):
        del addr

    def __getattr__(self, name):

        def call(*args):
            print(
                "[BridgeAll]",
                name,
                "ARGS:",
                repr(args),
                flush=True,
            )

            result = app_bridge_native.call(name, *args)

            try:
                text = repr(result)

                if len(text) > 2000:
                    text = text[:2000] + "...<truncated>"

                print(
                    "[BridgeAllResult]",
                    name,
                    "RESULT:",
                    text,
                    "TYPE:",
                    type(result).__name__,
                    flush=True,
                )
            except Exception as e:
                print(
                    "[BridgeAllResult]",
                    name,
                    "LOG ERROR:",
                    repr(e),
                    flush=True,
                )

            return result

        return call