
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlightEventLogger {

    private static final String LOG_FILE = "flight_log.txt";
    private static final Object fileLock = new Object(); // for thread-safe file writes
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Log levels
    public static final int LEVEL_INFO = 0;
    public static final int LEVEL_WARNING = 1;
    public static final int LEVEL_EMERGENCY = 2;
    
    // Async logging support
    private static final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();
    private static final Thread asyncLoggerThread = new Thread(new AsyncLogger(), "AsyncLogger-Thread");
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);
    
    // Start async logger when class is loaded
    static {
        asyncLoggerThread.setDaemon(true); // Allow JVM to exit even if this thread is still running
        asyncLoggerThread.start();
        
        // Add shutdown hook to flush logs on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isRunning.set(false);
            try {
                asyncLoggerThread.join(5000); // Wait up to 5 seconds for logger to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Flush any remaining logs directly
            while (!logQueue.isEmpty()) {
                LogEntry entry = logQueue.poll();
                if (entry != null) {
                    writeLogToFile(entry.logMessage);
                }
            }
        }, "LoggerShutdownHook"));
    }

    // Standard log method
    public static void log(String entityId, String message) {
        log(entityId, message, LEVEL_INFO);
    }
    
    // Log with explicit level
    public static void log(String entityId, String message, int level) {
        String timestamp = LocalDateTime.now().format(formatter);
        String threadName = Thread.currentThread().getName();
        String threadId = String.valueOf(Thread.currentThread().getId());
        String levelStr = getLevelString(level);
        
        String logMessage = String.format("[%s][Thread-%s:%s][%s] %s: %s", 
                                        timestamp, threadId, threadName, levelStr, entityId, message);

        // Print to console immediately (with color for different levels)
        printToConsole(logMessage, level);

        // Queue for async file writing
        try {
            logQueue.put(new LogEntry(logMessage, level));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Failed to queue log message: " + e.getMessage());
            // Fallback to direct write if queue fails
            writeLogToFile(logMessage);
        }
    }
    
    private static void printToConsole(String message, int level) {
        switch (level) {
            case LEVEL_WARNING:
                System.out.println("\u001B[33m" + message + "\u001B[0m"); // Yellow for warnings
                break;
            case LEVEL_EMERGENCY:
                System.out.println("\u001B[31m" + message + "\u001B[0m"); // Red for emergencies
                break;
            default:
                System.out.println(message); // Regular color for normal logs
                break;
        }
    }
    
    private static String getLevelString(int level) {
        switch (level) {
            case LEVEL_WARNING: return "WARNING";
            case LEVEL_EMERGENCY: return "EMERGENCY";
            default: return "INFO";
        }
    }
    
    private static void writeLogToFile(String logMessage) {
        synchronized (fileLock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                writer.write(logMessage);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write to log file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Async log processing class
    private static class AsyncLogger implements Runnable {
        @Override
        public void run() {
            while (isRunning.get() || !logQueue.isEmpty()) {
                try {
                    LogEntry entry = logQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        writeLogToFile(entry.logMessage);
                        
                        // Use the level for special handling of high-priority logs
                        if (entry.level >= LEVEL_WARNING) {
                            // For higher-level logs, ensure they're written immediately by flushing
                            synchronized (fileLock) {
                                try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                                    writer.flush();
                                } catch (IOException e) {
                                    System.err.println("Failed to flush log file: " + e.getMessage());
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Async logger interrupted: " + e.getMessage());
                }
            }
        }
    }
    
    // Log entry container
    private static class LogEntry {
        final String logMessage;
        final int level;
        
        LogEntry(String logMessage, int level) {
            this.logMessage = logMessage;
            this.level = level;
        }
    }
    
    // Log an emergency event
    public static void logEmergency(String entityId, String message) {
        log(entityId, message, LEVEL_EMERGENCY);
    }
    
    // Log a warning event
    public static void logWarning(String entityId, String message) {
        log(entityId, message, LEVEL_WARNING);
    }
}
