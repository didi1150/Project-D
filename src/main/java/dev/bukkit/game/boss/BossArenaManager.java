package dev.bukkit.game.boss;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Set<String> SKIPPED_TEMPLATE_FILES = Set.of("uid.dat", "session.lock", "session.lock_old");

    private final Plugin plugin;
    private final Logger logger;
    private final Path templateRoot;
    private final Path instanceRoot;

    // Single active arena instance for the server (global scope).
    private volatile BossArena activeArena;
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
     * Pre-warms a boss arena by beginning the template copy early.
     * <p>
     * Safe to call from any thread; the actual world load will always execute on
     * the Bukkit main thread.
     */
    public CompletableFuture<World> prewarmInstance(String bossId) {
        return createInstance(bossId);
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

    public CompletableFuture<Void> destroyInstance() {
        BossArena arena = activeArena;
        if (arena == null) {
            return CompletableFuture.completedFuture(null);
        }

        return runOnMainThread(() -> unloadArenaWorld(arena)).thenCompose(this::deleteInstanceFolderAsync)
                .thenAccept(__ -> activeArena = null);
    }

    public void cleanupOrphanInstances() {
        runOnMainThread(() -> null).thenRunAsync(() -> {
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
        }, ioExecutor);
    }

    public Optional<BossArena> getArena() {
        return Optional.ofNullable(activeArena);
    }

    public List<BossArena> getActiveArenas() {
        if (activeArena == null) {
            return List.of();
        }
        return List.of(activeArena);
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

    private Path resolveEditLiveFolder(String bossId) {
        return plugin.getServer().getWorldContainer().toPath().resolve(editWorldName(bossId));
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

    private Void prepareEditFolders(String bossId, Path templateFolder) {
        try {
            Path liveFolder = resolveEditLiveFolder(bossId);
            Files.createDirectories(liveFolder.getParent());

            if (Files.notExists(liveFolder)) {
                copyDirectory(templateFolder, liveFolder);
            } else if (Files.isSymbolicLink(liveFolder) || Files.isRegularFile(liveFolder)) {
                Files.deleteIfExists(liveFolder);
                copyDirectory(templateFolder, liveFolder);
            } else if (!Files.isDirectory(liveFolder)) {
                throw new IllegalStateException("The edit world path is not a directory: " + liveFolder);
            } else {
                copyDirectory(templateFolder, liveFolder);
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare template edit folder for boss: " + bossId + " ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", e);
        }
    }

    public CompletableFuture<Void> saveTemplateEditWorld(String bossId) {
        Objects.requireNonNull(bossId, "bossId");
        Path liveFolder = resolveEditLiveFolder(bossId);
        if (!Files.isDirectory(liveFolder)) {
            return CompletableFuture
                    .failedFuture(new IllegalArgumentException("Edit world does not exist for boss: " + bossId));
        }

        return runOnMainThread(() -> {
            World world = Bukkit.getWorld(editWorldName(bossId));
            if (world != null) {
                Bukkit.unloadWorld(world, true);
            }
            return null;
        }).thenRunAsync(() -> {
            Path templateFolder = templateRoot.resolve(bossId);
            Path tempStaging = templateFolder
                    .resolveSibling(templateFolder.getFileName() + ".tmp-" + UUID.randomUUID());
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
        }, ioExecutor);
    }

    public CompletableFuture<Void> quitTemplateEditWorld(String bossId, World fallbackWorld) {
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(fallbackWorld, "fallbackWorld");

        String worldName = editWorldName(bossId);

        // 1. First, save the current changes back to the main template folder
        return saveTemplateEditWorld(bossId).thenCompose(__ -> runOnMainThread(() -> {
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
        })).thenRunAsync(() -> {
            // 4. Delete the live edit world directory from the server root container
            Path liveFolder = resolveEditLiveFolder(bossId);
            try {
                deleteDirectory(liveFolder);
                logger.log(Level.INFO, "Successfully removed edit world container folder for boss: {0}", bossId);
            } catch (IOException e) {
                throw new IllegalStateException("Could not delete live edit world folder: " + liveFolder, e);
            }
        }, ioExecutor);
    }

    private CompletableFuture<Path> copyTemplateAsync(String bossId, Path templateFolder) {
        return CompletableFuture.supplyAsync(() -> {
            if (activeArena != null && activeArena.bossId().equals(bossId)) {
                throw new IllegalStateException("An active arena already exists for boss: " + bossId);
            }
            Path destination = createInstanceFolder(bossId);
            try {
                // ensure clean destination
                if (Files.exists(destination)) {
                    deleteDirectory(destination);
                }
                copyDirectory(templateFolder, destination);
                return destination;
            } catch (IOException e) {
                try {
                    deleteDirectory(destination);
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
            BossArena arena = new BossArena(bossId, worldName, Instant.now(), BossArena.State.LOADING);
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
                activeArena = activeArena.withState(BossArena.State.ACTIVE);
            }
            return world;
        });
    }

    private BossArena unloadArenaWorld(BossArena arena) {
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
            activeArena = arena.withState(BossArena.State.DESTROYING);
        }
        return arena;
    }

    private CompletableFuture<Void> deleteInstanceFolderAsync(BossArena arena) {
        return CompletableFuture.runAsync(() -> {
            Path folder = instanceRoot.resolve(arena.worldName());
            try {
                deleteDirectory(folder);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete boss arena folder: " + folder, e);
            }
        }, ioExecutor);
    }

    private Path createInstanceFolder(String bossId) {
        String safeBossId = bossId.replaceAll("[^A-Za-z0-9_-]", "_");
        String instanceName = "boss_" + safeBossId + "_instance";
        Path target = instanceRoot.resolve(instanceName);
        try {
            return Files.createDirectories(target);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create instance folder: " + target, e);
        }
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
}
