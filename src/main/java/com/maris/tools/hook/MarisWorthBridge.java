
package com.maris.tools.hook;

import com.maris.tools.MarisToolsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class MarisWorthBridge {

    private final MarisToolsPlugin plugin;
    private Plugin cachedWorthPlugin;

    public MarisWorthBridge(MarisToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isHooked() {
        Plugin worth = getWorthPlugin();
        return worth != null && worth.isEnabled();
    }

    public boolean sellContainer(Player player, Inventory inventory, String sourceName) {
        Plugin worth = getWorthPlugin();
        if (worth == null || !worth.isEnabled()) {
            return false;
        }
        try {
            Method method = worth.getClass().getMethod("sellContainerContents", Player.class, Inventory.class, String.class);
            method.invoke(worth, player, inventory, sourceName);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not call MarisWorth hook: " + throwable.getMessage());
            return false;
        }
    }

    private Plugin getWorthPlugin() {
        if (cachedWorthPlugin != null && cachedWorthPlugin.isEnabled()) {
            return cachedWorthPlugin;
        }
        cachedWorthPlugin = Bukkit.getPluginManager().getPlugin("MarisWorth");
        return cachedWorthPlugin;
    }
}
