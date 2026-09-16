package us.hebi.graalvm.jfx.substitutions;

import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import javafx.application.Application;
import javafx.application.Preloader;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * glass posts its run loop to the first thread of the process and waits for it. The java launcher
 * parks that thread in a CFRunLoop and runs main on a second one, while a native image runs main on
 * the first thread and hangs. Same method as upstream with the latch wait replaced by the run loop,
 * and only when called on the first thread.
 * <p>
 * Platform.startup() must return to its caller, so it cannot do the same handoff. When the first
 * thread is not already pumping a run loop, e.g. provided by a native launcher, it fails fast
 * instead of hanging.
 *
 * @author Florian Enner
 * @since 26 Aug 2026
 */
public final class MacStartup {

    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "com.sun.javafx.application.LauncherImpl")
    static final class Target_LauncherImpl {

        @Alias
        private static AtomicBoolean launchCalled;

        @Alias
        private static volatile RuntimeException launchException;

        @Alias
        private static native void launchApplication1(Class<? extends Application> appClass,
                Class<? extends Preloader> preloaderClass, String[] args) throws Exception;

        @Substitute
        public static void launchApplication(Class<? extends Application> appClass,
                Class<? extends Preloader> preloaderClass, String[] args) {
            if (com.sun.glass.ui.Application.isEventThread()) {
                throw new IllegalStateException("Application launch must not be called on the JavaFX Application Thread");
            }
            if (launchCalled.getAndSet(true)) {
                throw new IllegalStateException("Application launch must not be called more than once");
            }
            if (!Application.class.isAssignableFrom(appClass)) {
                throw new IllegalArgumentException("Error: " + appClass.getName()
                        + " is not a subclass of javafx.application.Application");
            }
            if (preloaderClass != null && !Preloader.class.isAssignableFrom(preloaderClass)) {
                throw new IllegalArgumentException("Error: " + preloaderClass.getName()
                        + " is not a subclass of javafx.application.Preloader");
            }

            Thread launcherThread = new Thread(() -> {
                try {
                    launchApplication1(appClass, preloaderClass, args);
                } catch (RuntimeException rte) {
                    launchException = rte;
                } catch (Exception ex) {
                    launchException = new RuntimeException("Application launch exception", ex);
                } catch (Error err) {
                    launchException = new RuntimeException("Application launch error", err);
                }
            });
            launcherThread.setName("JavaFX-Launcher");
            launcherThread.start();
            if (CoreFoundation.isMainThread()) {
                CoreFoundation.runMainLoopWhileAlive(launcherThread);
            } else {
                try {
                    launcherThread.join();
                } catch (InterruptedException ex) {
                    throw new RuntimeException("Unexpected exception: ", ex);
                }
            }

            if (launchException != null) {
                throw launchException;
            }
        }

    }

    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "javafx.application.Platform")
    static final class Target_Platform {

        @Substitute
        public static void startup(Runnable runnable) {
            if (!Target_PlatformImpl.initialized.get()
                    && "Mac".equals(Target_GlassPlatform.determinePlatform())
                    && !CoreFoundation.isFirstThreadInRunLoop()) {
                throw new IllegalStateException("Platform.startup() requires the first thread of a macOS process"
                        + " to run a CFRunLoop, which a native image does not provide. Use Application.launch(),"
                        + " or a wrapper launcher similar to native-launchers-maven-plugin.");
            }
            com.sun.javafx.application.PlatformImpl.startup(runnable, true);
        }

    }

    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "com.sun.javafx.application.PlatformImpl")
    static final class Target_PlatformImpl {

        @Alias
        static AtomicBoolean initialized;

    }

    // Headless and other pure-Java glass platforms never need the first thread
    @Platforms(Platform.MACOS.class)
    @TargetClass(className = "com.sun.glass.ui.Platform")
    static final class Target_GlassPlatform {

        @Alias
        static native String determinePlatform();

    }

    @Platforms(Platform.MACOS.class)
    private static final class CoreFoundation {

        private static final int kCFStringEncodingUTF8 = 0x08000100;

        static boolean isMainThread() {
            return pthreadMainNp() != 0;
        }

        // The java launcher parks in CFRunLoopRunInMode(kCFRunLoopDefaultMode, DBL_MAX, false) and
        // relies on the JVM exiting from another thread. Polling keeps the first thread from outliving
        // a launcher that fails before the run loop is up.
        static void runMainLoopWhileAlive(Thread thread) {
            try (CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString("kCFRunLoopDefaultMode")) {
                // Modes are compared by string value, so the mode does not have to be imported as a symbol.
                PointerBase defaultMode = createString(WordFactory.nullPointer(), name.get(), kCFStringEncodingUTF8);
                while (thread.isAlive()) {
                    runInMode(defaultMode, 0.25, (byte) 0);
                }
                release(defaultMode);
            }
        }

        // A blocked first thread cannot pump the queue glass posts its startup to, so the toolkit
        // can only come up when the caller is on another thread and the first thread runs a loop.
        static boolean isFirstThreadInRunLoop() {
            if (isMainThread()) {
                return false;
            }
            PointerBase mode = copyCurrentMode(getMainRunLoop());
            if (mode.isNull()) {
                return false;
            }
            release(mode);
            return true;
        }

        @CFunction("pthread_main_np")
        private static native int pthreadMainNp();

        @CFunction("CFRunLoopGetMain")
        private static native PointerBase getMainRunLoop();

        @CFunction("CFRunLoopCopyCurrentMode")
        private static native PointerBase copyCurrentMode(PointerBase runLoop);

        @CFunction("CFStringCreateWithCString")
        private static native PointerBase createString(PointerBase allocator, CCharPointer cString, int encoding);

        @CFunction("CFRunLoopRunInMode")
        private static native int runInMode(PointerBase mode, double seconds, byte returnAfterSourceHandled);

        @CFunction("CFRelease")
        private static native void release(PointerBase reference);

        private CoreFoundation() {
        }

    }

    private MacStartup() {
    }

}
