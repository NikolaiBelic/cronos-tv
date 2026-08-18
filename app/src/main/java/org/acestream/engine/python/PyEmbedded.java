package org.acestream.engine.python;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PyEmbedded {

    private static final String TAG = "CronosTV/PyEmbedded";

    private static boolean libraryLoaded = false;

    private final Context context;
    private final AtomicInteger pid = new AtomicInteger(-1);

    private final File pythonBinary;
    private final File mainScript;
    private final File stdLog;

    private final List<String> args = new ArrayList<>();
    private final List<String> env = new ArrayList<>();

    static {
        try {
            System.loadLibrary("pyembedded");
            libraryLoaded = true;
            Log.d(TAG, "libpyembedded cargado correctamente");
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            Log.e(TAG, "Error cargando libpyembedded", e);
        }
    }

    public PyEmbedded(Context context) {
        this.context = context.getApplicationContext();

        File filesDir = this.context.getFilesDir();
        File externalDir = this.context.getExternalFilesDir(null);

        if (externalDir == null) {
            throw new IllegalStateException("External files dir no disponible");
        }

        // La versión moderna de AceStream usa libpython38.so
        ApplicationInfo appInfo = this.context.getApplicationInfo();

        pythonBinary = new File(
                appInfo.nativeLibraryDir,
                "libpython38.so"
        );

        mainScript = new File(
                filesDir,
                "main.py"
        );

        stdLog = new File(
                externalDir,
                "acestream_std.log"
        );

        File tempDir = new File(externalDir, "tmp");

        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        // Token local para esta instancia
        String accessToken =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 10);

        Log.d(
                TAG,
                "CRONOS_DEBUG accessToken=" + accessToken
        );

        // Mismos parámetros básicos que usa AceStream
        args.add("--log-file");
        args.add(
                new File(
                        externalDir,
                        "acestream.log"
                ).getAbsolutePath()
        );

        args.add("--access-token");
        args.add(accessToken);

        args.add("--api-port");
        args.add("62062");

        args.add("--http-port");
        args.add("6878");

        File pythonDir =
                new File(filesDir, "python");

        env.add(
                "PYTHONHOME=" +
                        pythonDir.getAbsolutePath()
        );

        env.add(
                "PYTHONPATH=" +
                        new File(pythonDir, "lib").getAbsolutePath() +
                        ":" +
                        new File(
                                pythonDir,
                                "lib/modules"
                        ).getAbsolutePath() +
                        ":" +
                        new File(
                                pythonDir,
                                "lib/stdlib.zip"
                        ).getAbsolutePath() +
                        ":" +
                        filesDir.getAbsolutePath()
        );

        env.add(
                "LD_LIBRARY_PATH=" +
                        new File(
                                pythonDir,
                                "lib"
                        ).getAbsolutePath() +
                        ":" +
                        appInfo.nativeLibraryDir
        );

        env.add(
                "TEMP=" +
                        tempDir.getAbsolutePath()
        );

        env.add(
                "ACESTREAM_HOME=" +
                        externalDir.getAbsolutePath()
        );

        env.add("ANDROID_ROOT=/system");
        env.add("ANDROID_DATA=/data");

        Log.d(
                TAG,
                "Python binary: " +
                        pythonBinary.getAbsolutePath()
        );

        Log.d(
                TAG,
                "main.py: " +
                        mainScript.getAbsolutePath()
        );
    }

    public static native String getCompiledABI();

    private native int runScript(
            Context context,
            String pythonBinary,
            String[] args,
            String[] env,
            String workingDir,
            String logPath,
            int[] pid
    );

    private native int waitPid(int pid);

    public boolean isAlive() {
        return pid.get() != -1;
    }

    public void start() {

        if (!libraryLoaded) {
            throw new IllegalStateException(
                    "libpyembedded no está cargado"
            );
        }

        if (!mainScript.exists()) {
            throw new IllegalStateException(
                    "No existe main.py: " +
                            mainScript.getAbsolutePath()
            );
        }

        if (isAlive()) {
            Log.d(TAG, "Engine ya está ejecutándose");
            return;
        }

        Log.d(
                TAG,
                "Usando Python binary: " +
                        pythonBinary.getAbsolutePath()
        );

        int[] outputPid = new int[1];

        ArrayList<String> argv =
                new ArrayList<>();

        argv.add(
                mainScript.getAbsolutePath()
        );

        argv.addAll(args);

        Log.d(
                TAG,
                "Python binary exists=" + pythonBinary.exists()
        );

        Log.d(
                TAG,
                "Intentando arrancar main.py..."
        );

        int result = runScript(
                context,
                pythonBinary.getAbsolutePath(),
                argv.toArray(new String[0]),
                env.toArray(new String[0]),
                mainScript.getParent(),
                stdLog.getAbsolutePath(),
                outputPid
        );

        Log.d(
                TAG,
                "runScript result=" + result +
                        " pid=" + outputPid[0]
        );

        if (result < 0) {
            throw new IllegalStateException(
                    "runScript falló: " + result
            );
        }

        pid.set(outputPid[0]);

        Log.d(
                TAG,
                "🚀 Proceso Engine arrancado. PID=" +
                        pid.get()
        );

        new Thread(() -> {

            int currentPid = pid.get();

            int exitCode =
                    waitPid(currentPid);

            Log.d(
                    TAG,
                    "Proceso Engine terminado. PID=" +
                            currentPid +
                            " exitCode=" +
                            exitCode
            );

            pid.set(-1);

        }, "CronosTV-Engine-Waiter").start();
    }

    public void kill() {
        if (isAlive()) {

            int currentPid = pid.get();

            Process.killProcess(currentPid);

            Log.d(
                    TAG,
                    "Proceso Engine detenido. PID=" +
                            currentPid
            );
        }
    }
}