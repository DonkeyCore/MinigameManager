package me.donkeycore.minigamemanager.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MinigameConfig {
	
	private final JavaPlugin plugin;
	private FileConfiguration config;
	private File configFile;
	private String fileName;
	
	public MinigameConfig(JavaPlugin plugin, FileConfiguration config, File configFile) {
		this.plugin = plugin;
		this.config = config;
		this.configFile = configFile;
		this.fileName = configFile.getName();
	}
	
	public void reloadConfig() {
		if (configFile == null)
			configFile = new File(plugin.getDataFolder(), fileName);
		config = YamlConfiguration.loadConfiguration(configFile);
		Reader defConfigStream = null;
		try {
			defConfigStream = new InputStreamReader(plugin.getResource(fileName), "UTF8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		if (defConfigStream != null) {
			YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
			config.setDefaults(defConfig);
		}
	}
	
	public FileConfiguration getConfig() {
		if (config == null)
			reloadConfig();
		return config;
	}
	
	public void saveConfig() {
		if (config == null || configFile == null)
			return;
		try {
			getConfig().save(configFile);
		} catch (IOException ex) {
			plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
		}
	}
	
	public void saveDefaultConfig() {
		if (configFile == null)
			configFile = new File(plugin.getDataFolder(), fileName);
		if (!configFile.exists())
			plugin.saveResource("locations.yml", false);
	}
	
	public Location[] getMinigameSpawns(String minigame) {
		ConfigurationSection mcs = getConfig().getConfigurationSection(minigame);
		if (mcs == null)
			throw new IllegalArgumentException(minigame + " is not a valid default minigame");
		ConfigurationSection cs = mcs.getConfigurationSection("spawns");
		Set<String> keys = cs.getKeys(false);
		Location[] locations = new Location[keys.size()];
		int i = 0;
		for (String key : keys) {
			World world = Bukkit.getWorld(cs.getString("world"));
			if (world == null)
				throw new RuntimeException("Invalid world for " + key + " (from " + minigame + " spawns)");
			double x = cs.getDouble("x");
			double y = cs.getDouble("y");
			double z = cs.getDouble("z");
			float yaw = Float.parseFloat(cs.getString("yaw"));
			float pitch = Float.parseFloat(cs.getString("pitch"));
			locations[i++] = new Location(world, x, y, z, yaw, pitch);
		}
		return locations;
	}
	
}
