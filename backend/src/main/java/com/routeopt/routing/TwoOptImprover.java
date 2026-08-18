package com.routeopt.routing;

import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2-opt local search: repeatedly reverse a sub-segment of the tour and keep the reversal if it
 * lowers the cost, until a full pass finds no improvement.
 *
 * <p><strong>Why the cost is recomputed from scratch on every candidate.</strong> The textbook
 * 2-opt shortcut evaluates a move in O(1) by adding and subtracting the four affected edges. That
 * shortcut is only valid when the objective is a sum over edges of a symmetric matrix. Ours is not:
 * lateness depends on the arrival clock, which every earlier stop influences, and the priority term
 * depends on each stop's position in the sequence. Reversing a segment shifts both. So each
 * candidate costs a full O(n) evaluation and a pass is O(n³) overall — comfortably fast for the
 * n ≲ 100 stops a single vehicle can serve in a day, and correct, which the shortcut would not be.
 * Do not "optimize" this back into the four-edge form.
 */
public final class TwoOptImprover {

    private static final Logger log = LoggerFactory.getLogger(TwoOptImprover.class);

    private TwoOptImprover() {}

    /**
     * @param sequence the starting tour; not modified
     * @param maxIterations safety valve on the number of improving moves applied
     * @return an improved tour whose cost is never worse than the input's
     */
    public static int[] improve(
            int[] sequence,
            List<RouteStop> stops,
            DistanceMatrix matrix,
            LocalTime departureTime,
            RouteEvaluator evaluator,
            int maxIterations) {

        int n = sequence.length;
        int[] best = sequence.clone();
        double bestCost = evaluator.evaluate(best, stops, matrix, departureTime).cost();

        if (n < 3) {
            return best;
        }

        int applied = 0;
        boolean improved = true;

        while (improved && applied < maxIterations) {
            improved = false;

            for (int i = 0; i < n - 1 && applied < maxIterations; i++) {
                for (int j = i + 1; j < n; j++) {
                    int[] candidate = reverseSegment(best, i, j);
                    double candidateCost =
                            evaluator.evaluate(candidate, stops, matrix, departureTime).cost();

                    if (candidateCost < bestCost - 1e-9) {
                        best = candidate;
                        bestCost = candidateCost;
                        improved = true;
                        applied++;
                        break; // restart the scan from the new tour
                    }
                }
            }
        }

        if (applied >= maxIterations) {
            log.warn("2-opt stopped at the {}-move safety limit; the tour may not be locally optimal",
                    maxIterations);
        }
        log.debug("2-opt applied {} improving move(s), final cost {}", applied, bestCost);
        return best;
    }

    private static int[] reverseSegment(int[] sequence, int from, int to) {
        int[] copy = sequence.clone();
        int left = from;
        int right = to;
        while (left < right) {
            int tmp = copy[left];
            copy[left] = copy[right];
            copy[right] = tmp;
            left++;
            right--;
        }
        return copy;
    }
}
