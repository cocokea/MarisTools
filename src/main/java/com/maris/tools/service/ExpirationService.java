package com.maris.tools.service;

import com.maris.tools.MarisToolsPlugin;
import com.maris.tools.tool.ToolDefinition;
import com.maris.tools.tool.ToolFactory;
import com.maris.tools.util.FoliaSupport;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;

public final class ExpirationService {

    public static final String NBT_TOOL_ID = "maristools-id";
    public static final String NBT_EXPIRES_AT = "maristools-expire-runtime-ms";
    public static final String NBT_DURATION = "maristools-duration-ms";

    private final MarisToolsPlugin plugin;
    private final RuntimeClockService runtimeClockService;
    private FoliaSupport.ScheduledHandle scanTask;

    public ExpirationService(MarisToolsPlugin plugin, RuntimeClockService runtimeClockService) {
        this.plugin = plugin;
        this.runtimeClockService = runtimeClockService;
    }

    public void start() {
        stop();
        runtimeClockService.startPersistTask();
        long scanTicks = plugin.getConfig().getLong("settings.expired-scan-ticks", 200L);
        this.scanTask = FoliaSupport.runGlobalRepeating(plugin, scanTicks, scanTicks, this::scanAllAccessibleInventories);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        runtimeClockService.stopPersistTask();
    }

    /**
     * Prefer calling MarisToolsPlugin.reloadAll() so scheduler lifecycle stays centralized.
     */
    @Deprecated(forRemoval = false)
    public void reload() {
        start();
        scanAllAccessibleInventories();
    }

    public long getRemainingMillis(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return -1L;
        }
        return NBT.get(itemStack, nbt -> nbt.hasTag(NBT_EXPIRES_AT) ? nbt.getLong(NBT_EXPIRES_AT) - runtimeClockService.getRuntimeMillis() : -1L);
    }

    public boolean isExpired(ItemStack itemStack) {
        if (!hasTimer(itemStack)) {
            return false;
        }
        return getRemainingMillis(itemStack) <= 0L;
    }

    public boolean hasTimer(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }
        return NBT.get(itemStack, (Function<de.tr7zw.nbtapi.iface.ReadableItemNBT, Boolean>) nbt -> nbt.hasTag(NBT_EXPIRES_AT));
    }

    /**
     * Inventory reads/writes must happen on the owning player's thread on Folia.
     * Do not inline inventory access into the global scheduler callback.
     */
    public void scanOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaSupport.runForPlayer(plugin, player, () -> {
                scanInventory(player, player.getInventory(), true, true);
                scanInventory(player, player.getEnderChest(), false, true);
                if (player.getOpenInventory() != null) {
                    scanInventory(player, player.getOpenInventory().getTopInventory(), false, true);
                }
            });
        }
    }

    public void scanAllAccessibleInventories() {
        // Folia-safe periodic scan:
        // only inspect inventories that are already directly accessible through players.
        // Container inventories are refreshed via event-driven scans (open/close/click/move/pickup).
        scanOnlinePlayers();
    }

    /**
     * Called by a global repeating task, but each player's inventories are dispatched
     * back onto that player's scheduler before any read/write occurs.
     */
    public void refreshLoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaSupport.runForPlayer(plugin, player, () -> {
                scanInventory(player, player.getInventory(), false, true);
                scanInventory(player, player.getEnderChest(), false, true);
                if (player.getOpenInventory() != null) {
                    scanInventory(player, player.getOpenInventory().getTopInventory(), false, true);
                }
            });
        }
    }

    public void scanContainerState(BlockState state) {
        if (state instanceof Container container) {
            scanInventory(null, container.getInventory(), false, true);
        }
    }

    public void scanInventory(Player owner, Inventory inventory, boolean notify, boolean refreshLore) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR || !hasTimer(item)) {
                continue;
            }
            if (isExpired(item)) {
                contents[slot] = null;
                changed = true;
                if (notify && owner != null) {
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }
                continue;
            }
            if (refreshLore) {
                long remaining = getRemainingMillis(item);
                String toolId = NBT.get(item, (Function<de.tr7zw.nbtapi.iface.ReadableItemNBT, String>) nbt -> nbt.getOrNull(NBT_TOOL_ID, String.class));
                ToolDefinition definition = toolId == null ? null : plugin.toolConfigService().getTool(toolId);
                long warningMillis = plugin.getConfig().getLong("settings.timer-warning-ms", 86_400_000L);
                if (ToolFactory.refreshTimedLore(item, definition, remaining, warningMillis)) {
                    contents[slot] = item;
                    changed = true;
                }
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }
}
