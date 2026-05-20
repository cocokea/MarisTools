package com.maris.tools;

import com.maris.tools.command.ToolsCommand;
import com.maris.tools.config.MessageService;
import com.maris.tools.config.ToolConfigService;
import com.maris.tools.hook.MarisAfkZoneBridge;
import com.maris.tools.listener.ToolListener;
import com.maris.tools.hook.MarisWorthBridge;
import com.maris.tools.service.ExpirationService;
import com.maris.tools.service.RuntimeClockService;
import com.maris.tools.util.FoliaSupport;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MarisToolsPlugin extends JavaPlugin {
    private YamlConfiguration messageConfig;
    private YamlConfiguration toolsConfig;
    private MessageService messages;
    private ToolConfigService toolConfigService;
    private RuntimeClockService runtimeClockService;
    private ExpirationService expirationService;
    private ToolListener toolListener;
    private MarisWorthBridge marisWorthBridge;
    private MarisAfkZoneBridge marisAfkZoneBridge;
    private ToolsCommand toolsCommand;

    @Override
    public void onEnable() {
        
        saveDefaultConfig();
        MarisPluginStartup.bootstrap(this, "cocokea/MarisTools");
saveDefaultConfig();
        saveResourceIfMissing("message.yml");
        saveResourceIfMissing("tools.yml");
        loadExtraConfigs();

        this.messages = new MessageService(this, messageConfig);
        this.toolConfigService = new ToolConfigService(this, toolsConfig);
        this.runtimeClockService = new RuntimeClockService(this);
        this.expirationService = new ExpirationService(this, runtimeClockService);
        this.marisWorthBridge = new MarisWorthBridge(this);
        this.marisAfkZoneBridge = new MarisAfkZoneBridge(this);

        runtimeClockService.start();
        expirationService.start();

        this.toolListener = new ToolListener(this, runtimeClockService, expirationService);
        getServer().getPluginManager().registerEvents(toolListener, this);

        registerCommandBindings();
    }

    @Override
    public void onDisable() {
        if (toolListener != null) {
            toolListener.stopLoreRefreshTask();
            HandlerList.unregisterAll(toolListener);
        }
        if (expirationService != null) {
            expirationService.stop();
        }
        if (runtimeClockService != null) {
            runtimeClockService.stop();
        }
    }

    public void reloadAll() {
        reloadConfig();
        loadExtraConfigs();

        if (toolListener != null) {
            toolListener.stopLoreRefreshTask();
            HandlerList.unregisterAll(toolListener);
        }
        if (expirationService != null) {
            expirationService.stop();
        }

        this.messages = new MessageService(this, messageConfig);
        this.toolConfigService = new ToolConfigService(this, toolsConfig);
        this.runtimeClockService.reload();
        this.expirationService.start();

        this.toolListener = new ToolListener(this, runtimeClockService, expirationService);
        getServer().getPluginManager().registerEvents(toolListener, this);
        registerCommandBindings();
        expirationService.scanAllAccessibleInventories();
    }

    private void registerCommandBindings() {
        if (toolsCommand == null) {
            toolsCommand = new ToolsCommand(this, toolConfigService, runtimeClockService, expirationService, messages);
        } else {
            toolsCommand.reloadDependencies(toolConfigService, runtimeClockService, expirationService, messages);
        }
        PluginCommand command = getCommand("tools");
        if (command != null) {
            command.setExecutor(toolsCommand);
            command.setTabCompleter(toolsCommand);
        }
    }

    private void loadExtraConfigs() {
        this.messageConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "message.yml"));
        this.toolsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "tools.yml"));
    }

    private void saveResourceIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    public MessageService messages() {
        return messages;
    }

    public ToolConfigService toolConfigService() {
        return toolConfigService;
    }

    public MarisWorthBridge marisWorthBridge() {
        return marisWorthBridge;
    }

    public MarisAfkZoneBridge marisAfkZoneBridge() {
        return marisAfkZoneBridge;
    }

}

