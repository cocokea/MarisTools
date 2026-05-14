package com.maris.tools.command;

import com.maris.tools.MarisToolsPlugin;
import com.maris.tools.config.MessageService;
import com.maris.tools.config.ToolConfigService;
import com.maris.tools.service.ExpirationService;
import com.maris.tools.service.RuntimeClockService;
import com.maris.tools.tool.ToolDefinition;
import com.maris.tools.tool.ToolFactory;
import com.maris.tools.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ToolsCommand implements CommandExecutor, TabCompleter {

    private final MarisToolsPlugin plugin;
    private ToolConfigService toolConfigService;
    private RuntimeClockService runtimeClockService;
    private ExpirationService expirationService;
    private MessageService messages;

    public ToolsCommand(MarisToolsPlugin plugin, ToolConfigService toolConfigService, RuntimeClockService runtimeClockService,
                        ExpirationService expirationService, MessageService messages) {
        this.plugin = plugin;
        reloadDependencies(toolConfigService, runtimeClockService, expirationService, messages);
    }

    public void reloadDependencies(ToolConfigService toolConfigService, RuntimeClockService runtimeClockService,
                                   ExpirationService expirationService, MessageService messages) {
        this.toolConfigService = toolConfigService;
        this.runtimeClockService = runtimeClockService;
        this.expirationService = expirationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("maristools.command.reload")) {
                messages.send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            plugin.messages().send(sender, "reload-success");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("maristools.command.give")) {
                messages.send(sender, "no-permission");
                return true;
            }
            if (args.length < 4) {
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "player-not-found", "%player%", args[1]);
                return true;
            }
            ToolDefinition definition = toolConfigService.getTool(args[2]);
            if (definition == null) {
                messages.send(sender, "invalid-tool", "%tool%", args[2]);
                return true;
            }
            long duration = DurationParser.parseMillis(args[3].toLowerCase(Locale.ROOT));
            if (duration <= 0L) {
                messages.send(sender, "invalid-duration", "%input%", args[3]);
                return true;
            }
            long warningMillis = plugin.getConfig().getLong("settings.timer-warning-ms", 86_400_000L);
            ItemStack item = ToolFactory.create(definition, duration, runtimeClockService, warningMillis);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                sender.sendMessage(messages.get("prefix") + "Inventory of " + target.getName()
                        + " is full, dropped " + definition.id() + " at their location.");
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            }
            expirationService.scanInventory(target, target.getInventory(), false, true);
            messages.send(sender, "give-success", "%tool%", definition.id(), "%player%", target.getName(), "%duration%", args[3]);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("maristools.command.give")) {
                result.add("give");
            }
            if (sender.hasPermission("maristools.command.reload")) {
                result.add("reload");
            }
            return filter(result, args[0]);
        }
        if (!args[0].equalsIgnoreCase("give") || !sender.hasPermission("maristools.command.give")) {
            return List.of();
        }
        if (args.length == 2) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                result.add(player.getName());
            }
            return filter(result, args[1]);
        }
        if (args.length == 3) {
            result.addAll(toolConfigService.getToolIds());
            return filter(result, args[2]);
        }
        if (args.length == 4) {
            result.addAll(List.of("1s", "30s", "1m", "5m", "1h", "1d", "7d", "30d"));
            return filter(result, args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> input, String current) {
        String lower = current.toLowerCase(Locale.ROOT);
        return input.stream().filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
