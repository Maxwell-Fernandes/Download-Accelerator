import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DownloadAccelerator {
    private static final int NUM_THREADS = 4; // Number of threads for parallel downloads
    private static boolean paused = false;

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
                System.err.println("Error: Could not determine file size. Server may not support range requests.");
                return;
            }
            metadata.setFileSize(fileSize);

            if (!supportsRangeRequests(url)) {
                System.err.println("Error: Server does not support range requests. Downloading as a single file...");
                new ChunkDownloader(url, filename, 0, fileSize - 1, metadata).run();
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

    private static long getFileSize(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD");
        connection.connect();
        long length = connection.getContentLengthLong();
        connection.disconnect();
        return length > 0 ? length : -1;
    }

    private static boolean supportsRangeRequests(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD");
        connection.connect();
        boolean supportsRange = connection.getHeaderField("Accept-Ranges") != null;
        connection.disconnect();
        return supportsRange;
    }
}
