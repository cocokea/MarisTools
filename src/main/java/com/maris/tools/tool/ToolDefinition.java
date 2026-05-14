package com.maris.tools.tool;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public record ToolDefinition(String id, Material material, String name, List<String> lore,
                             Map<Enchantment, Integer> enchants) {
}
