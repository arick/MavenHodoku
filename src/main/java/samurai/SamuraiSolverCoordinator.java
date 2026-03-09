/*
 * SamuraiSolverCoordinator.java
 *
 * Orchestrates solving of a SamuraiSudoku by running the existing single-grid
 * SudokuSolver on each of the five sub-grids in turn and propagating overlap
 * cells between grids after each round.
 */
package samurai;

import solver.SudokuSolver;
import solver.SudokuSolverFactory;
import solver.SudokuStepFinder;
import sudoku.SolutionStep;
import sudoku.Sudoku2;

/**
 * Coordinates solving across the five grids of a {@link SamuraiSudoku}.
 * <p>
 * The strategy is iterative: each round applies the full single-grid solver to
 * every sub-grid, then pushes any newly-solved overlap cells into neighboring
 * grids.  Rounds repeat until the puzzle is solved or no grid makes progress.
 */
public class SamuraiSolverCoordinator {

    /** One independent solver instance per sub-grid. */
    private final SudokuSolver[] solvers = new SudokuSolver[SamuraiSudoku.NUM_GRIDS];

    /** One independent step finder per sub-grid (for single-step solving). */
    private final SudokuStepFinder[] stepFinders = new SudokuStepFinder[SamuraiSudoku.NUM_GRIDS];

    /** Maximum rounds before giving up. */
    private static final int MAX_ROUNDS = 50;

    public SamuraiSolverCoordinator() {
        for (int i = 0; i < SamuraiSudoku.NUM_GRIDS; i++) {
            solvers[i] = SudokuSolverFactory.getDefaultSolverInstance();
            stepFinders[i] = new SudokuStepFinder();
        }
    }

    /**
     * Attempts to fully solve the given {@link SamuraiSudoku} in place.
     *
     * @param samurai the puzzle to solve
     * @return {@code true} if the puzzle was fully solved, {@code false} if
     *         progress stalled before completion
     */
    public boolean solve(SamuraiSudoku samurai) {
        // Ensure overlaps are consistent before we start
        samurai.syncAllOverlaps();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (samurai.isSolved()) {
                return true;
            }

            boolean anyProgress = false;

            for (int g = 0; g < SamuraiSudoku.NUM_GRIDS; g++) {
                Sudoku2 grid = samurai.getGrid(g);
                int before = grid.getUnsolvedCellsAnz();

                solvers[g].setSudoku(grid);
                solvers[g].solve();

                int after = grid.getUnsolvedCellsAnz();
                if (after < before) {
                    anyProgress = true;
                    // Push newly solved overlap cells into neighboring grids,
                    // then intersect candidate sets across the overlap boundary
                    samurai.syncOverlapFromGrid(g);
                    samurai.syncOverlapCandidates();
                }
            }

            if (!anyProgress) {
                // No grid made progress; puzzle may require inter-grid deduction
                // or is genuinely unsolvable with current techniques
                break;
            }
        }

        return samurai.isSolved();
    }

    /**
     * Applies a single solving step to the first sub-grid that still has
     * unsolved cells, then syncs overlap cells.
     * <p>
     * Uses {@link SudokuStepFinder} to find and apply one hint step.
     *
     * @param samurai the puzzle
     * @return {@code true} if a step was applied
     */
    public boolean solveStep(SamuraiSudoku samurai) {
        for (int g = 0; g < SamuraiSudoku.NUM_GRIDS; g++) {
            Sudoku2 grid = samurai.getGrid(g);
            if (grid.getUnsolvedCellsAnz() == 0) {
                continue;
            }
            int before = grid.getUnsolvedCellsAnz();

            SudokuStepFinder finder = stepFinders[g];
            finder.setSudoku(grid);
            // Find the next applicable step in difficulty order
            SolutionStep step = finder.getStep(sudoku.SolutionType.FULL_HOUSE);
            if (step == null) {
                step = finder.getStep(sudoku.SolutionType.NAKED_SINGLE);
            }
            if (step == null) {
                step = finder.getStep(sudoku.SolutionType.HIDDEN_SINGLE);
            }
            if (step != null) {
                finder.doStep(step);
            }

            if (grid.getUnsolvedCellsAnz() < before) {
                samurai.syncOverlapFromGrid(g);
                samurai.syncOverlapCandidates();
                return true;
            }
        }
        return false;
    }
}
