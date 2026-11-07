package com.sasha.bearMines.commands;

import com.sasha.bearMines.BearMines;
import com.sasha.bearMines.models.Mine;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.block.data.BlockData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BearMinesCommand implements CommandExecutor {

    private final BearMines plugin;
    private final Map<UUID, Location[]> playerSelections = new HashMap<>();

    public BearMinesCommand(BearMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawMsg("not-player"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        UUID playerId = player.getUniqueId();
        playerSelections.putIfAbsent(playerId, new Location[2]);

        switch (subCommand) {
            case "reload":
                if (!hasPermission(player, "admin")) return true;
                plugin.reloadPluginConfigs();
                player.sendMessage(plugin.getMsg("config-reloaded"));
                break;

            case "list":
                if (!hasPermission(player, "list")) return true;

                java.util.Collection<Mine> allMines = plugin.getMineManager().getAllMines();
                if (allMines.isEmpty()) {
                    player.sendMessage(plugin.getMsg("no-mines"));
                    return true;
                }

                player.sendMessage("§e---- Registered BearMines ----");
                for (Mine m : allMines) {
                    TextComponent msg = new TextComponent("§8- §a" + m.getName() + " §7(" + m.getWorldName() + ")");
                    msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bm tp " + m.getName()));

                    StringBuilder hoverText = new StringBuilder("§e§l" + m.getName() + "\n§7Click to Teleport\n\n§7Blocks:\n");
                    if (m.getComposition().isEmpty()) {
                        hoverText.append("§c100% Air");
                    } else {
                        m.getComposition().forEach((data, pct) -> hoverText.append("§b").append(data.getMaterial().name()).append("§7: ").append(pct).append("%\n"));
                    }

                    msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText.toString())));
                    player.spigot().sendMessage(msg);
                }
                break;

            case "timers":
                if (!hasPermission(player, "list")) return true;

                java.util.List<Mine> activeMines = new ArrayList<>(plugin.getMineManager().getAllMines());
                activeMines.removeIf(m -> m.getResetDelay() <= 0);
                activeMines.sort(java.util.Comparator.comparingInt(Mine::getTimeLeft));

                if (activeMines.isEmpty()) {
                    player.sendMessage(plugin.getMsg("no-timers"));
                    return true;
                }

                int page = 1;
                if (args.length > 1) {
                    try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }

                int itemsPerPage = 15;
                int maxPages = (int) Math.ceil(activeMines.size() / (double) itemsPerPage);
                if (page < 1) page = 1;
                if (page > maxPages) page = maxPages;

                player.sendMessage("§e---- BearMines Timers §7(Page " + page + "/" + maxPages + ") §e----");

                int startIndex = (page - 1) * itemsPerPage;
                int endIndex = Math.min(startIndex + itemsPerPage, activeMines.size());

                for (int i = startIndex; i < endIndex; i++) {
                    Mine m = activeMines.get(i);
                    int secondsTotal = m.getTimeLeft();
                    int mins = secondsTotal / 60;
                    int secs = secondsTotal % 60;
                    player.sendMessage("§8- §a" + m.getName() + " §7- Resets in: §c" + mins + "m " + secs + "s");
                }
                break;

            case "p1":
            case "p2":
                if (!hasPermission(player, "create")) return true;
                int pointIndex = subCommand.equals("p1") ? 0 : 1;
                boolean useFeet = args.length > 1 && args[1].equalsIgnoreCase("-feet");

                Location targetLoc;
                if (useFeet) {
                    targetLoc = player.getLocation().getBlock().getLocation();
                } else {
                    Block targetBlock = player.getTargetBlockExact(5);
                    if (targetBlock == null || targetBlock.getType() == Material.AIR) {
                        player.sendMessage(plugin.getMsg("point-invalid"));
                        return true;
                    }
                    targetLoc = targetBlock.getLocation();
                }

                playerSelections.get(playerId)[pointIndex] = targetLoc;

                String pointMsg = plugin.getMsg("point-set")
                        .replace("%point%", String.valueOf(pointIndex + 1))
                        .replace("%x%", String.valueOf(targetLoc.getBlockX()))
                        .replace("%y%", String.valueOf(targetLoc.getBlockY()))
                        .replace("%z%", String.valueOf(targetLoc.getBlockZ()))
                        .replace("%world%", targetLoc.getWorld().getName());
                player.sendMessage(pointMsg);
                break;

            case "create":
                if (!hasPermission(player, "create")) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bm create <mine name>");
                    return true;
                }

                Location loc1 = playerSelections.get(playerId)[0];
                Location loc2 = playerSelections.get(playerId)[1];

                if (loc1 == null || loc2 == null) {
                    if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                        try {
                            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
                            LocalSession localSession = WorldEdit.getInstance().getSessionManager().get(wePlayer);

                            Region region = localSession.getSelection(localSession.getSelectionWorld());
                            BlockVector3 min = region.getMinimumPoint();
                            BlockVector3 max = region.getMaximumPoint();
                            org.bukkit.World weWorld = BukkitAdapter.adapt(region.getWorld());

                            loc1 = new Location(weWorld, min.x(), min.y(), min.z());
                            loc2 = new Location(weWorld, max.x(), max.y(), max.z());

                            player.sendMessage(plugin.getMsg("we-injected"));

                        } catch (IncompleteRegionException e) {
                            player.sendMessage(plugin.getMsg("we-incomplete"));
                            return true;
                        }
                    } else {
                        player.sendMessage(plugin.getMsg("points-missing"));
                        return true;
                    }
                }

                if (!loc1.getWorld().equals(loc2.getWorld())) {
                    player.sendMessage(plugin.getMsg("points-different-worlds"));
                    return true;
                }

                String mineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

                Mine newMine = new Mine(plugin, mineName, loc1.getWorld().getName(),
                        loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ(),
                        loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ());

                plugin.getMineManager().registerMine(newMine);

                FileConfiguration config = plugin.getMinesConfig();
                String path = "mines." + mineName.replace(" ", "_") + ".";

                config.set(path + "world", loc1.getWorld().getName());
                config.set(path + "minX", newMine.getMinX());
                config.set(path + "minY", newMine.getMinY());
                config.set(path + "minZ", newMine.getMinZ());
                config.set(path + "maxX", newMine.getMaxX());
                config.set(path + "maxY", newMine.getMaxY());
                config.set(path + "maxZ", newMine.getMaxZ());

                plugin.saveMinesConfig();
                playerSelections.put(playerId, new Location[2]);

                player.sendMessage(plugin.getMsg("mine-created").replace("%mine%", mineName));
                break;

            case "info":
                if (!hasPermission(player, "info")) return true;

                Mine targetMine = null;
                if (args.length > 1) {
                    String infoName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    targetMine = plugin.getMineManager().getMine(infoName);
                } else {
                    Location pLoc = player.getLocation();
                    for (Mine m : plugin.getMineManager().getAllMines()) {
                        if (pLoc.getWorld().getName().equals(m.getWorldName()) &&
                                pLoc.getBlockX() >= m.getMinX() && pLoc.getBlockX() <= m.getMaxX() &&
                                pLoc.getBlockY() >= m.getMinY() && pLoc.getBlockY() <= m.getMaxY() &&
                                pLoc.getBlockZ() >= m.getMinZ() && pLoc.getBlockZ() <= m.getMaxZ()) {
                            targetMine = m;
                            break;
                        }
                    }
                }

                if (targetMine == null) {
                    player.sendMessage("§cPlease specify a mine or stand inside one. (/bm info <name>)");
                    return true;
                }

                player.sendMessage("§e---- Mine Info: §6" + targetMine.getName() + " §e----");
                player.sendMessage("§7Volume: §a" + targetMine.getVolume() + " blocks");
                player.sendMessage("§7Auto-Reset: §a" + (targetMine.getResetDelay() > 0 ? targetMine.getResetDelay() + " mins" : "Disabled"));
                player.sendMessage("§7TP Set: " + (targetMine.getTpLocation() != null ? "§aYes" : "§cNo"));
                player.sendMessage("§7Composition:");

                double totalPercent = 0.0;
                if (targetMine.getComposition().isEmpty()) {
                    player.sendMessage("  §cNone (100% Air)");
                } else {
                    for (Map.Entry<BlockData, Double> entry : targetMine.getComposition().entrySet()) {
                        player.sendMessage("  §8- §b" + entry.getKey().getMaterial().name() + "§7: " + entry.getValue() + "%");
                        totalPercent += entry.getValue();
                    }
                    if (totalPercent < 100.0) {
                        player.sendMessage("  §8- §bAIR§7: " + String.format("%.2f", (100.0 - totalPercent)) + "%");
                    }
                }
                break;

            case "set":
                if (!hasPermission(player, "composition")) return true;
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /bm set <mine name> <block> <percentage>%");
                    return true;
                }

                String percentageStr = args[args.length - 1].replace("%", "");
                String blockStr = args[args.length - 2].toUpperCase();
                String setMineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 2));

                double percentage;
                try {
                    percentage = Double.parseDouble(percentageStr);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getMsg("invalid-number"));
                    return true;
                }

                Material material = Material.matchMaterial(blockStr);
                if (material == null || !material.isBlock()) {
                    player.sendMessage(plugin.getMsg("invalid-block").replace("%block%", blockStr));
                    return true;
                }

                Mine setMine = plugin.getMineManager().getMine(setMineName);
                if (setMine == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", setMineName));
                    return true;
                }

                // Convert to BlockData and add to memory composition Map
                setMine.addComposition(material.createBlockData(), percentage);

                FileConfiguration setConfig = plugin.getMinesConfig();
                String setPath = "mines." + setMine.getName().replace(" ", "_") + ".composition." + material.name();
                setConfig.set(setPath, percentage);
                plugin.saveMinesConfig();

                // Calculate the remaining percentage for the message
                double totalSet = 0.0;
                for (double val : setMine.getComposition().values()) {
                    totalSet += val;
                }
                double remaining = Math.max(0.0, 100.0 - totalSet);
                java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");

                player.sendMessage(plugin.getMsg("comp-added")
                        .replace("%block%", material.name())
                        .replace("%pct%", String.valueOf(percentage))
                        .replace("%mine%", setMine.getName())
                        + " §7(" + df.format(remaining) + "% Free)");
                break;

            case "unset":
                if (!hasPermission(player, "composition")) return true;
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /bm unset <mine name> <block|all>");
                    return true;
                }

                String unsetBlockStr = args[args.length - 1].toUpperCase();
                String unsetMineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));

                Mine unsetMine = plugin.getMineManager().getMine(unsetMineName);
                if (unsetMine == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", unsetMineName));
                    return true;
                }

                FileConfiguration unsetConfig = plugin.getMinesConfig();
                String baseUnsetPath = "mines." + unsetMine.getName().replace(" ", "_") + ".composition";

                // Wipe all block data if user uses "all"
                if (unsetBlockStr.equalsIgnoreCase("ALL")) {
                    unsetMine.getComposition().clear();
                    unsetConfig.set(baseUnsetPath, null);
                    plugin.saveMinesConfig();

                    player.sendMessage("§aSuccessfully cleared all blocks from mine §e" + unsetMine.getName() + "§a.");
                    return true;
                }

                Material unsetMaterial = Material.matchMaterial(unsetBlockStr);
                if (unsetMaterial == null || !unsetMaterial.isBlock()) {
                    player.sendMessage(plugin.getMsg("invalid-block").replace("%block%", unsetBlockStr));
                    return true;
                }

                // Safely remove the matching BlockData key from memory
                unsetMine.getComposition().keySet().removeIf(bData -> bData.getMaterial() == unsetMaterial);

                // Erase from config
                unsetConfig.set(baseUnsetPath + "." + unsetMaterial.name(), null);
                plugin.saveMinesConfig();

                player.sendMessage(plugin.getMsg("comp-removed")
                        .replace("%block%", unsetMaterial.name())
                        .replace("%mine%", unsetMine.getName()));
                break;

            case "reset":
                if (!hasPermission(player, "reset")) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bm reset <mine name> [-s]");
                    return true;
                }

                boolean silent = args[args.length - 1].equalsIgnoreCase("-s");
                int endArg = silent ? args.length - 1 : args.length;
                String resetMineName = String.join(" ", Arrays.copyOfRange(args, 1, endArg));

                Mine resetMine = plugin.getMineManager().getMine(resetMineName);
                if (resetMine == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", resetMineName));
                    return true;
                }

                resetMine.reset();

                if (!silent) {
                    plugin.getServer().broadcastMessage(plugin.getMsg("mine-reset-manual").replace("%mine%", resetMine.getName()));
                } else {
                    player.sendMessage(plugin.getMsg("mine-reset-silent").replace("%mine%", resetMine.getName()));
                }
                break;

            case "flag":
                if (!hasPermission(player, "flag")) return true;
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /bm flag <mine name> <setting> <value>");
                    player.sendMessage("§7Settings: resetDelay, resetWarnings, silent");
                    return true;
                }

                String flagValue = args[args.length - 1].toLowerCase();
                String flagSetting = args[args.length - 2].toLowerCase();
                String flagMineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 2));

                Mine flagMine = plugin.getMineManager().getMine(flagMineName);
                if (flagMine == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", flagMineName));
                    return true;
                }

                FileConfiguration flagConfig = plugin.getMinesConfig();
                String flagPath = "mines." + flagMine.getName().replace(" ", "_") + ".";

                switch (flagSetting) {
                    case "resetdelay":
                        try {
                            int delay = Integer.parseInt(flagValue);
                            flagMine.setResetDelay(delay);
                            flagConfig.set(flagPath + "resetDelay", delay);
                            player.sendMessage(plugin.getMsg("flag-delay").replace("%mine%", flagMine.getName()).replace("%val%", String.valueOf(delay)));
                        } catch (NumberFormatException e) {
                            player.sendMessage(plugin.getMsg("invalid-number"));
                        }
                        break;

                    case "resetwarnings":
                        java.util.List<Integer> warningsList = new java.util.ArrayList<>();
                        try {
                            for (String warnStr : flagValue.split(",")) {
                                warningsList.add(Integer.parseInt(warnStr.trim()));
                            }
                            flagMine.setResetWarnings(warningsList);
                            flagConfig.set(flagPath + "resetWarnings", warningsList);
                            player.sendMessage(plugin.getMsg("flag-warnings").replace("%mine%", flagMine.getName()).replace("%val%", flagValue));
                        } catch (NumberFormatException e) {
                            player.sendMessage("§cWarnings must be numbers separated by commas (e.g., 10,5,1)!");
                        }
                        break;

                    case "silent":
                        boolean isSilent = Boolean.parseBoolean(flagValue);
                        flagMine.setSilent(isSilent);
                        flagConfig.set(flagPath + "silent", isSilent);
                        player.sendMessage(plugin.getMsg("flag-silent").replace("%mine%", flagMine.getName()).replace("%val%", String.valueOf(isSilent)));
                        break;

                    default:
                        player.sendMessage("§cUnknown setting! Available: resetDelay, resetWarnings, silent");
                        break;
                }

                plugin.saveMinesConfig();
                break;

            case "erase":
                if (!hasPermission(player, "erase")) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bm erase <mine name>");
                    return true;
                }

                String eraseMineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Mine eraseMine = plugin.getMineManager().getMine(eraseMineName);
                if (eraseMine == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", eraseMineName));
                    return true;
                }

                plugin.getMineManager().removeMine(eraseMineName);

                FileConfiguration eraseConfig = plugin.getMinesConfig();
                eraseConfig.set("mines." + eraseMine.getName().replace(" ", "_"), null);
                plugin.saveMinesConfig();

                player.sendMessage(plugin.getMsg("mine-erased").replace("%mine%", eraseMine.getName()));
                break;

            case "tp":
                if (!hasPermission(player, "tp")) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bm tp <mine name>");
                    return true;
                }

                String goMineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Mine goMine = plugin.getMineManager().getMine(goMineName);
                if (goMine == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", goMineName));
                    return true;
                }

                if (goMine.getTpLocation() == null) {
                    player.sendMessage(plugin.getMsg("tp-not-set"));
                    return true;
                }

                player.teleport(goMine.getTpLocation());
                player.sendMessage(plugin.getMsg("tp-success").replace("%mine%", goMine.getName()));
                break;

            case "settp":
                if (!hasPermission(player, "settp")) return true;
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /bm settp <mine name>");
                    return true;
                }

                String tpMineName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Mine tpMineConfig = plugin.getMineManager().getMine(tpMineName);
                if (tpMineConfig == null) {
                    player.sendMessage(plugin.getMsg("mine-not-found").replace("%mine%", tpMineName));
                    return true;
                }

                Location currentLoc = player.getLocation();
                tpMineConfig.setTpLocation(currentLoc);

                FileConfiguration tpConfig = plugin.getMinesConfig();
                String tPath = "mines." + tpMineConfig.getName().replace(" ", "_") + ".tp.";
                tpConfig.set(tPath + "x", currentLoc.getX());
                tpConfig.set(tPath + "y", currentLoc.getY());
                tpConfig.set(tPath + "z", currentLoc.getZ());
                tpConfig.set(tPath + "yaw", currentLoc.getYaw());
                tpConfig.set(tPath + "pitch", currentLoc.getPitch());
                plugin.saveMinesConfig();

                player.sendMessage(plugin.getMsg("tp-set").replace("%mine%", tpMineConfig.getName()));
                break;

            default:
                player.sendMessage(plugin.getRawMsg("unknown-command"));
                break;
        }

        return true;
    }

    private boolean hasPermission(Player player, String subNode) {
        if (player.hasPermission("bn.bearmines.admin") || player.hasPermission("bn.bearmines.cmd." + subNode)) {
            return true;
        }
        player.sendMessage(plugin.getRawMsg("no-permission").replace("%node%", "bn.bearmines.cmd." + subNode));
        return false;
    }

    private void sendHelpMenu(Player player) {
        player.sendMessage("§e========== §6BearMines Commands §e==========");
        player.sendMessage("§b/bm help §7- Display this menu");
        player.sendMessage("§b/bm list §7- List all active mine sectors");
        player.sendMessage("§b/bm timers [page] §7- View automated reset countdowns");
        player.sendMessage("§b/bm reload §7- Reload config files");
        player.sendMessage("§b/bm p1 [-feet] §7- Select edge position 1");
        player.sendMessage("§b/bm p2 [-feet] §7- Select edge position 2");
        player.sendMessage("§b/bm create <name> §7- Compile selection into mine structural data");
        player.sendMessage("§b/bm info [name] §7- Check compositional distribution layout");
        player.sendMessage("§b/bm set <name> <block> <%> §7- Map block properties");
        player.sendMessage("§b/bm unset <name> <block|all> §7- Wipe block drop mappings");
        player.sendMessage("§b/bm flag <name> <setting> <val> §7- Configure structural interval loops");
        player.sendMessage("§b/bm reset <name> [-s] §7- Force structural cycle manually");
        player.sendMessage("§b/bm erase <name> §7- Wipe a structural profile entirely");
        player.sendMessage("§b/bm tp <name> §7- Teleport to landing zone");
        player.sendMessage("§b/bm settp <name> §7- Bind landing zone vector directly at your feet");
    }
}