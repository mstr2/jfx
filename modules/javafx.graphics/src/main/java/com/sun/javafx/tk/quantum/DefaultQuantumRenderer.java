package com.sun.javafx.tk.quantum;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.glass.ui.Application;
import com.sun.glass.ui.Screen;
import com.sun.javafx.tk.RenderJob;
import com.sun.prism.GraphicsPipeline;
import com.sun.prism.impl.PrismSettings;
import com.sun.scenario.effect.impl.Renderer;
import com.sun.scenario.effect.impl.prism.PrFilterContext;
import java.util.HashMap;

/*
 * Quantum Renderer
 */
final class DefaultQuantumRenderer extends ThreadPoolExecutor implements QuantumRenderer  {
    private Thread          _renderer;
    private Throwable       _initThrowable = null;
    private CountDownLatch  initLatch = new CountDownLatch(1);

    DefaultQuantumRenderer() throws InterruptedException {
        super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        setThreadFactory(new QuantumThreadFactory());
        prestartCoreThread();
        initLatch.await();
    }

    @Override
    public Thread getThread() {
        return _renderer;
    }

    @Override
    public Throwable initThrowable() {
        return _initThrowable;
    }

    @Override
    public void setInitThrowable(Throwable th) {
        _initThrowable = th;
    }

    private class PipelineRunnable implements Runnable {
        private Runnable    work;

        public PipelineRunnable(Runnable runner) {
            work = runner;
        }

        public void init() {
            try {
                if (GraphicsPipeline.createPipeline() == null) {
                    String MSG = "Error initializing QuantumRenderer: no suitable pipeline found";
                    System.err.println(MSG);
                    throw new RuntimeException(MSG);
                } else {
                    Map device = GraphicsPipeline.getPipeline().getDeviceDetails();
                    if (device == null) {
                        device = new HashMap();
                    }
                    device.put(com.sun.glass.ui.View.Capability.kHiDPIAwareKey,
                               PrismSettings.allowHiDPIScaling);
                    Map map =  Application.getDeviceDetails();
                    if (map != null) {
                        device.putAll(map);
                    }
                    Application.setDeviceDetails(device);
                }
            } catch (Throwable th) {
                DefaultQuantumRenderer.this.setInitThrowable(th);
            } finally {
                initLatch.countDown();
            }
        }

        public void cleanup() {
            GraphicsPipeline pipeline = GraphicsPipeline.getPipeline();
            if (pipeline != null) {
                pipeline.dispose();
            }
        }

        @Override public void run() {
            try {
                init();
                work.run();
            } finally {
                cleanup();
            }
        }
    }

    private class QuantumThreadFactory implements ThreadFactory {
        final AtomicInteger threadNumber = new AtomicInteger(0);

        @Override public Thread newThread(Runnable r) {
            final PipelineRunnable pipeline = new PipelineRunnable(r);
            Thread th = new Thread(pipeline);
            th.setName("QuantumRenderer-" + threadNumber.getAndIncrement());
            th.setDaemon(true);
            th.setUncaughtExceptionHandler((t, thr) -> {
                System.err.println(t.getName() + " uncaught: " + thr.getClass().getName());
                thr.printStackTrace();
            });
            _renderer = th;

            assert threadNumber.get() == 1;

            return _renderer;
        }
    }

    @Override protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return (RenderJob)runnable;
    }

    /* java.util.concurrent.ThreadPoolExecutor */

    @Override public void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        /*
         * clean up what we can after every render job
         *
         * we should really be keeping RenderJob/Scene pools
         */
        if (usePurgatory) {
            Screen screen = Screen.getMainScreen();
            Renderer renderer = Renderer.getRenderer(PrFilterContext.getInstance(screen));
            renderer.releasePurgatory();
        }
    }
}
