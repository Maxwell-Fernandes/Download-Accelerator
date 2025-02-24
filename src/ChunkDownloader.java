import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
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
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);
                connection.connect();

                try (InputStream inputStream = connection.getInputStream();
                     RandomAccessFile raf = new RandomAccessFile(filename, "rw")) {
                    raf.seek(startByte);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        raf.write(buffer, 0, bytesRead);
                        metadata.setDownloadedBytes(metadata.getDownloadedBytes() + bytesRead);
                        MetadataStore.saveMetadata(metadata);
                    }
                    System.out.printf("Chunk [%d - %d] downloaded successfully.\n", startByte, endByte);
                    return;
                }
            } catch (IOException e) {
                attempts++;
                System.err.printf("Retry %d for chunk [%d - %d]\n", attempts, startByte, endByte);
                if (attempts == MAX_RETRIES) {
                    System.err.printf("Failed to download chunk [%d - %d] after %d attempts.\n", startByte, endByte, MAX_RETRIES);
                }
            }
        }
    }
}
