import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

class ChunkDownloader implements Runnable {
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_RETRIES = 5;
    private static final long METADATA_SAVE_INTERVAL = 1024 * 1024; // Save every 1MB instead of every 8KB
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds

    private final String url;
    private final String filename;
    private final long startByte;
    private final long endByte;
    private final Metadata metadata;
    private final int chunkId;
    private final AtomicBoolean paused;

    public ChunkDownloader(String url, String filename, long startByte, long endByte, Metadata metadata, int chunkId, AtomicBoolean paused) {
        this.url = url;
        this.filename = filename;
        this.startByte = startByte;
        this.endByte = endByte;
        this.metadata = metadata;
        this.chunkId = chunkId;
        this.paused = paused;
    }

    @Override
    public void run() {
        // Resume from last saved progress for this chunk
        long chunkBytesDownloaded = metadata.getChunkProgress(chunkId);
        long currentByte = startByte + chunkBytesDownloaded;
        int attempts = 0;

        while (currentByte <= endByte && attempts < MAX_RETRIES) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestProperty("Range", "bytes=" + currentByte + "-" + endByte);
                connection.setRequestProperty("User-Agent", "DownloadAccelerator/2.0");
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
                    System.err.printf("Chunk %d [%d - %d]: Server returned code %d\n", chunkId, startByte, endByte, responseCode);
                    attempts++;
                    waitWithExponentialBackoff(attempts);
                    continue;
                }

                try (InputStream inputStream = connection.getInputStream();
                     RandomAccessFile raf = new RandomAccessFile(filename, "rw")) {

                    raf.seek(currentByte);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long bytesReadSinceLastSave = 0;
                    long chunkCurrentBytes = chunkBytesDownloaded;

                    while ((bytesRead = inputStream.read(buffer)) != -1 && currentByte <= endByte) {
                        // Check pause flag
                        while (paused.get()) {
                            try {
                                Thread.sleep(100); // Check every 100ms
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                System.err.printf("Chunk %d interrupted while paused\n", chunkId);
                                return;
                            }
                        }

                        // Check for thread interruption
                        if (Thread.currentThread().isInterrupted()) {
                            System.err.printf("Chunk %d interrupted\n", chunkId);
                            return;
                        }

                        raf.write(buffer, 0, bytesRead);
                        currentByte += bytesRead;
                        chunkCurrentBytes += bytesRead;

                        // Update atomic counter for thread-safe progress tracking
                        metadata.addDownloadedBytes(bytesRead);
                        bytesReadSinceLastSave += bytesRead;

                        // Save metadata periodically (every 1MB) instead of on every buffer read
                        if (bytesReadSinceLastSave >= METADATA_SAVE_INTERVAL) {
                            metadata.setChunkProgress(chunkId, chunkCurrentBytes);
                            metadata.syncDownloadedBytes();
                            MetadataStore.saveMetadata(metadata);
                            bytesReadSinceLastSave = 0;
                        }
                    }

                    // Final save for this chunk
                    metadata.setChunkProgress(chunkId, chunkCurrentBytes);
                    metadata.syncDownloadedBytes();
                    metadata.setCompleted(metadata.getDownloadedBytes() >= metadata.getFileSize());
                    MetadataStore.saveMetadata(metadata);

                    System.out.printf("Chunk %d [%d - %d] downloaded successfully.\n", chunkId, startByte, endByte);
                    return; // Chunk finished successfully, exit loop

                } catch (IOException e) {
                    attempts++;
                    System.err.printf("Chunk %d I/O error (attempt %d/%d): %s\n", chunkId, attempts, MAX_RETRIES, e.getMessage());
                    if (attempts < MAX_RETRIES) {
                        waitWithExponentialBackoff(attempts);
                    } else {
                        System.err.printf("Failed to download chunk %d [%d - %d] after %d attempts.\n", chunkId, startByte, endByte, MAX_RETRIES);
                    }
                }
            } catch (IOException e) {
                attempts++;
                System.err.printf("Chunk %d connection error (attempt %d/%d): %s\n", chunkId, attempts, MAX_RETRIES, e.getMessage());
                if (attempts < MAX_RETRIES) {
                    waitWithExponentialBackoff(attempts);
                } else {
                    System.err.printf("Failed to connect for chunk %d [%d - %d] after %d attempts.\n", chunkId, startByte, endByte, MAX_RETRIES);
                }
            }
        }
    }

    // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    private void waitWithExponentialBackoff(int attempt) {
        try {
            long waitTime = (long) Math.min(1000 * Math.pow(2, attempt - 1), 16000);
            System.out.printf("Chunk %d waiting %dms before retry...\n", chunkId, waitTime);
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}