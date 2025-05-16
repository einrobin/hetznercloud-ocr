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

    public void startWatching() throws IOException {
        WatchService watchService = FileSystems.getDefault().newWatchService();
        this.folderPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        Files.walkFileTree(this.folderPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FolderWatcher.this.onNewFile.accept(file);
                return super.visitFile(file, attrs);
            }
        });

        Thread watcherThread = new Thread(() -> {
            try {
                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path filename = (Path) event.context();
                            Path fullPath = folderPath.resolve(filename);
                            onNewFile.accept(fullPath);
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
}
