package org.filps.botshield;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BotShieldCommand implements CommandExecutor {

    private final BotShield plugin;

    public BotShieldCommand(BotShield plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("BotShield Commands:");
            sender.sendMessage("/botshield reload - Reloads the configuration and settings.");
            sender.sendMessage("/botshield help - Shows this help message.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (sender instanceof Player && !sender.hasPermission("botshield.reload")) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }

            if (!plugin.loadConfig()) {
                sender.sendMessage("Error loading configuration.");
                return true;
            }

            if (!plugin.validateApiKey()) {
                sender.sendMessage("Error: Invalid API key.");
                return true;
            }

            if (!plugin.loadSettings()) {
                sender.sendMessage("Error loading settings. Default settings have been used.");
            } else {
                sender.sendMessage("Configuration and settings reloaded.");
            }

            return true;
        }

        sender.sendMessage("Unknown command. Usage: /botshield help");
        return true;
    }
}
