import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
public class AirTrafficController implements Runnable {

    private final String name;
    private final Airport airport;
    private final BlockingQueue<Airplane> airplaneQueue = new LinkedBlockingQueue<>();

    public AirTrafficController(String name, Airport airport) {
        this.name = name;
        this.airport = airport;
    }

    public void assignAirplane(Airplane airplane) {
        try {
            airplaneQueue.put(airplane);
            FlightEventLogger.log(airplane.getId(), "assigned to Controller " + name);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Airplane airplane = airplaneQueue.take(); // wait for next airplane
                FlightEventLogger.log(airplane.getId(), "cleared by Controller " + name + " for landing check.");
                boolean success = false;
                while (!success) {
                    success = airport.requestOperation(airplane.getId(), airplane.getOperationType());
                    if (!success) {
                        FlightEventLogger.log(airplane.getId(), "no lanes free for " + airplane.getOperationType() + ". Retrying soon...");
                        Thread.sleep(1000);
                    }
                }
                FlightEventLogger.log(airplane.getId(), "completed " + airplane.getOperationType() + " via Controller " + name);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
