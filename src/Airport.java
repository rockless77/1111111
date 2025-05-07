
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class Airport {
    private final List<Lane> lanes;

    public Airport(int laneCount) {
        lanes = new ArrayList<>();
        for (int i = 1; i <= laneCount; i++) {
            lanes.add(new Lane(i));
        }
    }

    public boolean requestOperation(String airplaneId, String operationType) {
        Collections.shuffle(lanes); // randomize the lane order
        for (Lane lane : lanes) {
            if (lane.useLane(airplaneId, operationType)) {
                return true;
            }
        }
        return false;
    }
}
