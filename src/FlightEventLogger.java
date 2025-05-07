
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FlightEventLogger {

    private static final String LOG_FILE = "flight_log.txt";
    private static final Object lock = new Object(); // for thread-safe writes
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static void log(String airplaneId, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String threadName = Thread.currentThread().getName();
        String logMessage = "[" + timestamp + "][" + threadName + "] Airplane " + airplaneId + ": " + message;

        // Print to console
        System.out.println(logMessage);

        // Write to file
        synchronized (lock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                writer.write(logMessage);
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
