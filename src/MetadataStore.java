import java.io.*;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

public class MetadataStore {
    private static final String METADATA_FILE_EXTENSION = ".dwnld";
    private static final Logger LOGGER = Logger.getLogger(MetadataStore.class.getName());
    private static final boolean DEBUG_MODE = true;
    private static final String KEY_FILE = "metadata.key";
    private static final String ALGORITHM = "AES";

    private static Key secretKey = loadOrGenerateKey();

    private static Key loadOrGenerateKey() {
        File keyFile = new File(KEY_FILE);
        if (keyFile.exists()) {
            try (FileInputStream fis = new FileInputStream(keyFile)) {
                byte[] keyBytes = fis.readAllBytes();
                return new SecretKeySpec(keyBytes, ALGORITHM);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to load encryption key, generating new one.", e);
            }
        }

        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(128, new SecureRandom());
            Key newKey = keyGen.generateKey();
            try (FileOutputStream fos = new FileOutputStream(KEY_FILE)) {
                fos.write(newKey.getEncoded());
            }
            return newKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    public static synchronized void saveMetadata(Metadata metadata) throws IOException {
        String metadataFile = metadata.getFilename() + METADATA_FILE_EXTENSION;
        String backupFile = metadataFile + ".bak";

        File file = new File(metadataFile);
        File backup = new File(backupFile);

        if (file.exists()) {
            if (!file.renameTo(backup)) {
                LOGGER.severe("Failed to create metadata backup.");
                throw new IOException("Failed to create metadata backup.");
            }
        }

        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            byte[] encryptedData = encrypt(serialize(metadata));
            fos.write(encryptedData);
            if (DEBUG_MODE) {
                LOGGER.info("Metadata saved securely: " + metadataFile);
            }
        } catch (IOException e) {
            if (backup.exists()) {
                backup.renameTo(file);
            }
            LOGGER.log(Level.SEVERE, "Error saving metadata: " + metadataFile, e);
            throw e;
        } finally {
            if (backup.exists()) {
                backup.delete();
            }
        }
    }

    public static synchronized Metadata loadMetadata(String filename) throws IOException, ClassNotFoundException {
        String metadataFile = filename + METADATA_FILE_EXTENSION;
        try (FileInputStream fis = new FileInputStream(metadataFile)) {
            byte[] encryptedData = fis.readAllBytes();
            Metadata metadata = deserialize(decrypt(encryptedData));
            if (DEBUG_MODE) {
                LOGGER.info("Metadata loaded securely: " + metadataFile);
            }
            return metadata;
        } catch (FileNotFoundException e) {
            LOGGER.warning("Metadata file not found: " + metadataFile);
            return null;
        }
    }

    public static synchronized void deleteMetadata(String filename) {
        String metadataFile = filename + METADATA_FILE_EXTENSION;
        File file = new File(metadataFile);
        if (file.exists() && file.delete()) {
            if (DEBUG_MODE) {
                LOGGER.info("Metadata deleted successfully: " + metadataFile);
            }
        } else {
            LOGGER.warning("Failed to delete metadata file: " + metadataFile);
        }
    }

    private static byte[] encrypt(byte[] data) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IOException("Error encrypting metadata", e);
        }
    }

    private static byte[] decrypt(byte[] data) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IOException("Error decrypting metadata", e);
        }
    }

    private static byte[] serialize(Metadata metadata) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(metadata);
            return bos.toByteArray();
        }
    }

    private static Metadata deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Metadata) ois.readObject();
        }
    }
}
