package dev.bukkit.ability;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.summon.SoulFragment;
import dev.bukkit.summon.SoulTome;
import dev.bukkit.summon.SummonedEntityFactory;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.CostMode;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.stat.StatType;

/**
 * Soul Summon — instant effect: pops every soul held in the caster's Soul Tome
 * and summons each one as a player-owned ally near the caster. There is no
 * active-summon limit — every soul in the tome comes out, and previously
 * summoned mobs stay where they are. Souls that are too powerful for the
 * Support level, or that could not be spawned, stay on the tome.
 *
 * <p>
 * The ability's mana cost is deducted by the effect manager BEFORE this
 * effect runs; when not a single soul was summoned (empty tome, no tome held,
 * every spawn failed) the mana is refunded and no cooldown starts, so a failed
 * attempt costs nothing.
 */
public class BukkitSoulSummonEffect extends Effect {

    public BukkitSoulSummonEffect(String cooldownKey) {
        // Instant: the summon happens inside cast(); the effect expires after
        // the first tick.
        super(null, 1, true, cooldownKey);
    }

    private static Player resolvePlayer(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().orElse(null);
        }
        return null;
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        Player player = resolvePlayer(caster);
        if (player == null) {
            return;
        }
        ItemStack tome = SoulTome.findTome(player);
        if (tome == null) {
            refundMana(caster);
            player.sendMessage(ChatColor.GRAY + "You need a Soul Tome in your inventory to summon souls.");
            return;
        }

        int level = DMain.getInstance().getProgressionService()
                .getProgression(player.getUniqueId(), RPGClassType.SUPPORT).getLevel();

        // Pop souls one at a time (LIFO) so the attempt is atomic per soul:
        // anything that fails (tier gate, spawn failure) is put straight back.
        int summoned = 0;
        SoulFragment fragment;
        while ((fragment = SoulTome.popSoul(tome)) != null) {
            if (!dev.bukkit.summon.SummonStats.canCapture(level, fragment.tier())) {
                SoulTome.addSoul(tome, fragment, 99); // too powerful: keep it for later
                player.sendMessage(ChatColor.RED + "Your Support level is too low to summon the "
                        + fragment.mobType().name().toLowerCase().replace('_', ' ') + " soul; it stays on the tome.");
                continue;
            }
            if (SummonedEntityFactory.spawnSummon(player, fragment, level) == null) {
                SoulTome.addSoul(tome, fragment, 99);
                player.sendMessage(ChatColor.RED + "Could not summon the "
                        + fragment.mobType().name().toLowerCase().replace('_', ' ') + " soul here; it stays on the tome.");
                continue;
            }
            summoned++;
            player.sendMessage(ChatColor.GREEN + "Summoned your "
                    + fragment.mobType().name().toLowerCase().replace('_', ' ') + " ("
                    + (fragment.tier() == SpawnTier.BASIC ? "Basic" : fragment.tier().name().toLowerCase()) + ").");
        }

        if (summoned == 0) {
            refundMana(caster);
            player.sendMessage(ChatColor.GRAY + "Your Soul Tome holds no souls. Slay dungeon mobs to capture them.");
            return; // no mana, no cooldown on a failed attempt
        }

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "Summoned " + summoned + " soul" + (summoned == 1 ? "" : "s") + ".");
        cooldownSink.startCooldown();
    }

    /**
     * Returns the mana this cast was charged by the effect manager: the
     * attempt failed (nothing was summoned), so refund it. The resolved
     * (dynamic formula + discounted) price matches what
     * {@code BukkitEffectManager.cast} deducted. Costs are config-only
     * (abilities.yml {@code SOUL_SUMMON} entry); if the ability were missing
     * from the registry there is no price to restore.
     */
    private static void refundMana(RPGEntity caster) {
        double amount = AbilityRegistry.get("SOUL_SUMMON")
                .map(ability -> ability.getCost().getCosts().stream()
                        .filter(entry -> entry.mode() == CostMode.MANA)
                        .mapToDouble(entry -> entry.resolve(caster))
                        .sum())
                .orElse(0.0);
        double cost = ManaDiscountUtils.discountedCost(caster, CostMode.MANA.getResourceType(), amount);
        caster.getStatManager().modifyStat(StatType.MANA_RESOURCE, cost);
    }

    @Override
    public void cancel() {
    }
}