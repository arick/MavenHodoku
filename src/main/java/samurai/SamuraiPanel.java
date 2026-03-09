/*
 * SamuraiPanel.java
 *
 * A self-contained Swing panel that renders a complete Samurai Sudoku puzzle
 * and accepts the same keyboard/mouse input as the original SudokuPanel.
 */
package samurai;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import sudoku.Options;
import sudoku.Sudoku2;

/**
 * Renders all five grids of a {@link SamuraiSudoku} on a single Swing panel
 * and handles keyboard and mouse input identical to the original SudokuPanel.
 *
 * <h3>Keyboard bindings</h3>
 * <ul>
 *   <li>Arrow keys — move cursor (wraps within the active sub-grid)</li>
 *   <li>Home / End — move to first / last column; Ctrl+Home/End — first / last row</li>
 *   <li>1–9 (keyboard &amp; numpad) — set cell value</li>
 *   <li>Ctrl + 1–9 — toggle candidate</li>
 *   <li>0 / Delete / Backspace — clear cell</li>
 *   <li>Enter — set naked single if only one candidate remains</li>
 * </ul>
 *
 * <h3>Mouse bindings</h3>
 * <ul>
 *   <li>Left-click — select cell (requests keyboard focus)</li>
 *   <li>Right-click — toggle candidate under cursor (if cell is empty)</li>
 * </ul>
 */
public class SamuraiPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Total cells along each axis of the 21×21 canvas. */
    private static final int CANVAS_SIZE = 21;

    /** Translucent tint drawn over overlap 3×3 blocks. */
    private static final Color OVERLAP_TINT = new Color(255, 230, 180, 80);

    /** Overlap definitions: {cornerGridIndex, centerGridIndex, cornerBlockIndex, centerBlockIndex} */
    private static final int[][] OVERLAP_DEFS = {
        { SamuraiSudoku.GRID_TOP_LEFT,     SamuraiSudoku.GRID_CENTER, 8, 0 },
        { SamuraiSudoku.GRID_TOP_RIGHT,    SamuraiSudoku.GRID_CENTER, 6, 2 },
        { SamuraiSudoku.GRID_BOTTOM_LEFT,  SamuraiSudoku.GRID_CENTER, 2, 6 },
        { SamuraiSudoku.GRID_BOTTOM_RIGHT, SamuraiSudoku.GRID_CENTER, 0, 8 }
    };

    // -------------------------------------------------------------------------
    // Key-code table matching SudokuPanel (handles laptops that send keyChar)
    // -------------------------------------------------------------------------
    private static final int[] KEY_CODES = {
        KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3,
        KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7,
        KeyEvent.VK_8, KeyEvent.VK_9
    };

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private SamuraiSudoku samurai;
    private boolean showCandidates = true;

    /** Active cell: [0] = gridIndex (0–4), [1] = cellIndex (0–80). Null when nothing selected. */
    private int[] activeCell = null;

    // Layout (recalculated on resize)
    private int cellSize  = 30;
    private int offsetX   = 0;
    private int offsetY   = 0;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SamuraiPanel() {
        setBackground(Options.getInstance().getDefaultCellColor());
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handleMousePressed(e);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPressed(e);
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
        activeCell = null;
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

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private void recalculateLayout() {
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        cellSize = Math.max(1, size / CANVAS_SIZE);
        int gridPixels = cellSize * CANVAS_SIZE;
        offsetX = (w - gridPixels) / 2;
        offsetY = (h - gridPixels) / 2;
    }

    private int px(int canvasCol) { return offsetX + canvasCol * cellSize; }
    private int py(int canvasRow) { return offsetY + canvasRow * cellSize; }

    private int canvasRow(int gridIndex, int localRow) {
        return SamuraiSudoku.CANVAS_ORIGINS[gridIndex][0] + localRow;
    }
    private int canvasCol(int gridIndex, int localCol) {
        return SamuraiSudoku.CANVAS_ORIGINS[gridIndex][1] + localCol;
    }

    // -------------------------------------------------------------------------
    // Mouse handling
    // -------------------------------------------------------------------------

    private void handleMousePressed(MouseEvent e) {
        if (samurai == null) return;

        int canvasC = (e.getX() - offsetX) / cellSize;
        int canvasR = (e.getY() - offsetY) / cellSize;
        int[] hit = canvasToGridCell(canvasR, canvasC);

        if (hit == null) {
            activeCell = null;
            repaint();
            return;
        }

        activeCell = hit;
        repaint();

        // Right-click on an empty cell: toggle the candidate under the cursor
        if (e.getButton() == MouseEvent.BUTTON3) {
            int gridIndex = hit[0];
            int cellIndex = hit[1];
            Sudoku2 grid = samurai.getGrid(gridIndex);
            if (grid.getValue(cellIndex) == 0) {
                // Determine which candidate sub-cell was clicked
                int cand = candidateAtPixel(e.getX(), e.getY(), gridIndex, cellIndex);
                if (cand >= 1 && cand <= 9) {
                    toggleCandidate(gridIndex, cellIndex, cand);
                }
            }
        }
    }

    /**
     * Returns which candidate (1–9) the pixel (px, py) falls on within
     * the 3×3 candidate grid of a cell, or -1 if none.
     */
    private int candidateAtPixel(int mouseX, int mouseY, int gridIndex, int cellIndex) {
        int row = cellIndex / 9;
        int col = cellIndex % 9;
        int cellPx = px(canvasCol(gridIndex, col));
        int cellPy = py(canvasRow(gridIndex, row));
        int subSize = Math.max(1, cellSize / 3);
        int dx = mouseX - cellPx;
        int dy = mouseY - cellPy;
        int subCol = dx / subSize;
        int subRow = dy / subSize;
        if (subRow < 0 || subRow > 2 || subCol < 0 || subCol > 2) return -1;
        return subRow * 3 + subCol + 1;
    }

    /**
     * Maps canvas (row, col) → {gridIndex, cellIndex}, lowest-numbered grid for overlaps.
     */
    private int[] canvasToGridCell(int canvasR, int canvasC) {
        for (int g = 0; g < SamuraiSudoku.NUM_GRIDS; g++) {
            int originRow = SamuraiSudoku.CANVAS_ORIGINS[g][0];
            int originCol = SamuraiSudoku.CANVAS_ORIGINS[g][1];
            int localRow  = canvasR - originRow;
            int localCol  = canvasC - originCol;
            if (localRow >= 0 && localRow < 9 && localCol >= 0 && localCol < 9) {
                return new int[]{ g, localRow * 9 + localCol };
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Keyboard handling  (mirrors SudokuPanel.handleKeys)
    // -------------------------------------------------------------------------

    private void handleKeyPressed(KeyEvent e) {
        if (samurai == null || activeCell == null) return;

        int keyCode  = e.getKeyCode();
        int mods     = e.getModifiersEx();
        boolean ctrl = (mods & KeyEvent.CTRL_DOWN_MASK) != 0;

        // Laptop compatibility: if the char is a digit, use its key code
        char keyChar = e.getKeyChar();
        if (Character.isDigit(keyChar)) {
            keyCode = KEY_CODES[keyChar - '0'];
        }

        int gridIndex = activeCell[0];
        int cellIndex = activeCell[1];
        int row = cellIndex / 9;
        int col = cellIndex % 9;

        switch (keyCode) {

            // --- Navigation ---
            case KeyEvent.VK_DOWN:
                activeCell[1] = (row < 8 ? row + 1 : 0) * 9 + col;
                break;
            case KeyEvent.VK_UP:
                activeCell[1] = (row > 0 ? row - 1 : 8) * 9 + col;
                break;
            case KeyEvent.VK_RIGHT:
                activeCell[1] = row * 9 + (col < 8 ? col + 1 : 0);
                break;
            case KeyEvent.VK_LEFT:
                activeCell[1] = row * 9 + (col > 0 ? col - 1 : 8);
                break;
            case KeyEvent.VK_HOME:
                activeCell[1] = ctrl ? col : row * 9 + 0;
                break;
            case KeyEvent.VK_END:
                activeCell[1] = ctrl ? 8 * 9 + col : row * 9 + 8;
                break;

            // --- Set naked single ---
            case KeyEvent.VK_ENTER: {
                Sudoku2 grid = samurai.getGrid(gridIndex);
                if (grid.getValue(cellIndex) == 0) {
                    int[] cands = grid.getAllCandidates(cellIndex);
                    if (cands.length == 1) {
                        samurai.setCellValue(gridIndex, cellIndex, cands[0], false);
                    }
                }
                break;
            }

            // --- Clear cell ---
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_BACK_SPACE:
            case KeyEvent.VK_0:
            case KeyEvent.VK_NUMPAD0:
                if (!ctrl) {
                    Sudoku2 grid = samurai.getGrid(gridIndex);
                    if (!grid.isFixed(cellIndex)) {
                        samurai.setCellValue(gridIndex, cellIndex, 0, false);
                    }
                }
                break;

            // --- Digits 1–9: set value or toggle candidate ---
            case KeyEvent.VK_1: case KeyEvent.VK_NUMPAD1: handleDigit(gridIndex, cellIndex, 1, ctrl); break;
            case KeyEvent.VK_2: case KeyEvent.VK_NUMPAD2: handleDigit(gridIndex, cellIndex, 2, ctrl); break;
            case KeyEvent.VK_3: case KeyEvent.VK_NUMPAD3: handleDigit(gridIndex, cellIndex, 3, ctrl); break;
            case KeyEvent.VK_4: case KeyEvent.VK_NUMPAD4: handleDigit(gridIndex, cellIndex, 4, ctrl); break;
            case KeyEvent.VK_5: case KeyEvent.VK_NUMPAD5: handleDigit(gridIndex, cellIndex, 5, ctrl); break;
            case KeyEvent.VK_6: case KeyEvent.VK_NUMPAD6: handleDigit(gridIndex, cellIndex, 6, ctrl); break;
            case KeyEvent.VK_7: case KeyEvent.VK_NUMPAD7: handleDigit(gridIndex, cellIndex, 7, ctrl); break;
            case KeyEvent.VK_8: case KeyEvent.VK_NUMPAD8: handleDigit(gridIndex, cellIndex, 8, ctrl); break;
            case KeyEvent.VK_9: case KeyEvent.VK_NUMPAD9: handleDigit(gridIndex, cellIndex, 9, ctrl); break;

            default:
                return; // nothing to repaint
        }

        repaint();
    }

    /** Sets a cell value (no Ctrl) or toggles a candidate (Ctrl held). */
    private void handleDigit(int gridIndex, int cellIndex, int digit, boolean ctrl) {
        Sudoku2 grid = samurai.getGrid(gridIndex);
        if (grid.isFixed(cellIndex)) return;

        if (ctrl) {
            toggleCandidate(gridIndex, cellIndex, digit);
        } else {
            samurai.setCellValue(gridIndex, cellIndex, digit, false);
        }
    }

    /** Toggles a candidate in a cell (mirrors SudokuPanel.toggleCandidateInCell). */
    private void toggleCandidate(int gridIndex, int cellIndex, int candidate) {
        Sudoku2 grid = samurai.getGrid(gridIndex);
        if (grid.getValue(cellIndex) != 0) return;
        boolean userMode = !showCandidates;
        boolean current = grid.isCandidate(cellIndex, candidate, userMode);
        grid.setCandidate(cellIndex, candidate, !current, userMode);
    }

    // -------------------------------------------------------------------------
    // Painting
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (cellSize <= 0) recalculateLayout();

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (samurai == null) {
            for (int gi = 0; gi < SamuraiSudoku.NUM_GRIDS; gi++) {
                drawGridBackground(g2, gi, null);
                drawGridLines(g2, gi);
            }
            return;
        }

        for (int gi = 0; gi < SamuraiSudoku.NUM_GRIDS; gi++) {
            drawGrid(g2, gi, samurai.getGrid(gi));
        }
        drawOverlapTints(g2);
        drawSelection(g2);
    }

    // -------------------------------------------------------------------------
    // Grid rendering helpers
    // -------------------------------------------------------------------------

    private void drawGrid(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        drawGridBackground(g2, gridIndex, grid);
        drawValues(g2, gridIndex, grid);
        if (showCandidates) drawCandidates(g2, gridIndex, grid);
        drawGridLines(g2, gridIndex);
    }

    private void drawGridBackground(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int block = Sudoku2.getBlock(r * 9 + c);
                g2.setColor(block % 2 == 0
                        ? Options.getInstance().getDefaultCellColor()
                        : Options.getInstance().getAlternateCellColor());
                g2.fillRect(px(canvasCol(gridIndex, c)), py(canvasRow(gridIndex, r)),
                        cellSize, cellSize);
            }
        }
    }

    private void drawValues(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        int fontSize = Math.max(8, (int) (cellSize * 0.6));
        Font fixedFont  = new Font(Font.SANS_SERIF, Font.BOLD,  fontSize);
        Font playerFont = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);

        for (int i = 0; i < Sudoku2.LENGTH; i++) {
            int val = grid.getValue(i);
            if (val == 0) continue;
            int r = i / 9;
            int c = i % 9;
            boolean isFixed = grid.isFixed(i);
            g2.setFont(isFixed ? fixedFont : playerFont);
            g2.setColor(isFixed
                    ? Options.getInstance().getCellFixedValueColor()
                    : Options.getInstance().getCellValueColor());
            String s = Integer.toString(val);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s,
                    px(canvasCol(gridIndex, c)) + (cellSize - fm.stringWidth(s)) / 2,
                    py(canvasRow(gridIndex, r)) + (cellSize + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private void drawCandidates(Graphics2D g2, int gridIndex, Sudoku2 grid) {
        int subSize  = Math.max(2, cellSize / 3);
        int fontSize = Math.max(5, (int) (subSize * 0.7));
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Options.getInstance().getCandidateColor());

        for (int i = 0; i < Sudoku2.LENGTH; i++) {
            if (grid.getValue(i) != 0) continue;
            int r = i / 9;
            int c = i % 9;
            int basePx = px(canvasCol(gridIndex, c));
            int basePy = py(canvasRow(gridIndex, r));

            for (int cand : grid.getAllCandidates(i)) {
                int sr = (cand - 1) / 3;
                int sc = (cand - 1) % 3;
                String s = String.valueOf(cand);
                g2.drawString(s,
                        basePx + sc * subSize + (subSize - fm.stringWidth(s)) / 2,
                        basePy + sr * subSize + (subSize + fm.getAscent() - fm.getDescent()) / 2);
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
        g2.setColor(Color.BLACK);
        for (int col = 0; col <= 9; col++) {
            g2.setStroke(new BasicStroke(col % 3 == 0 ? thick : thin));
            int x = x0 + col * cellSize;
            g2.drawLine(x, y0, x, y0 + gridPx);
        }
        for (int row = 0; row <= 9; row++) {
            g2.setStroke(new BasicStroke(row % 3 == 0 ? thick : thin));
            int y = y0 + row * cellSize;
            g2.drawLine(x0, y, x0 + gridPx, y);
        }
    }

    private void drawOverlapTints(Graphics2D g2) {
        g2.setColor(OVERLAP_TINT);
        for (int[] def : OVERLAP_DEFS) {
            int cornerGrid  = def[0];
            int cornerBlock = def[2];
            int firstCell   = Sudoku2.BLOCKS[cornerBlock][0];
            int localRow    = firstCell / 9;
            int localCol    = firstCell % 9;
            g2.fillRect(px(canvasCol(cornerGrid, localCol)),
                        py(canvasRow(cornerGrid, localRow)),
                        cellSize * 3, cellSize * 3);
        }
    }

    private void drawSelection(Graphics2D g2) {
        if (activeCell == null) return;
        int gridIndex = activeCell[0];
        int cellIndex = activeCell[1];
        int r = cellIndex / 9;
        int c = cellIndex % 9;
        int x = px(canvasCol(gridIndex, c));
        int y = py(canvasRow(gridIndex, r));

        // Highlight background
        g2.setColor(Options.getInstance().getAktCellColor());
        g2.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);

        // Redraw value/candidates over highlight
        Sudoku2 grid = samurai.getGrid(gridIndex);
        int val = grid.getValue(cellIndex);
        if (val != 0) {
            int fontSize = Math.max(8, (int) (cellSize * 0.6));
            boolean isFixed = grid.isFixed(cellIndex);
            g2.setFont(new Font(Font.SANS_SERIF, isFixed ? Font.BOLD : Font.PLAIN, fontSize));
            g2.setColor(isFixed
                    ? Options.getInstance().getCellFixedValueColor()
                    : Options.getInstance().getCellValueColor());
            FontMetrics fm = g2.getFontMetrics();
            String s = Integer.toString(val);
            g2.drawString(s,
                    x + (cellSize - fm.stringWidth(s)) / 2,
                    y + (cellSize + fm.getAscent() - fm.getDescent()) / 2);
        } else if (showCandidates) {
            int subSize  = Math.max(2, cellSize / 3);
            int fontSize = Math.max(5, (int) (subSize * 0.7));
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
            g2.setColor(Options.getInstance().getCandidateColor());
            FontMetrics fm = g2.getFontMetrics();
            for (int cand : grid.getAllCandidates(cellIndex)) {
                int sr = (cand - 1) / 3;
                int sc = (cand - 1) % 3;
                String s = String.valueOf(cand);
                g2.drawString(s,
                        x + sc * subSize + (subSize - fm.stringWidth(s)) / 2,
                        y + sr * subSize + (subSize + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }
}
