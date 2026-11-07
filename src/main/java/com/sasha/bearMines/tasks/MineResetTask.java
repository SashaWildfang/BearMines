package com.sasha.bearMines.tasks;

import com.sasha.bearMines.BearMines;
import com.sasha.bearMines.models.Mine;
import org.bukkit.scheduler.BukkitRunnable;

public class MineResetTask extends BukkitRunnable {

    private final BearMines plugin;

    public MineResetTask(BearMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // This loop runs every 60 seconds.
        // It grabs every active mine from memory and ticks its internal clock down by 1 minute.
        for (Mine mine : plugin.getMineManager().getAllMines()) {
            mine.tickTimer();
        }
    }
}