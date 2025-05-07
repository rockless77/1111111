public class Airplane  {
    private final String id;
    private final String operationType; // "landing" or "departure"

    public Airplane(String id, String operationType) {
        this.id = id;
        this.operationType = operationType;
    }

    public String getId() {
        return id;
    }

    public String getOperationType() {
        return operationType;
    }
    }

