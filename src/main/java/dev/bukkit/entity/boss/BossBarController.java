package dev.bukkit.entity.boss;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

public class BossBarController {

    private final UUID id = UUID.randomUUID();
    private final String title;
    private final Set<UUID> viewers = new HashSet<>();
    private float progress = 1.0f;

    public BossBarController(String title) {
        this.title = title;
    }

    public void addViewer(Player player) {
        viewers.add(player.getUniqueId());
        sendBar(player, true);
    }

    public void removeViewer(Player player) {
        viewers.remove(player.getUniqueId());
        sendBar(player, false);
    }

    public void setVisibleToPlayers(Iterable<? extends Player> players) {
        for (Player player : players) {
            addViewer(player);
        }
    }

    public void updateProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        for (UUID viewer : new HashSet<>(viewers)) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                sendBar(player, true);
            }
        }
    }

    public void remove() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            sendBar(player, false);
        });
    }

    private void sendBar(Player player, boolean visible) {
        if (!player.isOnline()) {
            viewers.remove(player.getUniqueId());
            return;
        }

        int action = visible ? 0 : 1;
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = pm.createPacket(PacketType.Play.Server.BOSS);
        packet.getUUIDs().write(0, id);
        packet.getIntegers().write(0, action);
        packet.getChatComponents().write(0, WrappedChatComponent.fromText(title));
        packet.getFloat().write(0, progress);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception ignored) {
            // Packet-based boss bars are intentionally best-effort for compatibility
            // without a
            // dedicated server-version specific packet mapper.
        }

        if (!visible) {
            viewers.remove(player.getUniqueId());
        }
    }

    public String getTitle() {
        return title;
    }

    public float getProgress() {
        return progress;
    }
}
