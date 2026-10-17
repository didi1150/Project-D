package dev.bukkit.glow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Per-viewer glowing entities implemented with ProtocolLib's packet wrappers.
 *
 * <p>
 * The service sends the entity's shared-flags metadata (with the glowing bit)
 * only to the requested viewer, and creates a client-only scoreboard team per
 * color so the outline is colored. It never mutates the server-side scoreboard
 * and never imports NMS classes.
 * </p>
 */
public final class PlayerGlowService implements Listener {

    private static final byte GLOWING_FLAG = 1 << 6;

    private static final int TEAM_CREATE = 0;
    private static final int TEAM_REMOVE = 1;
    private static final int TEAM_ADD_ENTRY = 3;
    private static final int TEAM_REMOVE_ENTRY = 4;

    private final Plugin plugin;
    private final String teamPrefix;
    private final Map<UUID, ViewerGlow> viewers = new ConcurrentHashMap<>();
    private final Map<EnumWrappers.ChatFormatting, TeamData> teams = new ConcurrentHashMap<>();

    private PacketAdapter packetListener;
    private volatile boolean enabled = true;

    public PlayerGlowService(Plugin plugin) {
        this.plugin = plugin;
        this.teamPrefix = "pdg" + Integer.toString(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE), 36) + "-";

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.packetListener = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.ENTITY_METADATA, PacketType.Play.Server.BUNDLE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handlePacket(event);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(packetListener);
    }

    /**
     * Makes {@code entity} glow for {@code receiver} in the given color. Repeated
     * calls with the same color are cheap no-ops.
     */
    public void setGlowing(Entity entity, Player receiver, NamedTextColor color) {
        if (!enabled || entity == null || receiver == null) {
            return;
        }
        try {
            setGlowingInternal(entity, receiver, color);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to apply glow to " + entity.getUniqueId(), e);
        }
    }

    private void setGlowingInternal(Entity entity, Player receiver, NamedTextColor color) {
        EnumWrappers.ChatFormatting formatting = toFormatting(color);
        ViewerGlow viewer = viewers.computeIfAbsent(receiver.getUniqueId(), ignored -> new ViewerGlow());
        GlowEntry existing = viewer.glows.get(entity.getUniqueId());

        if (existing != null) {
            existing.entityId = entity.getEntityId();
            existing.worldId = entity.getWorld().getUID();
            if (existing.color == formatting) {
                ensureTeam(receiver, viewer, formatting);
                return;
            }

            TeamData oldTeam = teams.get(existing.color);
            if (oldTeam != null) {
                send(receiver, teamEntryPacket(oldTeam.name, TEAM_REMOVE_ENTRY, existing.teamEntry));
            }
            existing.color = formatting;
            ensureTeam(receiver, viewer, formatting);
            TeamData newTeam = teams.get(formatting);
            if (newTeam != null) {
                send(receiver, teamEntryPacket(newTeam.name, TEAM_ADD_ENTRY, existing.teamEntry));
            }
            sendMetadata(receiver, existing.entityId, (byte) (existing.otherFlags | GLOWING_FLAG));
            return;
        }

        byte flags = readFlags(entity);
        GlowEntry entry = new GlowEntry(entity, formatting, (byte) (flags & ~GLOWING_FLAG));
        viewer.glows.put(entity.getUniqueId(), entry);

        ensureTeam(receiver, viewer, formatting);
        TeamData team = teams.get(formatting);
        if (team != null) {
            send(receiver, teamEntryPacket(team.name, TEAM_ADD_ENTRY, entry.teamEntry));
        }
        sendMetadata(receiver, entry.entityId, (byte) (entry.otherFlags | GLOWING_FLAG));
    }

    /**
     * Removes the custom glow from {@code entity} for {@code receiver}. Has no
     * effect on glow supplied by other plugins or vanilla.
     */
    public void unsetGlowing(Entity entity, Player receiver) {
        if (entity == null || receiver == null) {
            return;
        }
        ViewerGlow viewer = viewers.get(receiver.getUniqueId());
        if (viewer == null) {
            return;
        }
        GlowEntry entry = viewer.glows.remove(entity.getUniqueId());
        if (entry == null) {
            return;
        }

        TeamData team = teams.get(entry.color);
        if (team != null) {
            send(receiver, teamEntryPacket(team.name, TEAM_REMOVE_ENTRY, entry.teamEntry));
        }
        sendMetadata(receiver, entry.entityId, resolveRestoreFlags(entry));
    }

    /** Removes every glow this service applied for the receiver. */
    public void unsetAll(Player receiver) {
        if (receiver == null) {
            return;
        }
        ViewerGlow viewer = viewers.remove(receiver.getUniqueId());
        if (viewer == null) {
            return;
        }

        for (GlowEntry entry : viewer.glows.values()) {
            TeamData team = teams.get(entry.color);
            if (team != null) {
                send(receiver, teamEntryPacket(team.name, TEAM_REMOVE_ENTRY, entry.teamEntry));
            }
            sendMetadata(receiver, entry.entityId, resolveRestoreFlags(entry));
        }
        for (EnumWrappers.ChatFormatting color : viewer.sentTeams) {
            sendTeamRemove(receiver, color);
        }
    }

    /** Disables the service and removes its listeners. */
    public void disable() {
        if (!enabled) {
            return;
        }
        enabled = false;

        if (packetListener != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(packetListener);
            } catch (Exception ignored) {
            }
            packetListener = null;
        }
        HandlerList.unregisterAll(this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            unsetAll(player);
        }
        viewers.clear();
        teams.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    // ---- Packet listener ------------------------------------------------------

    private void handlePacket(PacketEvent event) {
        if (!enabled) {
            return;
        }
        Player receiver = event.getPlayer();
        if (receiver == null) {
            return;
        }
        ViewerGlow viewer = viewers.get(receiver.getUniqueId());
        if (viewer == null || viewer.glows.isEmpty()) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            applyGlowFlag(receiver, viewer, event.getPacket());
        } else if (event.getPacketType() == PacketType.Play.Server.BUNDLE) {
            try {
                Iterable<PacketContainer> packets = event.getPacket().getPacketBundles().read(0);
                if (packets == null) {
                    return;
                }
                for (PacketContainer packet : packets) {
                    if (packet.getType() == PacketType.Play.Server.ENTITY_METADATA) {
                        applyGlowFlag(receiver, viewer, packet);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Re-applies the glowing bit to a per-viewer metadata packet. The server
     * broadcasts some metadata to every nearby player, so the packet must be
     * adjusted for each glow viewer rather than mutated globally.
     */
    private void applyGlowFlag(Player receiver, ViewerGlow viewer, PacketContainer packet) {
        int entityId;
        try {
            entityId = packet.getIntegers().read(0);
        } catch (Exception ignored) {
            return;
        }

        GlowEntry entry = findEntry(viewer, receiver, entityId);
        if (entry == null) {
            return;
        }

        List<WrappedDataValue> values;
        try {
            values = packet.getDataValueCollectionModifier().read(0);
        } catch (Exception ignored) {
            return;
        }
        if (values == null) {
            values = Collections.emptyList();
        }

        List<WrappedDataValue> updated = null;
        boolean found = false;
        for (int i = 0; i < values.size(); i++) {
            WrappedDataValue value = values.get(i);
            if (value.getIndex() != 0) {
                continue;
            }

            found = true;
            byte flags = asByte(value.getValue());
            entry.otherFlags = (byte) (flags & ~GLOWING_FLAG);
            byte newFlags = (byte) (flags | GLOWING_FLAG);
            if (newFlags != flags) {
                updated = new ArrayList<>(values);
                updated.set(i, new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), newFlags));
            }
            break;
        }

        if (!found) {
            updated = new ArrayList<>(values);
            updated.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class),
                    (byte) (entry.otherFlags | GLOWING_FLAG)));
        }

        if (updated != null) {
            try {
                packet.getDataValueCollectionModifier().write(0, updated);
            } catch (Exception ignored) {
            }
        }
    }

    private static GlowEntry findEntry(ViewerGlow viewer, Player receiver, int entityId) {
        UUID worldId = receiver.getWorld().getUID();
        for (GlowEntry entry : viewer.glows.values()) {
            if (entry.entityId == entityId && entry.worldId.equals(worldId)) {
                return entry;
            }
        }
        return null;
    }

    private static byte asByte(Object value) {
        if (value instanceof Number number) {
            return number.byteValue();
        }
        return 0;
    }

    // ---- Packet construction --------------------------------------------------

    private void ensureTeam(Player receiver, ViewerGlow viewer, EnumWrappers.ChatFormatting color) {
        if (viewer.sentTeams.contains(color)) {
            return;
        }
        TeamData team = getOrCreateTeam(color);
        if (send(receiver, team.createPacket)) {
            viewer.sentTeams.add(color);
        }
    }

    private TeamData getOrCreateTeam(EnumWrappers.ChatFormatting color) {
        return teams.computeIfAbsent(color, key -> {
            String name = teamPrefix + key.ordinal();
            return new TeamData(name, createTeamPacket(name, key));
        });
    }

    private static PacketContainer createTeamPacket(String teamName, EnumWrappers.ChatFormatting color) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
        packet.getStrings().write(0, teamName);
        packet.getIntegers().write(0, TEAM_CREATE);

        var parameters = GlowTeamParameters.create(color);
        packet.getOptionalTeamParameters().write(0, Optional.of(parameters));
        writeEntries(packet, Collections.emptyList());
        return packet;
    }

    private static PacketContainer teamEntryPacket(String teamName, int mode, String entry) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
        packet.getStrings().write(0, teamName);
        packet.getIntegers().write(0, mode);
        packet.getOptionalTeamParameters().write(0, Optional.empty());
        writeEntries(packet, List.of(entry));
        return packet;
    }

    private static void writeEntries(PacketContainer packet, List<String> entries) {
        StructureModifier<List<String>> lists = packet.getLists(Converters.passthrough(String.class));
        if (lists.size() > 0) {
            lists.write(0, entries);
            return;
        }
        StructureModifier<Object> collections = packet.getModifier().withType(Collection.class);
        if (collections.size() > 0) {
            collections.write(0, new ArrayList<>(entries));
        }
    }

    private void sendTeamRemove(Player receiver, EnumWrappers.ChatFormatting color) {
        TeamData team = teams.get(color);
        if (team == null) {
            return;
        }
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SCOREBOARD_TEAM);
        packet.getStrings().write(0, team.name);
        packet.getIntegers().write(0, TEAM_REMOVE);
        packet.getOptionalTeamParameters().write(0, Optional.empty());
        writeEntries(packet, Collections.emptyList());
        send(receiver, packet);
    }

    private void sendMetadata(Player receiver, int entityId, byte flags) {
        try {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entityId);
            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);
            packet.getDataValueCollectionModifier().write(0,
                    List.of(new WrappedDataValue(0, serializer, flags)));
            send(receiver, packet);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to send glow metadata", e);
        }
    }

    private boolean send(Player receiver, PacketContainer packet) {
        if (receiver == null || packet == null || !receiver.isOnline()) {
            return false;
        }
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(receiver, packet);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to send glow packet", e);
            return false;
        }
    }

    // ---- State ----------------------------------------------------------------

    private static byte readFlags(Entity entity) {
        return readFlags(entity, (byte) 0);
    }

    /**
     * Reads the entity's current shared flags. The fallback is used when the
     * entity is unavailable or the watcher cannot be read.
     */
    private static byte readFlags(Entity entity, byte fallback) {
        try {
            Byte flags = WrappedDataWatcher.getEntityWatcher(entity).getByte(0);
            return flags == null ? fallback : flags;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Restores the entity's current server-side glow state instead of blindly
     * clearing the glowing bit. Another system (for example the absorption
     * shield) may still want the entity to glow after our outline goes away.
     */
    private static byte resolveRestoreFlags(GlowEntry entry) {
        try {
            Entity entity = Bukkit.getEntity(entry.entityUuid);
            if (entity != null) {
                return readFlags(entity, entry.otherFlags);
            }
        } catch (Exception ignored) {
        }
        return entry.otherFlags;
    }

    private static EnumWrappers.ChatFormatting toFormatting(NamedTextColor color) {
        if (color == null) {
            return EnumWrappers.ChatFormatting.WHITE;
        }
        try {
            return EnumWrappers.ChatFormatting.valueOf(color.toString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return EnumWrappers.ChatFormatting.WHITE;
        }
    }

    private record TeamData(String name, PacketContainer createPacket) {
    }

    private static final class ViewerGlow {
        private final Map<UUID, GlowEntry> glows = new ConcurrentHashMap<>();
        private final Set<EnumWrappers.ChatFormatting> sentTeams = ConcurrentHashMap.newKeySet();
    }

    private static final class GlowEntry {
        private volatile int entityId;
        private volatile UUID worldId;
        private final UUID entityUuid;
        private final String teamEntry;
        private EnumWrappers.ChatFormatting color;
        private volatile byte otherFlags;

        private GlowEntry(Entity entity, EnumWrappers.ChatFormatting color, byte otherFlags) {
            this.entityId = entity.getEntityId();
            this.worldId = entity.getWorld().getUID();
            this.entityUuid = entity.getUniqueId();
            this.teamEntry = entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
            this.color = color;
            this.otherFlags = otherFlags;
        }
    }
}
