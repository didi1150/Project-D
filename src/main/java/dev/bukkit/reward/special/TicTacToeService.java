package dev.bukkit.reward.special;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.DMain;
import dev.core.minigame.TicTacToeBoard;
import dev.core.minigame.TicTacToeBoard.Mark;
import dev.core.reward.special.SpecialItemBuff;
import dev.core.utils.AdventureText;

/**
 * Runs forced Tic Tac Toe games between two players in a shared chest menu.
 * The initiator always plays red and moves first; the target plays blue.
 */
public final class TicTacToeService {

    private static final TicTacToeService INSTANCE = new TicTacToeService();

    private static final int INVENTORY_SIZE = 45;
    private static final int[] BOARD_SLOTS = { 12, 13, 14, 21, 22, 23, 30, 31, 32 };
    private static final int RED_INDICATOR_SLOT = 10;
    private static final int BLUE_INDICATOR_SLOT = 16;
    private static final long CLOSE_DELAY_TICKS = 20L;

    private static final Material[] RAINBOW_PANES = {
            Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE
    };

    private final Map<UUID, Game> gamesByPlayer = new HashMap<>();
    private final Map<UUID, Game> gamesById = new HashMap<>();

    private TicTacToeService() {
    }

    public static TicTacToeService getInstance() {
        return INSTANCE;
    }

    public boolean start(Player initiator, Player target) {
        if (initiator == null || target == null || !initiator.isOnline() || !target.isOnline()) {
            return false;
        }
        if (initiator.getUniqueId().equals(target.getUniqueId())) {
            return false;
        }
        if (gamesByPlayer.containsKey(initiator.getUniqueId())) {
            initiator.sendMessage("§eYou are already playing Tic Tac Toe.");
            return false;
        }
        if (gamesByPlayer.containsKey(target.getUniqueId())) {
            initiator.sendMessage("§e" + target.getName() + " is already playing Tic Tac Toe.");
            return false;
        }
        DMain plugin = DMain.getInstance();
        if (plugin == null) {
            return false;
        }

        UUID gameId = UUID.randomUUID();
        TicTacToeInventoryHolder holder = new TicTacToeInventoryHolder(gameId);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                AdventureText.legacy("Tic Tac Toe"));
        holder.setInventory(inventory);

        Game game = new Game(gameId, initiator.getUniqueId(), target.getUniqueId(), inventory);
        buildMenu(game);
        gamesByPlayer.put(initiator.getUniqueId(), game);
        gamesByPlayer.put(target.getUniqueId(), game);
        gamesById.put(gameId, game);

        initiator.openInventory(inventory);
        target.openInventory(inventory);
        initiator.sendMessage("§dTic Tac Toe: §cYou are red§d and move first against §9" + target.getName() + "§d.");
        target.sendMessage("§dTic Tac Toe: §9You are blue§d and play against §c" + initiator.getName() + "§d.");
        initiator.sendMessage("§7The game ends only on a win or a draw.");
        target.sendMessage("§7The game ends only on a win or a draw.");
        initiator.playSound(initiator.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.3f);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.1f);

        game.borderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickBorder(game), 1L, 2L);
        return true;
    }

    public boolean isGameInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof TicTacToeInventoryHolder;
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TicTacToeInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);

        Game game = gamesById.get(holder.getGameId());
        if (game == null || !game.active || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }
        if (event.getClickedInventory() != game.inventory) {
            return;
        }
        int boardIndex = boardIndex(event.getSlot());
        if (boardIndex < 0) {
            return;
        }

        Mark mark = game.markFor(player.getUniqueId());
        if (mark != game.turn) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
            player.sendActionBar(AdventureText.legacy("§eWait for your turn."));
            return;
        }
        if (!game.board.place(boardIndex, mark)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.2f);
            return;
        }

        game.inventory.setItem(event.getSlot(), wool(mark));
        playMoveSound(game, mark);

        Mark winner = game.board.winner();
        if (winner != null) {
            finishWin(game, winner);
            return;
        }
        if (game.board.isFull()) {
            finishDraw(game, "§eTic Tac Toe: the board is full. It's a draw.");
            return;
        }

        game.turn = mark == Mark.RED ? Mark.BLUE : Mark.RED;
        game.updateIndicators();
        game.sendTurnHint();
    }

    public void handleDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TicTacToeInventoryHolder) {
            event.setCancelled(true);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TicTacToeInventoryHolder holder)) {
            return;
        }
        Game game = gamesById.get(holder.getGameId());
        if (game == null || !game.active || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }
        DMain plugin = DMain.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (game.active && player.isOnline()) {
                player.openInventory(game.inventory);
            }
        });
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        Game game = gamesByPlayer.get(player.getUniqueId());
        if (game == null || !game.active) {
            return;
        }
        finishDraw(game, "§eTic Tac Toe: " + player.getName() + " left the server. The game is a draw.");
    }

    public void shutdown() {
        for (Game game : new HashMap<>(gamesById).values()) {
            game.active = false;
            game.cancelTask();
            closeFor(game, game.redId);
            closeFor(game, game.blueId);
        }
        gamesByPlayer.clear();
        gamesById.clear();
    }

    private void finishWin(Game game, Mark winnerMark) {
        if (!game.active) {
            return;
        }
        UUID winnerId = winnerMark == Mark.RED ? game.redId : game.blueId;
        UUID loserId = winnerMark == Mark.RED ? game.blueId : game.redId;
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        String winnerName = winner == null ? "The winner" : winner.getName();
        String loserName = loser == null ? "the opponent" : loser.getName();

        game.end();

        SpecialItemBuff buff = winner == null ? null
                : SpecialItemBuffService.getInstance().applyRandom(winner).orElse(null);
        String buffName = buff == null ? "a reward" : buff.displayName();
        String message = "§aTic Tac Toe: §f" + winnerName + "§a wins against §f" + loserName
                + "§a and receives §d" + buffName + "§a!";
        if (winner != null) {
            winner.sendMessage(message);
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        }
        if (loser != null) {
            loser.sendMessage(message);
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.8f);
        }
        scheduleClose(game);
    }

    private void finishDraw(Game game, String message) {
        if (!game.active) {
            return;
        }
        game.end();
        for (UUID id : new UUID[] { game.redId, game.blueId }) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            }
        }
        scheduleClose(game);
    }

    private void scheduleClose(Game game) {
        DMain plugin = DMain.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeFor(game, game.redId);
            closeFor(game, game.blueId);
        }, CLOSE_DELAY_TICKS);
    }

    private void closeFor(Game game, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof TicTacToeInventoryHolder holder
                && holder.getGameId().equals(game.id)) {
            player.closeInventory();
        }
    }

    private void buildMenu(Game game) {
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            game.inventory.setItem(slot, filler.clone());
        }
        for (int slot : BOARD_SLOTS) {
            game.inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE));
        }
        renderBorder(game.inventory, 0);
        game.updateIndicators();
    }

    private void tickBorder(Game game) {
        if (!game.active) {
            game.cancelTask();
            return;
        }
        game.borderPhase++;
        renderBorder(game.inventory, game.borderPhase);
    }

    private void renderBorder(Inventory inventory, int phase) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (!isBorderSlot(slot)) {
                continue;
            }
            Material material = RAINBOW_PANES[Math.floorMod(phase + slot, RAINBOW_PANES.length)];
            inventory.setItem(slot, pane(material));
        }
    }

    private static boolean isBorderSlot(int slot) {
        int row = slot / 9;
        int column = slot % 9;
        return row == 0 || row == 4 || column == 0 || column == 8;
    }

    private static int boardIndex(int slot) {
        for (int i = 0; i < BOARD_SLOTS.length; i++) {
            if (BOARD_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void playMoveSound(Game game, Mark mark) {
        Sound sound = mark == Mark.RED ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.BLOCK_NOTE_BLOCK_PLING;
        float pitch = mark == Mark.RED ? 0.8f : 1.5f;
        for (UUID id : new UUID[] { game.redId, game.blueId }) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), sound, 0.7f, pitch);
            }
        }
    }

    private static ItemStack wool(Mark mark) {
        Material material = mark == Mark.RED ? Material.RED_WOOL : Material.BLUE_WOOL;
        String name = mark == Mark.RED ? "§cRed" : "§9Blue";
        return named(material, name);
    }

    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(AdventureText.withoutItalic(AdventureText.legacy(name)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack pane(Material material) {
        return named(material, " ");
    }

    private final class Game {
        private final UUID id;
        private final UUID redId;
        private final UUID blueId;
        private final Inventory inventory;
        private final TicTacToeBoard board = new TicTacToeBoard();
        private Mark turn = Mark.RED;
        private boolean active = true;
        private int borderPhase;
        private BukkitTask borderTask;

        private Game(UUID id, UUID redId, UUID blueId, Inventory inventory) {
            this.id = id;
            this.redId = redId;
            this.blueId = blueId;
            this.inventory = inventory;
        }

        private boolean isParticipant(UUID playerId) {
            return redId.equals(playerId) || blueId.equals(playerId);
        }

        private Mark markFor(UUID playerId) {
            return redId.equals(playerId) ? Mark.RED : Mark.BLUE;
        }

        private void updateIndicators() {
            String redName = playerName(redId);
            String blueName = playerName(blueId);
            inventory.setItem(RED_INDICATOR_SLOT,
                    named(Material.RED_WOOL, "§c" + redName + " §7— red" + turnSuffix(Mark.RED)));
            inventory.setItem(BLUE_INDICATOR_SLOT,
                    named(Material.BLUE_WOOL, "§9" + blueName + " §7— blue" + turnSuffix(Mark.BLUE)));
        }

        private String turnSuffix(Mark mark) {
            return mark == turn ? " §e(turn)" : "";
        }

        private void sendTurnHint() {
            Player player = Bukkit.getPlayer(turn == Mark.RED ? redId : blueId);
            if (player != null && player.isOnline()) {
                player.sendActionBar(AdventureText.legacy("§eYour turn."));
            }
        }

        private void end() {
            if (!active) {
                return;
            }
            active = false;
            cancelTask();
            gamesByPlayer.remove(redId, this);
            gamesByPlayer.remove(blueId, this);
            gamesById.remove(id, this);
        }

        private void cancelTask() {
            if (borderTask != null && !borderTask.isCancelled()) {
                borderTask.cancel();
            }
        }

        private String playerName(UUID playerId) {
            Player player = Bukkit.getPlayer(playerId);
            return player == null ? "Player" : player.getName();
        }
    }
}
