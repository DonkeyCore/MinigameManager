package me.donkeycore.minigamemanager.rotations;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.donkeycore.minigamemanager.api.minigame.Minigame;
import me.donkeycore.minigamemanager.api.rotation.Rotation;
import me.donkeycore.minigamemanager.api.rotation.RotationManager;
import me.donkeycore.minigamemanager.api.rotation.RotationState;
import me.donkeycore.minigamemanager.config.MessageType;
import me.donkeycore.minigamemanager.core.MinigameManager;

public final class DefaultRotationManager implements RotationManager {
	
	private final MinigameManager manager;
	private final List<DefaultRotation> rotations = new ArrayList<>();
	private boolean running;
	private boolean force = false;
	
	public DefaultRotationManager(MinigameManager manager, int rotations) {
		this.manager = manager;
		for (int i = 0; i < rotations; i++)
			this.rotations.add(new DefaultRotation(this, i));
		this.running = true;
	}
	
	@Override
	public boolean join(Player player) {
		Validate.notNull(player);
		int maxPlayers = manager.getMinigameConfig().getMaximumPlayers();
		for (DefaultRotation r : rotations) {
			if (r.getPlayers().size() >= maxPlayers)
				continue;
			r.join(player.getUniqueId());
			if (r.getState() == RotationState.LOBBY && r.getPlayers().size() >= manager.getMinigameConfig().getMinimumPlayers())
				chooseMinigame(r);
			return true;
		}
		return false;
	}
	
	@Override
	public boolean join(Player player, int id) {
		Validate.notNull(player, "Player cannot be null!");
		Validate.isTrue(id >= 0 && id < rotations.size(), id + " is not a valid rotation ID! Current number of rotations: " + rotations.size());
		DefaultRotation r = rotations.get(id);
		int maxPlayers = manager.getMinigameConfig().getMaximumPlayers();
		if (r.getPlayers().size() >= maxPlayers)
			return false;
		r.join(player.getUniqueId());
		if (r.getState() == RotationState.LOBBY && r.getPlayers().size() >= manager.getMinigameConfig().getMinimumPlayers())
			chooseMinigame(r);
		return true;
	}
	
	@Override
	public boolean leave(Player player, boolean kicked) {
		Validate.notNull(player, "Player cannot be null!");
		UUID uuid = player.getUniqueId();
		for (DefaultRotation r : rotations) {
			if (r.hasPlayer(uuid)) {
				r.leave(uuid, kicked);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public Rotation[] getRotations() {
		return rotations.toArray(new Rotation[rotations.size()]);
	}
	
	@Override
	public Rotation getRotation(int id) {
		Validate.isTrue(id >= 0 && id < rotations.size(), id + " is not a valid rotation ID! Current number of rotations: " + rotations.size());
		return rotations.get(id);
	}
	
	@Override
	public Rotation getRotation(Player player) {
		Validate.notNull(player, "Player cannot be null!");
		UUID uuid = player.getUniqueId();
		for (Rotation r : rotations) {
			if (r.hasPlayer(uuid))
				return r;
		}
		return null;
	}
	
	@Override
	public void chooseMinigame(Rotation rotation) {
		Validate.notNull(rotation, "The rotation cannot be null!");
		Validate.isTrue(rotation instanceof DefaultRotation, "Rotation type " + rotation.getClass().getSimpleName() + " is invalid for " + getClass().getSimpleName());
		final DefaultRotation r = (DefaultRotation) rotation;
		// Don't want to do anything if shutting down
		if (!running)
			return;
		// Don't run if already playing
		if(rotation.getState() != RotationState.LOBBY)
			return;
		final DefaultRotationManager rm = this;
		Bukkit.getScheduler().runTask(MinigameManager.getPlugin(), new Runnable() {
			public void run() {
				Minigame minigame = null;
				int tries = 10;
				// Try 10 times to get a random minigame before giving up
				do
					minigame = getRandomMinigame(r);
				while (--tries > 0 && minigame == null);
				if (minigame == null) {
					// Message players
					r.announce(ChatColor.translateAlternateColorCodes('&', manager.getMinigameConfig().getMessage(MessageType.NOT_ENOUGH_PLAYERS)));
					// Wait for more players to join
				} else {
					r.setState(RotationState.COUNTDOWN);
					// Announce next minigame
					r.announce(ChatColor.translateAlternateColorCodes('&', manager.getMinigameConfig().getMessage(MessageType.NEXT_MINIGAME).replace("%minigame%", minigame.getName())));
					// Async countdown timer
					Countdown countdown = new Countdown(manager, rm, r, minigame, force);
					force = false;
					BukkitTask bt = Bukkit.getScheduler().runTaskTimer(MinigameManager.getPlugin(), countdown, 20L, 20L);
					countdown.setTask(bt);
				}
			}
		});
	}
	
	protected void start(DefaultRotation r, Minigame minigame) {
		Validate.notNull(r, "Rotation cannot be null!");
		Validate.notNull(minigame, "Minigame cannot be null!");
		// Set the rotation minigame and copy current players to another list
		r.beginMinigame(minigame);
		// Teleport everybody to possibly random spawns and optionally send them a mapinfo message 
		for (UUID u : r.getPlayers()) {
			Player player = Bukkit.getPlayer(u);
			if (player == null)
				r.leave(u);
			else
				player.teleport(minigame.getStartingLocation());
			String[] mapinfo = manager.getMinigameLocations().getMapInfo(minigame.getName(), "spawns");
			if (mapinfo.length > 0)
				player.sendMessage(ChatColor.translateAlternateColorCodes('&', manager.getMinigameConfig().getMessage(MessageType.MAPINFO)).replace("%name%", mapinfo[0]).replace("%author%", mapinfo[1]));
		}
	}

	@Override
	public void force(Rotation r) {
		if(r.getState() != RotationState.LOBBY)
			return;
		force = true;
		chooseMinigame(r);
	}
	
	@Override
	public void shutdown() {
		this.running = false;
		for (DefaultRotation r : rotations) {
			r.finish();
			// Avoid ConcurrentModificationExcpetion
			UUID[] uuids = r.getPlayers().toArray(new UUID[r.getPlayers().size()]);
			for (UUID uuid : uuids)
				r.leave(uuid, true);
		}
	}
	
	@SuppressWarnings("unchecked")
	private Minigame getRandomMinigame(Rotation r) {
		Set<Class<? extends Minigame>> m = getMinigamesWithMinimum(r.getPlayers().size());
		if (m.size() < 1)
			return null;
		// Get random minigame
		Class<? extends Minigame> clazz = (Class<? extends Minigame>) m.toArray()[new Random().nextInt(m.size())];
		try {
			// Attempt to create a new instance of the minigame
			return clazz.getConstructor(Rotation.class).newInstance(r);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			return null;
		}
		
	}
	
	private Set<Class<? extends Minigame>> getMinigamesWithMinimum(int currentPlayers) {
		Set<Class<? extends Minigame>> validMinigames = new HashSet<>();
		// Cycle through the minigames with their corresponding minimums
		for (Entry<Class<? extends Minigame>, Integer> e : manager.getMinigamesWithMinimums().entrySet()) {
			// If the current players are sufficient for the minigame, add it to the valid minigames
			if (force || currentPlayers >= e.getValue())
				validMinigames.add(e.getKey());
		}
		return validMinigames;
	}
	
}
