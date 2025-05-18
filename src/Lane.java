import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

public class Lane {
    // Lane status constants
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_OCCUPIED = "OCCUPIED";
    public static final String STATUS_MAINTENANCE = "MAINTENANCE";
    public static final String STATUS_TURBULENCE_COOLDOWN = "TURBULENCE_COOLDOWN";
    
    private final int laneNumber;
    private volatile String status = STATUS_AVAILABLE;
    private volatile String currentAirplaneId = null;
    private final ReentrantLock laneLock = new ReentrantLock(true); // fair lock for operation sequencing
    private final Random random = new Random();
    
    // Configurable operation times (in milliseconds)
    private static final int LANDING_TIME_BASE = 3000;
    private static final int TAKEOFF_TIME_BASE = 2000;
    private static final int TURBULENCE_COOLDOWN_TIME_BASE = 1000;
    private static final int TIME_VARIATION = 1000; // +/- random variation

    public Lane(int laneNumber) {
        this.laneNumber = laneNumber;
        FlightEventLogger.log("SYSTEM", "Lane " + laneNumber + " is now " + status);
    }
    
    public int getLaneNumber() {
        return laneNumber;
    }
    
    public String getStatus() {
        return status;
    }
    
    private void setStatus(String newStatus, String airplaneId) {
        String oldStatus = this.status;
        String oldAirplaneId = this.currentAirplaneId;
        
        this.status = newStatus;
        this.currentAirplaneId = airplaneId;
        
        // Log with more details when an airplane is assigned or released from a lane
        if (oldAirplaneId == null && airplaneId != null) {
            // An airplane was assigned to this lane
            FlightEventLogger.log(airplaneId, "Lane " + laneNumber + " is now assigned and " + newStatus);
        } else if (oldAirplaneId != null && airplaneId == null) {
            // An airplane was released from this lane
            FlightEventLogger.log(oldAirplaneId, "Lane " + laneNumber + " is now released and " + newStatus);
        } else {
            // Status change without changing airplane assignment
            FlightEventLogger.log(airplaneId != null ? airplaneId : "SYSTEM", 
                                "Lane " + laneNumber + " changed from " + oldStatus + 
                                " to " + newStatus);
        }
    }

    public synchronized boolean useLane(String airplaneId, String operationType) {
        if (!status.equals(STATUS_AVAILABLE) || !laneLock.tryLock()) {
            return false;
        }
        
        try {
            setStatus(STATUS_OCCUPIED, airplaneId);
            FlightEventLogger.log(airplaneId, "is using Lane " + laneNumber + " for " + operationType);
            
            // Process the operation based on type
            if (operationType.equals("landing")) {
                performLanding(airplaneId);
            } else { // departure/takeoff
                performTakeoff(airplaneId);
            }
            
            // Apply turbulence cooldown period
            applyTurbulenceCooldown();
            
            // Lane is available again
            setStatus(STATUS_AVAILABLE, null);
            return true;
        } finally {
            laneLock.unlock();
        }
    }
    
    private void performLanding(String airplaneId) {
        // Calculate actual landing time with variation
        int actualLandingTime = LANDING_TIME_BASE + random.nextInt(TIME_VARIATION * 2) - TIME_VARIATION;
        try {
            FlightEventLogger.log(airplaneId, "beginning landing on Lane " + laneNumber + 
                                 " (estimated time: " + (actualLandingTime/1000.0) + " seconds)");
            TimeUnit.MILLISECONDS.sleep(actualLandingTime);
            FlightEventLogger.log(airplaneId, "has successfully landed on Lane " + laneNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlightEventLogger.log(airplaneId, "landing was interrupted on Lane " + laneNumber);
        }
    }
    
    private void performTakeoff(String airplaneId) {
        // Calculate actual takeoff time with variation
        int actualTakeoffTime = TAKEOFF_TIME_BASE + random.nextInt(TIME_VARIATION * 2) - TIME_VARIATION;
        try {
            FlightEventLogger.log(airplaneId, "beginning takeoff from Lane " + laneNumber + 
                                 " (estimated time: " + (actualTakeoffTime/1000.0) + " seconds)");
            TimeUnit.MILLISECONDS.sleep(actualTakeoffTime);
            FlightEventLogger.log(airplaneId, "has successfully taken off from Lane " + laneNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlightEventLogger.log(airplaneId, "takeoff was interrupted on Lane " + laneNumber);
        }
    }
    
    private void applyTurbulenceCooldown() {
        // Calculate actual cooldown time with variation
        int actualCooldownTime = TURBULENCE_COOLDOWN_TIME_BASE + random.nextInt(TIME_VARIATION);
        try {
            setStatus(STATUS_TURBULENCE_COOLDOWN, null);
            FlightEventLogger.log("SYSTEM", "Lane " + laneNumber + " cooling down for turbulence safety (" + 
                               (actualCooldownTime/1000.0) + " seconds)");
            TimeUnit.MILLISECONDS.sleep(actualCooldownTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlightEventLogger.log("SYSTEM", "Turbulence cooldown was interrupted on Lane " + laneNumber);
        }
    }
    
    // Method to put lane in maintenance mode (can be triggered periodically or on demand)
    public synchronized boolean startMaintenance(int durationMs) {
        if (!status.equals(STATUS_AVAILABLE) || !laneLock.tryLock()) {
            return false;
        }
        
        try {
            setStatus(STATUS_MAINTENANCE, null);
            FlightEventLogger.log("SYSTEM", "Lane " + laneNumber + " is under maintenance for " + 
                               (durationMs/1000.0) + " seconds");
            
            // Schedule the maintenance to complete after duration
            new Thread(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(durationMs);
                    setStatus(STATUS_AVAILABLE, null);
                    FlightEventLogger.log("SYSTEM", "Lane " + laneNumber + " maintenance completed");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    FlightEventLogger.log("SYSTEM", "Maintenance was interrupted on Lane " + laneNumber);
                    setStatus(STATUS_AVAILABLE, null);
                }
            }, "Maintenance-Lane-" + laneNumber).start();
            
            return true;
        } finally {
            laneLock.unlock();
        }
    }
}
