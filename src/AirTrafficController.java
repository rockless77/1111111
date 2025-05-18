import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;

public class AirTrafficController implements Runnable {
    // Controller status constants
    public static final String STATUS_ON_DUTY = "ON_DUTY";
    public static final String STATUS_BREAK = "ON_BREAK";
    public static final String STATUS_EMERGENCY = "HANDLING_EMERGENCY";
    
    private final String name;
    private final Airport airport;
    private final BlockingQueue<Airplane> airplaneQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Airplane> emergencyQueue = new LinkedBlockingQueue<>();
    private volatile String status = STATUS_ON_DUTY;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Random random = new Random();
    private final int maxConcurrentAirplanes;
    private volatile int currentActiveAirplanes = 0;
    
    public AirTrafficController(String name, Airport airport) {
        this(name, airport, 3); // Default max concurrent airplanes
    }
    
    public AirTrafficController(String name, Airport airport, int maxConcurrentAirplanes) {
        this.name = name;
        this.airport = airport;
        this.maxConcurrentAirplanes = maxConcurrentAirplanes;
        airport.addController(this);
        FlightEventLogger.log("SYSTEM", "Controller " + name + " initialized with capacity for " + 
                           maxConcurrentAirplanes + " concurrent airplanes");
    }
    
    public String getName() {
        return name;
    }
    
    public String getStatus() {
        return status;
    }
    
    private void setStatus(String newStatus) {
        String oldStatus = this.status;
        this.status = newStatus;
        FlightEventLogger.log("SYSTEM", "Controller " + name + " status changed from " + 
                           oldStatus + " to " + newStatus);
    }
    
    public synchronized boolean canAcceptAirplane() {
        return currentActiveAirplanes < maxConcurrentAirplanes && 
               status.equals(STATUS_ON_DUTY);
    }
    
    public void assignAirplane(Airplane airplane) {
        try {
            // Register with airport if not already in range
            airport.registerAirplane(airplane);
            
            if (airplane.getStatus().equals(Airplane.STATUS_EMERGENCY)) {
                // Handle emergency aircraft with priority
                emergencyQueue.put(airplane);
                FlightEventLogger.log(airplane.getId(), "EMERGENCY aircraft assigned to Controller " + name);
            } else {
                // Regular assignment
                airplaneQueue.put(airplane);
                FlightEventLogger.log(airplane.getId(), "assigned to Controller " + name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlightEventLogger.log(airplane.getId(), "assignment to Controller " + name + " was interrupted");
        }
    }
    
    public void handleEmergency() {
        setStatus(STATUS_EMERGENCY);
    }
    
    @Override
    public void run() {
        while (running.get()) {
            try {
                // First check for emergencies
                Airplane airplane = null;
                
                if (status.equals(STATUS_EMERGENCY)) {
                    FlightEventLogger.log("SYSTEM", "Controller " + name + " handling emergency situation");
                    TimeUnit.SECONDS.sleep(3); // Simulate handling emergency procedures
                    setStatus(STATUS_ON_DUTY);
                }
                
                // Poll for emergency aircraft first
                airplane = emergencyQueue.poll();
                
                if (airplane == null) {
                    // If no emergency, check for regular aircraft
                    airplane = airplaneQueue.poll(1, TimeUnit.SECONDS);
                }
                
                // If no aircraft, continue loop
                if (airplane == null) {
                    continue;
                }
                
                // Handle aircraft operation
                handleAirplaneOperation(airplane);
                
                // Occasionally take a short break (5% chance after each operation)
                if (random.nextInt(100) < 5 && status.equals(STATUS_ON_DUTY)) {
                    takeBreak();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                FlightEventLogger.log("SYSTEM", "Controller " + name + " operation was interrupted");
            }
        }
        FlightEventLogger.log("SYSTEM", "Controller " + name + " has ended shift");
    }
    
    private void handleAirplaneOperation(Airplane airplane) {
        try {
            synchronized (this) {
                currentActiveAirplanes++;
            }
            
            FlightEventLogger.log(airplane.getId(), "cleared by Controller " + name + 
                               " for " + airplane.getOperationType() + " operation.");
            
            // Try to find a lane for the operation
            boolean success = false;
            int attemptCount = 0;
            while (!success && attemptCount < 5) { // Limit retry attempts
                success = airport.requestOperation(airplane.getId(), airplane.getOperationType());
                if (!success) {
                    FlightEventLogger.log(airplane.getId(), "no lanes free for " + 
                                       airplane.getOperationType() + ". Attempt " + 
                                       (++attemptCount) + "/5. Retrying soon...");
                    TimeUnit.SECONDS.sleep(2);
                }
            }
            
            if (success) {
                FlightEventLogger.log(airplane.getId(), "completed " + airplane.getOperationType() + 
                                   " operation via Controller " + name);
            } else {
                // Operation couldn't be completed after multiple attempts
                if (airplane.getOperationType().equals("landing")) {
                    FlightEventLogger.log(airplane.getId(), "diverted to another airport after failed landing attempts");
                    airport.deregisterAirplane(airplane.getId());
                } else {
                    FlightEventLogger.log(airplane.getId(), "operation postponed due to unavailable lanes");
                    // Put back in queue with lower priority by requeuing
                    airplaneQueue.put(airplane);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlightEventLogger.log(airplane.getId(), "operation handling interrupted by Controller " + name);
        } finally {
            synchronized (this) {
                currentActiveAirplanes--;
            }
        }
    }
    
    private void takeBreak() {
        try {
            setStatus(STATUS_BREAK);
            int breakDuration = 3 + random.nextInt(7); // 3-10 seconds break
            FlightEventLogger.log("SYSTEM", "Controller " + name + " taking a " + breakDuration + 
                               " second break");
            TimeUnit.SECONDS.sleep(breakDuration);
            setStatus(STATUS_ON_DUTY);
            FlightEventLogger.log("SYSTEM", "Controller " + name + " back on duty");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setStatus(STATUS_ON_DUTY); // Ensure we're back on duty if interrupted
        }
    }
    
    public void endShift() {
        running.set(false);
        FlightEventLogger.log("SYSTEM", "Controller " + name + " ending shift");
    }
}
