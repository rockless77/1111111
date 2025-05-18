
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Airport {
    // Airport status constants
    public static final String STATUS_OPERATIONAL = "OPERATIONAL";
    public static final String STATUS_WEATHER_ALERT = "WEATHER_ALERT";
    public static final String STATUS_EMERGENCY = "EMERGENCY";
    
    private final String name;
    private final List<Lane> lanes;
    private final ConcurrentHashMap<String, AirTrafficController> controllers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Airplane> airplanesInRange = new ConcurrentHashMap<>();
    private volatile String status = STATUS_OPERATIONAL;
    private final Random random = new Random();
    
    // Thread pools for various operations
    private final ExecutorService operationsExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService maintenanceScheduler = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService weatherSimulator = Executors.newScheduledThreadPool(1);
    
    // Configurable parameters
    private static final int LANE_MAINTENANCE_INTERVAL_MIN = 15; // minutes
    private static final int LANE_MAINTENANCE_DURATION_MIN = 5; // minutes
    private static final int WEATHER_EVENT_PROBABILITY = 10; // percentage chance per check (hourly)
    private static final int WEATHER_EVENT_DURATION_MIN = 10; // minutes
    
    public Airport(String name, int laneCount) {
        this.name = name;
        this.lanes = new ArrayList<>();
        for (int i = 1; i <= laneCount; i++) {
            lanes.add(new Lane(i));
        }
        FlightEventLogger.log("SYSTEM", "Airport " + name + " initialized with " + laneCount + " lanes.");
        
        // Start maintenance scheduler
        scheduleMaintenance();
        
        // Start weather simulator
        simulateWeatherEvents();
    }
    
    // Constructor overload for backward compatibility
    public Airport(int laneCount) {
        this("International Airport", laneCount);
    }
    
    public void addController(AirTrafficController controller) {
        controllers.put(controller.getName(), controller);
        FlightEventLogger.log("SYSTEM", "Controller " + controller.getName() + " added to airport " + name);
    }
    
    public AirTrafficController getController(String controllerName) {
        return controllers.get(controllerName);
    }
    
    public List<Lane> getLanes() {
        return new ArrayList<>(lanes); // Return a copy to maintain encapsulation
    }
    
    public String getName() {
        return name;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String newStatus) {
        String oldStatus = this.status;
        this.status = newStatus;
        FlightEventLogger.log("SYSTEM", "Airport " + name + " status changed from " + oldStatus + " to " + newStatus);
    }
    
    /**
     * Register an airplane in the airport's range
     */
    public void registerAirplane(Airplane airplane) {
        airplanesInRange.put(airplane.getId(), airplane);
        FlightEventLogger.log(airplane.getId(), "entered the range of " + name + " airport");
    }
    
    /**
     * Remove an airplane from the airport's range
     */
    public void deregisterAirplane(String airplaneId) {
        Airplane airplane = airplanesInRange.remove(airplaneId);
        if (airplane != null) {
            FlightEventLogger.log(airplaneId, "left the range of " + name + " airport");
        }
    }
    
    /**
     * Get all airplanes currently in range
     */
    public List<Airplane> getAirplanesInRange() {
        return new ArrayList<>(airplanesInRange.values());
    }
    
    /**
     * Request a lane for an operation with the specified airplane.
     * This is now ran asynchronously with proper airplane status updates.
     */
    public boolean requestOperation(String airplaneId, String operationType) {
        if (!STATUS_OPERATIONAL.equals(status)) {
            FlightEventLogger.log(airplaneId, "operation delayed due to airport status: " + status);
            return false;
        }
        
        Airplane airplane = airplanesInRange.get(airplaneId);
        if (airplane == null) {
            FlightEventLogger.log("SYSTEM", "Error: Airplane " + airplaneId + " not in range of " + name);
            return false;
        }
        
        // Prepare airplane for operation
        if (operationType.equals("landing")) {
            airplane.setStatus(Airplane.STATUS_APPROACHING);
        } else { // takeoff
            airplane.setStatus(Airplane.STATUS_TAXIING);
        }
        
        // Randomize lane selection for load balancing
        Collections.shuffle(lanes);
        
        // Try each lane
        for (Lane lane : lanes) {
            if (lane.useLane(airplaneId, operationType)) {
                // Update airplane status based on operation result
                if (operationType.equals("landing")) {
                    airplane.setStatus(Airplane.STATUS_LANDED);
                    
                    // Schedule taxiing to gate
                    operationsExecutor.submit(() -> {
                        try {
                            // Simulate taxiing to gate
                            Thread.sleep(1000 + random.nextInt(2000));
                            airplane.setStatus(Airplane.STATUS_AT_GATE);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } else { // takeoff
                    airplane.setStatus(Airplane.STATUS_IN_AIR);
                    
                    // Schedule airplane to leave range
                    operationsExecutor.submit(() -> {
                        try {
                            // Simulate flying away
                            Thread.sleep(5000 + random.nextInt(5000));
                            deregisterAirplane(airplaneId);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
                return true;
            }
        }
        
        FlightEventLogger.log(airplaneId, "no lanes available for " + operationType);
        return false;
    }
    
    /**
     * Schedule periodic maintenance for lanes
     */
    private void scheduleMaintenance() {
        maintenanceScheduler.scheduleAtFixedRate(() -> {
            // Select a random lane for maintenance if airport is operational
            if (STATUS_OPERATIONAL.equals(status) && !lanes.isEmpty()) {
                int laneIndex = random.nextInt(lanes.size());
                Lane laneForMaintenance = lanes.get(laneIndex);
                
                // Convert minutes to milliseconds
                int maintenanceDurationMs = LANE_MAINTENANCE_DURATION_MIN * 60 * 1000;
                
                // Shortened for simulation purposes
                maintenanceDurationMs = maintenanceDurationMs / 60; // Use seconds instead of minutes for demo
                
                laneForMaintenance.startMaintenance(maintenanceDurationMs);
            }
        }, LANE_MAINTENANCE_INTERVAL_MIN, LANE_MAINTENANCE_INTERVAL_MIN, TimeUnit.MINUTES);
    }
    
    /**
     * Simulate random weather events
     */
    private void simulateWeatherEvents() {
        weatherSimulator.scheduleAtFixedRate(() -> {
            // Check if a weather event should occur
            if (STATUS_OPERATIONAL.equals(status) && random.nextInt(100) < WEATHER_EVENT_PROBABILITY) {
                setStatus(STATUS_WEATHER_ALERT);
                
                // Convert minutes to milliseconds
                int weatherDurationMs = WEATHER_EVENT_DURATION_MIN * 60 * 1000;
                
                // Shortened for simulation purposes
                weatherDurationMs = weatherDurationMs / 60; // Use seconds instead of minutes for demo
                
                // Create a final copy for use in the lambda
                final int finalWeatherDurationMs = weatherDurationMs;
                
                FlightEventLogger.log("SYSTEM", "Weather alert at " + name + ". Expected duration: " + 
                                   (weatherDurationMs/1000.0) + " seconds");
                
                // Schedule return to operational status
                operationsExecutor.submit(() -> {
                    try {
                        Thread.sleep(finalWeatherDurationMs);
                        if (STATUS_WEATHER_ALERT.equals(status)) {
                            setStatus(STATUS_OPERATIONAL);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }, 0, 1, TimeUnit.HOURS);
    }
    
    /**
     * Simulate an emergency situation
     */
    public void declareEmergency(String reason) {
        setStatus(STATUS_EMERGENCY);
        FlightEventLogger.log("SYSTEM", "EMERGENCY at " + name + ": " + reason);
        
        // Notify all controllers
        for (AirTrafficController controller : controllers.values()) {
            controller.handleEmergency();
        }
        
        // Schedule emergency resolution
        operationsExecutor.submit(() -> {
            try {
                Thread.sleep(5000); // Emergency lasts for 5 seconds
                setStatus(STATUS_OPERATIONAL);
                FlightEventLogger.log("SYSTEM", "Emergency at " + name + " resolved");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    /**
     * Shutdown airport operations cleanly
     */
    public void shutdown() {
        FlightEventLogger.log("SYSTEM", "Airport " + name + " shutting down");
        operationsExecutor.shutdown();
        maintenanceScheduler.shutdown();
        weatherSimulator.shutdown();
    }
}
