public class Airplane {
    // Constants for statuses
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_TAXIING = "TAXIING";
    public static final String STATUS_TAKING_OFF = "TAKING_OFF";
    public static final String STATUS_IN_AIR = "IN_AIR";
    public static final String STATUS_APPROACHING = "APPROACHING";
    public static final String STATUS_LANDING = "LANDING";
    public static final String STATUS_LANDED = "LANDED";
    public static final String STATUS_AT_GATE = "AT_GATE";
    public static final String STATUS_EMERGENCY = "EMERGENCY";
    
    // Constants for flight purpose
    public static final String PURPOSE_PASSENGERS = "PASSENGERS";
    public static final String PURPOSE_CARGO = "CARGO";
    public static final String PURPOSE_MIXED = "MIXED";
    
    private final String id;
    private final String operationType; // "landing" or "departure"
    private final String purpose; // flight purpose
    private volatile String status; // current airplane status
    private final int passengerCapacity;
    private final double cargoCapacityTons;
    private final String model;
    
    public Airplane(String id, String operationType, String purpose, String model, 
                   int passengerCapacity, double cargoCapacityTons) {
        this.id = id;
        this.operationType = operationType;
        this.purpose = purpose;
        this.model = model;
        this.passengerCapacity = passengerCapacity;
        this.cargoCapacityTons = cargoCapacityTons;
        
        // Set initial status based on operation
        this.status = operationType.equals("landing") ? STATUS_APPROACHING : STATUS_AT_GATE;
    }
    
    // constructor 
    public Airplane(String id, String operationType) {
        this(id, operationType, PURPOSE_PASSENGERS, "Generic Model", 100, 1.0);
    }

    public String getId() {
        return id;
    }

    public String getOperationType() {
        return operationType;
    }
    
    public String getPurpose() {
        return purpose;
    }
    
    public String getStatus() {
        return status;
    }
    
    public synchronized void setStatus(String newStatus) {
        this.status = newStatus;
        FlightEventLogger.log(id, "Status changed to " + newStatus);
    }
    
    public String getModel() {
        return model;
    }
    
    public int getPassengerCapacity() {
        return passengerCapacity;
    }
    
    public double getCargoCapacityTons() {
        return cargoCapacityTons;
    }
    
    @Override
    public String toString() {
        return "Airplane{id='" + id + "', model='" + model + "', purpose='" + purpose + 
               "', status='" + status + "', operation='" + operationType + "'}";
    }
}

