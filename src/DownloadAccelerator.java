import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadAccelerator {
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 16;
    private static final long MIN_CHUNK_SIZE = 1024 * 1024; // 1MB minimum per chunk
    private static final int CHECK_INTERVAL_MS = 1000; // Check progress every second
    private static final AtomicBoolean paused = new AtomicBoolean(false);
    private static final AtomicBoolean exitRequested = new AtomicBoolean(false);
    private static final Logger LOGGER = Logger.getLogger(DownloadAccelerator.class.getName());

    public static void main(String[] args) {
        Scanner controlScanner = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter download URL: ");
            String url = reader.readLine();

            // Validate URL
            if (!isValidUrl(url)) {
                System.err.println("Error: Invalid URL. Please provide a valid HTTP or HTTPS URL.");
                return;
            }

            System.out.print("Enter filename to save as: ");
            String filename = reader.readLine();

            // Validate and sanitize filename
            filename = sanitizeFilename(filename);
            if (filename == null || filename.isEmpty()) {
                System.err.println("Error: Invalid filename.");
                return;
            }

            Metadata metadata = MetadataStore.loadMetadata(filename);
            if (metadata == null) {
                metadata = new Metadata(url, filename);
                System.out.println("Starting new download...");
            } else {
                System.out.println("Resuming previous download...");
                System.out.printf("Already downloaded: %.2f MB\n", metadata.getDownloadedBytes() / (1024.0 * 1024.0));
            }

            long fileSize = getFileSize(url);

            if (fileSize <= 0) {
                System.err.println("Warning: Could not determine file size. Downloading as a single file.");
                LOGGER.log(Level.WARNING, "Could not determine file size for URL: " + url + ". Downloading as single file.");
                fileSize = Long.MAX_VALUE;
                metadata.setFileSize(fileSize);

                new ChunkDownloader(url, filename, 0, fileSize - 1, metadata, 0, paused).run();

                if (metadata.isCompleted()) {
                    MetadataStore.deleteMetadata(filename);
                }
                return;
            }

            metadata.setFileSize(fileSize);
            System.out.printf("File size: %.2f MB\n", fileSize / (1024.0 * 1024.0));

            if (!supportsRangeRequests(url)) {
                System.err.println("Warning: Server does not support range requests. Downloading as a single file...");
                LOGGER.log(Level.WARNING, "Server does not support range requests for URL: " + url + ". Downloading as single file.");
                new ChunkDownloader(url, filename, 0, fileSize - 1, metadata, 0, paused).run();

                if (metadata.isCompleted()) {
                    MetadataStore.deleteMetadata(filename);
                }
                return;
            }

            // Dynamic thread pool sizing based on file size
            int numThreads = calculateOptimalThreads(fileSize);
            System.out.printf("Using %d threads for download\n", numThreads);

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long chunkSize = fileSize / numThreads;

            for (int i = 0; i < numThreads; i++) {
                long start = i * chunkSize;
                long end = (i == numThreads - 1) ? fileSize - 1 : start + chunkSize - 1;
                executor.execute(new ChunkDownloader(url, filename, start, end, metadata, i, paused));
            }

            // Make variables effectively final for lambda
            final Metadata finalMetadata = metadata;
            final long finalFileSize = fileSize;

            // Progress monitoring thread
            Thread progressThread = new Thread(() -> {
                long lastBytes = finalMetadata.getDownloadedBytes();
                while (!executor.isTerminated() && !exitRequested.get()) {
                    try {
                        Thread.sleep(CHECK_INTERVAL_MS);
                        long currentBytes = finalMetadata.getDownloadedBytes();
                        long downloadedDelta = currentBytes - lastBytes;
                        double speedMBps = (downloadedDelta / 1024.0 / 1024.0) / (CHECK_INTERVAL_MS / 1000.0);
                        double progress = (currentBytes * 100.0) / finalFileSize;

                        if (!paused.get()) {
                            System.out.printf("\rProgress: %.2f%% | Downloaded: %.2f MB / %.2f MB | Speed: %.2f MB/s",
                                    progress, currentBytes / (1024.0 * 1024.0), finalFileSize / (1024.0 * 1024.0), speedMBps);
                        }

                        lastBytes = currentBytes;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            progressThread.setDaemon(true);
            progressThread.start();

            // Control thread for pause/resume/exit
            controlScanner = new Scanner(System.in);
            final Scanner finalControlScanner = controlScanner;
            Thread controlThread = new Thread(() -> {
                System.out.println("\nCommands: 'pause', 'resume', 'exit'");
                while (!executor.isTerminated() && !exitRequested.get()) {
                    if (finalControlScanner.hasNextLine()) {
                        String command = finalControlScanner.nextLine().trim().toLowerCase();
                        if (command.equals("pause")) {
                            paused.set(true);
                            System.out.println("\nDownload paused. Enter 'resume' to continue.");
                        } else if (command.equals("resume")) {
                            paused.set(false);
                            System.out.println("Download resumed.");
                        } else if (command.equals("exit")) {
                            exitRequested.set(true);
                            System.out.println("\nSaving progress and exiting...");
                            executor.shutdownNow();
                            break;
                        }
                    }
                }
            });
            controlThread.setDaemon(true);
            controlThread.start();

            executor.shutdown();
            boolean terminated = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            // Final metadata save
            metadata.syncDownloadedBytes();
            MetadataStore.saveMetadata(metadata);

            if (exitRequested.get()) {
                System.out.println("\nDownload paused. Progress saved. Run again to resume.");
                return;
            }

            if (metadata.getDownloadedBytes() >= fileSize) {
                metadata.setCompleted(true);
            }

            if (metadata.isCompleted()) {
                MetadataStore.deleteMetadata(filename);
                System.out.println("\n\nDownload completed successfully!");
            } else {
                System.err.println("\n\nDownload failed. Some chunks could not be downloaded. Run again to retry.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error in download", e);
            e.printStackTrace();
        } finally {
            if (controlScanner != null) {
                controlScanner.close();
            }
        }
    }

    private static long getFileSize(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();
            long length = connection.getContentLengthLong();
            connection.disconnect();
            return length > 0 ? length : -1;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error getting file size for URL: " + fileUrl, e);
            return -1;
        }
    }

    private static boolean supportsRangeRequests(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.connect();
            boolean supportsRange = connection.getHeaderField("Accept-Ranges") != null;
            connection.disconnect();
            return supportsRange;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error checking range request support for URL: " + fileUrl, e);
            return false;
        }
    }

    /**
     * Validates if the URL is a valid HTTP/HTTPS URL
     */
    private static boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol().toLowerCase();

            // Only allow HTTP and HTTPS protocols
            if (!protocol.equals("http") && !protocol.equals("https")) {
                System.err.println("Error: Only HTTP and HTTPS protocols are supported.");
                return false;
            }

            // Ensure host is present
            if (url.getHost() == null || url.getHost().isEmpty()) {
                return false;
            }

            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Sanitizes filename to prevent path traversal and invalid characters
     */
    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return null;
        }

        // Remove leading/trailing whitespace
        filename = filename.trim();

        // Prevent path traversal attacks
        filename = filename.replaceAll("\\.\\./", "");
        filename = filename.replaceAll("\\.\\\\", "");

        // Get only the filename, not the path
        try {
            filename = Paths.get(filename).getFileName().toString();
        } catch (InvalidPathException e) {
            LOGGER.warning("Invalid path provided: " + filename);
            return null;
        }

        // Remove or replace invalid characters
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Ensure filename is not empty after sanitization
        if (filename.isEmpty() || filename.equals(".") || filename.equals("..")) {
            return null;
        }

        // Limit filename length
        if (filename.length() > 255) {
            filename = filename.substring(0, 255);
        }

        return filename;
    }

    /**
     * Calculates optimal number of threads based on file size
     * Small files: fewer threads to avoid overhead
     * Large files: more threads for better parallelization
     */
    private static int calculateOptimalThreads(long fileSize) {
        // For files smaller than MIN_CHUNK_SIZE, use single thread
        if (fileSize < MIN_CHUNK_SIZE) {
            return MIN_THREADS;
        }

        // Calculate threads based on file size
        // Each thread should handle at least MIN_CHUNK_SIZE
        int optimalThreads = (int) Math.min(fileSize / MIN_CHUNK_SIZE, MAX_THREADS);

        // Ensure at least MIN_THREADS
        return Math.max(optimalThreads, MIN_THREADS);
    }
}