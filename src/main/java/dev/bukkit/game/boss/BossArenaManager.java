package dev.bukkit.game.boss;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.bukkit.game.dungeon.proceduralDungeon.BukkitVoidWorldGenerator;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;

/**
 * Manages boss arena world instances created from template world folders.
 * <p>
 * Thread-safety:
 * <ul>
 * <li>Public file I/O work is performed off the Bukkit main thread.</li>
 * <li>World load/unload operations are always scheduled on the Bukkit main
 * thread.</li>
 * <li>Registry operations are safe for concurrent access.</li>
 * </ul>
 */
public final class BossArenaManager {
    private static final Set<String> SKIPPED_TEMPLATE_FILES = Set.of("uid.dat", "session.lock", "session.lock_old",
            "metadata.dat");
    private static final String VOID_FLAT_SETTINGS = "{\"layers\":[],\"biome\":\"minecraft:plains\",\"lakes\":false,\"features\":false,\"structure_overrides\":[]}";

    private final Plugin plugin;
    private final Logger logger;
    private final Path templateRoot;
    private final Path instanceRoot;

    // Single active arena instance for the server (global scope).
    private volatile Arena activeArena;
    // Tracks an in-flight creation operation for serialization.
    private volatile CompletableFuture<World> pendingCreation;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BossArenaManager-IO");
        thread.setDaemon(true);
        return thread;
    });

    public BossArenaManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.templateRoot = plugin.getDataFolder().toPath().resolve("boss_templates");
        this.instanceRoot = plugin.getServer().getWorldContainer().toPath();

        try {
            Files.createDirectories(templateRoot);
            Files.createDirectories(instanceRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create boss arena directories: " + e.getMessage(), e);
        }
    }

    public static BossArenaManager createDefault(Plugin plugin) {
        return new BossArenaManager(plugin);
    }

    /**
     * Creates a boss arena instance from the named template and returns the loaded
     * world.
     * <p>
     * This method may be called from any thread. Template folder copy occurs off
     * the main thread, while world loading is scheduled on the Bukkit main thread.
     */
    public CompletableFuture<World> createInstance(String bossId) {
        Objects.requireNonNull(bossId, "bossId");

        synchronized (this) {
            if (activeArena != null) {
                return CompletableFuture
                        .failedFuture(new IllegalStateException("An active boss arena already exists."));
            }
            if (pendingCreation != null) {
                return pendingCreation;
            }

            pendingCreation = CompletableFuture.supplyAsync(() -> resolveTemplateFolder(bossId), ioExecutor)
                    .thenCompose(templateFolder -> copyTemplateAsync(bossId, templateFolder))
                    .thenCompose(copyFolder -> loadWorldAsync(bossId, copyFolder)).whenComplete((world, throwable) -> {
                        synchronized (this) {
                            pendingCreation = null;
                            if (throwable != null) {
                                logger.log(Level.WARNING, "Failed to create boss arena for bossId {0}", bossId);
                            }
                        }
                    });

            return pendingCreation;
        }
    }

    public World getActiveWorld() {
        Arena arena = activeArena;
        if (arena == null || arena.state() != ArenaState.ACTIVE) {
            return null;
        }
        return Bukkit.getWorld(arena.worldName());
    }

    public CompletableFuture<Void> destroyInstance() {
        Arena arena = activeArena;
        if (arena == null) {
            return CompletableFuture.completedFuture(null);
        }

        return runOnMainThread(() -> unloadArenaWorld(arena)).thenCompose(this::deleteInstanceFolderAsync)
                .thenAccept(__ -> activeArena = null);
    }

    public void cleanupOrphanInstances() {
        ioExecutor.execute(() -> {
            try {
                if (Files.notExists(instanceRoot)) {
                    return;
                }
                Files.list(instanceRoot).filter(Files::isDirectory).forEach(path -> {
                    try {
                        if (isOrphanInstanceFolder(path)) {
                            deleteDirectory(path);
                            logger.log(Level.INFO, "Deleted orphan boss arena folder: {0}", path);
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Failed to delete orphan boss arena folder: " + path, e);
                    }
                });
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not scan boss instance root for orphan cleanup.", e);
            }
        });
    }

    public void shutdown() {
        ioExecutor.shutdown();
    }

    private Path resolveTemplateFolder(String bossId) {
        Path templateFolder = templateRoot.resolve(bossId);
        if (!Files.isDirectory(templateFolder)) {
            throw new IllegalArgumentException("Boss template not found: " + templateFolder);
        }
        return templateFolder;
    }

    private String editWorldName(String bossId) {
        return "boss_template_edit_" + bossId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private Path resolveLegacyEditFolder(String bossId) {
        return plugin.getServer().getWorldContainer().toPath().resolve(editWorldName(bossId));
    }

    private Path resolveDimensionFolder(String worldName) {
        String dimensionName = worldName.toLowerCase(Locale.ROOT).replace(" ", "_");
        return plugin.getServer().getLevelDirectory().resolve("dimensions").resolve("minecraft").resolve(dimensionName);
    }

    private boolean isLegacyTemplate(Path templateFolder) {
        return Files.isRegularFile(templateFolder.resolve("level.dat"))
                || Files.isRegularFile(templateFolder.resolve("level.dat_old"));
    }

    private void stageTemplateForLoad(Path templateFolder, String worldName, Path legacyFolder) throws IOException {
        Path dimensionFolder = resolveDimensionFolder(worldName);
        if (isLegacyTemplate(templateFolder)) {
            deleteDirectory(dimensionFolder);
            deleteDirectory(legacyFolder);
            copyDirectory(templateFolder, legacyFolder);
        } else {
            deleteDirectory(legacyFolder);
            deleteDirectory(dimensionFolder);
            copyDirectory(templateFolder, dimensionFolder);
        }
    }

    public Path getTemplateRoot() {
        return templateRoot;
    }

    public CompletableFuture<World> loadTemplateEditWorld(String bossId) {
        Objects.requireNonNull(bossId, "bossId");
        String worldName = editWorldName(bossId);

        return runOnMainThread(() -> Bukkit.getWorld(worldName)).thenCompose(existing -> {
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            return CompletableFuture.supplyAsync(() -> resolveTemplateFolder(bossId), ioExecutor)
                    .thenApplyAsync(templateFolder -> prepareEditFolders(bossId, templateFolder), ioExecutor)
                    .thenCompose(ignored -> runOnMainThread(() -> {
                        WorldCreator creator = new WorldCreator(worldName).generatorSettings("default");
                        creator.environment(Bukkit.getWorlds().get(0).getEnvironment());
                        creator.type(WorldType.NORMAL);
                        creator.generateStructures(false);
                        creator.createWorld();

                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            throw new IllegalStateException("Failed to load boss template edit world: " + worldName);
                        }
                        world.setAutoSave(true);
                        return world;
                    }));
        });
    }

    /**
     * Creates a new, empty (void) boss template and opens it as an editable world.
     * <p>
     * Fails if a template with the given id already exists so prebuilt template
     * maps are never overwritten. Existing prebuilt templates should continue to be
     * opened with {@link #loadTemplateEditWorld(String)}.
     */
    public CompletableFuture<World> createEmptyTemplateEditWorld(String bossId) {
        Objects.requireNonNull(bossId, "bossId");
        if (!bossId.matches("[A-Za-z0-9_-]+")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid boss template id: " + bossId));
        }
        if (Files.exists(templateRoot.resolve(bossId))) {
            return CompletableFuture
                    .failedFuture(new IllegalArgumentException("Boss template already exists: " + bossId));
        }

        String worldName = editWorldName(bossId);
        return runOnMainThread(() -> Bukkit.getWorld(worldName)).thenCompose(existing -> {
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            return CompletableFuture.runAsync(() -> deleteStaleEditFolder(bossId), ioExecutor)
                    .thenCompose(ignored -> runOnMainThread(() -> createVoidEditWorld(worldName)));
        }).thenCompose(world -> saveTemplateEditWorld(bossId).thenApply(ignored -> world))
                .thenCompose(ignored -> loadTemplateEditWorld(bossId));
    }

    private Void deleteStaleEditFolder(String bossId) {
        Path legacyFolder = resolveLegacyEditFolder(bossId);
        Path dimensionFolder = resolveDimensionFolder(editWorldName(bossId));
        try {
            deleteDirectory(legacyFolder);
            deleteDirectory(dimensionFolder);
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Could not clear stale template edit world: " + dimensionFolder, e);
        }
    }

    private World createVoidEditWorld(String worldName) {
        WorldCreator creator = new WorldCreator(worldName).generator(new BukkitVoidWorldGenerator())
                .generatorSettings(VOID_FLAT_SETTINGS).environment(Bukkit.getWorlds().get(0).getEnvironment())
                .type(WorldType.FLAT).generateStructures(false);
        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create empty boss template edit world: " + worldName);
        }
        world.setSpawnLocation(0, 65, 0);
        world.setAutoSave(true);
        return world;
    }

    private Void prepareEditFolders(String bossId, Path templateFolder) {
        try {
            stageTemplateForLoad(templateFolder, editWorldName(bossId), resolveLegacyEditFolder(bossId));
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare template edit folder for boss: " + bossId + " ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", e);
        }
    }

    public CompletableFuture<Void> saveTemplateEditWorld(String bossId) {
        return saveTemplateEditWorldAndGetFolder(bossId).thenApply(liveFolder -> null);
    }

    private CompletableFuture<Path> saveTemplateEditWorldAndGetFolder(String bossId) {
        Objects.requireNonNull(bossId, "bossId");
        String worldName = editWorldName(bossId);

        CompletableFuture<Path> future = runOnMainThread(() -> {
            World world = Bukkit.getWorld(worldName);
            Path liveFolder;
            if (world != null) {
                liveFolder = world.getWorldPath();
                World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (fallback != null && !fallback.equals(world)) {
                    world.getPlayers().forEach(player -> player.teleport(fallback.getSpawnLocation()));
                }
                if (!Bukkit.unloadWorld(world, true)) {
                    throw new IllegalStateException("Failed to unload template edit world: " + worldName);
                }
            } else {
                Path dimensionFolder = resolveDimensionFolder(worldName);
                liveFolder = Files.isDirectory(dimensionFolder) ? dimensionFolder : resolveLegacyEditFolder(bossId);
            }
            if (!Files.isDirectory(liveFolder)) {
                throw new IllegalArgumentException("Edit world does not exist for boss: " + bossId);
            }
            return liveFolder;
        }).thenApplyAsync(liveFolder -> {
            saveFolderAsTemplate(bossId, liveFolder);
            return liveFolder;
        }, ioExecutor);

        return future.whenComplete((folder, throwable) -> {
            if (throwable != null) {
                logger.log(Level.WARNING, "Failed to save boss template '" + bossId + "'", throwable);
            }
        });
    }

    private void saveFolderAsTemplate(String bossId, Path liveFolder) {
        Path templateFolder = templateRoot.resolve(bossId);
        Path tempStaging = templateFolder.resolveSibling(templateFolder.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            copyDirectory(liveFolder, tempStaging);
            Path backup = Files.exists(templateFolder)
                    ? templateFolder.resolveSibling(templateFolder.getFileName() + ".bak-" + UUID.randomUUID())
                    : null;
            if (backup != null) {
                Files.move(templateFolder, backup, StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.move(tempStaging, templateFolder, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveFailure) {
                if (backup != null) {
                    Files.move(backup, templateFolder, StandardCopyOption.ATOMIC_MOVE);
                }
                throw moveFailure;
            }
            if (backup != null) {
                deleteDirectory(backup);
            }
        } catch (IOException e) {
            try {
                deleteDirectory(tempStaging);
            } catch (IOException cleanupFailure) {
                logger.log(Level.WARNING, "Failed cleaning staged template save: " + tempStaging, cleanupFailure);
            }
            throw new IllegalStateException("Could not save boss template from edit world: " + bossId, e);
        }
    }

    public CompletableFuture<Void> quitTemplateEditWorld(String bossId, World fallbackWorld) {
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(fallbackWorld, "fallbackWorld");

        String worldName = editWorldName(bossId);

        // 1. First, save the current changes back to the main template folder
        return saveTemplateEditWorldAndGetFolder(bossId).thenCompose(liveFolder -> runOnMainThread(() -> {
            World editWorld = Bukkit.getWorld(worldName);
            if (editWorld != null) {
                // 2. Teleport players out of the edit world to the fallback world
                editWorld.getPlayers().forEach(player -> player.teleport(fallbackWorld.getSpawnLocation()));

                // 3. Unload the world (do not save again since saveTemplateEditWorld already
                // handled it)
                boolean unloaded = Bukkit.unloadWorld(editWorld, false);
                if (!unloaded) {
                    throw new IllegalStateException("Failed to unload template edit world: " + worldName);
                }
            }
            return null;
        }).thenRunAsync(() -> {
            // 4. Delete the live edit world directory from the server root container
            try {
                deleteDirectory(liveFolder);
                deleteDirectory(resolveLegacyEditFolder(bossId));
                logger.log(Level.INFO, "Successfully removed edit world container folder for boss: {0}", bossId);
            } catch (IOException e) {
                throw new IllegalStateException("Could not delete live edit world folder: " + liveFolder, e);
            }
        }, ioExecutor));
    }

    private String instanceWorldName(String bossId) {
        String safeBossId = bossId.replaceAll("[^A-Za-z0-9_-]", "_");
        return "boss_" + safeBossId + "_instance";
    }

    private CompletableFuture<Path> copyTemplateAsync(String bossId, Path templateFolder) {
        return CompletableFuture.supplyAsync(() -> {
            if (activeArena != null && activeArena.bossId().equals(bossId)) {
                throw new IllegalStateException("An active arena already exists for boss: " + bossId);
            }
            String worldName = instanceWorldName(bossId);
            Path destination = resolveInstanceFolder(bossId);
            try {
                stageTemplateForLoad(templateFolder, worldName, destination);
                return destination;
            } catch (IOException e) {
                try {
                    deleteDirectory(destination);
                    deleteDirectory(resolveDimensionFolder(worldName));
                } catch (IOException cleanupFailure) {
                    logger.log(Level.WARNING, "Failed cleaning partial boss arena copy: " + destination,
                            cleanupFailure);
                }
                throw new IllegalStateException("Failed to copy boss template for " + bossId + " to " + destination, e);
            }
        }, ioExecutor);
    }

    private CompletableFuture<World> loadWorldAsync(String bossId, Path instanceFolder) {
        return runOnMainThread(() -> {
            String worldName = instanceFolder.getFileName().toString();
            Arena arena = new Arena(bossId, worldName, ArenaState.LOADING);
            synchronized (this) {
                activeArena = arena;
            }

            WorldCreator creator = new WorldCreator(worldName).generatorSettings("default");
            creator.environment(Bukkit.getWorlds().get(0).getEnvironment());
            creator.type(WorldType.NORMAL);
            creator.generateStructures(false);
            creator.createWorld();

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new IllegalStateException("Failed to load boss arena world: " + worldName);
            }

            world.setDifficulty(world.getDifficulty());
            world.setStorm(false);
            world.setThundering(false);
            world.setTime(0);

            synchronized (this) {
                activeArena = activeArena.withState(ArenaState.ACTIVE);
            }
            return world;
        });
    }

    private Arena unloadArenaWorld(Arena arena) {
        World world = Bukkit.getWorld(arena.worldName());
        if (world == null) {
            return arena;
        }

        // Teleport players to a safe fallback world (first loaded world) instead of
        // teleporting them to the world we're about to unload.
        World safeWorld = null;
        if (!Bukkit.getWorlds().isEmpty()) {
            safeWorld = Bukkit.getWorlds().get(0);
        }
        final World fallback = safeWorld;
        world.getPlayers().forEach(player -> {
            if (fallback != null && !fallback.equals(world)) {
                player.teleport(fallback.getSpawnLocation());
            } else {
                player.teleport(player.getWorld().getSpawnLocation());
            }
        });

        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            throw new IllegalStateException("Could not unload boss arena world: " + arena.worldName());
        }
        synchronized (this) {
            activeArena = arena.withState(ArenaState.DESTROYING);
        }
        return arena;
    }

    private CompletableFuture<Void> deleteInstanceFolderAsync(Arena arena) {
        return CompletableFuture.runAsync(() -> {
            Path folder = instanceRoot.resolve(arena.worldName());
            Path dimensionFolder = resolveDimensionFolder(arena.worldName());
            try {
                deleteDirectory(folder);
                deleteDirectory(dimensionFolder);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete boss arena folder: " + dimensionFolder, e);
            }
        }, ioExecutor);
    }

    private Path resolveInstanceFolder(String bossId) {
        return instanceRoot.resolve(instanceWorldName(bossId));
    }

    private boolean isOrphanInstanceFolder(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        String folderName = path.getFileName().toString();
        // New deterministic instance folder: boss_<bossId>_instance
        if (folderName.startsWith("boss_") && folderName.endsWith("_instance")) {
            return false;
        }

        // Legacy per-party pattern was: <bossId>_<partyId>_<random>
        // Detect legacy folders by having 3 parts and a UUID-like middle part (contains
        // '-')
        String[] parts = folderName.split("_");
        return parts.length == 3 && parts[1].contains("-");
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path destinationDir = target.resolve(source.relativize(dir));
                Files.createDirectories(destinationDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (SKIPPED_TEMPLATE_FILES.contains(fileName)) {
                    return FileVisitResult.CONTINUE;
                }
                Path destination = target.resolve(source.relativize(file));
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(Path folder) throws IOException {
        if (Files.notExists(folder)) {
            return;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private <T> CompletableFuture<T> runOnMainThread(SupplierWithException<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private record Arena(String bossId, String worldName, ArenaState state) {
        private Arena withState(ArenaState state) {
            return new Arena(bossId, worldName, state);
        }
    }

    private enum ArenaState {
        LOADING, ACTIVE, DESTROYING
    }
}
