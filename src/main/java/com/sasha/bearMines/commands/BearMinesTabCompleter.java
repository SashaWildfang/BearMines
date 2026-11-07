package com.sasha.bearMines.commands;

import com.sasha.bearMines.BearMines;
import com.sasha.bearMines.models.Mine;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BearMinesTabCompleter implements TabCompleter {

    private final BearMines plugin;

    public BearMinesTabCompleter(BearMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // Argument 1: Subcommands
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "help", "list", "timers", "reload", "p1", "p2", "create",
                    "info", "set", "unset", "flag", "reset", "erase", "tp", "settp"
            );
            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Argument 2: Mine Names (for commands that require them)
        List<String> mineCommands = Arrays.asList("info", "set", "unset", "flag", "reset", "erase", "tp", "settp");
        if (args.length == 2 && mineCommands.contains(args[0].toLowerCase())) {
            return plugin.getMineManager().getAllMines().stream()
                    .map(Mine::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Argument 3: Blocks, Flags, or Reset Modifiers
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                // Suggest all valid blocks
                return Arrays.stream(Material.values())
                        .filter(Material::isBlock)
                        .map(Material::name)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());

            } else if (args[0].equalsIgnoreCase("unset")) {
                // Suggest "all" and the blocks currently inside the mine
                Mine mine = plugin.getMineManager().getMine(args[1]);
                List<String> options = new ArrayList<>();

                // Add the new 'all' option
                options.add("all");

                if (mine != null) {
                    for (BlockData bData : mine.getComposition().keySet()) {
                        options.add(bData.getMaterial().name());
                    }
                }
                return options.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());

            } else if (args[0].equalsIgnoreCase("flag")) {
                // Suggest valid flags
                List<String> flags = Arrays.asList("resetDelay", "resetWarnings", "silent");
                return flags.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());

            } else if (args[0].equalsIgnoreCase("reset")) {
                // Suggest the silent flag modifier
                if ("-s".startsWith(args[2].toLowerCase())) {
                    return Arrays.asList("-s");
                }
            }
        }

        // Argument 4: Percentages or Flag Values
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("set")) {
                // Suggest common percentages
                List<String> percentages = Arrays.asList("10", "25", "50", "100");
                return percentages.stream()
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());

            } else if (args[0].equalsIgnoreCase("flag")) {
                // Suggest relevant values based on the flag chosen
                String flag = args[2].toLowerCase();
                List<String> values = new ArrayList<>();

                if (flag.equals("resetdelay")) {
                    values.addAll(Arrays.asList("10", "30", "60"));
                } else if (flag.equals("resetwarnings")) {
                    values.add("10,5,1");
                } else if (flag.equals("silent")) {
                    values.addAll(Arrays.asList("true", "false"));
                }

                return values.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}