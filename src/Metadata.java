import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Metadata implements Serializable {
    private static final long serialVersionUID = 2L; // Incremented due to structure change
    private String filename;        // Local filename
    private String url;             // Download URL
    private long fileSize;          // Total file size (if known)
    private long downloadedBytes;   // Number of bytes already downloaded (for serialization)
    private boolean completed;      // True if download is complete

    // Per-chunk progress tracking (chunkId -> bytes downloaded in that chunk)
    private ConcurrentHashMap<Integer, Long> chunkProgress;

    // Transient atomic counter for thread-safe updates (not serialized)
    private transient AtomicLong downloadedBytesAtomic;

    // Constructors, getters, and setters
    public Metadata(String url, String filename) {
        this.url = url;
        this.filename = filename;
        this.downloadedBytes = 0;
        this.completed = false;
        this.chunkProgress = new ConcurrentHashMap<>();
        this.downloadedBytesAtomic = new AtomicLong(0);
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
        if (downloadedBytesAtomic != null) {
            return downloadedBytesAtomic.get();
        }
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
        if (downloadedBytesAtomic == null) {
            downloadedBytesAtomic = new AtomicLong(downloadedBytes);
        } else {
            downloadedBytesAtomic.set(downloadedBytes);
        }
    }

    public void addDownloadedBytes(long bytes) {
        if (downloadedBytesAtomic == null) {
            downloadedBytesAtomic = new AtomicLong(downloadedBytes);
        }
        downloadedBytesAtomic.addAndGet(bytes);
    }

    public void syncDownloadedBytes() {
        if (downloadedBytesAtomic != null) {
            this.downloadedBytes = downloadedBytesAtomic.get();
        }
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public ConcurrentHashMap<Integer, Long> getChunkProgress() {
        if (chunkProgress == null) {
            chunkProgress = new ConcurrentHashMap<>();
        }
        return chunkProgress;
    }

    public void setChunkProgress(int chunkId, long bytesDownloaded) {
        getChunkProgress().put(chunkId, bytesDownloaded);
    }

    public long getChunkProgress(int chunkId) {
        return getChunkProgress().getOrDefault(chunkId, 0L);
    }

    // Initialize transient fields after deserialization
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.downloadedBytesAtomic = new AtomicLong(this.downloadedBytes);
        if (this.chunkProgress == null) {
            this.chunkProgress = new ConcurrentHashMap<>();
        }
    }
}