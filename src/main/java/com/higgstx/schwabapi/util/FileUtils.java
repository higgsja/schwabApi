package com.higgstx.schwabapi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * File and IO utility functions extracted from TokenManager and other classes
 */
public final class FileUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    
    private FileUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Safely save an object to a JSON file with backup
     * @param object The object to save
     * @param filePath The file path to save to
     * @param objectMapper The Jackson ObjectMapper to use
     * @throws IOException if saving fails
     */
    public static void saveJsonWithBackup(Object object, String filePath, ObjectMapper objectMapper) throws IOException {
        Path targetPath = Paths.get(filePath);
        Path tempPath = Paths.get(filePath + ".tmp");
        Path backupPath = Paths.get(filePath + ".backup");
        
        // Create backup of existing file
        if (Files.exists(targetPath)) {
            Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Created backup of existing file: {}", filePath);
        }
        
        // Write to temporary file first
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), object);
        
        // Atomically move temporary file to target
        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        logger.debug("Successfully saved object to file: {}", filePath);
    }
    
    /**
     * Load an object from a JSON file
     * @param filePath The file path to load from
     * @param objectMapper The Jackson ObjectMapper to use
     * @param objectClass The class to deserialize into
     * @param <T> The type to deserialize into
     * @return The loaded object, or null if file doesn't exist or can't be read
     */
    public static <T> T loadJson(String filePath, ObjectMapper objectMapper, Class<T> objectClass) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.debug("File not found: {}", file.getAbsolutePath());
                return null;
            }
            
            if (!file.canRead()) {
                logger.error("Cannot read file: {}", file.getAbsolutePath());
                return null;
            }
            
            return objectMapper.readValue(file, objectClass);
        } catch (IOException e) {
            logger.error("Error loading JSON from file {}: {}", filePath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a file exists and is readable
     * @param filePath The file path to check
     * @return true if the file exists and is readable
     */
    public static boolean isReadableFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.exists(path) && Files.isReadable(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            logger.debug("Error checking file readability for {}: {}", filePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Safely delete a file, ignoring errors
     * @param filePath The file path to delete
     * @return true if the file was deleted or didn't exist
     */
    public static boolean safeDelete(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.debug("Deleted file: {}", filePath);
                return true;
            }
            return true; // File didn't exist, consider it "deleted"
        } catch (IOException e) {
            logger.warn("Failed to delete file {}: {}", filePath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Clean up old backup files, keeping only the specified number
     * @param baseFilePath The base file path (backups will have .backup extension)
     * @param maxBackups Maximum number of backup files to keep
     */
    public static void cleanupOldBackups(String baseFilePath, int maxBackups) {
        try {
            Path baseFile = Paths.get(baseFilePath);
            Path parentDir = baseFile.getParent();
            if (parentDir == null) {
                parentDir = Paths.get(".");
            }
            
            final String backupPattern = baseFile.getFileName().toString() + ".backup";
            
            try (Stream<Path> stream = Files.list(parentDir)) {
                stream.filter(path -> path.getFileName().toString().startsWith(backupPattern))
                        .sorted((p1, p2) -> {
                            try {
                                return Files.getLastModifiedTime(p2).compareTo(
                                        Files.getLastModifiedTime(p1));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .skip(maxBackups)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                logger.debug("Deleted old backup: {}", path.getFileName());
                            } catch (IOException e) {
                                logger.warn("Failed to delete old backup {}: {}", 
                                           path.getFileName(), e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            logger.debug("Error cleaning up backups for {}: {}", baseFilePath, e.getMessage());
        }
    }
    
    /**
     * Write a string to a file, creating parent directories if needed
     * @param filePath The file path to write to
     * @param content The content to write
     * @throws IOException if writing fails
     */
    public static void writeString(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        
        // Create parent directories if they don't exist
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        
        Files.writeString(path, content);
        logger.debug("Wrote content to file: {}", filePath);
    }
    
    /**
     * Read a string from a file
     * @param filePath The file path to read from
     * @return The file content, or null if file doesn't exist or can't be read
     */
    public static String readString(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.debug("File not found: {}", filePath);
                return null;
            }
            
            return Files.readString(path);
        } catch (IOException e) {
            logger.error("Error reading file {}: {}", filePath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the absolute path of a file
     * @param filePath The file path
     * @return The absolute path as a string
     */
    public static String getAbsolutePath(String filePath) {
        return Paths.get(filePath).toAbsolutePath().toString();
    }
    
    /**
     * Create a directory if it doesn't exist
     * @param dirPath The directory path
     * @return true if the directory exists or was created successfully
     */
    public static boolean ensureDirectoryExists(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.debug("Created directory: {}", dirPath);
            }
            return true;
        } catch (IOException e) {
            logger.error("Failed to create directory {}: {}", dirPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the file size in bytes
     * @param filePath The file path
     * @return The file size in bytes, or -1 if the file doesn't exist or can't be read
     */
    public static long getFileSize(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return Files.size(path);
            }
            return -1;
        } catch (IOException e) {
            logger.debug("Error getting file size for {}: {}", filePath, e.getMessage());
            return -1;
        }
    }
    
    /**
     * Check if a directory exists and is writable
     * @param dirPath The directory path
     * @return true if the directory exists and is writable
     */
    public static boolean isWritableDirectory(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            return Files.exists(path) && Files.isDirectory(path) && Files.isWritable(path);
        } catch (Exception e) {
            logger.debug("Error checking directory writability for {}: {}", dirPath, e.getMessage());
            return false;
        }
    }
    
    /**
     * Copy a file with backup of the destination
     * @param sourcePath The source file path
     * @param destPath The destination file path
     * @param createBackup Whether to create a backup of the destination file if it exists
     * @throws IOException if copying fails
     */
    public static void copyWithBackup(String sourcePath, String destPath, boolean createBackup) throws IOException {
        Path source = Paths.get(sourcePath);
        Path dest = Paths.get(destPath);
        
        if (!Files.exists(source)) {
            throw new IOException("Source file does not exist: " + sourcePath);
        }
        
        // Create backup if requested and destination exists
        if (createBackup && Files.exists(dest)) {
            Path backupPath = Paths.get(destPath + ".backup");
            Files.copy(dest, backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Created backup: {}", backupPath);
        }
        
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Copied {} to {}", sourcePath, destPath);
    }
}