import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    // Simulation parameters
    private static final int NUM_LANES = 5;
    private static final int NUM_CONTROLLERS = 3;
    private static final int SIMULATION_DURATION_SECONDS = 120;
    private static final int NEW_AIRPLANES_MIN = 1;
    private static final int NEW_AIRPLANES_MAX = 3;
    private static final int AIRPLANE_SPAWN_INTERVAL_SECONDS = 5;
    private static final int EMERGENCY_PROBABILITY = 50; 
    
    // Predefined airplane models for variety
    private static final String[] AIRPLANE_MODELS = {
        "Boeing 737", "Boeing 747", "Boeing 777", "Boeing 787",
        "Airbus A320", "Airbus A330", "Airbus A350", "Airbus A380",
        "Embraer E190", "Bombardier CRJ900"
    };
    
    // Airport names for variety
    private static final String[] AIRPORT_NAMES = {
        "JFK International", "Heathrow", "Charles de Gaulle", "Frankfurt", 
        "Dubai International", "Singapore Changi", "Narita", "Sydney"
    };
    
    // Purpose options
    private static final String[] PURPOSES = {
        Airplane.PURPOSE_PASSENGERS, Airplane.PURPOSE_CARGO, Airplane.PURPOSE_MIXED
    };
    
    // Operation types
    private static final String[] OPERATIONS = {"landing", "departure"};
    
    // Running state
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);
    
    public static void main(String[] args) {
        // Display welcome message
        System.out.println("===============================================");
        System.out.println("   AIRPORT SIMULATION WITH PARALLEL BEHAVIOR");
        System.out.println("===============================================\n");
        
        // Initialize random generator
        Random random = new Random();
        
        // Create the airport with random name
        String airportName = AIRPORT_NAMES[random.nextInt(AIRPORT_NAMES.length)];
        Airport airport = new Airport(airportName, NUM_LANES);
        
        // Create controllers and threads
        AirTrafficController[] controllers = new AirTrafficController[NUM_CONTROLLERS];
        Thread[] controllerThreads = new Thread[NUM_CONTROLLERS];
        
        System.out.println("Starting controllers...");
        for (int i = 0; i < NUM_CONTROLLERS; i++) {
            controllers[i] = new AirTrafficController("CTRL-" + (i+1), airport);
            controllerThreads[i] = new Thread(controllers[i], "Controller-" + (i+1) + "-Thread");
            controllerThreads[i].start();
        }
        
        // Create a scheduled executor for airplane generation
        ScheduledExecutorService spawner = Executors.newScheduledThreadPool(1);
        
        // Create an executor for handling simulation events
        ExecutorService eventExecutor = Executors.newCachedThreadPool();
        
        // Start airplane generator
        System.out.println("\nStarting airplane generator...");
        spawner.scheduleAtFixedRate(() -> {
            if (!isRunning.get()) {
                return;
            }
            
            try {
                // Generate a random number of airplanes
                int numAirplanes = NEW_AIRPLANES_MIN + random.nextInt(NEW_AIRPLANES_MAX - NEW_AIRPLANES_MIN + 1);
                
                for (int i = 0; i < numAirplanes; i++) {
                    // Generate a unique airplane ID
                    String airplaneId = generateAirplaneId();
                    
                    // Random model
                    String model = AIRPLANE_MODELS[random.nextInt(AIRPLANE_MODELS.length)];
                    
                    // Random purpose
                    String purpose = PURPOSES[random.nextInt(PURPOSES.length)];
                    
                    // Random operation (landing/departure)
                    String operation = OPERATIONS[random.nextInt(OPERATIONS.length)];
                    
                    // Random passenger and cargo capacities based on the model
                    int passengerCapacity = 100 + random.nextInt(400);
                    double cargoCapacityTons = 1.0 + random.nextDouble() * 50.0;
                    
                    // Create airplane
                    Airplane airplane = new Airplane(airplaneId, operation, purpose, model, 
                                                    passengerCapacity, cargoCapacityTons);
                    
                    // Log airplane purpose and details
                    String purposeDetails = "";
                    // Format cargo tonnage to 1 decimal place
                    String formattedCargoTons = String.format("%.1f", cargoCapacityTons);
                    
                    if (purpose.equals(Airplane.PURPOSE_PASSENGERS)) {
                        purposeDetails = "passenger flight carrying " + passengerCapacity + " people";
                    } else if (purpose.equals(Airplane.PURPOSE_CARGO)) {
                        purposeDetails = "cargo flight carrying " + formattedCargoTons + " tons of goods";
                    } else {
                        purposeDetails = "mixed flight carrying " + passengerCapacity + " people and " + 
                                         formattedCargoTons + " tons of goods";
                    }
                    FlightEventLogger.log(airplaneId, "New " + model + " " + purposeDetails);
                    
                    // Check if this should be an emergency (only for landing)
                    if (operation.equals("landing") && random.nextInt(100) < EMERGENCY_PROBABILITY) {
                        // This will be an emergency landing
                        airplane.setStatus(Airplane.STATUS_EMERGENCY);
                        FlightEventLogger.logEmergency(airplaneId, "Declaring emergency landing!");
                    }
                    
                    // Select a controller with the lowest load
                    AirTrafficController selectedController = selectController(controllers);
                    
                    // Assign airplane to controller
                    selectedController.assignAirplane(airplane);
                }
                
            } catch (Exception e) {
                FlightEventLogger.logWarning("SYSTEM", "Error generating airplanes: " + e.getMessage());
                e.printStackTrace();
            }
        }, 2, AIRPLANE_SPAWN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Schedule occasional airport emergency (separate from airplane emergencies)
        ScheduledExecutorService emergencyGenerator = Executors.newScheduledThreadPool(1);
        emergencyGenerator.scheduleAtFixedRate(() -> {
            if (!isRunning.get() || random.nextInt(100) >= 20) { // 20% chance every 30 seconds
                return;
            }
            
            // Generate a random emergency reason
            String[] emergencyReasons = {
                "Security breach", "Weather conditions", "Runway intrusion", 
                "Equipment failure", "Wildlife on runway"
            };
            String reason = emergencyReasons[random.nextInt(emergencyReasons.length)];
            
            // Declare airport emergency
            airport.declareEmergency(reason);
            
        }, 30, 30, TimeUnit.SECONDS);
        
        // Countdown timer for simulation duration
        System.out.println("\nSimulation will run for " + SIMULATION_DURATION_SECONDS + " seconds.\n");
        System.out.println("Press ENTER at any time to end the simulation early.\n");
        
        // Start a separate thread for accepting user input to end simulation early
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            try {
                scanner.nextLine(); // Wait for ENTER
                isRunning.set(false);
                System.out.println("\nReceived shutdown signal. Ending simulation...");
            } finally {
                // Properly close the scanner to prevent resource leak
                scanner.close();
            }
        }, "User-Input-Thread").start();
        
        // Sleep for the simulation duration
        try {
            for (int i = 0; i < SIMULATION_DURATION_SECONDS && isRunning.get(); i++) {
                Thread.sleep(1000);
                if (i > 0 && i % 30 == 0) {
                    int remainingTime = SIMULATION_DURATION_SECONDS - i;
                    System.out.println("\nSimulation status: " + i + " seconds elapsed, " + 
                                     remainingTime + " seconds remaining.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // End the simulation
        isRunning.set(false);
        System.out.println("\nShutting down simulation...");
        
        // Shutdown executors
        spawner.shutdown();
        emergencyGenerator.shutdown();
        
        // End controller shifts
        for (AirTrafficController controller : controllers) {
            controller.endShift();
        }
        
        // Wait for controller threads to finish
        try {
            for (Thread thread : controllerThreads) {
                thread.join(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Shutdown airport
        airport.shutdown();
        
        // Shutdown event executor
        eventExecutor.shutdown();
        
        System.out.println("\nSimulation complete. Check flight_log.txt for complete logs.");
    }
    
    // Generate a unique airplane ID
    private static String generateAirplaneId() {
        // Format: Airlines code (2 letters) + Flight number (4 digits)
        String[] airlineCodes = {"AA", "BA", "DL", "UA", "LH", "AF", "EK", "SQ", "CX"};
        Random random = new Random();
        String airlineCode = airlineCodes[random.nextInt(airlineCodes.length)];
        int flightNumber = 1000 + random.nextInt(9000); // 1000-9999
        return airlineCode + flightNumber;
    }
    
    // Find the controller with the lowest workload
    private static AirTrafficController selectController(AirTrafficController[] controllers) {
        // Simple round-robin selection for now
        Random random = new Random();
        return controllers[random.nextInt(controllers.length)];
    }
}



