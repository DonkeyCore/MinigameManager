package me.donkeycore.minigamemanager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.donkeycore.minigamemanager.api.Minigame;
import me.donkeycore.minigamemanager.api.MinigameAttributes;
import me.donkeycore.minigamemanager.api.Rotation;
import me.donkeycore.minigamemanager.api.RotationManager;
import me.donkeycore.minigamemanager.api.RotationState;
import me.donkeycore.minigamemanager.core.MinigameManager;

public class CommandMinigame implements CommandExecutor {
	
	private final MinigameManager manager;
	
	public CommandMinigame(MinigameManager manager) {
		this.manager = manager;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		//<command> [help|list|info minigame|<start|stop|status> <rotation>|reload]
		if (cmd.getName().equalsIgnoreCase("minigamemanager")) {
			if (args.length == 0) {
				sender.sendMessage("§e=========================");
				sender.sendMessage("§e>    Minigame§6Manager");
				sender.sendMessage("§e>     Version §6" + MinigameManager.getPlugin().getDescription().getVersion());
				sender.sendMessage("§e>     by §6DonkeyCore");
				sender.sendMessage("§e>    Usage: §6/mm help");
				sender.sendMessage("§e=========================");
			} else if (args.length == 1) {
				if (args[0].equalsIgnoreCase("help") && sender.hasPermission("minigamemanager.admin.help")) {
					sender.sendMessage("§e===<§6MinigameManager Help§e>===");
					sender.sendMessage("§e> §6/mm help §e- View this help page");
					sender.sendMessage("§e> §6/mm reload §e- Reload the configs");
					sender.sendMessage("§e> §6/mm list §e- Get a list of the minigames");
					sender.sendMessage("§e> §6/mm info <minigame> §e- View information about <minigame>");
					sender.sendMessage("§e> §6/mm start <rotation> §e- Start the rotation cycle; <rotation>: Rotation ID");
					sender.sendMessage("§e> §6/mm stop <rotation> §e- Stop the rotation cycle; <rotation>: Rotation ID");
					sender.sendMessage("§e> §6/mm status <rotation> §e- View the status of rotation; <rotation>: Rotation ID");
					sender.sendMessage("§e> §e===<§6MinigameManager Help§e>===");
				} else if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("minigamemanager.admin.reload")) {
					MinigameManager.getPlugin().reloadConfig();
					MinigameManager.getMinigameManager().getMinigameConfig().reloadConfig();
					MinigameManager.getMinigameManager().getMinigameLocations().reloadConfig();
					MinigameManager.getPlugin().loadDefaultMinigames();
					sender.sendMessage("§aThe configs have been reloaded!");
				} else if (args[0].equalsIgnoreCase("list") && sender.hasPermission("minigamemanager.admin.list")) {
					String minigames = "";
					for (Class<? extends Minigame> mclazz : manager.getMinigames()) {
						MinigameAttributes attr = mclazz.getAnnotation(MinigameAttributes.class);
						if (attr == null)
							continue;
						minigames += "§e, §6" + attr.name();
					}
					if (minigames.length() > 4)
						sender.sendMessage("§eEnabled minigames: " + minigames.substring(4));
					else
						sender.sendMessage("§eEnabled minigames: §c(none)");
				} else
					return false;
			} else if (args.length == 2) {
				int id = -1;
				try {
					id = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {}
				RotationManager rm = manager.getRotationManager();
				boolean valid = id >= 0 && id < rm.getRotations().length;
				Rotation r = valid ? rm.getRotation(id) : null;
				if (args[0].equalsIgnoreCase("start") && sender.hasPermission("minigamemanager.admin.start")) {
					if (!valid)
						sender.sendMessage("§cThat is not a valid rotation id!");
					else {
						if (r.getState() == RotationState.STOPPED) {
							r.resume();
							sender.sendMessage("§aStarted rotation #" + id);
						} else
							sender.sendMessage("§cRotation is already running");
					}
				} else if (args[0].equalsIgnoreCase("stop") && sender.hasPermission("minigamemanager.admin.stop")) {
					if (!valid)
						sender.sendMessage("§cThat is not a valid rotation id!");
					else {
						if (r.getState() == RotationState.STOPPED)
							sender.sendMessage("§cRotation is already stopped");
						else {
							
						}
					}
				} else if (args[0].equalsIgnoreCase("status") && sender.hasPermission("minigamemanager.admin.status")) {
					if (!valid)
						sender.sendMessage("§cThat is not a valid rotation id!");
					else {
						
					}
				} else if (args[0].equalsIgnoreCase("info")) {
					for (Class<? extends Minigame> mclazz : manager.getMinigames()) {
						MinigameAttributes attr = mclazz.getAnnotation(MinigameAttributes.class);
						if (attr == null)
							continue;
						if (args[1].equalsIgnoreCase(attr.name())) {
							sender.sendMessage("§e===<§6Minigame Information§e>===");
							sender.sendMessage("§e> Name: §6" + attr.name());
							String[] _authors = attr.authors();
							String authors = "";
							if (_authors.length > 0) {
								if (_authors.length == 1)
									authors = "42" + _authors[0];
								else {
									for (String a : _authors)
										authors += ", " + a;
								}
								sender.sendMessage("§e> Author(s): §6" + authors.substring(2));
							}
							sender.sendMessage("§e> Type: §6" + attr.type().toString());
							sender.sendMessage("§e===<§6Minigame Information§e>===");
							return true;
						}
					}
					sender.sendMessage("§cCould not find minigame with name: §4" + args[1]);
				}
			} else
				return false;
			return true;
		} else
			return false;
	}
	
}
