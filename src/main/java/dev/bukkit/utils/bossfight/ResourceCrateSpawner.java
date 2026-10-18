package dev.bukkit.utils.bossfight;

import dev.bukkit.event.BukkitEventBus;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.Random;

public class ResourceCrateSpawner {

    private final Material resourceMaterial;
    private final int amount;
    private final float dropCooldownInSec;

    private TextDisplay cooldownDisplay;
    private SpinningBlockDisplay showcaseDisplay;
    private ResourceCrateDisplay crateDisplay;

    private final Vector3Int pos;

    private World world;

    private float ticksSinceLastDrop;

    public ResourceCrateSpawner(Material resourceMaterial, int amount, float dropCooldownInSec, Vector3Int pos) {
        this.resourceMaterial = resourceMaterial;
        this.amount = amount;
        this.dropCooldownInSec = dropCooldownInSec;
        this.pos = pos;
        BukkitEventBus.getInstance().subscribe(new EventAction<>(event -> {
            if (crateDisplay != null) {
                if (!crateDisplay.isSpawned()) {
                    ticksSinceLastDrop += event.getTickDelta();
                    updateCooldownDisplay();
                    if (ticksSinceLastDrop / 20 >= dropCooldownInSec) {
                        ticksSinceLastDrop = 0;
                        spawnPackage();
                    }
                }
            }
        }, TickEvent.class));
    }

    public void spawnDisplay(World world) {
        this.world = world;
        cooldownDisplay = spawnCooldownDisplay(world, new Vector3f(pos.toVector3f()).add(0.5f, 4, 0.5f), "Crate in: " + dropCooldownInSec + " secs", 2f);
        crateDisplay = new ResourceCrateDisplay(Material.BARREL, resourceMaterial, amount,
                new Vector3f(pos.toVector3f()).add(-0.25f, 1.25f, -0.25f), 1.5f, 2, world);
    }

    private TextDisplay spawnCooldownDisplay(World world, Vector3f pos, String text, float scale) {
        Vector3f spawnPoint = new Vector3f(pos);
        Location loc = new Location(world, spawnPoint.x, spawnPoint.y , spawnPoint.z);
        TextDisplay textDisplay = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        textDisplay.setBrightness(new Display.Brightness(15,15));
        textDisplay.text(Component.text(text));
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setBackgroundColor(Color.WHITE.setAlpha(0));
        Transformation transformation = textDisplay.getTransformation();
        transformation.getScale().set(new Vector3f(scale));
        textDisplay.setTransformation(transformation);
        return textDisplay;
    }

    private void updateCooldownDisplay() {
        if (cooldownDisplay != null) {
            float cooldown = dropCooldownInSec - (ticksSinceLastDrop / 20);
            cooldown = ((int) (cooldown * 10)) / 10f;
            if (cooldown > 0) {
                cooldownDisplay.text(Component.text("Crate in: " + cooldown + " secs"));
            } else {
                cooldownDisplay.text(Component.text("Crate spawned"));
            }
        }
    }

    private void spawnPackage() {
        crateDisplay.spawnDisplay(world);
    }

    public void clearDisplay() {
        if (showcaseDisplay != null) {
            showcaseDisplay.clearDisplay();
            showcaseDisplay = null;
        }
        if (cooldownDisplay != null) {
            cooldownDisplay.remove();
            cooldownDisplay = null;
        }
        if (crateDisplay != null) {
            crateDisplay.clearDisplay();
            crateDisplay = null;
        }
    }

    private class SpinningBlockDisplay {
        private final Material material;
        protected final float scale;
        private final float rotationSpeed;

        protected Vector3f pos;
        protected BlockDisplay display;
        private float currentYaw;

        public SpinningBlockDisplay(Material material, Vector3f pos, float scale, float rotationSpeed) {
            this.material = material;
            this.pos = pos;
            this.scale = scale;
            this.rotationSpeed = rotationSpeed;
            BukkitEventBus.getInstance().subscribe(new EventAction<>(event -> {
                onTick(event.getTickDelta());
            }, TickEvent.class));
        }

        protected void onTick(float tickDelta) {
            if (isSpawned()) {
                //TODO maybe also add small hovering effect
                currentYaw += tickDelta * rotationSpeed;
                display.setRotation(currentYaw, 0);
            }
        }

        public boolean isSpawned() {
            return display != null;
        }

        public void setPos(World world, Vector3f pos) {
            this.pos = pos;
            spawnDisplay(world);
        }

        public void spawnDisplay(World world) {
            clearDisplay();
            display = spawnBlockDisplay(world);
        }

        public void clearDisplay() {
            if (isSpawned()) {
                display.remove();
                display = null;
            }
        }

        protected void updateScale(float scale) {
            Vector3f offset = new Vector3f(scale).mul(0.5f);
            Transformation transformation = display.getTransformation();
            transformation.getScale().set(new Vector3f(scale));
            transformation.getTranslation().set(new Vector3f(offset).mul(-1));
            display.setTransformation(transformation);
        }

        private BlockDisplay spawnBlockDisplay(World world) {
            Vector3f offset = new Vector3f(scale).mul(0.5f);
            Vector3f spawnPoint = new Vector3f(pos).add(offset);
            Location loc = new Location(world, spawnPoint.x, spawnPoint.y , spawnPoint.z);
            BlockDisplay blockDisplay = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
            blockDisplay.setBlock(Bukkit.createBlockData(material));
            blockDisplay.setBrightness(new Display.Brightness(15,15));

            Transformation transformation = blockDisplay.getTransformation();
            transformation.getScale().set(new Vector3f(scale));
            transformation.getTranslation().set(new Vector3f(offset).mul(-1));
            blockDisplay.setTransformation(transformation);

            return blockDisplay;
        }
    }

    private class ResourceCrateDisplay extends SpinningBlockDisplay {
        private final Material resourceMaterial;
        private final int resourceAmount;
        private Interaction interactionEntity;
        private TextDisplay textDisplay;

        private int openingStage = 0;
        private int attackAnimTicks = 0;

        public ResourceCrateDisplay(Material material, Material resourceMaterial, int resourceAmount, Vector3f pos, float scale, float rotationSpeed, World world) {
            super(material, pos, scale, rotationSpeed);
            this.resourceMaterial = resourceMaterial;
            this.resourceAmount = resourceAmount;
            BukkitEventBus.getInstance().subscribe(new EventAction<>(event -> {
                //TODO fix bug where first hit doesnt register correctly / doesnt trigger anim
                Entity entity = event.getEntity();
                if (entity.getType() == EntityType.INTERACTION && event.getDamager().getType() == EntityType.PLAYER) {
                    if (attackAnimTicks > 0) {
                        return;
                    }
                    world.playSound(display, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1, 1);
                    openingStage++;
                    attackAnimTicks = 10;
                    event.setCancelled(true);
                }
            }, EntityDamageByEntityEvent.class));
        }

        @Override
        protected void onTick(float tickDelta) {
            super.onTick(tickDelta);
            if (isSpawned()) {
                if (attackAnimTicks > 0) {
                    attackAnimTicks--;
                    float p = attackAnimTicks;
                    if (attackAnimTicks == 9) {
                        p = -1;
                    }
                    if (attackAnimTicks == 8) {
                        p = 0;
                    }
                    if (attackAnimTicks == 7) {
                        p = 1;
                    }
                    if (attackAnimTicks < 7) {
                        p -= 4;
                        p /= 3;
                        p = Math.max(p, 0);
                    }
                    int pitch = new Random().nextInt(20, 25);
                    display.setRotation(display.getYaw(), Math.min(pitch * p, 10));
                    p = attackAnimTicks;
                    if (attackAnimTicks >= 5) {
                        p -= 5;
                        p *= -1;
                    }
                    p /= 5;
                    p /= 10;
                    if (p < 0) {
                        p *= 2;
                    } else {
                        p /= 2;
                    }
                    updateScale(scale + p);

                    TextColor red = TextColor.color(1f, 0, 0);
                    TextColor white = TextColor.color(1f, 1f, 1f);
                    p = attackAnimTicks;
                    if (attackAnimTicks > 4) {
                        p -= 5;
                    }
                    TextColor textColor;
                    if (attackAnimTicks > 2) {
                        p -= 2;
                        p /= 2;
                        textColor = TextColor.lerp(p, red, white);
                    } else {
                        p /= 3;
                        textColor = TextColor.lerp(p, white, red);
                    }
                    textDisplay.text(textDisplay.text().color(textColor));

                    if (attackAnimTicks == 0) {
                        if (openingStage == 3) {
                            openingStage = 0;
                            Location loc = new Location(world, pos.x + 0.5, pos.y + 0.5 , pos.z + 0.5);
                            ItemStack item = new ItemStack(resourceMaterial, resourceAmount);
                            world.dropItem(loc, item);
                            world.playSound(loc, Sound.BLOCK_BARREL_OPEN, 2, 2);
                            clearDisplay();
                        }
                    }
                }
            }
        }

        @Override
        public void spawnDisplay(World world) {
            super.spawnDisplay(world);
            interactionEntity = spawnInteractionEntity(world);
            float offset = (scale / 2);
            textDisplay = spawnCooldownDisplay(world, new Vector3f(pos).add(offset,2,offset), "Attack to open", 1);
        }

        @Override
        public void clearDisplay() {
            super.clearDisplay();
            if (interactionEntity != null) {
                interactionEntity.remove();
                interactionEntity = null;
            }
            if (textDisplay != null) {
                textDisplay.remove();
                textDisplay = null;
            }
        }

        private Interaction spawnInteractionEntity(World world) {
            Vector3f offset = new Vector3f(scale, 0, scale).mul(0.5f);
            Vector3f spawnPoint = new Vector3f(pos).add(offset);
            Location loc = new Location(world, spawnPoint.x, spawnPoint.y , spawnPoint.z);
            Interaction interaction = (Interaction) world.spawnEntity(loc, EntityType.INTERACTION);
            interaction.setInteractionWidth(scale * 1.4f);
            interaction.setInteractionHeight(scale);
            return interaction;
        }
    }
}
