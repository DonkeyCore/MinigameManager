package minigamemanager.rotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import minigamemanager.api.minigame.Minigame;
import minigamemanager.api.minigame.Minigame.Bonus;
import minigamemanager.api.minigame.MinigameErrors;
import minigamemanager.api.profile.PlayerProfile;
import minigamemanager.api.rotation.Rotation;
import minigamemanager.api.rotation.RotationManager;
import minigamemanager.api.rotation.RotationState;
import minigamemanager.api.scoreboard.ScoreboardBuilder;
import minigamemanager.api.winner.WinnerList;
import minigamemanager.config.MessageType;
import minigamemanager.config.MinigameMessages;
import minigamemanager.config.MinigameSettings;
import minigamemanager.core.MinigameManager;

/**
 * The default rotation object to be used to represent a rotation where players
 * cycle through minigames
 * 
 * @author DonkeyCore
 */
public final class DefaultRotation implements Rotation {
	
	/**
	 * All players in the rotation, both playing and not
	 */
	private final List<UUID> players = new ArrayList<>();
	/**
	 * Separate array for those in-game to separate newly joining from currently
	 * playing
	 */
	private final List<UUID> inGame = new ArrayList<>();
	/**
	 * The parent rotation manager
	 */
	private final RotationManager rm;
	/**
	 * The id of this rotation
	 */
	private final int id;
	/**
	 * The current minigame
	 */
	private Minigame minigame = null;
	/**
	 * The last minigame played
	 */
	private Class<? extends Minigame> lastMinigame = null;
	/**
	 * The current rotation state
	 */
	private RotationState state = RotationState.LOBBY;
	/**
	 * Blank scoreboard for use when leaving a minigame to clear any active
	 * scoreboards
	 */
	private final Scoreboard blankScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
	
	/**
	 * Create a new rotation
	 * 
	 * @param rm The parent rotation manager
	 * @param id The id of this rotation
	 */
	public DefaultRotation(RotationManager rm, int id) {
		this.rm = rm;
		this.id = id;
	}
	
	/**
	 * Have a player join the rotation
	 * 
	 * @param uuid The UUID of the player to join
	 */
	protected void join(UUID uuid) {
		Player p = Bukkit.getPlayer(uuid);
		if (p != null) {
			// only add if they're not already in there
			if (players.contains(uuid))
				throw new IllegalArgumentException("Player was already in rotation!");
			// add them and teleport to lobby with a welcoming message
			players.add(uuid);
			MinigameManager manager = MinigameManager.getMinigameManager();
			p.teleport(manager.getDefaultMinigameLocations().getRotationLocation("lobby"));
			clean(p);
			p.sendMessage(manager.getMessages().getMessage(MessageType.JOIN).replace("%rotation%", "" + (id + 1)));
			// send a sorry message if the rotation is in-game
			if (getState() == RotationState.INGAME)
				p.sendMessage(manager.getMessages().getMessage(MessageType.JOIN_AFTER_START).replace("%rotation%", "" + (id + 1)));
			// heal them
			p.setHealth(p.getMaxHealth());
			p.setFoodLevel(20);
		}
	}
	
	/**
	 * Have a player leave the rotation
	 * 
	 * @param uuid The UUID of the player to leave
	 */
	protected void leave(UUID uuid) {
		leave(uuid, false);
	}
	
	/**
	 * Have a player leave the rotation
	 * 
	 * @param uuid The UUID of the player to leave
	 * @param kicked Whether they were kicked out of the rotation
	 */
	protected void leave(UUID uuid, boolean kicked) {
		// only let them leave if they're already in there
		if (players.contains(uuid)) {
			players.remove(uuid);
			if (inGame.contains(uuid))
				inGame.remove(uuid);
			// clear the scoreboard, teleport to spawn, and send a message
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) {
				clean(p);
				MinigameManager manager = MinigameManager.getMinigameManager();
				p.teleport(manager.getDefaultMinigameLocations().getRotationLocation("spawn"));
				p.setGameMode(GameMode.ADVENTURE);
				p.sendMessage(manager.getMessages().getMessage(kicked ? MessageType.KICK : MessageType.LEAVE));
			}
			// sad, sad times
			if (inGame.size() < 2 && state != RotationState.LOBBY && state != RotationState.STOPPED) {
				if (minigame != null)
					minigame.end(MinigameErrors.NOT_ENOUGH_PLAYERS, null);
				else
					finish(MinigameErrors.NOT_ENOUGH_PLAYERS, null);
			}
		} else
			throw new IllegalArgumentException("Player was not in rotation!");
	}
	
	@Override
	public boolean hasPlayer(UUID uuid) {
		Validate.notNull(uuid);
		return players.contains(uuid);
	}
	
	@Override
	public List<UUID> getPlayers() {
		return players;
	}
	
	@Override
	public boolean isInGame(UUID uuid) {
		Validate.notNull(uuid);
		return inGame.contains(uuid);
	}
	
	@Override
	public List<UUID> getInGame() {
		return inGame;
	}
	
	@Override
	public int getId() {
		return id;
	}
	
	/**
	 * Begin the minigame
	 * 
	 * @param minigame The minigame to start
	 * 
	 * @return Whether the process was successful
	 */
	protected boolean beginMinigame(Minigame minigame) {
		Validate.notNull(minigame);
		// players must always be at least 1 for testing, and at least 2 for releases
		if (players.size() < 1 || (players.size() < 2 && MinigameManager.isRelease()))
			return false;
		setMinigame(minigame); // Just in case
		// set state, add all players to ingame list, set default gamemode, and start the fun!
		setState(RotationState.INGAME);
		inGame.addAll(players);
		for (UUID u : getInGame()) {
			Player player = Bukkit.getPlayer(u);
			clean(player);
			minigame.setAlive(player, true);
		}
		return true;
	}
	
	protected void setMinigame(Minigame minigame) {
		this.minigame = minigame;
	}
	
	@Override
	public void finish(int error, WinnerList winners) {
		if (error == MinigameErrors.SUCCESS) {
			MinigameMessages messages = MinigameManager.getMinigameManager().getMessages();
			for (UUID uuid : inGame)
				PlayerProfile.getPlayerProfile(uuid).playedGame();
			if (winners != null) {
				minigame.titleAll(messages.getMessage(MessageType.ANNOUNCE_WINNER).replace("%winner%", winners.getFirstPlaceName()), null, 5, 20, 5);
				if (MinigameManager.getMinigameManager().getMinigameSettings().eloEnabled()) {
					UUID[] first = winners.getFirstPlace();
					UUID[] second = winners.getSecondPlace();
					UUID[] third = winners.getThirdPlace();
					if (first != null) {
						UUID[] notFirst = subtract(inGame, first);
						for (UUID f : first) {
							PlayerProfile fp = PlayerProfile.getPlayerProfile(f);
							for (UUID u : notFirst)
								fp.winELO(PlayerProfile.getPlayerProfile(u).getData().getELO());
						}
					}
					if (second != null) {
						UUID[] notSecond = subtract(inGame, second);
						for (UUID s : second) {
							PlayerProfile sp = PlayerProfile.getPlayerProfile(s);
							for (UUID u : notSecond) {
								long uelo = PlayerProfile.getPlayerProfile(u).getData().getELO();
								if (contains(first, u))
									sp.loseELO(uelo);
								else
									sp.winELO(uelo);
							}
						}
					}
					if (third != null) {
						UUID[] notThird = subtract(inGame, third);
						for (UUID t : third) {
							PlayerProfile tp = PlayerProfile.getPlayerProfile(t);
							for (UUID u : notThird) {
								long uelo = PlayerProfile.getPlayerProfile(u).getData().getELO();
								if (contains(first, u) || contains(second, u))
									tp.loseELO(uelo);
								else
									tp.winELO(uelo);
							}
						}
					}
					for (UUID uuid : inGame) {
						PlayerProfile p = PlayerProfile.getPlayerProfile(uuid);
						if ((first == null || !contains(first, uuid)) && (second == null || !contains(second, uuid)) && (third == null || !contains(third, uuid))) {
							for (UUID u : inGame)
								p.loseELO(PlayerProfile.getPlayerProfile(u).getData().getELO());
						}
						((Player) p.getPlayer()).sendMessage(messages.getMessage(MessageType.UPDATED_ELO).replace("%elo%", p.getData().getELO() + ""));
					}
				}
			}
			for (Bonus bonus : minigame.getBonuses()) {
				PlayerProfile.getPlayerProfile(bonus.getUUID()).deposit(bonus.getCurrency());
				MinigameSettings settings = MinigameManager.getMinigameManager().getMinigameSettings();
				String currency = bonus.getCurrency() + "";
				if (settings.useCurrencyPrefix())
					currency = settings.getCurrencyPrefix() + currency;
				else
					currency = currency + settings.getCurrencySuffix();
				Bukkit.getPlayer(bonus.getUUID()).sendMessage(messages.getMessage(MessageType.AWARDED_BONUS).replace("%currency%", currency).replace("%reason%", bonus.getReason()));
			}
		}
		// stop everything with an optional error, then restart the countdown
		stop(error);
		resume();
	}
	
	private static UUID[] subtract(List<UUID> root, UUID[] subtract) {
		List<UUID> rootClone = new ArrayList<UUID>(root);
		for (UUID u : subtract) {
			if (rootClone.contains(u))
				rootClone.remove(u);
		}
		return rootClone.toArray(new UUID[rootClone.size()]);
	}
	
	private static boolean contains(UUID[] root, UUID check) {
		for (UUID u : root) {
			if (u.equals(check))
				return true;
		}
		return false;
	}
	
	@Override
	public void stop(int error) {
		MinigameManager manager = MinigameManager.getMinigameManager();
		// don't stop several times in a row
		if (getState() == RotationState.STOPPED)
			throw new IllegalStateException("Cannot stop if already stopped!");
		setState(RotationState.STOPPED);
		// stop any minigames if they're going
		if (minigame != null) {
			minigame.onEnd(error);
			// clear listeners and cancel tasks
			manager.clearListeners(minigame);
			for (BukkitTask bt : minigame.getTasks())
				bt.cancel();
			minigame.getTasks().clear();
			// reset minigame variables
			lastMinigame = minigame.getClass();
			minigame = null;
		}
		// clear/reset everything, and teleport everybody to the lobby
		for (UUID u : inGame)
			clean(Bukkit.getPlayer(u));
		// show all players
		for (UUID u : players) {
			Player p = Bukkit.getPlayer(u);
			for (UUID u2 : players) {
				if (!u.equals(u2))
					Bukkit.getPlayer(u2).showPlayer(p);
			}
		}
		inGame.clear();
		teleportAll(manager.getDefaultMinigameLocations().getRotationLocation("lobby"));
		setLobbyScoreboard();
	}
	
	private void clean(final Player player) {
		for (PotionEffect pe : player.getActivePotionEffects())
			player.removePotionEffect(pe.getType());
		quench(player);
		player.getInventory().clear();
		player.setScoreboard(blankScoreboard);
		player.setGameMode(GameMode.ADVENTURE);
		player.setHealth(player.getMaxHealth());
		player.setFoodLevel(20);
		player.setAllowFlight(false);
		player.setFlying(false);
	}
	
	private void quench(final Player player) {
		// for some reason, it is required to make a delayed task to stop a player from being on fire
		Bukkit.getScheduler().scheduleSyncDelayedTask(MinigameManager.getPlugin(), new Runnable() {
			
			@Override
			public void run() {
				player.setFireTicks(0);
			}
		});
	}
	
	void setLobbyScoreboard() {
		for (UUID u : players)
			setLobbyScoreboard(u);
	}
	
	void setLobbyScoreboard(UUID u) {
		MinigameManager manager = MinigameManager.getMinigameManager();
		if (state != RotationState.INGAME && manager.getMinigameSettings().lobbyScoreboard()) {
			MinigameMessages m = manager.getMessages();
			Player player = Bukkit.getPlayer(u);
			ScoreboardBuilder lobby = new ScoreboardBuilder("lobby" + id, m.getMessage(MessageType.LOBBY_SCOREBOARD__TITLE).replace("%id%", "" + (id + 1)).replace("%display%", player.getDisplayName()).replace("%name%", player.getName()));
			String lines = m.getMessage(MessageType.LOBBY_SCOREBOARD__CONTENTS);
			lines = lines.replace("%id%", "" + (id + 1)).replace("%display%", player.getDisplayName()).replace("%name%", player.getName());
			MinigameSettings s = manager.getMinigameSettings();
			lines = lines.replace("%players%", "" + players.size()).replace("%maxplayers%", "" + s.getMaximumPlayers());
			lines = lines.replace("%moneyname%", s.getCurrencyName());
			if (s.useCurrencyPrefix())
				lines = lines.replace("%money%", s.getCurrencyPrefix() + PlayerProfile.getPlayerProfile(u).getCurrency());
			else
				lines = lines.replace("%money%", PlayerProfile.getPlayerProfile(u).getData().getCurrency() + s.getCurrencySuffix());
			if (state == RotationState.STOPPED) {
				lines = lines.replace("%statusmessage%", m.getMessage(MessageType.LOBBY_SCOREBOARD__STATUS_MESSAGE));
				lines = lines.replace("%status%", m.getMessage(MessageType.LOBBY_SCOREBOARD__STATUS_STOPPED));
			} else if (state == RotationState.LOBBY) {
				lines = lines.replace("%statusmessage%", m.getMessage(MessageType.LOBBY_SCOREBOARD__STATUS_MESSAGE));
				lines = lines.replace("%status%", m.getMessage(MessageType.LOBBY_SCOREBOARD__STATUS_WAITING));
			} else if (state == RotationState.COUNTDOWN) { // Can be assumed but present for readability
				lines = lines.replace("%statusmessage%", m.getMessage(MessageType.LOBBY_SCOREBOARD__NEXT_MINIGAME));
				lines = lines.replace("%status%", minigame.getName().replace('_', ' '));
			}
			lobby.setOrderedLines(lines.split("\n"));
			player.setScoreboard(lobby.build());
		}
	}
	
	@Override
	public void resume() {
		// only resume if stopped
		if (getState() != RotationState.STOPPED)
			throw new IllegalStateException("Cannot resume if not stopped!");
		// set state to lobby and start the countdown via RotationManager implementation
		setState(RotationState.LOBBY);
		rm.start(this);
		setLobbyScoreboard();
	}
	
	@Override
	public void announce(String message) {
		Validate.notNull(message);
		for (UUID u : players)
			Bukkit.getPlayer(u).sendMessage(message);
	}
	
	@Override
	public void teleportAll(Location loc) {
		Validate.notNull(loc);
		for (UUID u : getPlayers()) {
			Player player = Bukkit.getPlayer(u);
			if (player == null)
				leave(u);
			else
				player.teleport(loc);
		}
	}
	
	@Override
	public RotationState getState() {
		return state;
	}
	
	/**
	 * Set the state of this rotation
	 * 
	 * @param state The new state
	 */
	protected void setState(RotationState state) {
		this.state = state;
	}
	
	@Override
	public Minigame getCurrentMinigame() {
		return minigame;
	}
	
	public Class<? extends Minigame> getLastMinigame() {
		return lastMinigame;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + ((inGame == null) ? 0 : inGame.hashCode());
		result = prime * result + ((minigame == null) ? 0 : minigame.hashCode());
		result = prime * result + ((players == null) ? 0 : players.hashCode());
		result = prime * result + ((rm == null) ? 0 : rm.hashCode());
		result = prime * result + ((state == null) ? 0 : state.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DefaultRotation other = (DefaultRotation) obj;
		if (id != other.id)
			return false;
		if (inGame == null) {
			if (other.inGame != null)
				return false;
		} else if (!inGame.equals(other.inGame))
			return false;
		if (minigame == null) {
			if (other.minigame != null)
				return false;
		} else if (!minigame.equals(other.minigame))
			return false;
		if (players == null) {
			if (other.players != null)
				return false;
		} else if (!players.equals(other.players))
			return false;
		if (state != other.state)
			return false;
		return true;
	}
	
}
