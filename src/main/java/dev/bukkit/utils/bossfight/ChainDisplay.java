package dev.bukkit.utils.bossfight;

import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ChainDisplay {
    private final Material material;
//    private final Color glowColor;
    private final List<ChainPartDisplay> parts = new ArrayList<>();;

    private Vector3Int firstPos;
    private Vector3Int secondPos;
    private float scale;
    private float sag;

    public ChainDisplay(Vector3Int firstPos, Vector3Int secondPos) {
        this(firstPos, secondPos, 1, 0);
    }

    public ChainDisplay(Vector3Int firstPos, Vector3Int secondPos, float scale, float sag) {
        this(Material.IRON_CHAIN);
        this.firstPos = firstPos;
        this.secondPos = secondPos;
        this.scale = scale;
        this.sag = sag;
    }

    public ChainDisplay() {
        this(Material.IRON_CHAIN);
    }

    public ChainDisplay(Material material) {
        this(material, 1, 0);
    }

    public ChainDisplay(Material material, float scale, float sag) {
        this.material = material;
        this.scale = scale;
        this.sag = sag;
    }

    public Vector3Int getFirstPos() {
        return firstPos;
    }

    public Vector3Int getSecondPos() {
        return secondPos;
    }

    public float getScale() {
        return scale;
    }

    public float getSag() {
        return sag;
    }

    public void setFirstPos(World world, Vector3Int firstPos) {
        this.firstPos = firstPos;
        updateDisplays(world);
    }

    public void setSecondPos(World world, Vector3Int secondPos) {
        this.secondPos = secondPos;
        updateDisplays(world);
    }

    public void setScale(World world, float scale) {
        this.scale = scale;
        updateDisplays(world);
    }

    public void setSag(World world, float sag) {
        this.sag = sag;
        updateDisplays(world);
    }

    public void updateDisplays(World world) {
        refreshChainParts(world);
    }

    public void spawnDisplay(World world) {
        if (firstPos == null || secondPos == null) {
            System.err.println("ChainDisplay: didn't set all the positions");
            return;
        }
        refreshChainParts(world);
    }

    private void refreshChainParts(World world) {
        if (firstPos == null || secondPos == null) {
            clearDisplays(false, null);
            return;
        }
        Vector3f start = firstPos.toVector3f();
        Vector3f end = secondPos.toVector3f();
        float ropeLength = start.distance(end) * (1 + sag);
        int segmentCount = (int) (ropeLength / scale) + (ropeLength % scale == 0 ? 0 : 1);
        Vector3f[] points = generateRope(start, end, ropeLength, segmentCount);
        if (parts.size() != segmentCount) {
            clearDisplays(true, null);
            for (int i = 0; i < points.length - 1; i++) {
                ChainPartDisplay part = new ChainPartDisplay(material);
                parts.add(part);
                part.updateDisplays(world, points[i], points[i + 1], scale);
            }
            return;
        }
        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).reposition(world, points[i], points[i + 1], scale);
        }
    }

    public static Vector3f[] generateRope(Vector3f start, Vector3f end, float ropeLength, int segmentCount) {
        float directDistance = start.distance(end);
        ropeLength = Math.max(ropeLength, directDistance);

        if (ropeLength <= directDistance + 0.0001f) {
            Vector3f[] points = new Vector3f[segmentCount + 1];
            for (int i = 0; i <= segmentCount; i++) {
                float t = (float) i / segmentCount;

                points[i] = new Vector3f(start).lerp(end, t);
            }
            return points;
        }

        Vector3f direction = new Vector3f(end).sub(start).normalize();
        Vector3f sagDirection = new Vector3f(0, -1, 0)
                .sub(new Vector3f(direction).mul(direction.dot(0, -1, 0)));

        if (sagDirection.lengthSquared() < 0.000001f) {
            sagDirection.set(1, 0, 0);
        } else {
            sagDirection.normalize();
        }

        float controlSag = findSag(start, end, sagDirection, ropeLength);
        Vector3f midpoint = new Vector3f(start).add(end).mul(0.5f);
        Vector3f p1 = new Vector3f(midpoint).fma(controlSag, sagDirection);

        Function<Float, Vector3f> getBezierPoint = t -> {
            float u = 1.0f - t;
            return new Vector3f(start).mul(u * u).fma(2.0f * u * t, p1).fma(t * t, end);
        };

        int sampleResolution = 200;
        float[] sampleTs = new float[sampleResolution + 1];
        float[] accumulatedLengths = new float[sampleResolution + 1];
        sampleTs[0] = 0.0f;
        accumulatedLengths[0] = 0.0f;

        Vector3f previousPoint = new Vector3f(start);

        for (int i = 1; i <= sampleResolution; i++) {
            float t = (float) i / sampleResolution;
            Vector3f currentPoint = getBezierPoint.apply(t);
            accumulatedLengths[i] = accumulatedLengths[i - 1] + previousPoint.distance(currentPoint);
            sampleTs[i] = t;
            previousPoint.set(currentPoint);
        }
        float totalLength = accumulatedLengths[sampleResolution];

        Vector3f[] points = new Vector3f[segmentCount + 1];
        points[0] = new Vector3f(start);
        points[segmentCount] = new Vector3f(end);

        float segmentLength = totalLength / segmentCount;

        for (int i = 1; i < segmentCount; i++) {
            float targetDistance = i * segmentLength;

            int low = 0;
            int high = sampleResolution;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (accumulatedLengths[mid] < targetDistance) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }

            int index = low;
            float lengthBefore = accumulatedLengths[index - 1];
            float lengthAfter = accumulatedLengths[index];
            float interpolation = (targetDistance - lengthBefore) / (lengthAfter - lengthBefore);
            float t = sampleTs[index - 1] + (sampleTs[index] - sampleTs[index - 1]) * interpolation;
            points[i] = getBezierPoint.apply(t);
        }

        return points;
    }


    private static float findSag(Vector3f p0, Vector3f p2, Vector3f sagDirection, float targetLength) {
        Vector3f midpoint = new Vector3f(p0).add(p2).mul(0.5f);

        Function<Float, Float> calculateLength = controlSag -> {
            Vector3f p1 = new Vector3f(midpoint).fma(controlSag, sagDirection);
            int samples = 100;
            float length = 0.0f;
            Vector3f previous = new Vector3f(p0);

            for (int i = 1; i <= samples; i++) {
                float t = (float) i / samples;
                float u = 1.0f - t;
                Vector3f current = new Vector3f(p0).mul(u * u).fma(2.0f * u * t, p1).fma(t * t, p2);
                length += previous.distance(current);
                previous.set(current);
            }

            return length;
        };

        float low = 0.0f;
        float high = p0.distance(p2);

        while (calculateLength.apply(high) < targetLength) {
            high *= 2.0f;
        }

        for (int i = 0; i < 20; i++) {
            float mid = (low + high) * 0.5f;
            float length = calculateLength.apply(mid);
            if (length < targetLength) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return (low + high) * 0.5f;
    }

    public void clearDisplays(boolean force, Player ignoredPlayer) {
        if (!parts.isEmpty()) {
//                if (!force) {
//                    if (isItemBeingUsedByOtherPlayers(ignoredPlayer, itemName)) return;
//                }
            for (ChainPartDisplay part : parts) {
                part.clearDisplays(false, ignoredPlayer);
            }
            parts.clear();
        }
    }
    private class ChainPartDisplay {
        private final Material material;
//        private final Color glowColor;

        private BlockDisplay display;

        public ChainPartDisplay(Material material) {
            this.material = material;
//            this.glowColor = glowColor;
        }

        public void updateDisplays(World world, Vector3f firstPos, Vector3f secondPos, float scale) {
            if (display == null || !display.isValid()) {
                display = spawnChainDisplay(world, firstPos, secondPos, scale);
            } else {
                applyTransform(display, world, firstPos, secondPos, scale);
            }
        }

        public void reposition(World world, Vector3f firstPos, Vector3f secondPos, float scale) {
            updateDisplays(world, firstPos, secondPos, scale);
        }

        private BlockDisplay spawnChainDisplay(World world, Vector3f firstPos, Vector3f secondPos, float scale) {
            Vector3f dir = new Vector3f(secondPos).sub(firstPos);
            scale = dir.length() + 0.1f;
            Vector3f spawnPoint = new Vector3f(firstPos).add(new Vector3f(dir).mul(0.5f)).add(new Vector3f(0.5f));
            Location loc = new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z);
            BlockDisplay blockDisplay = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
            blockDisplay.setBlock(Bukkit.createBlockData(material));
            blockDisplay.setBrightness(new Display.Brightness(15,15));
            applyTransform(blockDisplay, world, firstPos, secondPos, scale);
            return blockDisplay;
        }

        private void applyTransform(BlockDisplay blockDisplay, World world, Vector3f firstPos, Vector3f secondPos,
                float scale) {
            Vector3f dir = new Vector3f(secondPos).sub(firstPos);
            scale = dir.length() + 0.1f;
            Vector3f spawnPoint = new Vector3f(firstPos).add(new Vector3f(dir).mul(0.5f)).add(new Vector3f(0.5f));
            Location loc = new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z);
            blockDisplay.teleport(loc);

            Transformation transformation = blockDisplay.getTransformation();
            transformation.getTranslation().set(new Vector3f(scale * 0.5f).mul(-1));
            transformation.getScale().set(new Vector3f(scale));
            blockDisplay.setTransformation(transformation);
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
            if (dir.y < 0) {
                pitch -= 90;
            }
            else {
                pitch += 90;
            }
            blockDisplay.setRotation(yaw, pitch);
        }

        public void clearDisplays(boolean force, Player ignoredPlayer) {
            if (display != null) {
//                if (!force) {
//                    if (isItemBeingUsedByOtherPlayers(ignoredPlayer, itemName)) return;
//                }
                display.remove();
                display = null;
            }
        }
    }
}


