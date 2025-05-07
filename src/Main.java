import java.util.Random;//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        Airport airport = new Airport(3); // now 3 lanes
        AirTrafficController controller1 = new AirTrafficController("CTRL-1", airport);
        AirTrafficController controller2 = new AirTrafficController("CTRL-2", airport);

        Thread controllerThread1 = new Thread(controller1, "Controller-1-Thread");
        Thread controllerThread2 = new Thread(controller2, "Controller-2-Thread");

        controllerThread1.start();
        controllerThread2.start();

        Random random = new Random();
        String[] operations = {"landing", "departure"};

        for (int i = 1; i <= 10; i++) {
            String airplaneId = "A" + i;
            String operation = operations[random.nextInt(2)];
            Airplane airplane = new Airplane(airplaneId, operation);

            // Alternate assignment between controllers
            if (i % 2 == 0) {
                controller1.assignAirplane(airplane);
            } else {
                controller2.assignAirplane(airplane);
            }

            try {
                Thread.sleep(500); // simulate staggered airplane requests
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    }



