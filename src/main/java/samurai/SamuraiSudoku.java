/*
 * SamuraiSudoku.java
 *
 * Wrapper for five linked Sudoku2 instances arranged in the classic Samurai
 * pattern: one center grid and four corner grids, each sharing a single 3x3
 * block with the center.
 *
 * Canvas layout (each grid is 9x9, the 21x21 canvas uses 0-based row/col):
 *
 *   Grid 0 (top-left)    starts at canvas (row=0,  col=0)
 *   Grid 1 (top-right)   starts at canvas (row=0,  col=12)
 *   Grid 2 (center)      starts at canvas (row=6,  col=6)
 *   Grid 3 (bottom-left) starts at canvas (row=12, col=0)
 *   Grid 4 (bottom-right)starts at canvas (row=12, col=12)
 *
 * Overlap blocks (corner block index -> center block index):
 *   Grid 0 block 8 <-> Grid 2 block 0
 *   Grid 1 block 6 <-> Grid 2 block 2
 *   Grid 3 block 2 <-> Grid 2 block 6
 *   Grid 4 block 0 <-> Grid 2 block 8
 */
package samurai;

import sudoku.Sudoku2;

/**
 * Holds five {@link Sudoku2} instances that form a Samurai Sudoku puzzle.
 * <p>
 * Cell addressing uses per-grid indices (0–80) together with a grid index
 * (0–4).  All mutations must go through {@link #setCellValue} so that overlap
 * cells are automatically kept in sync between neighboring grids.
 */
public class SamuraiSudoku implements Cloneable {

    /** Number of sub-grids in a Samurai puzzle. */
    public static final int NUM_GRIDS = 5;

    /**
     * Grid index constants – use these for readability instead of raw ints.
     */
    public static final int GRID_TOP_LEFT     = 0;
    public static final int GRID_TOP_RIGHT    = 1;
    public static final int GRID_CENTER       = 2;
    public static final int GRID_BOTTOM_LEFT  = 3;
    public static final int GRID_BOTTOM_RIGHT = 4;

    /**
     * Canvas origin (row, col) for each grid on the 21x21 Samurai canvas.
     * Index: [gridIndex][0=row, 1=col]
     */
    public static final int[][] CANVAS_ORIGINS = {
        {  0,  0 },   // Grid 0: top-left
        {  0, 12 },   // Grid 1: top-right
        {  6,  6 },   // Grid 2: center
        { 12,  0 },   // Grid 3: bottom-left
        { 12, 12 }    // Grid 4: bottom-right
    };

    /**
     * The four overlap relationships.  Each entry is:
     *   { cornerGridIndex, centerGridIndex, cornerBlockIndex, centerBlockIndex }
     *
     * The cell-level mapping is always position-for-position within those blocks,
     * i.e. Sudoku2.BLOCKS[cornerBlockIndex][p] <-> Sudoku2.BLOCKS[centerBlockIndex][p]
     * for p = 0..8.
     */
    private static final int[][] OVERLAP_DEFS = {
        { GRID_TOP_LEFT,     GRID_CENTER, 8, 0 },
        { GRID_TOP_RIGHT,    GRID_CENTER, 6, 2 },
        { GRID_BOTTOM_LEFT,  GRID_CENTER, 2, 6 },
        { GRID_BOTTOM_RIGHT, GRID_CENTER, 0, 8 }
    };

    /**
     * Precomputed overlap peer table.
     * {@code overlapPeer[gridIndex][cellIndex]} is {@code -1} if the cell is not
     * an overlap cell, or encodes {@code (peerGrid << 7) | peerCell} otherwise.
     * 7 bits is enough for cell indices 0–80.
     */
    private static final int[][] OVERLAP_PEER;

    static {
        OVERLAP_PEER = new int[NUM_GRIDS][Sudoku2.LENGTH];
        for (int[] row : OVERLAP_PEER) {
            java.util.Arrays.fill(row, -1);
        }
        for (int[] def : OVERLAP_DEFS) {
            int cornerGrid  = def[0];
            int centerGrid  = def[1];
            int cornerBlock = def[2];
            int centerBlock = def[3];
            int[] cornerCells = Sudoku2.BLOCKS[cornerBlock];
            int[] centerCells = Sudoku2.BLOCKS[centerBlock];
            for (int p = 0; p < cornerCells.length; p++) {
                int cc = cornerCells[p];
                int ce = centerCells[p];
                // Each overlap cell knows about its peer in the other grid
                OVERLAP_PEER[cornerGrid][cc] = (centerGrid << 7) | ce;
                OVERLAP_PEER[centerGrid][ce] = (cornerGrid << 7) | cc;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    /** The five sub-grids. */
    private final Sudoku2[] grids = new Sudoku2[NUM_GRIDS];

    /** Guard flag to prevent recursive overlap propagation. */
    private boolean propagating = false;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SamuraiSudoku() {
        for (int i = 0; i < NUM_GRIDS; i++) {
            grids[i] = new Sudoku2();
        }
    }

    // -------------------------------------------------------------------------
    // Grid access
    // -------------------------------------------------------------------------

    /**
     * Returns the sub-grid for the given grid index (0–4).
     */
    public Sudoku2 getGrid(int gridIndex) {
        return grids[gridIndex];
    }

    // -------------------------------------------------------------------------
    // Cell mutation
    // -------------------------------------------------------------------------

    /**
     * Sets a cell value in the specified grid and propagates to the overlap peer
     * grid if this cell is an overlap cell.
     *
     * @param gridIndex grid index (0–4)
     * @param cellIndex cell index within the grid (0–80)
     * @param value     digit to set (1–9), or 0 to clear
     * @param isFixed   {@code true} if this is a given (cannot be changed by the player)
     * @return {@code false} if the grid becomes invalid after the change
     */
    public boolean setCellValue(int gridIndex, int cellIndex, int value, boolean isFixed) {
        boolean valid = grids[gridIndex].setCell(cellIndex, value, isFixed);

        if (!propagating) {
            int peer = OVERLAP_PEER[gridIndex][cellIndex];
            if (peer >= 0) {
                int peerGrid = peer >> 7;
                int peerCell = peer & 0x7F;
                // Only propagate if the peer cell doesn't already hold this value
                if (grids[peerGrid].getValue(peerCell) != value) {
                    propagating = true;
                    try {
                        grids[peerGrid].setCell(peerCell, value, isFixed);
                    } finally {
                        propagating = false;
                    }
                }
            }
            // After setting a value, candidate sets in the overlap region may have
            // diverged (the grid's solver removes candidates from buddies, but the
            // peer grid doesn't know about non-overlap-cell changes).  Intersect
            // both sides so each only keeps candidates that are valid in both grids.
            syncOverlapCandidates();
        }

        return valid;
    }

    /**
     * For every pair of overlap cells, removes any candidate from either side
     * that has already been eliminated on the other side.  Both grids must
     * agree: if a digit is gone in one grid's overlap cell it must be gone in
     * the peer's corresponding cell too.
     */
    public void syncOverlapCandidates() {
        for (int[] def : OVERLAP_DEFS) {
            int cornerGrid  = def[0];
            int centerGrid  = def[1];
            int cornerBlock = def[2];
            int centerBlock = def[3];
            int[] cornerCells = Sudoku2.BLOCKS[cornerBlock];
            int[] centerCells = Sudoku2.BLOCKS[centerBlock];
            for (int p = 0; p < cornerCells.length; p++) {
                int cc = cornerCells[p];
                int ce = centerCells[p];
                // Skip cells that already have a value set
                if (grids[cornerGrid].getValue(cc) != 0 || grids[centerGrid].getValue(ce) != 0) {
                    continue;
                }
                for (int cand = 1; cand <= 9; cand++) {
                    boolean inCorner = grids[cornerGrid].isCandidate(cc, cand);
                    boolean inCenter = grids[centerGrid].isCandidate(ce, cand);
                    if (inCorner && !inCenter) {
                        grids[cornerGrid].setCandidate(cc, cand, false);
                    } else if (!inCorner && inCenter) {
                        grids[centerGrid].setCandidate(ce, cand, false);
                    }
                }
            }
        }
    }

    /**
     * Returns {@code true} if the given cell is an overlap cell shared between
     * two grids.
     */
    public boolean isOverlapCell(int gridIndex, int cellIndex) {
        return OVERLAP_PEER[gridIndex][cellIndex] >= 0;
    }

    /**
     * Returns the peer grid index for an overlap cell, or {@code -1} if it is
     * not an overlap cell.
     */
    public int getOverlapPeerGrid(int gridIndex, int cellIndex) {
        int peer = OVERLAP_PEER[gridIndex][cellIndex];
        return peer < 0 ? -1 : (peer >> 7);
    }

    /**
     * Returns the peer cell index (within the peer grid) for an overlap cell,
     * or {@code -1} if it is not an overlap cell.
     */
    public int getOverlapPeerCell(int gridIndex, int cellIndex) {
        int peer = OVERLAP_PEER[gridIndex][cellIndex];
        return peer < 0 ? -1 : (peer & 0x7F);
    }

    // -------------------------------------------------------------------------
    // Overlap synchronization
    // -------------------------------------------------------------------------

    /**
     * Pushes all currently-set values in the overlap blocks of {@code gridIndex}
     * into the corresponding peer grid.  Call this after bulk-loading a grid
     * (e.g. via {@link Sudoku2#setSudoku(String)}) to ensure overlap cells are
     * consistent.
     *
     * @param gridIndex source grid whose overlap cells are pushed outward
     */
    public void syncOverlapFromGrid(int gridIndex) {
        for (int cellIndex = 0; cellIndex < Sudoku2.LENGTH; cellIndex++) {
            int peer = OVERLAP_PEER[gridIndex][cellIndex];
            if (peer < 0) {
                continue;
            }
            int peerGrid = peer >> 7;
            int peerCell = peer & 0x7F;
            int value = grids[gridIndex].getValue(cellIndex);
            if (value != 0 && grids[peerGrid].getValue(peerCell) != value) {
                boolean isFixed = grids[gridIndex].isFixed(cellIndex);
                propagating = true;
                try {
                    grids[peerGrid].setCell(peerCell, value, isFixed);
                } finally {
                    propagating = false;
                }
            }
        }
    }

    /**
     * Pushes overlaps from every grid outward.  Call once after loading all
     * five grids to ensure full consistency.
     */
    public void syncAllOverlaps() {
        for (int i = 0; i < NUM_GRIDS; i++) {
            syncOverlapFromGrid(i);
        }
        // Intersect candidate sets so both sides of every overlap agree
        syncOverlapCandidates();
    }

    // -------------------------------------------------------------------------
    // Puzzle status
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when all five grids are fully and correctly solved.
     */
    public boolean isSolved() {
        for (Sudoku2 grid : grids) {
            if (grid.getUnsolvedCellsAnz() != 0) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    /**
     * Serializes the puzzle as five pipe-separated 81-character strings
     * (givens only – dots for empty cells, digits for fixed cells).
     * Format: {@code SAMURAI:1.0\n<g0>|<g1>|<g2>|<g3>|<g4>}
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder("SAMURAI:1.0\n");
        for (int i = 0; i < NUM_GRIDS; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(grids[i].getSudoku(sudoku.ClipboardMode.CLUES_ONLY));
        }
        return sb.toString();
    }

    /**
     * Reconstructs a {@link SamuraiSudoku} from a string produced by
     * {@link #serialize()}.
     *
     * @throws IllegalArgumentException if the string is not a valid Samurai save
     */
    public static SamuraiSudoku deserialize(String data) {
        if (data == null || !data.startsWith("SAMURAI:1.0")) {
            throw new IllegalArgumentException("Not a valid Samurai Sudoku save file.");
        }
        String[] lines = data.split("\n", 2);
        if (lines.length < 2) {
            throw new IllegalArgumentException("Malformed Samurai save: missing grid data.");
        }
        String[] gridStrings = lines[1].split("\\|");
        if (gridStrings.length != NUM_GRIDS) {
            throw new IllegalArgumentException(
                "Expected " + NUM_GRIDS + " grids, found " + gridStrings.length);
        }
        SamuraiSudoku s = new SamuraiSudoku();
        for (int i = 0; i < NUM_GRIDS; i++) {
            s.grids[i].setSudoku(gridStrings[i].trim());
        }
        s.syncAllOverlaps();
        return s;
    }

    // -------------------------------------------------------------------------
    // Cloneable
    // -------------------------------------------------------------------------

    @Override
    public SamuraiSudoku clone() {
        try {
            SamuraiSudoku copy = (SamuraiSudoku) super.clone();
            // Sudoku2 is Cloneable; clone each sub-grid independently
            for (int i = 0; i < NUM_GRIDS; i++) {
                copy.grids[i] = (Sudoku2) grids[i].clone();
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("SamuraiSudoku clone failed", e);
        }
    }
}
