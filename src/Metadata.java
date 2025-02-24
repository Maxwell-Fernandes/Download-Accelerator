import java.io.Serializable;

public class Metadata implements Serializable {
    private static final long serialVersionUID = 1L; // Important for serialization
    private String filename;        // Local filename
    private String url;             // Download URL
    private long fileSize;          // Total file size (if known)
    private long downloadedBytes;   // Number of bytes already downloaded
    private boolean completed;      // True if download is complete

    // Constructors, getters, and setters
    public Metadata(String url, String filename) {
        this.url = url;
        this.filename = filename;
        this.downloadedBytes = 0;
        this.completed = false;
    }

    // Getters and Setters for all fields
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}