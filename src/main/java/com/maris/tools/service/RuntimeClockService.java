package com.maris.tools.service;

import com.maris.tools.MarisToolsPlugin;
import com.maris.tools.util.FoliaSupport;

public final class RuntimeClockService {

    private final MarisToolsPlugin plugin;
    private long bootNano;
    private long baseRuntimeMillis;
    private FoliaSupport.ScheduledHandle persistTask;

    public RuntimeClockService(MarisToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.baseRuntimeMillis = plugin.getConfig().getLong("runtime.elapsed-ms", 0L);
        this.bootNano = System.nanoTime();
    }

    public void stop() {
        stopPersistTask();
        persistNow();
    }

    public void reload() {
        long persistedRuntimeMillis = plugin.getConfig().getLong("runtime.elapsed-ms", 0L);
        long currentRuntimeMillis = Math.max(baseRuntimeMillis, getRuntimeMillis());
        this.baseRuntimeMillis = Math.max(currentRuntimeMillis, persistedRuntimeMillis);
        this.bootNano = System.nanoTime();
    }

    public long getRuntimeMillis() {
        long live = (System.nanoTime() - bootNano) / 1_000_000L;
        return baseRuntimeMillis + Math.max(0L, live);
    }

    public void startPersistTask() {
        stopPersistTask();
        this.persistTask = FoliaSupport.runGlobalRepeating(plugin, 1200L, 1200L, this::persistNow);
    }

    public void stopPersistTask() {
        if (persistTask != null) {
            persistTask.cancel();
            persistTask = null;
        }
    }

    public void persistNow() {
        long current = getRuntimeMillis();
        this.baseRuntimeMillis = current;
        this.bootNano = System.nanoTime();
        plugin.getConfig().set("runtime.elapsed-ms", current);
        plugin.saveConfig();
    }
}
