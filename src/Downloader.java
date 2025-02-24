import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;

public class Downloader {
    private static final int BUFFER_SIZE = 8192; // Adjust as needed

    public void download(Metadata metadata) throws IOException {
        URL url = new URL(metadata.getUrl());
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();

        // Resume download if previously interrupted
        long startByte = metadata.getDownloadedBytes();
        if (startByte > 0) {
            httpConn.setRequestProperty("Range", "bytes=" + startByte + "-");
        }

        int responseCode = httpConn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
            InputStream inputStream = httpConn.getInputStream();
            String saveFilePath = metadata.getFilename();

            try (FileOutputStream outputStream = new FileOutputStream(saveFilePath, true)) { // Append mode
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    metadata.setDownloadedBytes(metadata.getDownloadedBytes() + bytesRead);
                    MetadataStore.saveMetadata(metadata); // Save progress after each chunk
                }

                metadata.setCompleted(true);
                MetadataStore.saveMetadata(metadata);
                MetadataStore.deleteMetadata(metadata.getFilename());  //Remove metadata after finishing download.
                System.out.println("Download completed.");
            } catch (IOException e) {
                // Handle file writing errors
                System.err.println("Error writing to file: " + e.getMessage());
                throw e; // Re-throw to signal download failure
            } finally {
                inputStream.close(); // Ensure input stream is closed
                httpConn.disconnect(); // Disconnect
            }
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
    }
}