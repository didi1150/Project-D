package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

public class BukkitSwingBoneEffect extends Effect {

	private boolean hitOutward = false;
	private boolean hitReturn = false;

	private final List<UUID> forwardHits = new ArrayList<>();
	private final List<UUID> backwardHits = new ArrayList<>();

	private boolean inAnimation;
	private int ticks = 0;
	private float speed;
	private ArmorStand armorStand;
	private Runnable startCooldown;
	private Vector directionOutward;
	private Runnable resetCooldown;
	private String uuid;
	private ItemStack newItemStack;

	private static final int BONE_DAMAGE = 0;
	private static final double RETURN_DISTANCE_THRESHOLD = 0.5; // Distance to player to auto-complete return

	public BukkitSwingBoneEffect(String cooldownKey) {
		super(null, 1850, true, cooldownKey);
	}

	public String getUuid() {
		return uuid;
	}

	private void startBoneProjectile(Player player) {
		Location spawnLoc = player.getEyeLocation().subtract(0, 1.5, 0);
		armorStand = player.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
			stand.setInvisible(true);
			stand.setInvulnerable(true);
			stand.setGravity(false);
			stand.setSmall(false);
			stand.setBasePlate(false);
			stand.setMarker(true);
			stand.getEquipment().setHelmet(new ItemStack(Material.BONE));
		});

		speed = 0.6f;

		directionOutward = player.getEyeLocation().getDirection().normalize();

		inAnimation = true;
		player.playSound(spawnLoc, Sound.ENTITY_SKELETON_HURT, new Random().nextFloat(0.3f, 0.6f),
				new Random().nextFloat(1.75f, 2.0f));
	}

	private ItemStack copyItemStackWithMaterial(ItemStack original, Material newMaterial) {
		if (original == null || newMaterial == null)
			return null;

		ItemStack copy = original.clone();

		ItemStack newStack = new ItemStack(newMaterial, copy.getAmount());

		ItemMeta meta = copy.getItemMeta();
		if (meta != null) {
			newStack.setItemMeta(meta);
		}

		return newStack;
	}

	@Override
	public void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown) {
		this.resetCooldown = resetCooldown;
		if (inAnimation) {
			return;
		}
		if (caster instanceof BukkitPlayerEntity playerEntity) {
			Player player = playerEntity.getPlayer();
			uuid = player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer()
					.get(BukkitItemStackAdapter.UUID_ID_KEY, PersistentDataType.STRING);
			newItemStack = copyItemStackWithMaterial(player.getInventory().getItemInMainHand(), Material.GHAST_TEAR);
			player.getInventory().setItemInMainHand(newItemStack);
			this.startCooldown = startCooldown;
			startBoneProjectile(player);
		}
	}

	@Override
	public void cancel() {
		ticks = 0;
		if (armorStand != null && armorStand.isValid()) {
			armorStand.remove();
		}
		inAnimation = false;
	}

	@Override
	public void tick(RPGEntity caster, long now) {
		if (!(caster instanceof BukkitPlayerEntity playerEntity)) {
			return;
		}
		Player player = playerEntity.getPlayer();

		if (!inAnimation || armorStand == null || !armorStand.isValid()) {
			return;
		}

//		float backYaw = (float) Math.toRadians(player.getLocation().getYaw());
//		float backPitch = (float) Math.toRadians(player.getLocation().getPitch());

		Location teleportLoc = armorStand.getLocation();

		// Collision with solid block ends immediately
		if (teleportLoc.clone().add(0, 1.485, 0).getBlock().getType().isSolid()) {
			cleanup(player, teleportLoc, true);
			return;
		}

		if (ticks < 18) { // Outward phase
//			teleportLoc.add(-Math.sin(toYaw) * speed, -Math.sin(toPitch) * speed, Math.cos(toYaw) * speed);
			teleportLoc.add(directionOutward.clone().multiply(speed));
			dealDamage(playerEntity, now, forwardHits);

			if (!hitOutward) {
				hitOutward = true;
			}
			dealDamage(playerEntity, now, forwardHits);

		} else if (ticks < 36) { // Return phase
//			teleportLoc.add(Math.sin(backYaw) * speed, Math.sin(backPitch) * speed, -Math.cos(backYaw) * speed);

			Vector directionToPlayer = player.getLocation().toVector().subtract(teleportLoc.toVector()).normalize();
			teleportLoc.add(directionToPlayer.multiply(speed));
			player.getWorld().spawnParticle(Particle.ENCHANT, teleportLoc, 1);

			if (!hitReturn) {
				hitReturn = true;
			}
			dealDamage(playerEntity, now, backwardHits);

			if (teleportLoc.distanceSquared(player.getLocation()) <= RETURN_DISTANCE_THRESHOLD) {
				cleanup(player, teleportLoc, false);
				resetCooldown.run();
				return;
			}
		} else { // End of animation
			cleanup(player, teleportLoc, false);
			return;
		}

		teleportLoc.setYaw((teleportLoc.getYaw() + 24.0f) % 360f);
		armorStand.teleport(teleportLoc);

		ticks++;
	}

	private void cleanup(Player player, Location loc, boolean cooldown) {
		cancel();

		if (startCooldown != null && cooldown) {
			player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 50);
			startCooldown.run();
			long baseCooldownTime = 3000;
			EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresentOrElse(entity -> {
				long reducedCooldownTime = (long) (baseCooldownTime * 100 / (100
						+ entity.getStatManager().getCurrentValue(StatType.ABILITY_HASTE, System.currentTimeMillis())));
				Bukkit.getScheduler().runTaskLater(DMain.getInstance(), () -> {
					resetItem(player);

					player.playSound(loc, Sound.BLOCK_WOOD_PLACE, new Random().nextFloat(0.3f, 0.6f),
							new Random().nextFloat(1.25f, 1.5f));
				}, reducedCooldownTime / 1000 * 20);
			}, () -> {
				Bukkit.getScheduler().runTaskLater(DMain.getInstance(), () -> {
					resetItem(player);

					player.playSound(loc, Sound.BLOCK_WOOD_PLACE, new Random().nextFloat(0.3f, 0.6f),
							new Random().nextFloat(1.25f, 1.5f));
				}, baseCooldownTime / 1000 * 20);
			});
			player.playSound(loc, Sound.ENTITY_ITEM_BREAK, new Random().nextFloat(0.3f, 0.6f),
					new Random().nextFloat(0.8f, 1.2f));
		} else {
			resetItem(player);
			resetCooldown.run();
			player.playSound(loc, Sound.BLOCK_WOOD_PLACE, new Random().nextFloat(0.3f, 0.6f),
					new Random().nextFloat(1.25f, 1.5f));
		}
	}

	private void resetItem(Player player) {
		PlayerInventory inv = player.getInventory();
		for (int i = 0; i < inv.getSize(); i++) {
			ItemStack item = inv.getItem(i);
			if (item != null && uuid.equals(item.getItemMeta().getPersistentDataContainer()
					.get(BukkitItemStackAdapter.UUID_ID_KEY, PersistentDataType.STRING))) {
				newItemStack = copyItemStackWithMaterial(item, Material.BONE);
				inv.setItem(i, newItemStack);
				break;
			}
		}
	}

	private void dealDamage(BukkitPlayerEntity playerEntity, long now, List<UUID> hitList) {
		Player player = playerEntity.getPlayer();
		List<Entity> nearbyEntities = (List<Entity>) player.getWorld()
				.getNearbyEntities(armorStand.getLocation().clone().add(0, 1.575, 0), 0.4, 0.4, 0.4);

		for (Entity entity : nearbyEntities) {
			if (entity instanceof LivingEntity le && entity != player && entity.getType() != EntityType.PLAYER) {
				if (!hitList.contains(le.getUniqueId())) {
					hitList.add(le.getUniqueId());

					double multiplier = hitReturn ? 2 : 1;
					double attackDamage = (playerEntity.getStatManager().getCurrentValue(StatType.ATTACK_DAMAGE, now)
							+ BONE_DAMAGE) * multiplier;
					EntityManager.getInstance().getEntity(entity.getUniqueId()).ifPresentOrElse(target -> {
						target.dealRPGDamage(playerEntity, target, attackDamage, DamageType.PHYSICAL);
					}, () -> {
						// Visuals
						DamageUtils.damageMob(le, attackDamage, player);
					});
					Vector knockbackDirection = le.getLocation().toVector()
							.subtract(armorStand.getLocation().toVector()).normalize();
					le.setVelocity(knockbackDirection.multiply(0.1));
				}
			}
		}
	}

}
