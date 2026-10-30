package dev.bukkit.reward.special;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.DMain;
import dev.bukkit.utils.PartyPlayers;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.reward.special.SpecialItemBuff;

/**
 * Runs Gambler's Note guesses: snapshot the party's HP, collect one numeric
 * chat message during the 3-second window, then compare the estimate with the
 * HP the party actually lost.
 */
public final class GamblersNoteService {

    public static final long WINDOW_MILLIS = 3000L;
    public static final double MAX_PENALTY_HEALTH_FRACTION = 0.5;

    private static final GamblersNoteService INSTANCE = new GamblersNoteService();

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private GamblersNoteService() {
    }

    public static GamblersNoteService getInstance() {
        return INSTANCE;
    }

    public boolean isPending(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean start(Player caster) {
        if (caster == null || !caster.isOnline() || caster.isDead()) {
            return false;
        }
        if (sessions.containsKey(caster.getUniqueId())) {
            caster.sendMessage("§eYour Gambler's Note is already waiting for an answer.");
            return false;
        }
        DMain plugin = DMain.getInstance();
        if (plugin == null) {
            return false;
        }

        Map<UUID, Double> startingHealth = new HashMap<>();
        for (Player player : PartyPlayers.livingWith(caster)) {
            EntityManager.getInstance().getEntity(player.getUniqueId())
                    .ifPresent(entity -> startingHealth.put(player.getUniqueId(), entity.getHealth()));
        }

        Session session = new Session(startingHealth);
        sessions.put(caster.getUniqueId(), session);
        session.task = Bukkit.getScheduler().runTaskLater(plugin, () -> resolve(caster.getUniqueId()),
                WINDOW_MILLIS / 50L);

        caster.sendMessage("§eGambler's Note: type your team damage estimate in chat within §f3s§e.");
        caster.playSound(caster.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
        return true;
    }

    public boolean acceptGuess(Player player, double guess) {
        if (player == null) {
            return false;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.guess != null) {
            return false;
        }
        session.guess = Math.max(0, guess);
        player.sendMessage("§aGambler's Note: locked in §f" + format(session.guess) + "§a damage.");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        return true;
    }

    public void handleQuit(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (session != null) {
            session.cancelTask();
        }
    }

    public void shutdown() {
        for (Session session : sessions.values()) {
            session.cancelTask();
        }
        sessions.clear();
    }

    private void resolve(UUID casterId) {
        Session session = sessions.remove(casterId);
        if (session == null) {
            return;
        }
        session.cancelTask();

        Player caster = Bukkit.getPlayer(casterId);
        if (caster == null || !caster.isOnline()) {
            return;
        }

        double actualDamage = actualTeamDamage(session.startingHealth);
        Double guess = session.guess;
        boolean correct = guess != null && Math.round(guess) == Math.round(actualDamage);
        if (correct) {
            SpecialItemBuff buff = SpecialItemBuffService.getInstance()
                    .applyRandomToAll(PartyPlayers.livingWith(caster)).orElse(null);
            String buffName = buff == null ? "a blessing" : buff.displayName();
            sendToParty(caster, "§aGambler's Note: correct! The team took §f" + format(actualDamage)
                    + "§a damage. Everyone receives §d" + buffName + "§a.");
            caster.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            return;
        }

        double estimate = guess == null ? 0.0 : guess;
        double difference = Math.abs(estimate - actualDamage);
        RPGEntity target = EntityManager.getInstance().getEntity(casterId).orElse(null);
        double maxHealth = target == null ? 100.0 : target.getMaxHealth();
        double penalty = Math.min(difference, maxHealth * MAX_PENALTY_HEALTH_FRACTION);
        if (target != null && target.isAlive() && penalty > 0) {
            target.dealRPGDamage(null, target, penalty, DamageType.TRUE);
        }
        String answer = guess == null ? "no answer" : format(estimate);
        caster.sendMessage("§cGambler's Note: " + answer + " was off. The team took §f" + format(actualDamage)
                + "§c damage; you take §f" + format(penalty) + "§c.");
        caster.playSound(caster.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
    }

    private static double actualTeamDamage(Map<UUID, Double> startingHealth) {
        double lost = 0;
        for (Map.Entry<UUID, Double> entry : startingHealth.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            RPGEntity entity = EntityManager.getInstance().getEntity(entry.getKey()).orElse(null);
            if (entity == null) {
                continue;
            }
            if (!entity.isAlive()) {
                lost += entry.getValue();
            } else {
                lost += Math.max(0, entry.getValue() - entity.getHealth());
            }
        }
        return lost;
    }

    private static void sendToParty(Player anchor, String message) {
        for (Player player : PartyPlayers.livingWith(anchor)) {
            player.sendMessage(message);
        }
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return String.format("%.0f", value);
        }
        return String.format("%.1f", value);
    }

    private static final class Session {
        private final Map<UUID, Double> startingHealth;
        private volatile Double guess;
        private BukkitTask task;

        private Session(Map<UUID, Double> startingHealth) {
            this.startingHealth = Map.copyOf(startingHealth);
        }

        private void cancelTask() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }
}
