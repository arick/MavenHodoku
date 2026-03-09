/*
 * SamuraiFrame.java
 *
 * Top-level window for Samurai Sudoku mode.  Contains a SamuraiPanel, a
 * toolbar, and a minimal menu for opening/saving/solving puzzles.
 */
package samurai;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Self-contained window for playing Samurai Sudoku puzzles.
 * <p>
 * Opened from the main HoDoKu window via File → Samurai Sudoku.
 */
public class SamuraiFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final SamuraiPanel samuraiPanel = new SamuraiPanel();
    private final SamuraiSolverCoordinator solver = new SamuraiSolverCoordinator();
    private SamuraiSudoku currentPuzzle;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public SamuraiFrame() {
        setTitle("Samurai Sudoku");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(840, 860));

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(samuraiPanel, BorderLayout.CENTER);

        // Wire up cell-click: toggle between setting a value via input dialog
        samuraiPanel.setCellClickListener((gridIndex, cellIndex, button) -> {
            if (currentPuzzle == null) return;
            if (currentPuzzle.getGrid(gridIndex).isFixed(cellIndex)) return;
            if (button == 1) {
                String input = JOptionPane.showInputDialog(
                        SamuraiFrame.this,
                        "Enter digit (1–9), or 0 to clear:",
                        "Set Cell",
                        JOptionPane.PLAIN_MESSAGE);
                if (input == null || input.trim().isEmpty()) return;
                try {
                    int val = Integer.parseInt(input.trim());
                    if (val >= 0 && val <= 9) {
                        currentPuzzle.setCellValue(gridIndex, cellIndex, val, false);
                        samuraiPanel.repaint();
                        checkSolved();
                    }
                } catch (NumberFormatException ex) {
                    // ignore invalid input
                }
            }
        });

        newPuzzle();
        pack();
    }

    // -------------------------------------------------------------------------
    // Menu & toolbar
    // -------------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // --- File menu ---
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newItem = new JMenuItem("New Puzzle");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        newItem.addActionListener(e -> newPuzzle());
        fileMenu.add(newItem);

        fileMenu.addSeparator();

        JMenuItem openItem = new JMenuItem("Open…");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        openItem.addActionListener(e -> openPuzzle());
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("Save…");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        saveItem.addActionListener(e -> savePuzzle());
        fileMenu.add(saveItem);

        menuBar.add(fileMenu);

        // --- Solve menu ---
        JMenu solveMenu = new JMenu("Solve");
        solveMenu.setMnemonic(KeyEvent.VK_S);

        JMenuItem stepItem = new JMenuItem("Hint (one step)");
        stepItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.CTRL_MASK));
        stepItem.addActionListener(e -> solveStep());
        solveMenu.add(stepItem);

        JMenuItem solveAllItem = new JMenuItem("Solve All");
        solveAllItem.addActionListener(e -> solveAll());
        solveMenu.add(solveAllItem);

        menuBar.add(solveMenu);

        // --- View menu ---
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        JMenuItem toggleCandidates = new JMenuItem("Toggle Candidates");
        toggleCandidates.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        toggleCandidates.addActionListener(e -> {
            samuraiPanel.setShowCandidates(!samuraiPanel.isShowCandidates());
        });
        viewMenu.add(toggleCandidates);

        menuBar.add(viewMenu);

        return menuBar;
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        javax.swing.JButton newBtn = new javax.swing.JButton("New");
        newBtn.addActionListener(e -> newPuzzle());
        bar.add(newBtn);

        bar.addSeparator();

        javax.swing.JButton hintBtn = new javax.swing.JButton("Hint");
        hintBtn.addActionListener(e -> solveStep());
        bar.add(hintBtn);

        javax.swing.JButton solveBtn = new javax.swing.JButton("Solve All");
        solveBtn.addActionListener(e -> solveAll());
        bar.add(solveBtn);

        return bar;
    }

    // -------------------------------------------------------------------------
    // Puzzle lifecycle
    // -------------------------------------------------------------------------

    private void newPuzzle() {
        currentPuzzle = SamuraiGenerator.generate();
        samuraiPanel.setSamurai(currentPuzzle);
        setTitle("Samurai Sudoku");
    }

    private void solveStep() {
        if (currentPuzzle == null) return;
        boolean progress = solver.solveStep(currentPuzzle);
        samuraiPanel.repaint();
        if (!progress) {
            JOptionPane.showMessageDialog(this,
                    "No further steps found with current techniques.",
                    "Hint",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            checkSolved();
        }
    }

    private void solveAll() {
        if (currentPuzzle == null) return;
        boolean solved = solver.solve(currentPuzzle);
        samuraiPanel.repaint();
        if (solved) {
            JOptionPane.showMessageDialog(this, "Puzzle solved!", "Solved",
                    JOptionPane.INFORMATION_MESSAGE);
            setTitle("Samurai Sudoku — Solved");
        } else {
            JOptionPane.showMessageDialog(this,
                    "Could not fully solve the puzzle with current techniques.",
                    "Solve",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void checkSolved() {
        if (currentPuzzle != null && currentPuzzle.isSolved()) {
            JOptionPane.showMessageDialog(this, "Congratulations — puzzle complete!",
                    "Solved", JOptionPane.INFORMATION_MESSAGE);
            setTitle("Samurai Sudoku — Solved");
        }
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private void openPuzzle() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Samurai Sudoku (*.sam)", "sam"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            currentPuzzle = SamuraiSudoku.deserialize(sb.toString());
            samuraiPanel.setSamurai(currentPuzzle);
            setTitle("Samurai Sudoku — " + f.getName());
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void savePuzzle() {
        if (currentPuzzle == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Samurai Sudoku (*.sam)", "sam"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().endsWith(".sam")) {
            f = new File(f.getAbsolutePath() + ".sam");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
            writer.write(currentPuzzle.serialize());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
