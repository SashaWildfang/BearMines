package com.sasha.bearMines;

import com.sasha.bearMines.models.Mine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MineManager {

    private final BearMines plugin;
    private final Map<String, Mine> activeMines = new HashMap<>();

    public MineManager(BearMines plugin) {
        this.plugin = plugin;
    }

    public void loadMinesFromConfig() {
        // Clean up old holograms before reloading memory cache
        for (Mine m : activeMines.values()) {
            m.deleteHologram();
        }
        activeMines.clear();

        FileConfiguration config = plugin.getMinesConfig();
        ConfigurationSection minesSection = config.getConfigurationSection("mines");

        if (minesSection == null) {
            plugin.getLogger().info("No mines found in config to load.");
            return;
        }

        for (String mineName : minesSection.getKeys(false)) {
            String path = "mines." + mineName + ".";
            try {
                String worldName = config.getString(path + "world");
                int minX = config.getInt(path + "minX");
                int minY = config.getInt(path + "minY");
                int minZ = config.getInt(path + "minZ");
                int maxX = config.getInt(path + "maxX");
                int maxY = config.getInt(path + "maxY");
                int maxZ = config.getInt(path + "maxZ");

                Mine mine = new Mine(plugin, mineName.replace("_", " "), worldName, minX, minY, minZ, maxX, maxY, maxZ);

                ConfigurationSection compSection = config.getConfigurationSection(path + "composition");
                if (compSection != null) {
                    for (String blockName : compSection.getKeys(false)) {
                        Material mat = Material.matchMaterial(blockName);
                        if (mat != null && mat.isBlock()) {
                            double percent = compSection.getDouble(blockName);
                            mine.addComposition(mat.createBlockData(), percent);
                        }
                    }
                }

                if (config.contains(path + "resetDelay")) mine.setResetDelay(config.getInt(path + "resetDelay"));
                if (config.contains(path + "resetWarnings")) mine.setResetWarnings(config.getIntegerList(path + "resetWarnings"));
                if (config.contains(path + "silent")) mine.setSilent(config.getBoolean(path + "silent"));

                if (config.contains(path + "tp")) {
                    double x = config.getDouble(path + "tp.x");
                    double y = config.getDouble(path + "tp.y");
                    double z = config.getDouble(path + "tp.z");
                    float yaw = (float) config.getDouble(path + "tp.yaw");
                    float pitch = (float) config.getDouble(path + "tp.pitch");
                    org.bukkit.Location tpLoc = new org.bukkit.Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                    mine.setTpLocation(tpLoc);
                }

                activeMines.put(mine.getName().toLowerCase(), mine);
                mine.updateHologram(); // Spawn the hologram on load
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load mine: " + mineName + ". Check your formatting!");
            }
        }
        plugin.getLogger().info("Successfully loaded " + activeMines.size() + " mines into memory.");
    }

    public void registerMine(Mine mine) {
        activeMines.put(mine.getName().toLowerCase(), mine);
        mine.updateHologram();
    }

    public Mine getMine(String name) { return activeMines.get(name.toLowerCase()); }
    public Collection<Mine> getAllMines() { return activeMines.values(); }

    public Mine getMineAt(Location loc) {
        for (Mine m : activeMines.values()) {
            if (m.isInside(loc)) return m;
        }
        return null;
    }

    public void removeMine(String name) {
        Mine mine = getMine(name);
        if (mine != null) {
            mine.deleteHologram();
            activeMines.remove(name.toLowerCase());
        }
    }
}