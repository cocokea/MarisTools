package com.maris.tools.listener;

import com.maris.tools.MarisToolsPlugin;
import com.maris.tools.service.ExpirationService;
import com.maris.tools.service.RuntimeClockService;
import com.maris.tools.util.FoliaSupport;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class ToolListener implements Listener {

    private final Set<Material> blacklist;
    private final Set<String> blacklistedWorlds;
    private static final BlockFace[] CARDINALS = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    private static final int[][] LOG_NEIGHBORS = new int[][]{
            {0, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, 1, 0}, {-1, 1, 0},
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
            {0, 2, 0}, {1, 2, 0}, {-1, 2, 0}, {0, 2, 1}, {0, 2, -1}
    };
    private static final int[][] VEIN_NEIGHBORS = createVeinNeighbors();

    private final MarisToolsPlugin plugin;
    private final ExpirationService expirationService;
    private FoliaSupport.ScheduledHandle loreRefreshTask;

    public ToolListener(MarisToolsPlugin plugin, RuntimeClockService runtimeClockService,
                        ExpirationService expirationService) {
        this.plugin = plugin;
        this.expirationService = expirationService;
        this.blacklist = loadBlacklist(plugin);
        this.blacklistedWorlds = loadBlacklistedWorlds(plugin);
        restartLoreRefreshTask();
    }

    public void restartLoreRefreshTask() {
        stopLoreRefreshTask();
        long ticks = plugin.getConfig().getLong("settings.timer-refresh-ticks", 6000L);
        if (ticks <= 0L) {
            return;
        }
        this.loreRefreshTask = FoliaSupport.runGlobalRepeating(plugin, ticks, ticks, expirationService::refreshLoreOnlinePlayers);
    }

    public void stopLoreRefreshTask() {
        if (loreRefreshTask != null) {
            loreRefreshTask.cancel();
            loreRefreshTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String toolId = getToolId(tool);
        if (toolId == null) {
            return;
        }
        if (expirationService.isExpired(tool)) {
            breakExpiredMainHandTool(player);
            event.setCancelled(true);
            return;
        }

        if (isDrillTool(toolId)) {
            event.setCancelled(true);
            FoliaSupport.runAtLocation(plugin, event.getBlock().getLocation(), () -> runDrill(player, event.getBlock(), tool));
            return;
        }
        if (isWorldBlacklisted(event.getBlock().getWorld())) {
            return;
        }
        if (toolId.equals("veinminer")) {
            if (plugin.getConfig().getBoolean("settings.veinminer-requires-sneak", true) && !player.isSneaking()) {
                return;
            }
            if (!isVeinMineable(event.getBlock().getType())) {
                return;
            }
            if (plugin.getConfig().getBoolean("settings.veinminer-need-correct-tool", true)
                    && !event.getBlock().isPreferredTool(tool)) {
                return;
            }
            event.setCancelled(true);
            runVeinMiner(player, event.getBlock(), tool);
            return;
        }
        if (toolId.equals("treechopper")) {
            event.setCancelled(true);
            FoliaSupport.runAtLocation(plugin, event.getBlock().getLocation(), () -> runTreeChopper(player, event.getBlock(), tool));
            return;
        }
        if (toolId.equals("sellaxe")) {
            event.setCancelled(true);
            FoliaSupport.runAtLocation(plugin, event.getBlock().getLocation(), () -> handleSellAxe(player, event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        String toolId = getToolId(item);
        if (toolId == null) {
            return;
        }
        Player player = event.getPlayer();
        if (expirationService.isExpired(item)) {
            breakExpiredMainHandTool(player);
            event.setCancelled(true);
            return;
        }

        if (toolId.equals("sellaxe")) {
            if (isWorldBlacklisted(player.getWorld())) {
                return;
            }
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                Block clicked = event.getClickedBlock();
                if (clicked != null) {
                    FoliaSupport.runAtLocation(plugin, clicked.getLocation(), () -> handleSellAxe(player, clicked));
                }
            }
            return;
        }

        if (toolId.equals("bucket") && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            if (isWorldBlacklisted(player.getWorld())) {
                return;
            }
            Block target = resolveBucketTarget(event);
            if (target != null && isWater(target)) {
                ItemStack originalBucket = item.clone();
                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                FoliaSupport.runAtLocation(plugin, target.getLocation(), () -> {
                    drainWaterInstant(player, target, 27);
                    restoreBucket(player, event.getHand(), originalBucket);
                });
            }
            return;
        }

        if (toolId.equals("rocket") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            int slot = player.getInventory().getHeldItemSlot();
            ItemStack snapshot = item.clone();
            restoreSlotLater(player, slot, snapshot, 3);
        }
    }

    private boolean isDrillTool(String toolId) {
        return toolId.equals("drill") || toolId.equals("shovel");
    }

    private void handleSellAxe(Player player, Block clicked) {
        Inventory inventory = null;
        String sourceName = null;
        BlockState state = clicked.getState();
        if (state instanceof Chest chest) {
            inventory = chest.getInventory();
            sourceName = inventory instanceof DoubleChestInventory ? "Large Chest" : "Chest";
        } else if (state instanceof Barrel barrel) {
            inventory = barrel.getInventory();
            sourceName = "Barrel";
        } else if (state instanceof ShulkerBox shulkerBox) {
            inventory = shulkerBox.getInventory();
            sourceName = "Shulker Box";
        }
        if (inventory == null) {
            return;
        }
        if (!plugin.marisWorthBridge().isHooked()) {
            plugin.getLogger().warning("MarisWorth is not hooked, sell axe action skipped.");
            return;
        }
        plugin.marisWorthBridge().sellContainer(player, inventory, sourceName);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack original = event.getItemStack();
        if (!"bucket".equalsIgnoreCase(getToolId(original))) {
            ItemStack handItem = event.getPlayer().getInventory().getItem(event.getHand());
            if (!"bucket".equalsIgnoreCase(getToolId(handItem))) {
                return;
            }
            original = handItem.clone();
        }

        event.setCancelled(true);
        restoreBucket(event.getPlayer(), event.getHand(), original);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        ItemStack original = event.getPlayer().getInventory().getItem(event.getHand());
        if ("bucket".equalsIgnoreCase(getToolId(original))) {
            event.setCancelled(true);
            restoreBucket(event.getPlayer(), event.getHand(), original.clone());
        }
    }

    private void restoreBucket(Player player, EquipmentSlot hand, ItemStack original) {
        if (!"bucket".equalsIgnoreCase(getToolId(original))) {
            return;
        }
        ItemStack restored = original.clone();
        restored.setType(Material.BUCKET);
        restored.setAmount(1);
        FoliaSupport.runNextTick(plugin, player, () -> {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(restored);
            } else {
                player.getInventory().setItemInMainHand(restored);
            }
            player.updateInventory();
            expirationService.scanInventory(player, player.getInventory(), false, true);
        });
    }

    private void restoreSlotLater(Player player, int slot, ItemStack snapshot, int repeats) {
        if (repeats <= 0) {
            expirationService.scanInventory(player, player.getInventory(), false, true);
            return;
        }
        FoliaSupport.runNextTick(plugin, player, () -> {
            player.getInventory().setItem(slot, snapshot.clone());
            restoreSlotLater(player, slot, snapshot, repeats - 1);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSellAxeEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if ("sellaxe".equalsIgnoreCase(getToolId(hand))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSellAxeDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if ("sellaxe".equalsIgnoreCase(getToolId(player.getInventory().getItemInMainHand()))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (getToolId(next) != null) {
            Sound sound = resolveConfiguredSound("settings.held-sound", "BLOCK_AMETHYST_BLOCK_RESONATE");
            event.getPlayer().playSound(event.getPlayer().getLocation(), sound, 1f, 1f);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        expirationService.scanInventory(event.getPlayer(), event.getPlayer().getInventory(), true, true);
        expirationService.scanInventory(event.getPlayer(), event.getPlayer().getEnderChest(), false, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!"booster".equalsIgnoreCase(getToolId(item))) {
            return;
        }
        Player player = event.getPlayer();
        if (expirationService.isExpired(item)) {
            breakExpiredConsumedTool(player, event.getHand());
            return;
        }
        if (!plugin.marisAfkZoneBridge().activateBooster(player)) {
            return;
        }
        event.setReplacement(new ItemStack(Material.AIR));
        Sound sound = resolveConfiguredSound("settings.booster-success-sound", "BLOCK_AMETHYST_BLOCK_FALL");
        FoliaSupport.runNextTick(plugin, player, () -> {
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.playSound(player.getLocation(), sound, 1f, 1f);
            player.updateInventory();
            expirationService.scanInventory(player, player.getInventory(), false, true);
        });
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        expirationService.scanInventory(event.getPlayer() instanceof Player p ? p : null, event.getInventory(), false, true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked != null) {
            expirationService.scanInventory(null, clicked, false, true);
        }
        if (event.getView().getTopInventory() != null) {
            expirationService.scanInventory(null, event.getView().getTopInventory(), false, true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        expirationService.scanInventory(event.getPlayer() instanceof Player p ? p : null, event.getInventory(), false, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        expirationService.scanInventory(null, event.getSource(), false, true);
        expirationService.scanInventory(null, event.getDestination(), false, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        expirationService.scanInventory(null, event.getInventory(), false, true);
    }

    private void runDrill(Player player, Block center, ItemStack tool) {
        Sound breakSound = resolveConfiguredSound("settings.drill-break-sound", "BLOCK_AMETHYST_BLOCK_BREAK");

        double dx = player.getEyeLocation().getDirection().getX();
        double dz = player.getEyeLocation().getDirection().getZ();
        boolean xDominant = Math.abs(dx) >= Math.abs(dz);

        if (xDominant) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (!canUseTool(tool)) {
                        break;
                    }
                    breakDrillBlock(player, center.getWorld().getBlockAt(center.getX(), center.getY() + y, center.getZ() + z), tool);
                }
            }
        } else {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (!canUseTool(tool)) {
                        break;
                    }
                    breakDrillBlock(player, center.getWorld().getBlockAt(center.getX() + x, center.getY() + y, center.getZ()), tool);
                }
            }
        }

        updateMainHandTool(player, tool);
        player.playSound(center.getLocation(), breakSound, 1f, 1f);
    }

    private void breakDrillBlock(Player player, Block block, ItemStack tool) {
        if (block.getType() == Material.AIR) {
            return;
        }
        if (blacklist.contains(block.getType())) {
            return;
        }
        breakToolBlock(player, block, tool);
    }

    private void runVeinMiner(Player player, Block start, ItemStack tool) {
        Material targetType = start.getType();
        if (!isVeinMineable(targetType)) {
            breakToolBlock(player, start, tool);
            updateMainHandTool(player, tool);
            return;
        }

        int max = Math.max(1, plugin.getConfig().getInt("settings.veinminer-max-blocks", 48));
        int searchRadius = Math.max(1, plugin.getConfig().getInt("settings.veinminer-search-radius", 1));
        Queue<Block> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>(Math.min(max * 2, 512));
        List<Block> toBreak = new ArrayList<>(Math.min(max, 128));
        int totalExp = 0;
        queue.add(start);

        while (!queue.isEmpty() && toBreak.size() < max) {
            Block block = queue.poll();
            long key = blockKey(block);
            if (!visited.add(key) || block.getType() != targetType) {
                continue;
            }
            toBreak.add(block);
            for (int[] offset : veinOffsets(searchRadius)) {
                Block relative = block.getRelative(offset[0], offset[1], offset[2]);
                if (relative.getType() == targetType) {
                    queue.add(relative);
                }
            }
        }

        for (Block block : toBreak) {
            if (!canUseTool(tool)) {
                break;
            }
            totalExp += breakToolBlock(player, block, tool);
        }

        awardVeinExperience(player, start.getLocation(), totalExp);
        updateMainHandTool(player, tool);
        player.playSound(start.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1f);
    }

    private void runTreeChopper(Player player, Block start, ItemStack tool) {
        if (!isLogLike(start.getType())) {
            breakToolBlock(player, start, tool);
            updateMainHandTool(player, tool);
            return;
        }
        int max = plugin.getConfig().getInt("settings.tree-max-blocks", 512);
        Queue<Block> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>(Math.min(max * 2, 2048));
        List<Block> toBreak = new ArrayList<>(Math.min(max, 128));
        queue.add(start);
        while (!queue.isEmpty() && toBreak.size() < max) {
            Block block = queue.poll();
            long key = blockKey(block);
            if (!visited.add(key)) {
                continue;
            }
            if (!isLogLike(block.getType()) || blacklist.contains(block.getType())) {
                continue;
            }
            toBreak.add(block);
            for (int[] offset : LOG_NEIGHBORS) {
                Block relative = block.getRelative(offset[0], offset[1], offset[2]);
                if (isLogLike(relative.getType())) {
                    queue.add(relative);
                }
            }
        }
        for (Block block : toBreak) {
            if (!canUseTool(tool)) {
                break;
            }
            breakToolBlock(player, block, tool);
        }
        updateMainHandTool(player, tool);
    }

    private void drainWaterInstant(Player player, Block origin, int limit) {
        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 1f, 1f);
        Queue<Block> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>(Math.min(limit * 2, 128));
        List<Block> toDrain = new ArrayList<>(Math.min(limit, 27));
        queue.add(origin);

        while (!queue.isEmpty() && toDrain.size() < limit) {
            Block block = queue.poll();
            long key = blockKey(block);
            if (!visited.add(key) || !isWater(block)) {
                continue;
            }
            toDrain.add(block);
            for (BlockFace face : CARDINALS) {
                Block relative = block.getRelative(face);
                if (!visited.contains(blockKey(relative)) && isWater(relative)) {
                    queue.add(relative);
                }
            }
        }

        for (Block block : toDrain) {
            if (block.getBlockData() instanceof Waterlogged waterlogged) {
                waterlogged.setWaterlogged(false);
                block.setBlockData(waterlogged, false);
                continue;
            }
            block.setType(Material.AIR, false);
        }
    }

    private int breakToolBlock(Player player, Block block, ItemStack tool) {
        if (!canUseTool(tool) || block.getType() == Material.AIR) {
            return 0;
        }
        Location dropLocation = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (ItemStack drop : block.getDrops(tool, player)) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
                continue;
            }
            block.getWorld().dropItemNaturally(dropLocation, drop);
        }
        int exp = resolveExpDrop(block, player, tool);
        block.setType(Material.AIR, false);
        damageTool(tool);
        return exp;
    }

    private void damageTool(ItemStack tool) {
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        boolean shouldDamage = unbreakingLevel <= 0
                || ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0;
        if (!shouldDamage) {
            return;
        }
        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= tool.getType().getMaxDurability()) {
            tool.setAmount(0);
            return;
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
    }

    private boolean canUseTool(ItemStack tool) {
        return tool != null && tool.getType() != Material.AIR && tool.getAmount() > 0;
    }

    private void updateMainHandTool(Player player, ItemStack tool) {
        player.getInventory().setItemInMainHand(canUseTool(tool) ? tool : null);
    }

    private void breakExpiredMainHandTool(Player player) {
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
    }

    private void breakExpiredConsumedTool(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.updateInventory();
    }

    private Block resolveBucketTarget(PlayerInteractEvent event) {
        Block target = event.getClickedBlock();
        if (target == null) {
            target = event.getPlayer().getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
        }
        if (target == null) {
            return null;
        }
        Block center = target.getType() == Material.WATER ? target : target.getRelative(BlockFace.UP);
        return center.getType() == Material.WATER ? center : null;
    }

    private Sound resolveConfiguredSound(String path, String fallbackName) {
        String configured = plugin.getConfig().getString(path, fallbackName);
        Sound resolved = resolveSound(configured);
        if (resolved != null) {
            return resolved;
        }
        plugin.getLogger().warning("Invalid sound '" + configured + "' for " + path + ", falling back to " + fallbackName);
        Sound fallback = resolveSound(fallbackName);
        return fallback != null ? fallback : Sound.BLOCK_AMETHYST_BLOCK_BREAK;
    }

    private Sound resolveSound(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return (Sound) Sound.class.getField(value.trim().toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException ignored) {
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        NamespacedKey directKey = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized.replace('_', '.'));
        if (directKey != null) {
            Sound direct = Registry.SOUNDS.get(directKey);
            if (direct != null) {
                return direct;
            }
        }
        NamespacedKey legacyKey = NamespacedKey.minecraft(normalized.replace('.', '_'));
        return Registry.SOUNDS.get(legacyKey);
    }

    private Set<Material> loadBlacklist(MarisToolsPlugin plugin) {
        List<String> entries = plugin.getConfig().getStringList("settings.blacklist-blocks");
        Set<Material> materials = new HashSet<>();
        for (String entry : entries) {
            Material material = Material.matchMaterial(entry);
            if (material == null) {
                plugin.getLogger().warning("Unknown blacklist material in config.yml: " + entry);
                continue;
            }
            materials.add(material);
        }
        if (materials.isEmpty()) {
            materials.add(Material.BEDROCK);
        }
        return Set.copyOf(materials);
    }

    private Set<String> loadBlacklistedWorlds(MarisToolsPlugin plugin) {
        List<String> entries = plugin.getConfig().getStringList("settings.blacklist-worlds");
        Set<String> worlds = new HashSet<>();
        for (String entry : entries) {
            if (entry != null && !entry.isBlank()) {
                worlds.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(worlds);
    }

    private boolean isWorldBlacklisted(World world) {
        if (world == null) {
            return false;
        }
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (blacklistedWorlds.contains(worldName)) {
            return true;
        }
        String worldKey = world.getKey().toString().toLowerCase(Locale.ROOT);
        if (blacklistedWorlds.contains(worldKey)) {
            return true;
        }
        return blacklistedWorlds.contains(world.getKey().getKey().toLowerCase(Locale.ROOT));
    }

    private boolean isWater(Block block) {
        if (block == null) {
            return false;
        }
        if (block.getType() == Material.WATER) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private boolean isLogLike(Material material) {
        return Tag.LOGS.isTagged(material) || material.name().endsWith("_STEM") || material.name().endsWith("_HYPHAE") || material.name().endsWith("_WOOD");
    }

    private boolean isVeinMineable(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private int resolveExpDrop(Block block, Player player, ItemStack tool) {
        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return 0;
        }
        Material material = block.getType();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> random.nextInt(0, 3);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                    EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> random.nextInt(3, 8);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                    NETHER_QUARTZ_ORE, NETHER_GOLD_ORE -> random.nextInt(2, 6);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> random.nextInt(1, 6);
            case ANCIENT_DEBRIS -> random.nextInt(2, 5);
            default -> {
                try {
                    yield (int) Block.class
                            .getMethod("getExpDrop", Player.class, ItemStack.class)
                            .invoke(block, player, tool);
                } catch (ReflectiveOperationException ignored) {
                    yield 0;
                }
            }
        };
    }

    private void awardVeinExperience(Player player, Location origin, int totalExp) {
        if (totalExp <= 0) {
            return;
        }
        player.giveExp(totalExp);
        ExperienceOrb orb = origin.getWorld().spawn(origin.clone().add(0.5D, 0.5D, 0.5D), ExperienceOrb.class);
        orb.setExperience(totalExp);
        orb.setVelocity(orb.getVelocity().zero());
    }

    private static int[][] createVeinNeighbors() {
        List<int[]> offsets = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    offsets.add(new int[]{x, y, z});
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }

    private int[][] veinOffsets(int searchRadius) {
        return searchRadius <= 1 ? VEIN_NEIGHBORS : createSearchRadiusOffsets(searchRadius);
    }

    private int[][] createSearchRadiusOffsets(int searchRadius) {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    offsets.add(new int[]{x, y, z});
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }

    private String getToolId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        return NBT.get(item, (Function<de.tr7zw.nbtapi.iface.ReadableItemNBT, String>) nbt -> nbt.getOrNull(ExpirationService.NBT_TOOL_ID, String.class));
    }

    private long blockKey(Block block) {
        return blockKey(block.getX(), block.getY(), block.getZ());
    }

    private long blockKey(int x, int y, int z) {
        long packedY = ((long) (y + 2048)) & 0x1FFFL;
        return ((((long) x) & 0x3FFFFFFL) << 39)
                | ((((long) z) & 0x3FFFFFFL) << 13)
                | packedY;
    }
}
