package com.maris.tools.tool;

import com.maris.tools.service.ExpirationService;
import com.maris.tools.service.RuntimeClockService;
import com.maris.tools.util.ColorUtil;
import com.maris.tools.util.DurationFormatter;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ToolFactory {

    private ToolFactory() {
    }

    public static ItemStack create(ToolDefinition definition, long durationMillis, RuntimeClockService runtimeClockService,
                                   long warningMillis) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ColorUtil.color(definition.name()));
        meta.setLore(buildLore(definition.lore(), durationMillis, warningMillis));
        definition.enchants().forEach((enchant, level) -> meta.addEnchant(enchant, level, true));
        try {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        } catch (Throwable ignored) {
        }
        item.setItemMeta(meta);

        long expiresAt = runtimeClockService.getRuntimeMillis() + durationMillis;
        NBT.modify(item, nbt -> {
            nbt.setString(ExpirationService.NBT_TOOL_ID, definition.id());
            nbt.setLong(ExpirationService.NBT_DURATION, durationMillis);
            nbt.setLong(ExpirationService.NBT_EXPIRES_AT, expiresAt);
        });
        return item;
    }

    public static boolean refreshTimedLore(ItemStack item, ToolDefinition definition, long remainingMillis,
                                           long warningMillis) {
        if (item == null || item.getType() == Material.AIR || definition == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String desiredName = ColorUtil.color(definition.name());
        List<String> desiredLore = buildLore(definition.lore(), remainingMillis, warningMillis);
        List<String> extraLore = extractExtraLore(meta.getLore(), definition);
        if (!extraLore.isEmpty()) {
            desiredLore.addAll(extraLore);
        }
        boolean changed = !desiredName.equals(meta.getDisplayName()) || !desiredLore.equals(meta.getLore());

        meta.setDisplayName(desiredName);
        meta.setLore(desiredLore);
        try {
            if (!meta.getItemFlags().contains(ItemFlag.HIDE_ATTRIBUTES)) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                changed = true;
            }
        } catch (Throwable ignored) {
        }

        if (!changed) {
            return false;
        }

        item.setItemMeta(meta);
        return true;
    }

    private static List<String> buildLore(List<String> baseLore, long remainingMillis, long warningMillis) {
        List<String> lore = new ArrayList<>();
        for (String line : baseLore) {
            lore.add(ColorUtil.color(line));
        }
        appendTimerLore(lore, remainingMillis, warningMillis);
        return lore;
    }

    private static void appendTimerLore(List<String> lore, long remainingMillis, long warningMillis) {
        String color = remainingMillis < 259_200_000L ? "&c" : "&8";
        lore.add(ColorUtil.color(color + "Self Destruct:"));
        lore.add(ColorUtil.color(color + DurationFormatter.formatSelfDestruct(remainingMillis)));
    }

    private static List<String> extractExtraLore(List<String> currentLore, ToolDefinition definition) {
        if (currentLore == null || currentLore.isEmpty()) {
            return List.of();
        }

        List<String> baseLore = new ArrayList<>(definition.lore().size());
        for (String line : definition.lore()) {
            baseLore.add(ColorUtil.color(line));
        }

        int minimumToolLoreLines = baseLore.size() + 2;
        if (currentLore.size() < minimumToolLoreLines) {
            return List.of();
        }

        for (int i = 0; i < baseLore.size(); i++) {
            if (!baseLore.get(i).equals(currentLore.get(i))) {
                return List.of();
            }
        }

        if (!isTimerLabel(currentLore.get(baseLore.size()))) {
            return List.of();
        }

        return new ArrayList<>(currentLore.subList(minimumToolLoreLines, currentLore.size()));
    }

    private static boolean isTimerLabel(String line) {
        if (line == null) {
            return false;
        }
        String normalized = line.replace("\u00A7", "&").toLowerCase();
        return normalized.endsWith("self destruct:");
    }
}
