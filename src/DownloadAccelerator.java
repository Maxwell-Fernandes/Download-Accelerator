import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadAccelerator {
    private static final int NUM_THREADS = 4;
    private static boolean paused = false;
    private static final Logger LOGGER = Logger.getLogger(DownloadAccelerator.class.getName());

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter download URL: ");
            String url = reader.readLine();

            System.out.print("Enter filename to save as: ");
            String filename = reader.readLine();

            Metadata metadata = MetadataStore.loadMetadata(filename);
            if (metadata == null) {
                metadata = new Metadata(url, filename);
            }

            long fileSize = getFileSize(url);

            if (fileSize <= 0) {
                System.err.println("Warning: Could not determine file size. Downloading as a single file.");
                LOGGER.log(Level.WARNING, "Could not determine file size for URL: " + url + ". Downloading as single file.");
                // Initialize fileSize to a large value. ChunkDownloader handles the rest
                fileSize = Long.MAX_VALUE; // Effectively disables chunking

                metadata.setFileSize(fileSize);

                new ChunkDownloader(url, filename, 0, fileSize -1, metadata).run();

                if(metadata.isCompleted()){
                    MetadataStore.deleteMetadata(filename);
                }

                return;
            }

            metadata.setFileSize(fileSize);

            if (!supportsRangeRequests(url)) {
                System.err.println("Warning: Server does not support range requests. Downloading as a single file...");
                LOGGER.log(Level.WARNING, "Server does not support range requests for URL: " + url + ". Downloading as single file.");
                new ChunkDownloader(url, filename, 0, fileSize - 1, metadata).run();

                if(metadata.isCompleted()){
                    MetadataStore.deleteMetadata(filename);
                }

                return;
            }

            ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
            long chunkSize = fileSize / NUM_THREADS;

            for (int i = 0; i < NUM_THREADS; i++) {
                long start = i * chunkSize;
                long end = (i == NUM_THREADS - 1) ? fileSize - 1 : start + chunkSize - 1;
                executor.execute(new ChunkDownloader(url, filename, start, end, metadata));
            }

            Thread controlThread = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (!executor.isTerminated()) {
                    System.out.println("Enter 'pause' to pause, 'resume' to continue, or 'exit' to stop:");
                    String command = scanner.nextLine().trim().toLowerCase();
                    if (command.equals("pause")) {
                        paused = true;
                        System.out.println("Download paused.");
                    } else if (command.equals("resume")) {
                        paused = false;
                        System.out.println("Download resumed.");
                    } else if (command.equals("exit")) {
                        executor.shutdownNow();
                        System.out.println("Download aborted.");
                        scanner.close();
                        System.exit(0);
                    }
                }
                scanner.close();
            });
            controlThread.start();

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            if (metadata.getDownloadedBytes() >= fileSize) {
                metadata.setCompleted(true);
            }

            if (metadata.isCompleted()) {
                MetadataStore.saveMetadata(metadata);
                MetadataStore.deleteMetadata(filename);
                System.out.println("\nDownload completed successfully.");
            } else {
                System.err.println("\nDownload failed. Some chunks could not be downloaded.");
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            connection.connect();
            boolean supportsRange = connection.getHeaderField("Accept-Ranges") != null;
            connection.disconnect();
            return supportsRange;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error checking range request support for URL: " + fileUrl, e);
            return false;
        }
    }
}