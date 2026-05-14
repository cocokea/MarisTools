package com.maris.tools.config;

import com.maris.tools.MarisToolsPlugin;
import com.maris.tools.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class MessageService {

    private final FileConfiguration config;

    public MessageService(MarisToolsPlugin plugin, FileConfiguration config) {
        this.config = config;
    }

    public String get(String path) {
        return ColorUtil.color(config.getString(path, path));
    }

    public String prefixed(String path) {
        return get("prefix") + get(path);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(prefixed(path));
    }

    public void send(CommandSender sender, String path, String... replacements) {
        String msg = prefixed(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(msg);
    }
}
