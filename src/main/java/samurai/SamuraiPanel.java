/*
 * SamuraiPanel.java
 *
 * A self-contained Swing panel that renders a complete Samurai Sudoku puzzle.
 * All five sub-grids are drawn directly onto a single canvas without reusing
 * SudokuPanel (which is tightly coupled to MainFrame).
 *
 * Canvas layout:  21 x 21 cells arranged in a plus/cross pattern.
 * Only positions that belong to one of the five 9x9 sub-grids are drawn;
 * the four corner "dead zones" of the 21x21 bounding box are left empty.
 */
package samurai;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import sudoku.Options;
import sudoku.Sudoku2;

/**
 * Renders all five grids of a {@link SamuraiSudoku} on one Swing panel.
 * <p>
 * Cell selection, value display, candidate display and overlap highlighting are
 * all handled here.  Mouse clicks identify the sub-grid and cell via the canvas
 * coordinate system and notify a registered {@link CellClickListener}.
 */
public class SamuraiPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Total number of cells along each axis of the 21x21 canvas. */
    private static final int CANVAS_SIZE = 21;

    /**
     * Background tint applied over the overlap 3x3 blocks to make them
     * visually distinct.
     */
    private static final Color OVERLAP_TINT = new Color(255, 230, 180, 80);

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    /**
     * Notified when the user clicks a cell.
     */
    public interface CellClickListener {
        /**
         * @param gridIndex  the sub-grid (0–4)
         * @param cellIndex  the cell within that grid (0–80)
         * @param button     mouse button (1 = left, 3 = right)
         */
        void cellClicked(int gridIndex, int cellIndex, int button);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private SamuraiSudoku samurai;
    private boolean showCandidates = true;

    /** Currently selected cell: [0]=gridIndex, [1]=cellIndex; or null. */
    private int[] selectedCell = null;

    private CellClickListener clickListener;

    // Layout (recalculated on resize)
    private int cellSize = 30;
    private int offsetX = 0;
    private int offsetY = 0;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SamuraiPanel() {
        setBackground(Options.getInstance().getDefaultCellColor());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e);
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                recalculateLayout();
                repaint();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setSamurai(SamuraiSudoku samurai) {
        this.samurai = samurai;
        repaint();
    }

    public SamuraiSudoku getSamurai() {
        return samurai;
    }

    public void setShowCandidates(boolean show) {
        this.showCandidates = show;
        repaint();
    }

    public boolean isShowCandidates() {
        return showCandidates;
    }

    public void setCellClickListener(CellClickListener listener) {
        this.clickListener = listener;
    }

    /** Clears the current cell selection and repaints. */
    public void clearSelection() {
        selectedCell = null;
        repaint();
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private void recalculateLayout() {
        int w = getWidth();
        int h = getHeight();
        // The canvas is always a square; fit it within the available space
        int size = Math.min(w, h);
        cellSize = Math.max(1, size / CANVAS_SIZE);
        int gridPixels = cellSize * CANVAS_SIZE;
        offsetX = (w - gridPixels) / 2;
        offsetY = (h - gridPixels) / 2;
    }

    /**
     * Returns the pixel X of the left edge of a canvas column.
     */
    private int px(int canvasCol) {
        return offsetX + canvasCol * cellSize;
    }

    /**
     * Returns the pixel Y of the top edge of a canvas row.
     */
    private int py(int canvasRow) {
        return offsetY + canvasRow * cellSize;
    }

    /**
     * Converts a local (row, col) within a grid to a canvas row or col.
     */
    private int canvasRow(int gridIndex, int localRow) {
        return SamuraiSudoku.CANVAS_ORIGINS[gridIndex][0] + localRow;
    }

    private int canvasCol(int gridIndex, int localCol) {
        return SamuraiSudoku.CANVAS_ORIGINS[gridIndex][1] + localCol;
    }

    // -------------------------------------------------------------------------
    // Mouse handling
    // -------------------------------------------------------------------------

    private void handleClick(MouseEvent e) {
        if (samurai == null) {
            return;
        }
        int mouseX = e.getX();
        int mouseY = e.getY();

        // Convert pixel → canvas cell
        int canvasC = (mouseX - offsetX) / cellSize;
        int canvasR = (mouseY - offsetY) / cellSize;

        // Find which sub-grid owns this canvas position
        int[] hit = canvasToGridCell(canvasR, canvasC);
        if (hit == null) {
            selectedCell = null;
            repaint();
            return;
        }

        selectedCell = hit;
        repaint();

        if (clickListener != null) {
            clickListener.cellClicked(hit[0], hit[1], e.getButton());
        }
    }

    /**
     * Maps a canvas (row, col) to {gridIndex, cellIndex}, or {@code null} if
     * the position is not inside any sub-grid.  For overlap cells the
     * lowest-numbered grid is returned.
     */
    private int[] canvasToGridCell(int canvasR, int canvasC) {
        for (int g = 0; g < SamuraiSudoku.NUM_GRIDS; g++) {
            int originRow = SamuraiSudoku.CANVAS_ORIGINS[g][0];
            int originCol = SamuraiSudoku.CANVAS_ORIGINS[g][1];
            int localRow = canvasR - originRow;
            int localCol = canvasC - originCol;
            if (localRow >= 0 && localRow < 9 && localCol >= 0 && localCol < 9) {
                int cellIndex = localRow * 9 + localCol;
                return new int[]{g, cellIndex};
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (cellSize <= 0) {
            recalculateLayout();
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (samurai == null) {
            drawEmptyGrids(g2);
            return;
        }

        // Draw each sub-grid
        for (int gi = 0; gi < SamuraiSudoku.NUM_GRIDS; gi++) {
            drawGrid(g2, gi, samurai.getGrid(gi));
        }

        // Draw overlap tint on top of all grids
        drawOverlapTints(g2);

        // Draw selected cell highlight on top of everything
        drawSelection(g2);
    }

    // -------------------------------------------------------------------------
    // Grid rendering
    // -------------------------------------------------------------------------

    private void drawEmptyGrids(Graphics2D g2) {
        for (int g = 0; g < SamuraiSudoku.NUM_GRIDS; g++) {
            drawGridBackground(g2, g, null);
            drawGridLines(g2, g);
        }
    }

    private void drawGrid(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        drawGridBackground(g2, gridIndex, grid);
        drawValues(g2, gridIndex, grid);
        if (showCandidates) {
            drawCandidates(g2, gridIndex, grid);
        }
        drawGridLines(g2, gridIndex);
    }

    private void drawGridBackground(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int block = Sudoku2.getBlock(row * 9 + col);
                Color bg = (block % 2 == 0)
                        ? Options.getInstance().getDefaultCellColor()
                        : Options.getInstance().getAlternateCellColor();
                g2.setColor(bg);
                g2.fillRect(px(canvasCol(gridIndex, col)), py(canvasRow(gridIndex, row)),
                        cellSize, cellSize);
            }
        }
    }

    private void drawValues(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        int fontSize = Math.max(8, (int) (cellSize * 0.6));
        Font fixedFont  = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        Font playerFont = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);

        for (int cellIndex = 0; cellIndex < Sudoku2.LENGTH; cellIndex++) {
            int val = grid.getValue(cellIndex);
            if (val == 0) {
                continue;
            }
            int row = cellIndex / 9;
            int col = cellIndex % 9;
            int px = px(canvasCol(gridIndex, col));
            int py = py(canvasRow(gridIndex, row));

            boolean isFixed = grid.isFixed(cellIndex);
            Color textColor = isFixed
                    ? Options.getInstance().getCellFixedValueColor()
                    : Options.getInstance().getCandidateColor();
            g2.setColor(textColor);
            g2.setFont(isFixed ? fixedFont : playerFont);

            FontMetrics fm = g2.getFontMetrics();
            String s = Integer.toString(val);
            int tx = px + (cellSize - fm.stringWidth(s)) / 2;
            int ty = py + (cellSize + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(s, tx, ty);
        }
    }

    private void drawCandidates(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        int subSize = Math.max(2, cellSize / 3);
        int fontSize = Math.max(5, (int) (subSize * 0.7));
        Font candFont = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
        g2.setFont(candFont);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Options.getInstance().getCandidateColor());

        for (int cellIndex = 0; cellIndex < Sudoku2.LENGTH; cellIndex++) {
            if (grid.getValue(cellIndex) != 0) {
                continue;
            }
            int row = cellIndex / 9;
            int col = cellIndex % 9;
            int basePx = px(canvasCol(gridIndex, col));
            int basePy = py(canvasRow(gridIndex, row));

            int[] candidates = grid.getAllCandidates(cellIndex);
            for (int cand : candidates) {
                int subRow = (cand - 1) / 3;
                int subCol = (cand - 1) % 3;
                int cx = basePx + subCol * subSize + (subSize - fm.stringWidth(String.valueOf(cand))) / 2;
                int cy = basePy + subRow * subSize + (subSize + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(String.valueOf(cand), cx, cy);
            }
        }
    }

    private void drawGridLines(Graphics2D g2, int gridIndex) {
        int originRow = SamuraiSudoku.CANVAS_ORIGINS[gridIndex][0];
        int originCol = SamuraiSudoku.CANVAS_ORIGINS[gridIndex][1];

        float thin  = Math.max(1.0f, cellSize / 20.0f);
        float thick = thin * 2.5f;

        int x0 = px(originCol);
        int y0 = py(originRow);
        int gridPx = cellSize * 9;

        // Vertical lines
        for (int col = 0; col <= 9; col++) {
            boolean isBlockBorder = (col % 3 == 0);
            Stroke s = new BasicStroke(isBlockBorder ? thick : thin);
            g2.setStroke(s);
            g2.setColor(Color.BLACK);
            int x = x0 + col * cellSize;
            g2.drawLine(x, y0, x, y0 + gridPx);
        }
        // Horizontal lines
        for (int row = 0; row <= 9; row++) {
            boolean isBlockBorder = (row % 3 == 0);
            Stroke s = new BasicStroke(isBlockBorder ? thick : thin);
            g2.setStroke(s);
            g2.setColor(Color.BLACK);
            int y = y0 + row * cellSize;
            g2.drawLine(x0, y, x0 + gridPx, y);
        }
    }

    private void drawOverlapTints(Graphics2D g2) {
        // Each overlap is a 3x3 block on the canvas; tint it
        // Overlap defs mirror SamuraiSudoku.OVERLAP_DEFS: cornerGrid, centerGrid,
        // cornerBlock, centerBlock
        int[][] overlapDefs = {
            {SamuraiSudoku.GRID_TOP_LEFT,     SamuraiSudoku.GRID_CENTER, 8, 0},
            {SamuraiSudoku.GRID_TOP_RIGHT,    SamuraiSudoku.GRID_CENTER, 6, 2},
            {SamuraiSudoku.GRID_BOTTOM_LEFT,  SamuraiSudoku.GRID_CENTER, 2, 6},
            {SamuraiSudoku.GRID_BOTTOM_RIGHT, SamuraiSudoku.GRID_CENTER, 0, 8}
        };

        g2.setColor(OVERLAP_TINT);
        for (int[] def : overlapDefs) {
            int cornerGrid  = def[0];
            int cornerBlock = def[2];
            // The corner block's local top-left cell
            int firstCell = Sudoku2.BLOCKS[cornerBlock][0];
            int localRow  = firstCell / 9;
            int localCol  = firstCell % 9;
            int x = px(canvasCol(cornerGrid, localCol));
            int y = py(canvasRow(cornerGrid, localRow));
            g2.fillRect(x, y, cellSize * 3, cellSize * 3);
        }
    }

    private void drawSelection(Graphics2D g2) {
        if (selectedCell == null) {
            return;
        }
        int gridIndex = selectedCell[0];
        int cellIndex = selectedCell[1];
        int row = cellIndex / 9;
        int col = cellIndex % 9;
        int x = px(canvasCol(gridIndex, col));
        int y = py(canvasRow(gridIndex, row));

        g2.setColor(Options.getInstance().getAktCellColor());
        g2.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);

        // Re-draw the value/candidates over the selection highlight
        Sudoku2 grid = samurai.getGrid(gridIndex);
        int val = grid.getValue(cellIndex);
        if (val != 0) {
            int fontSize = Math.max(8, (int) (cellSize * 0.6));
            boolean isFixed = grid.isFixed(cellIndex);
            Font f = new Font(Font.SANS_SERIF, isFixed ? Font.BOLD : Font.PLAIN, fontSize);
            g2.setFont(f);
            g2.setColor(isFixed
                    ? Options.getInstance().getCellFixedValueColor()
                    : Options.getInstance().getCandidateColor());
            FontMetrics fm = g2.getFontMetrics();
            String s = Integer.toString(val);
            g2.drawString(s, x + (cellSize - fm.stringWidth(s)) / 2,
                    y + (cellSize + fm.getAscent() - fm.getDescent()) / 2);
        }
    }
}
