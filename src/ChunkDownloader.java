import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

class ChunkDownloader implements Runnable {
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_RETRIES = 3;
    private final String url;
    private final String filename;
    private final long startByte;
    private final long endByte;
    private final Metadata metadata;

    public ChunkDownloader(String url, String filename, long startByte, long endByte, Metadata metadata) {
        this.url = url;
        this.filename = filename;
        this.startByte = startByte;
        this.endByte = endByte;
        this.metadata = metadata;
    }

    @Override
    public void run() {
        long currentByte = startByte;
        int attempts = 0;

        while (currentByte <= endByte && attempts < MAX_RETRIES) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestProperty("Range", "bytes=" + currentByte + "-" + endByte);
                connection.connect();

                try (InputStream inputStream = connection.getInputStream();
                     RandomAccessFile raf = new RandomAccessFile(filename, "rw")) {

                    raf.seek(currentByte);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1 && currentByte <= endByte) {
                        raf.write(buffer, 0, bytesRead);
                        currentByte += bytesRead;
                        metadata.setDownloadedBytes(metadata.getDownloadedBytes() + bytesRead);
                        MetadataStore.saveMetadata(metadata);
                    }
                    System.out.printf("Chunk [%d - %d] downloaded successfully.\n", startByte, endByte);
                    metadata.setCompleted(metadata.getDownloadedBytes() >= metadata.getFileSize());
                    MetadataStore.saveMetadata(metadata);

                    return; // Chunk finished successfully, exit loop
                } catch (IOException e) {
                    attempts++;
                    System.err.printf("Retry %d for chunk [%d - %d]\n", attempts, startByte, endByte);
                    if (attempts == MAX_RETRIES) {
                        System.err.printf("Failed to download chunk [%d - %d] after %d attempts.\n", startByte, endByte, MAX_RETRIES);
                    }
                }
            } catch (IOException e) {
                attempts++;
                System.err.printf("Retry %d for chunk [%d - %d]\n", attempts, startByte, endByte);
                if (attempts == MAX_RETRIES) {
                    System.err.printf("Failed to connect to download chunk [%d - %d] after %d attempts.\n", startByte, endByte, MAX_RETRIES);
                }
            }
        }

    }
}