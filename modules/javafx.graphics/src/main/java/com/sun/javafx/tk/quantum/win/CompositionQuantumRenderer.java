package com.sun.javafx.tk.quantum.win;

import com.sun.glass.ui.Application;
import com.sun.glass.ui.Screen;
import com.sun.glass.ui.win.WinCompositor;
import com.sun.javafx.tk.quantum.QuantumRenderer;
import com.sun.prism.GraphicsPipeline;
import com.sun.prism.impl.PrismSettings;
import com.sun.scenario.effect.impl.Renderer;
import com.sun.scenario.effect.impl.prism.PrFilterContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CompositionQuantumRenderer extends AbstractExecutorService implements QuantumRenderer {

    private final Thread thread;
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private List<Runnable> scheduledItems = new ArrayList<>();
    private List<Runnable> scheduledItemsOnRendererThread = new ArrayList<>();
    private WinCompositor compositor;
    private Throwable initThrowable;

    public CompositionQuantumRenderer() {
        PrismSettings.isVsyncEnabled = false;

        thread = new Thread(new PipelineRunnable());
        thread.setName("QuantumRenderer");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, thr) -> {
            System.err.println(t.getName() + " uncaught: " + thr.getClass().getName());
            thr.printStackTrace();
        });
        thread.start();

        try {
            initLatch.await();
        } catch (InterruptedException ex) {
            if (PrismSettings.verbose) {
                ex.printStackTrace();
            }

            throw new RuntimeException(ex);
        }
    }

    public WinCompositor getCompositor() {
        return compositor;
    }

    @Override
    public Thread getThread() {
        return thread;
    }

    @Override
    public Throwable initThrowable() {
        return initThrowable;
    }

    @Override
    public void setInitThrowable(Throwable th) {
        initThrowable = th;
    }

    private void afterExecute(Runnable r, Throwable t) {
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

    private void onTick() {
        synchronized (thread) {
            scheduledItemsOnRendererThread.addAll(scheduledItems);

            if (scheduledItems.size() > 10e4) {
                scheduledItems = new ArrayList<>();
            } else {
                scheduledItems.clear();
            }
        }

        for (Runnable item : scheduledItemsOnRendererThread) {
            try {
                item.run();
            } catch (Throwable t) {
                afterExecute(item, t);
                System.err.println(Thread.currentThread().getName() + " uncaught: " + t.getClass().getName());
                t.printStackTrace();
            }
        }

        if (scheduledItemsOnRendererThread.size() > 10e4) {
            scheduledItemsOnRendererThread = new ArrayList<>();
        } else {
            scheduledItemsOnRendererThread.clear();
        }
    }

    @Override
    public void shutdown() {
        compositor.dispose();
        compositor = null;
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> result;

        synchronized (thread) {
            result = new ArrayList<>(scheduledItems);
            scheduledItems.clear();
        }

        return result;
    }

    @Override
    public boolean isShutdown() {
        return compositor == null;
    }

    @Override
    public boolean isTerminated() {
        return compositor == null;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        synchronized (thread) {
            scheduledItems.add(command);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private class PipelineRunnable implements Runnable {
        public void init() {
            try {
                if (GraphicsPipeline.createPipeline() == null) {
                    String MSG = "Error initializing CompositionQuantumRenderer: no suitable pipeline found";
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

                compositor = new WinCompositor();
            } catch (Throwable th) {
                CompositionQuantumRenderer.this.setInitThrowable(th);
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

        @Override
        public void run() {
            try {
                init();
                compositor.run(CompositionQuantumRenderer.this::onTick);
            } finally {
                cleanup();
            }
        }
    }
}
