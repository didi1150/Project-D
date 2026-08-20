package dev.bukkit.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.status.ActiveStatusEffect;
import dev.core.status.StatusEffectManager;

/**
 * Bukkit status effect manager: extends the pure-Java core manager with the
 * vanilla entity manipulation (per-type {@link StatusEffectBehavior}s) and the
 * stacked {@link TextDisplay}s hovering above the entity's name.
 *
 * <p>
 * Each active effect gets its own text display; displays stack vertically
 * above the name (application order, newest last). Displays follow the entity
 * and update their remaining-time text every tick. On expiry an effect either
 * fades out (scale-down animation) or disappears abruptly, per the effect's
 * fade flag. Displays are cleaned up when the effect ends, the entity dies or
 * vanishes, or the plugin disables.
 */
public class BukkitStatusEffectManager extends StatusEffectManager {

	public static final double DISPLAY_SPACING = 0.35;
	public static final int FADE_TICKS = 8;
	private static final float DISPLAY_SCALE = 0.8f;

	private static BukkitStatusEffectManager instance;

	private final Map<RPGEntity, Map<ActiveStatusEffect, TextDisplay>> displayByEffect = new HashMap<>();
	private final Map<ActiveStatusEffect, Long> lastShownSeconds = new HashMap<>();

	/**
	 * Protected so tests can subclass with a mocked vanilla entity; production
	 * code uses the {@link #getInstance()} singleton.
	 */
	protected BukkitStatusEffectManager() {
	}

	public static BukkitStatusEffectManager getInstance() {
		if (instance == null) {
			instance = new BukkitStatusEffectManager();
		}
		return instance;
	}

	@Override
	protected void onEffectApplied(RPGEntity entity, ActiveStatusEffect effect) {
		LivingEntity living = resolveLiving(entity);
		if (living == null) {
			return; // no vanilla entity backing this RPG entity (yet)
		}
		StatusEffectBehavior behavior = StatusEffectBehaviorRegistry.get(effect.getType());
		if (behavior != null) {
			behavior.onApply(new StatusEffectContext(entity, living, effect));
		}
		TextDisplay display = spawnDisplay(living, entity, effect);
		displayByEffect.computeIfAbsent(entity, k -> new HashMap<>()).put(effect, display);
	}

	@Override
	protected void onEffectRemoved(RPGEntity entity, ActiveStatusEffect effect) {
		LivingEntity living = resolveLiving(entity);
		StatusEffectBehavior behavior = StatusEffectBehaviorRegistry.get(effect.getType());
		if (behavior != null && living != null) {
			behavior.onEnd(new StatusEffectContext(entity, living, effect));
		}
		Map<ActiveStatusEffect, TextDisplay> displays = displayByEffect.get(entity);
		if (displays != null) {
			TextDisplay display = displays.remove(effect);
			lastShownSeconds.remove(effect);
			if (display != null && display.isValid()) {
				if (effect.isFadeOut()) {
					fadeOutDisplay(display);
				} else {
					display.remove();
				}
			}
			if (displays.isEmpty()) {
				displayByEffect.remove(entity);
			}
		}
	}

	@Override
	public void tick(long now) {
		super.tick(now); // expire effects; fires onEffectRemoved for each

		for (RPGEntity entity : new HashMap<>(displayByEffect).keySet()) {
			LivingEntity living = resolveLiving(entity);
			if (living == null || !living.isValid() || living.isDead()
					|| EntityManager.getInstance().getEntity(entity.getUuid()).isEmpty()) {
				removeAll(entity); // entity gone: clean displays + behaviors
				continue;
			}

			List<ActiveStatusEffect> effects = getActive(entity);
			Map<ActiveStatusEffect, TextDisplay> displays = displayByEffect.get(entity);
			if (displays == null || displays.isEmpty()) {
				continue;
			}
			for (ActiveStatusEffect effect : effects) {
				StatusEffectBehavior behavior = StatusEffectBehaviorRegistry.get(effect.getType());
				if (behavior != null) {
					behavior.onTick(new StatusEffectContext(entity, living, effect), now);
				}
			}
			repositionDisplays(living, effects, displays, now);
		}
	}

	@Override
	public void cancelAll() {
		super.cancelAll();
		displayByEffect.clear();
		lastShownSeconds.clear();
	}

	/**
	 * Resolves the vanilla {@link LivingEntity} backing an RPG entity: players
	 * via their Player, everything else through the entity registry (mobs,
	 * bosses). Protected so tests can substitute a mock.
	 */
	protected LivingEntity resolveLiving(RPGEntity entity) {
		if (entity instanceof BukkitPlayerEntity playerEntity) {
			return playerEntity.getPlayer().orElse(null);
		}
		if (Bukkit.getServer() == null) {
			return null;
		}
		Entity vanilla = Bukkit.getEntity(entity.getUuid());
		return vanilla instanceof LivingEntity living ? living : null;
	}

	private TextDisplay spawnDisplay(LivingEntity living, RPGEntity entity, ActiveStatusEffect effect) {
		World world = living.getWorld();
		int index = Math.max(0, getActive(entity).indexOf(effect));
		Location spawnLoc = living.getLocation().add(0, living.getHeight() + 0.55 + index * DISPLAY_SPACING, 0);

		return world.spawn(spawnLoc, TextDisplay.class, d -> {
			d.setText(formatText(effect, effect.remaining(System.currentTimeMillis())));
			d.setBillboard(Display.Billboard.CENTER);
			d.setSeeThrough(false);
			d.setGravity(false);
			d.setInvulnerable(true);
			d.setShadowed(false);
			Transformation transformation = d.getTransformation();
			transformation.getScale().set(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE);
			d.setTransformation(transformation);
		});
	}

	private void repositionDisplays(LivingEntity living, List<ActiveStatusEffect> effects,
			Map<ActiveStatusEffect, TextDisplay> displays, long now) {
		double baseY = living.getHeight() + 0.55;
		int index = 0;
		for (ActiveStatusEffect effect : effects) {
			TextDisplay display = displays.get(effect);
			if (display != null && display.isValid()) {
				display.teleport(living.getLocation().add(0, baseY + index * DISPLAY_SPACING, 0));
				updateText(display, effect, now);
			}
			index++;
		}
	}

	private void updateText(TextDisplay display, ActiveStatusEffect effect, long now) {
		long remaining = effect.remaining(now);
		long shown = remaining < 0 ? -1 : remaining / 1000;
		Long lastShown = lastShownSeconds.get(effect);
		if (lastShown != null && lastShown == shown && display.getText() != null) {
			return; // same whole-second bucket: no text churn
		}
		lastShownSeconds.put(effect, shown);
		display.setText(formatText(effect, remaining));
	}

	private String formatText(ActiveStatusEffect effect, long remainingMillis) {
		StringBuilder sb = new StringBuilder();
		sb.append(effect.getType().getColor()).append(effect.getType().getIcon()).append(' ')
				.append(effect.getType().getDisplayName());
		if (remainingMillis >= 0) {
			sb.append(" §f").append(String.format("%.1f", remainingMillis / 1000.0)).append('s');
		}
		return sb.toString();
	}

	/** Scale the display down over a few ticks, then remove it. */
	private void fadeOutDisplay(TextDisplay display) {
		DMain plugin = DMain.getInstance();
		if (plugin == null) {
			display.remove();
			return;
		}
		new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (!display.isValid() || ticks >= FADE_TICKS) {
					display.remove();
					cancel();
					return;
				}
				float progress = (ticks + 1f) / FADE_TICKS;
				float scale = DISPLAY_SCALE * (1.0f - progress * 0.9f);
				Transformation transformation = display.getTransformation();
				transformation.getScale().set(scale, scale, scale);
				display.setTransformation(transformation);
				ticks++;
			}
		}.runTaskTimer(plugin, 1L, 1L);
	}
}