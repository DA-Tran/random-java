package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;

/**
 * A solve broken into explained stages.
 *
 * <p>This is the shape that makes teaching possible. A bare move list tells you
 * what to do but not why; each {@link Step} carries the stage it belongs to, a
 * plain-English reason, and the cube state afterwards, so the same data drives
 * "solve it for me", "show me one step at a time" and "give me a hint".
 *
 * @param method   the technique used, e.g. "Beginner layer-by-layer"
 * @param steps    the stages in order
 * @param moves    every move in the whole solve
 * @param scramble the scramble this solves, when known
 */
public record Solution(String method, List<Step> steps, String moves, String scramble) {

    /** One explained stage of a solve. */
    public record Step(
            String stage,
            String explanation,
            String moves,
            int moveCount,
            String algorithm,
            String facelets) {
    }

    public int moveCount() {
        int total = 0;
        for (Step step : steps) {
            total += step.moveCount();
        }
        return total;
    }

    /** Builds a solution while a solver runs. */
    public static final class Builder {
        private final String method;
        private final String scramble;
        private final int size;
        private final List<Step> steps = new ArrayList<>();

        public Builder(String method, String scramble, int size) {
            this.method = method;
            this.scramble = scramble;
            this.size = size;
        }

        /**
         * Records a stage.
         *
         * @param stage       short stage name shown as a heading
         * @param explanation why this stage does what it does
         * @param moves       the moves performed
         * @param algorithm   the named algorithm used, or blank when intuitive
         * @param cube        the cube after the stage, captured for stepping
         */
        public void add(String stage, String explanation, List<Move> moves,
                String algorithm, FaceletCube cube) {
            List<Move> tidied = Move.tidy(moves);
            steps.add(new Step(stage, explanation, Move.format(tidied), tidied.size(),
                    algorithm == null ? "" : algorithm, cube.toFacelets()));
        }

        /** Merges consecutive steps of the same stage so the output reads as stages. */
        public Solution build() {
            List<Step> merged = new ArrayList<>();
            for (Step step : steps) {
                if (step.moveCount() == 0) {
                    continue;
                }
                if (!merged.isEmpty()) {
                    Step last = merged.get(merged.size() - 1);
                    if (last.stage().equals(step.stage())) {
                        // Re-tidy across the join, so a stage that ends on D and
                        // the next that starts on D reads as D2 rather than D D.
                        List<Move> joined = Move.tidy(
                                Move.parse((last.moves() + " " + step.moves()).trim(), size));
                        merged.set(merged.size() - 1, new Step(
                                last.stage(),
                                last.explanation(),
                                Move.format(joined),
                                joined.size(),
                                last.algorithm().isEmpty() ? step.algorithm() : last.algorithm(),
                                step.facelets()));
                        continue;
                    }
                }
                merged.add(step);
            }
            StringBuilder all = new StringBuilder();
            for (Step step : merged) {
                if (all.length() > 0 && !step.moves().isEmpty()) {
                    all.append(' ');
                }
                all.append(step.moves());
            }
            return new Solution(method, List.copyOf(merged), all.toString().trim(), scramble);
        }
    }
}
