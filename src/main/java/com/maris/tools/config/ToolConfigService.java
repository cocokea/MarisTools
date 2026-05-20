package com.maris.tools.config;

import com.maris.tools.MarisToolsPlugin;
import com.maris.tools.tool.ToolDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ToolConfigService {

    private final MarisToolsPlugin plugin;
    private final Map<String, ToolDefinition> tools = new HashMap<>();

    public ToolConfigService(MarisToolsPlugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        ConfigurationSection section = config.getConfigurationSection("tools");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection toolSection = section.getConfigurationSection(id);
            if (toolSection == null) {
                continue;
            }

            Material material = Material.matchMaterial(toolSection.getString("material", "STONE"));
            if (material == null) {
                plugin.getLogger().warning("Invalid material for tool " + id);
                continue;
            }

            String name = toolSection.getString("name", id);
            List<String> lore = new ArrayList<>(toolSection.getStringList("lore"));
            boolean hideEnchants = toolSection.getBoolean("hide-enchants", false);
            Map<Enchantment, Integer> enchants = new HashMap<>();
            ConfigurationSection enchantSection = toolSection.getConfigurationSection("enchants");
            if (enchantSection != null) {
                for (String enchantKey : enchantSection.getKeys(false)) {
                    Enchantment enchantment = resolveEnchantment(enchantKey);
                    if (enchantment == null) {
                        plugin.getLogger().warning("Unknown enchantment '" + enchantKey + "' for tool " + id);
                        continue;
                    }
                    enchants.put(enchantment, enchantSection.getInt(enchantKey));
                }
            }
            tools.put(id.toLowerCase(Locale.ROOT), new ToolDefinition(id.toLowerCase(Locale.ROOT), material, name, lore, enchants, hideEnchants));
        }
    }

    public ToolDefinition getTool(String id) {
        return tools.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<String> getToolIds() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    private Enchantment resolveEnchantment(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        NamespacedKey key = normalized.contains(":") ? NamespacedKey.fromString(normalized) : NamespacedKey.minecraft(normalized);

        Enchantment enchantment = resolveViaRegistry(key);
        if (enchantment != null) {
            return enchantment;
        }

        if (key != null) {
            try {
                Enchantment byKey = Enchantment.getByKey(key);
                if (byKey != null) {
                    return byKey;
                }
            } catch (Throwable ignored) {
            }
        }

        String legacy = normalized.toUpperCase(Locale.ROOT).replace(':', '_');
        try {
            return Enchantment.getByName(legacy);
        } catch (Throwable ignored) {
            plugin.getLogger().warning("Failed to resolve enchantment '" + input + "' using registry, key, or legacy lookup.");
            return null;
        }
    }

    private Enchantment resolveViaRegistry(NamespacedKey key) {
        if (key == null) {
            return null;
        }
        try {
            Field field = org.bukkit.Registry.class.getField("ENCHANTMENT");
            Object registry = field.get(null);
            Method getMethod = registry.getClass().getMethod("get", NamespacedKey.class);
            Object value = getMethod.invoke(registry, key);
            return value instanceof Enchantment enchantment ? enchantment : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}