package com.github.einrobin.ocr.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FolderWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FolderWatcher.class);
    private static final AtomicInteger THREAD_ID = new AtomicInteger();

    private final Path folderPath;
    private final Consumer<Path> onNewFile;

    public FolderWatcher(Path folderPath, Consumer<Path> onNewFile) {
        this.folderPath = folderPath;
        this.onNewFile = onNewFile;
    }

    private void registerAll(Path start, WatchService watchService) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void startWatching() throws IOException {
        WatchService watchService = FileSystems.getDefault().newWatchService();

        this.registerAll(this.folderPath, watchService);

        Files.walkFileTree(this.folderPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isDirectory(file)) {
                    FolderWatcher.this.onNewFile(file);
                }
                return super.visitFile(file, attrs);
            }
        });

        Thread watcherThread = new Thread(() -> {
            try {
                while (true) {
                    WatchKey key = watchService.take();
                    Path dir = (Path) key.watchable();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path filename = (Path) event.context();
                            Path child = dir.resolve(filename);

                            if (Files.isDirectory(child)) {
                                try {
                                    this.registerAll(child, watchService);
                                } catch (IOException e) {
                                    LOGGER.error("Failed to add watch on new sub folder {}", child);
                                }
                            } else {
                                this.onNewFile(child);
                            }
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        LOGGER.warn("Folder watching on {} has been interrupted because the key is no longer valid", this.folderPath);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    watchService.close();
                } catch (IOException e) {
                    LOGGER.warn("An error occurred during watch service close on directory {}", this.folderPath, e);
                }
            }
        }, "FolderWatcher-" + THREAD_ID.incrementAndGet());

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void onNewFile(Path file) {
        if (this.waitUntilFileIsStable(file)) {
            this.onNewFile.accept(file);
        } else {
            LOGGER.error("File {} did not finish uploading", file);
        }
    }

    private boolean waitUntilFileIsStable(Path file) {
        try {
            long previousSize = -1;
            int unchangedCount = 0;
            while (unchangedCount < 3) {
                long currentSize = Files.size(file);
                if (currentSize == previousSize) {
                    unchangedCount++;
                } else {
                    unchangedCount = 0;
                    previousSize = currentSize;
                }
                Thread.sleep(500);
            }

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Thread interrupted while waiting for file {}", file);
        } catch (IOException e) {
            LOGGER.warn("Error while waiting for file {}", file);
        }

        return false;
    }
}
