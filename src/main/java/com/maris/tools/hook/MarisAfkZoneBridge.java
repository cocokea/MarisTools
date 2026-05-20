package com.maris.tools.hook;

import com.maris.tools.MarisToolsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class MarisAfkZoneBridge {
    private final MarisToolsPlugin plugin;

    public MarisAfkZoneBridge(MarisToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean activateBooster(Player player) {
        if (player == null) {
            return false;
        }
        Plugin afkPlugin = Bukkit.getPluginManager().getPlugin("MarisAFKZone");
        if (afkPlugin == null || !afkPlugin.isEnabled()) {
            return false;
        }
        try {
            Method durationMethod = afkPlugin.getClass().getMethod("defaultBoosterDurationMillis");
            Method extraMethod = afkPlugin.getClass().getMethod("defaultBoosterExtraShards");
            Method activateMethod = afkPlugin.getClass().getMethod("activateBooster", UUID.class, String.class, long.class, int.class);
            long durationMillis = ((Number) durationMethod.invoke(afkPlugin)).longValue();
            int extraShards = ((Number) extraMethod.invoke(afkPlugin)).intValue();
            Object result = activateMethod.invoke(afkPlugin, player.getUniqueId(), player.getName(), durationMillis, extraShards);
            return result instanceof Boolean value && value;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not activate AFK booster: " + exception.getMessage());
            return false;
        }
    }
}
