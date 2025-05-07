public class Lane {
    private final int laneNumber;
    private boolean occupied = false;

    public Lane(int laneNumber) {
        this.laneNumber = laneNumber;
    }

    public synchronized boolean useLane(String airplaneId, String operationType) {
        if (occupied) {
            return false;
        } else {
            occupied = true;
            FlightEventLogger.log(airplaneId, "is using Lane " + laneNumber + " for " + operationType + ".");
            try {
                Thread.sleep(2000); // simulate operation
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            FlightEventLogger.log(airplaneId, "has finished " + operationType + " on Lane " + laneNumber);

            try {
                Thread.sleep(1000); // simulate cooldown for turbulence/safety
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            occupied = false;
            return true;
        }
    }
}
