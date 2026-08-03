package cl.mauricio.balatromods;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class RecoveryPlannerTest {
    @Test
    public void splitsCandidatesAndKeepsFailingHalf() {
        List<String> suspects = List.of("A", "B", "C", "D", "E");
        List<String> testing = RecoveryPlanner.nextTest(suspects);

        assertEquals(List.of("A", "B", "C"), testing);
        assertEquals(
                List.of("A", "B", "C"),
                RecoveryPlanner.afterResult(suspects, testing, false)
        );
    }

    @Test
    public void keepsDisabledHalfWhenGameOpens() {
        List<String> suspects = List.of("A", "B", "C", "D");
        List<String> testing = List.of("A", "B");

        assertEquals(
                List.of("C", "D"),
                RecoveryPlanner.afterResult(suspects, testing, true)
        );
        assertEquals(2, RecoveryPlanner.estimatedSteps(suspects.size()));
    }
}
