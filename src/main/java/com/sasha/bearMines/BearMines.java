package com.sasha.bearMines;

import com.sasha.bearMines.commands.BearMinesCommand;
import com.sasha.bearMines.commands.BearMinesTabCompleter;
import com.sasha.bearMines.listeners.MineEventListener;
import com.sasha.bearMines.tasks.MineResetTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class BearMines extends JavaPlugin {

    private File minesFile;
    private FileConfiguration minesConfig;
    private MineManager mineManager;
    private boolean hasDecentHolograms = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createMinesConfig();

        hasDecentHolograms = Bukkit.getPluginManager().getPlugin("DecentHolograms") != null;
        if (hasDecentHolograms) {
            getLogger().info("DecentHolograms detected! Enabling holographic timers.");
        }

        mineManager = new MineManager(this);
        mineManager.loadMinesFromConfig();

        getCommand("bearmines").setTabCompleter(new BearMinesTabCompleter(this));
        getCommand("bearmines").setExecutor(new BearMinesCommand(this));

        getServer().getPluginManager().registerEvents(new MineEventListener(this), this);

        new MineResetTask(this).runTaskTimer(this, 20L, 20L);

        getLogger().info("BearMines enabled! Configured with YAML data storage.");
    }

    @Override
    public void onDisable() {
        for (com.sasha.bearMines.models.Mine m : mineManager.getAllMines()) {
            m.deleteHologram(); // Clean up holograms so they don't duplicate on restart
        }
        saveMinesConfig();
        getLogger().info("BearMines disabled. Data saved cleanly.");
    }

    public boolean hasHolograms() { return hasDecentHolograms; }
    public MineManager getMineManager() { return this.mineManager; }
    public FileConfiguration getMinesConfig() { return this.minesConfig; }

    public void reloadPluginConfigs() {
        reloadConfig();
        minesConfig = YamlConfiguration.loadConfiguration(minesFile);
        mineManager.loadMinesFromConfig();
    }

    public String getMsg(String path) {
        String prefix = getConfig().getString("messages.prefix", "&8[&eBearMines&8] ");
        String msg = getConfig().getString("messages." + path, "&cMessage missing: " + path);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public String getRawMsg(String path) {
        String msg = getConfig().getString("messages." + path, "&cMessage missing: " + path);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public void saveMinesConfig() {
        try {
            if (minesConfig != null && minesFile != null) {
                minesConfig.save(minesFile);
            }
        } catch (IOException e) {
            getLogger().severe("Could not save data to mines.yml!");
            e.printStackTrace();
        }
    }

    private void createMinesConfig() {
        minesFile = new File(getDataFolder(), "mines.yml");
        if (!minesFile.getParentFile().exists()) {
            minesFile.getParentFile().mkdirs();
        }
        if (!minesFile.exists()) {
            try { minesFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        minesConfig = YamlConfiguration.loadConfiguration(minesFile);
    }
}