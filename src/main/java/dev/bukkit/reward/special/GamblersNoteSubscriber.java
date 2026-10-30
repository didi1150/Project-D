package dev.bukkit.reward.special;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;
import dev.core.utils.AdventureText;
import io.papermc.paper.event.player.AsyncChatEvent;

/** Captures the numeric chat guess for Gambler's Note. */
@EventSubscriber
public final class GamblersNoteSubscriber {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final GamblersNoteService service = GamblersNoteService.getInstance();

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!service.isPending(player.getUniqueId())) {
            return;
        }
        Double guess = parseGuess(AdventureText.plainOrEmpty(event.message()));
        if (guess == null) {
            return;
        }
        if (service.acceptGuess(player, guess)) {
            event.setCancelled(true);
        }
    }

    @Subscribe
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer().getUniqueId());
    }

    private static Double parseGuess(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            double value = Double.parseDouble(matcher.group());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
