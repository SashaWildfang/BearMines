package com.sasha.bearMines.models;

import com.sasha.bearMines.BearMines;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mine {

    private final BearMines plugin;
    private final String name;
    private final String worldName;

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    private Location tpLocation;
    private final Map<BlockData, Double> composition = new HashMap<>();

    // New variables for Advanced Features
    private int blocksMined = 0;
    private boolean isResetting = false;
    private final String holoName;

    public Mine(BearMines plugin, String name, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.plugin = plugin;
        this.name = name;
        this.worldName = worldName;
        this.holoName = "BM_" + name.replace(" ", "_");

        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public Map<BlockData, Double> getComposition() { return composition; }
    public Location getTpLocation() { return tpLocation; }

    public void setTpLocation(Location tpLocation) { this.tpLocation = tpLocation; }
    public void addComposition(BlockData blockData, double percentage) { this.composition.put(blockData, percentage); }
    public void removeComposition(BlockData blockData) { this.composition.remove(blockData); }

    public int getVolume() { return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1); }
    public World getWorld() { return Bukkit.getWorld(worldName); }

    public boolean isInside(Location loc) {
        return loc.getWorld().getName().equals(worldName) &&
                loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
                loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public void incrementMinedBlocks() {
        if (isResetting) return;
        blocksMined++;
        updateHologram();

        double thresholdPct = plugin.getConfig().getDouble("settings.auto-reset-percentage", 80.0) / 100.0;
        if (blocksMined >= getVolume() * thresholdPct) {
            if (!isSilent) {
                Bukkit.broadcastMessage(plugin.getMsg("mine-reset-auto").replace("%mine%", name));
            }
            reset();
        }
    }

    public void reset() {
        if (isResetting) return;
        World world = getWorld();
        if (world == null) return;

        isResetting = true;

        // Teleport players out safely before changing blocks
        for (org.bukkit.entity.Player p : world.getPlayers()) {
            if (isInside(p.getLocation())) {
                Location dest = (tpLocation != null) ? tpLocation : new Location(world, p.getLocation().getX(), maxY + 2, p.getLocation().getZ());
                p.teleport(dest);
                p.sendMessage(plugin.getMsg("mine-resetting-moved"));
            }
        }

        BlockData airData = Bukkit.createBlockData(org.bukkit.Material.AIR);
        int blocksPerTick = plugin.getConfig().getInt("settings.blocks-per-tick", 500);

        // Staggered Async Reset
        new BukkitRunnable() {
            int x = minX, y = minY, z = minZ;

            @Override
            public void run() {
                int processed = 0;
                while (processed < blocksPerTick) {
                    double randomRoll = Math.random() * 100.0;
                    double currentThreshold = 0.0;
                    BlockData selectedData = airData;

                    for (Map.Entry<BlockData, Double> entry : composition.entrySet()) {
                        currentThreshold += entry.getValue();
                        if (randomRoll <= currentThreshold) {
                            selectedData = entry.getKey();
                            break;
                        }
                    }

                    world.getBlockAt(x, y, z).setBlockData(selectedData, false);

                    // Move to next coordinate
                    z++;
                    if (z > maxZ) { z = minZ; y++; }
                    if (y > maxY) { y = minY; x++; }
                    if (x > maxX) {
                        // Reset Complete
                        isResetting = false;
                        blocksMined = 0;
                        timeLeft = resetDelay * 60; // Restart timer
                        updateHologram();
                        cancel();
                        return;
                    }
                    processed++;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // --- Timer & Hologram Variables ---
    private int resetDelay = 0;
    private int timeLeft = 0;
    private List<Integer> resetWarnings = new ArrayList<>();
    private boolean isSilent = false;

    public int getResetDelay() { return resetDelay; }
    public int getTimeLeft() { return timeLeft; }

    public void setResetDelay(int resetDelay) {
        this.resetDelay = resetDelay;
        this.timeLeft = resetDelay * 60;
    }

    public List<Integer> getResetWarnings() { return resetWarnings; }
    public void setResetWarnings(List<Integer> resetWarnings) { this.resetWarnings = resetWarnings; }
    public boolean isSilent() { return isSilent; }
    public void setSilent(boolean silent) { isSilent = silent; }

    public void tickTimer() {
        if (resetDelay > 0 && !isResetting) {
            timeLeft--;

            if (timeLeft % 60 == 0) {
                int minutesLeft = timeLeft / 60;
                if (!isSilent && resetWarnings.contains(minutesLeft)) {
                    Bukkit.broadcastMessage(plugin.getMsg("timer-warning")
                            .replace("%mine%", name)
                            .replace("%time%", String.valueOf(minutesLeft)));
                }
            }

            if (timeLeft <= 0) {
                if (!isSilent) Bukkit.broadcastMessage(plugin.getMsg("mine-reset-auto").replace("%mine%", name));
                reset();
            }
        }

        // Update hologram every second regardless to keep the timer visual accurate
        updateHologram();
    }

    public void updateHologram() {
        if (!plugin.hasHolograms() || !plugin.getConfig().getBoolean("hologram.enabled")) return;
        World w = getWorld();
        if (w == null) return;

        double centerX = minX + (maxX - minX + 1) / 2.0;
        double centerZ = minZ + (maxZ - minZ + 1) / 2.0;
        double centerY = maxY + plugin.getConfig().getDouble("hologram.height-offset", 3.0);
        Location center = new Location(w, centerX, centerY, centerZ);

        List<String> rawLines = plugin.getConfig().getStringList("hologram.lines");
        List<String> parsedLines = new ArrayList<>();

        int mins = timeLeft / 60;
        int secs = timeLeft % 60;
        String timeFormatted = mins + "m " + secs + "s";

        double currentPct = ((double) blocksMined / getVolume()) * 100.0;

        for (String line : rawLines) {
            parsedLines.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line
                    .replace("%mine%", name)
                    .replace("%time%", timeFormatted)
                    .replace("%mined%", String.valueOf(blocksMined))
                    .replace("%total%", String.valueOf(getVolume()))
                    .replace("%percent%", String.format("%.1f", currentPct))
            ));
        }

        Hologram holo = DHAPI.getHologram(holoName);
        if (holo == null) {
            DHAPI.createHologram(holoName, center, parsedLines);
        } else {
            DHAPI.setHologramLines(holo, parsedLines);
            DHAPI.moveHologram(holo, center);
        }
    }

    public void deleteHologram() {
        if (plugin.hasHolograms()) {
            Hologram holo = DHAPI.getHologram(holoName);
            if (holo != null) holo.delete();
        }
    }
}