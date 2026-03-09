/*
 * SamuraiGenerator.java
 *
 * Generates a valid Samurai Sudoku puzzle by producing five compatible 9x9
 * sub-grids using a digit-relabeling strategy.
 *
 * Strategy:
 *   1. Generate the center grid (grid 2) with the existing SudokuGenerator.
 *   2. Solve the center grid to obtain the full solution.
 *   3. For each corner grid:
 *        a. Generate an independent 9x9 puzzle and solve it.
 *        b. Build a digit-relabeling map (bijection f: 1..9 → 1..9) that makes
 *           the corner's overlap block equal the center's corresponding block
 *           in the full solution.
 *        c. Apply f to all given values in the corner puzzle.
 *        d. Load the relabeled puzzle into the SamuraiSudoku.
 *   4. Sync overlap cells across all grids.
 *
 * This works because any digit relabeling of a valid Sudoku is still a valid
 * Sudoku.  After relabeling, the corner's overlap block values exactly match
 * the center's, so the combined Samurai is consistent.
 */
package samurai;

import generator.SudokuGeneratorFactory;
import solver.SudokuSolverFactory;
import solver.SudokuSolver;
import sudoku.ClipboardMode;
import sudoku.Sudoku2;

/**
 * Generates a playable {@link SamuraiSudoku} puzzle.
 */
public class SamuraiGenerator {

    /**
     * Corner grid definitions: {gridIndex, cornerBlockIndex, centerBlockIndex}
     * The corner's block at cornerBlockIndex must match the center's block at
     * centerBlockIndex in the full solution.
     */
    private static final int[][] CORNER_DEFS = {
        { SamuraiSudoku.GRID_TOP_LEFT,     8, 0 },
        { SamuraiSudoku.GRID_TOP_RIGHT,    6, 2 },
        { SamuraiSudoku.GRID_BOTTOM_LEFT,  2, 6 },
        { SamuraiSudoku.GRID_BOTTOM_RIGHT, 0, 8 }
    };

    /**
     * Generates a fresh Samurai Sudoku puzzle.
     *
     * @return a fully populated {@link SamuraiSudoku} ready to be played
     */
    public static SamuraiSudoku generate() {
        generator.SudokuGenerator gen =
                SudokuGeneratorFactory.getDefaultGeneratorInstance();
        SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();

        // --- Center grid ---
        Sudoku2 centerPuzzle = gen.generateSudoku(false);
        Sudoku2 centerSolution = solveToCompletion(centerPuzzle, solver);

        SamuraiSudoku samurai = new SamuraiSudoku();
        loadGrid(samurai, SamuraiSudoku.GRID_CENTER, centerPuzzle);

        // --- Corner grids ---
        for (int[] def : CORNER_DEFS) {
            int gridIndex    = def[0];
            int cornerBlock  = def[1];
            int centerBlock  = def[2];

            // Extract target values from center solution's block
            int[] targetValues = blockValues(centerSolution, centerBlock);

            // Generate a corner puzzle and solve it
            Sudoku2 cornerPuzzle   = gen.generateSudoku(false);
            Sudoku2 cornerSolution = solveToCompletion(cornerPuzzle, solver);

            // Build relabeling so cornerSolution's overlap block matches target
            int[] relabeling = buildRelabeling(cornerSolution, cornerBlock, targetValues);

            // Apply relabeling to the corner puzzle's given values
            Sudoku2 relabeled = applyRelabeling(cornerPuzzle, relabeling);

            loadGrid(samurai, gridIndex, relabeled);
        }

        // Push overlap cells from each grid into its neighbor
        samurai.syncAllOverlaps();
        return samurai;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a fully solved copy of {@code puzzle}, or the best-effort result
     * if the solver stalls (should not happen for well-formed puzzles).
     */
    private static Sudoku2 solveToCompletion(Sudoku2 puzzle, SudokuSolver solver) {
        Sudoku2 copy = puzzle.clone();
        solver.setSudoku(copy);
        solver.solve();
        return copy;
    }

    /**
     * Extracts the nine cell values from a block in a fully solved grid, in
     * the order they appear in {@link Sudoku2#BLOCKS}.
     */
    private static int[] blockValues(Sudoku2 solution, int blockIndex) {
        int[] vals = new int[9];
        for (int p = 0; p < 9; p++) {
            vals[p] = solution.getValue(Sudoku2.BLOCKS[blockIndex][p]);
        }
        return vals;
    }

    /**
     * Builds a digit relabeling array {@code f} (1-indexed, size 10) such that
     * {@code f[solution.getValue(BLOCKS[blockIndex][p])] == targetValues[p]}.
     * <p>
     * This is always possible because both the solution block and targetValues
     * contain each of 1–9 exactly once.
     */
    private static int[] buildRelabeling(Sudoku2 solution, int blockIndex,
                                         int[] targetValues) {
        int[] f = new int[10]; // f[oldDigit] = newDigit
        for (int p = 0; p < 9; p++) {
            int oldDigit = solution.getValue(Sudoku2.BLOCKS[blockIndex][p]);
            f[oldDigit] = targetValues[p];
        }
        return f;
    }

    /**
     * Returns a new {@link Sudoku2} that is a copy of {@code puzzle} with all
     * given values remapped through {@code relabeling}.
     */
    private static Sudoku2 applyRelabeling(Sudoku2 puzzle, int[] relabeling) {
        Sudoku2 result = new Sudoku2();
        for (int i = 0; i < Sudoku2.LENGTH; i++) {
            int val = puzzle.getValue(i);
            if (val != 0 && puzzle.isFixed(i)) {
                result.setCell(i, relabeling[val], true);
            }
        }
        return result;
    }

    /**
     * Loads the given values from {@code puzzle} into {@code samurai}'s
     * sub-grid at {@code gridIndex}.
     */
    private static void loadGrid(SamuraiSudoku samurai, int gridIndex,
                                  Sudoku2 puzzle) {
        String clues = puzzle.getSudoku(ClipboardMode.CLUES_ONLY);
        samurai.getGrid(gridIndex).setSudoku(clues);
    }
}
