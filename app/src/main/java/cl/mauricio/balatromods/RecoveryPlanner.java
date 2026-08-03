package cl.mauricio.balatromods;

import java.util.ArrayList;
import java.util.List;

public final class RecoveryPlanner {
    private RecoveryPlanner() {
    }

    public static List<String> nextTest(List<String> suspects) {
        if (suspects == null || suspects.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, (suspects.size() + 1) / 2);
        return List.copyOf(suspects.subList(0, size));
    }

    public static List<String> afterResult(
            List<String> suspects,
            List<String> testing,
            boolean gameOpened
    ) {
        if (suspects == null || suspects.size() <= 1) {
            return suspects == null ? List.of() : List.copyOf(suspects);
        }
        if (!gameOpened) {
            return testing == null ? List.of() : List.copyOf(testing);
        }
        List<String> remaining = new ArrayList<>(suspects);
        if (testing != null) {
            remaining.removeAll(testing);
        }
        return List.copyOf(remaining);
    }

    public static int estimatedSteps(int candidates) {
        if (candidates <= 1) {
            return 1;
        }
        return (int) Math.ceil(Math.log(candidates) / Math.log(2));
    }
}
