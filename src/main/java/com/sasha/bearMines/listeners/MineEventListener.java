package com.sasha.bearMines.listeners;

import com.sasha.bearMines.BearMines;
import com.sasha.bearMines.models.Mine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class MineEventListener implements Listener {

    private final BearMines plugin;

    public MineEventListener(BearMines plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        Mine mine = plugin.getMineManager().getMineAt(loc);
        if (mine == null) return;

        // 1. Enforce Mine Tiers (Permissions)
        String permNode = "bn.bearmines.mine." + mine.getName().toLowerCase().replace(" ", "");
        if (!player.hasPermission("bn.bearmines.admin") && !player.hasPermission(permNode)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMsg("mine-locked").replace("%node%", permNode));
            return;
        }

        // 2. Track volume for Percentage-Based Resets
        // We only count it if they are actually breaking a non-air block
        if (event.getBlock().getType() != Material.AIR) {
            mine.incrementMinedBlocks();
        }
    }
}