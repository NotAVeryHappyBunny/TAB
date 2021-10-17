package me.neznamy.tab.platforms.velocity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.GameProfile.Property;

import me.neznamy.tab.api.ProtocolVersion;
import me.neznamy.tab.api.chat.IChatBaseComponent;
import me.neznamy.tab.api.protocol.PacketPlayOutBoss;
import me.neznamy.tab.api.protocol.PacketPlayOutChat;
import me.neznamy.tab.api.protocol.PacketPlayOutPlayerInfo;
import me.neznamy.tab.api.protocol.PacketPlayOutPlayerInfo.PlayerInfoData;
import me.neznamy.tab.api.protocol.PacketPlayOutPlayerListHeaderFooter;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.features.PluginMessageHandler;
import me.neznamy.tab.shared.proxy.ProxyTabPlayer;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Flag;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;

/**
 * TabPlayer for Velocity
 */
public class VelocityTabPlayer extends ProxyTabPlayer {

	//the velocity player
	private Player player;
	
	//uuid used in tablist
	private UUID tablistId;
	
	//player's visible boss bars
	private Map<UUID, BossBar> bossbars = new HashMap<>();

	/**
	 * Constructs new instance for given player
	 * @param p - velocity player
	 */
	public VelocityTabPlayer(Player p, PluginMessageHandler plm) {
		super(plm, p.getUniqueId(), p.getUsername(), p.getCurrentServer().isPresent() ? p.getCurrentServer().get().getServerInfo().getName() : "-", "N/A");
		player = p;
		UUID offlineId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + getName()).getBytes(StandardCharsets.UTF_8));
		tablistId = TAB.getInstance().getConfiguration().getConfig().getBoolean("use-online-uuid-in-tablist", true) ? getUniqueId() : offlineId;
		version = ProtocolVersion.fromNetworkId(player.getProtocolVersion().getProtocol());
	}
	
	@Override
	public boolean hasPermission0(String permission) {
		return player.hasPermission(permission);
	}
	
	@Override
	public int getPing() {
		return (int) player.getPing();
	}
	
	@Override
	public void sendPacket(Object packet) {
		long time = System.nanoTime();
		if (packet == null || !player.isActive()) return;
		if (packet instanceof PacketPlayOutChat){
			handle((PacketPlayOutChat) packet);
		}
		if (packet instanceof PacketPlayOutPlayerListHeaderFooter) {
			handle((PacketPlayOutPlayerListHeaderFooter) packet);
		}
		if (packet instanceof PacketPlayOutBoss) {
			handle((PacketPlayOutBoss) packet);
		}
		if (packet instanceof PacketPlayOutPlayerInfo) {
			handle((PacketPlayOutPlayerInfo) packet);
		}
		TAB.getInstance().getCPUManager().addMethodTime("sendPacket", System.nanoTime()-time);
	}

	private void handle(PacketPlayOutChat packet) {
		player.sendMessage(Identity.nil(), Main.stringToComponent(packet.getMessage().toString(getVersion())), MessageType.valueOf(packet.getType().name()));
	}
	
	private void handle(PacketPlayOutPlayerListHeaderFooter packet) {
		player.getTabList().setHeaderAndFooter(Main.stringToComponent(packet.getHeader().toString(getVersion())), Main.stringToComponent(packet.getFooter().toString(getVersion())));
	}
	
	private void handle(PacketPlayOutBoss packet) {
		BossBar bar;
		switch (packet.getOperation()) {
		case ADD:
			bar = BossBar.bossBar(Main.stringToComponent(IChatBaseComponent.optimizedComponent(packet.getName()).toString(getVersion())), 
					packet.getPct(), 
					Color.valueOf(packet.getColor().toString()), 
					Overlay.valueOf(packet.getOverlay().toString()));
			if (packet.isCreateWorldFog()) bar.addFlag(Flag.CREATE_WORLD_FOG);
			if (packet.isDarkenScreen()) bar.addFlag(Flag.DARKEN_SCREEN);
			if (packet.isPlayMusic()) bar.addFlag(Flag.PLAY_BOSS_MUSIC);
			bossbars.put(packet.getId(), bar);
			player.showBossBar(bar);
			break;
		case REMOVE:
			player.hideBossBar(bossbars.get(packet.getId()));
			bossbars.remove(packet.getId());
			break;
		case UPDATE_PCT:
			bossbars.get(packet.getId()).percent(packet.getPct());
			break;
		case UPDATE_NAME:
			bossbars.get(packet.getId()).name(Main.stringToComponent(IChatBaseComponent.optimizedComponent(packet.getName()).toString(getVersion())));
			break;
		case UPDATE_STYLE:
			bar = bossbars.get(packet.getId());
			bar.overlay(Overlay.valueOf(packet.getOverlay().toString()));
			bar.color(Color.valueOf(packet.getColor().toString()));
			break;
		case UPDATE_PROPERTIES:
			bar = bossbars.get(packet.getId());
			processFlag(bar, packet.isCreateWorldFog(), Flag.CREATE_WORLD_FOG);
			processFlag(bar, packet.isDarkenScreen(), Flag.DARKEN_SCREEN);
			processFlag(bar, packet.isPlayMusic(), Flag.PLAY_BOSS_MUSIC);
			break;
		default:
			break;
		}
	}
	
	private void processFlag(BossBar bar, boolean targetValue, Flag flag) {
		if (targetValue) {
			if (!bar.hasFlag(flag)) {
				bar.addFlag(flag);
			}
		} else {
			if (bar.hasFlag(flag)) {
				bar.removeFlag(flag);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void handle(PacketPlayOutPlayerInfo packet) {
		for (PlayerInfoData data : packet.getEntries()) {
			switch (packet.getAction()) {
			case ADD_PLAYER:
				if (player.getTabList().containsEntry(data.getUniqueId())) continue;
				player.getTabList().addEntry(TabListEntry.builder()
						.tabList(player.getTabList())
						.displayName(data.getDisplayName() == null ? null : Main.stringToComponent(data.getDisplayName().toString(getVersion())))
						.gameMode(data.getGameMode().ordinal()-1)
						.profile(new GameProfile(data.getUniqueId(), data.getName(), data.getSkin() == null ? new ArrayList<>() : (List<Property>) data.getSkin()))
						.latency(data.getLatency())
						.build());
				break;
			case REMOVE_PLAYER:
				player.getTabList().removeEntry(data.getUniqueId());
				break;
			case UPDATE_DISPLAY_NAME:
				getEntry(data.getUniqueId()).setDisplayName(data.getDisplayName() == null ? null : Main.stringToComponent(data.getDisplayName().toString(getVersion())));
				break;
			case UPDATE_LATENCY:
				getEntry(data.getUniqueId()).setLatency(data.getLatency());
				break;
			case UPDATE_GAME_MODE:
				getEntry(data.getUniqueId()).setGameMode(data.getGameMode().ordinal()-1);
				break;
			default:
				break;
			}
		}
	}
	
	private TabListEntry getEntry(UUID id) {
		for (TabListEntry entry : player.getTabList().getEntries()) {
			if (entry.getProfile().getId().equals(id)) return entry;
		}
		//return dummy entry to not cause NPE
		//possibly add logging into the future to see when this happens
		return TabListEntry.builder()
				.tabList(player.getTabList())
				.displayName(Component.text(""))
				.gameMode(0)
				.profile(new GameProfile(UUID.randomUUID(), "empty", new ArrayList<>()))
				.latency(0)
				.build();
	}
	
	@Override
	public Object getSkin() {
		return player.getGameProfile().getProperties();
	}
	
	@Override
	public Player getPlayer() {
		return player;
	}
	
	@Override
	public UUID getTablistUUID() {
		return tablistId;
	}
	
	@Override
	public boolean isOnline() {
		return player.isActive();
	}

	@Override
	public int getGamemode() {
		return 0; //shrug
	}
}