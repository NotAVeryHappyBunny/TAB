package me.neznamy.tab.platforms.bungeecord;

import java.io.File;
import java.util.List;

import me.neznamy.tab.api.PermissionPlugin;
import me.neznamy.tab.api.protocol.PacketBuilder;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.features.GlobalPlayerlist;
import me.neznamy.tab.shared.features.NameTag;
import me.neznamy.tab.shared.features.PluginMessageHandler;
import me.neznamy.tab.shared.features.bossbar.BossBarManagerImpl;
import me.neznamy.tab.shared.permission.LuckPerms;
import me.neznamy.tab.shared.permission.UltraPermissions;
import me.neznamy.tab.shared.permission.VaultBridge;
import me.neznamy.tab.shared.placeholders.UniversalPlaceholderRegistry;
import me.neznamy.tab.shared.proxy.ProxyPlatform;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Bungeecord implementation of Platform
 */
public class BungeePlatform extends ProxyPlatform {

	//instance of plugin
	private Plugin plugin;
	
	private BungeePacketBuilder packetBuilder = new BungeePacketBuilder();
	
	/**
	 * Constructs new instance with given parameter
	 * @param plugin - main class
	 */
	public BungeePlatform(Plugin plugin, PluginMessageHandler plm) {
		super(plm);
		this.plugin = plugin;
	}
	
	@Override
	public PermissionPlugin detectPermissionPlugin() {
		if (TAB.getInstance().getConfiguration().isBukkitPermissions()) {
			return new VaultBridge(plm);
		} else if (ProxyServer.getInstance().getPluginManager().getPlugin("LuckPerms") != null) {
			return new LuckPerms(ProxyServer.getInstance().getPluginManager().getPlugin("LuckPerms").getDescription().getVersion());
		} else if (ProxyServer.getInstance().getPluginManager().getPlugin("UltraPermissions") != null) {
			return new UltraPermissions(ProxyServer.getInstance().getPluginManager().getPlugin("UltraPermissions").getDescription().getVersion());
		} else {
			return new VaultBridge(plm);
		}
	}

	@Override
	public void loadFeatures() {
		TAB tab = TAB.getInstance();
		new BungeePlaceholderRegistry().registerPlaceholders(tab.getPlaceholderManager());
		new UniversalPlaceholderRegistry().registerPlaceholders(tab.getPlaceholderManager());
		if (tab.getConfiguration().isPipelineInjection()) tab.getFeatureManager().registerFeature("injection", new BungeePipelineInjector());
		if (tab.getConfiguration().getConfig().getBoolean("scoreboard-teams.enabled", true)) tab.getFeatureManager().registerFeature("nametag16", new NameTag());
		tab.loadUniversalFeatures();
		if (tab.getConfiguration().getConfig().getBoolean("bossbar.enabled", false)) tab.getFeatureManager().registerFeature("bossbar", new BossBarManagerImpl());
		if (tab.getConfiguration().getConfig().getBoolean("global-playerlist.enabled", false)) 	tab.getFeatureManager().registerFeature("globalplayerlist", new GlobalPlayerlist());
		for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
			tab.addPlayer(new BungeeTabPlayer(p, plm));
		}
	}
	
	@Override
	public void sendConsoleMessage(String message, boolean translateColors) {
		ProxyServer.getInstance().getConsole().sendMessage(new TextComponent(translateColors ? message.replace('&', '\u00a7') : message));
	}
	
	@Override
	public String getServerVersion() {
		return ProxyServer.getInstance().getVersion();
	}

	@Override
	public String getSeparatorType() {
		return "server";
	}

	@Override
	public File getDataFolder() {
		return plugin.getDataFolder();
	}

	@Override
	public void callLoadEvent() {
		ProxyServer.getInstance().getPluginManager().callEvent(new BungeeTABLoadEvent());
	}

	@Override
	public int getMaxPlayers() {
		return ProxyServer.getInstance().getConfigurationAdapter().getListeners().iterator().next().getMaxPlayers();
	}
	
	@Override
	public String getConfigName() {
		return "bungeeconfig.yml";
	}

	@Override
	public PacketBuilder getPacketBuilder() {
		return packetBuilder;
	}
	
	@Override
	public Object getSkin(List<String> properties) {
		String[][] array = new String[1][3];
		array[0][0] = "textures";
		array[0][1] = properties.get(0);
		array[0][2] = properties.get(1);
		return array;
	}
}