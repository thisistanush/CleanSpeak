package com.tanush.cleanspeech.ui;

import com.tanush.cleanspeech.audio.AudioConverter;
import com.tanush.cleanspeech.audio.AudioEditor;
import com.tanush.cleanspeech.audio.NoiseReducer;
import com.tanush.cleanspeech.edit.EditPlanGenerator;
import com.tanush.cleanspeech.model.EditPlan;
import com.tanush.cleanspeech.model.TranscriptWord;
import com.tanush.cleanspeech.transcript.TranscriptWriter;
import com.tanush.cleanspeech.transcribe.GeminiTranscriber;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Modern Swing UI for CleanSpeech Audio Editor.
 * Features drag-and-drop, progress display, and clean design.
 * Uses only built-in Java libraries - no external dependencies.
 */
public class CleanSpeechUI extends JFrame {

    // UI Components
    private JLabel dropLabel;
    private JPanel dropZone;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    private JButton processButton;
    private JButton browseButton;

    // Selected file
    private File selectedFile;

    // Processing state
    private boolean isProcessing = false;

    // Colors for modern dark theme
    private static final Color BG_DARK = new Color(26, 26, 46);
    private static final Color BG_PANEL = new Color(22, 33, 62);
    private static final Color ACCENT_GREEN = new Color(72, 187, 120);
    private static final Color TEXT_LIGHT = Color.WHITE;
    private static final Color TEXT_DIM = new Color(160, 174, 192);

    public CleanSpeechUI() {
        super("CleanSpeech - Audio Editor");
        initializeUI();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 650);
        setLocationRelativeTo(null);
        setResizable(true);

        // Main panel with dark background
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Title
        JLabel title = new JLabel("✨ CleanSpeech Audio Editor");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(TEXT_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Remove filler words automatically");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_DIM);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Drop zone
        dropZone = createDropZone();

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setBackground(BG_DARK);
        buttonPanel.setMaximumSize(new Dimension(500, 50));

        browseButton = createStyledButton("📁 Browse Files", new Color(74, 85, 104));
        browseButton.addActionListener(e -> browseForFile());

        processButton = createStyledButton("🚀 Clean Audio", ACCENT_GREEN);
        processButton.setEnabled(false);
        processButton.addActionListener(e -> processAudio());

        buttonPanel.add(browseButton);
        buttonPanel.add(processButton);

        // Progress section
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressPanel.setBackground(BG_DARK);
        progressPanel.setMaximumSize(new Dimension(450, 60));

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(400, 20));
        progressBar.setMaximumSize(new Dimension(400, 20));
        progressBar.setForeground(ACCENT_GREEN);
        progressBar.setBackground(BG_PANEL);
        progressBar.setBorderPainted(false);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel("Drop an MP3 file or click Browse");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        progressPanel.add(progressBar);
        progressPanel.add(Box.createVerticalStrut(10));
        progressPanel.add(statusLabel);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(15, 15, 35));
        logArea.setForeground(new Color(99, 245, 167));
        logArea.setCaretColor(ACCENT_GREEN);
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(480, 180));
        scrollPane.setMaximumSize(new Dimension(500, 200));
        scrollPane.setBorder(BorderFactory.createLineBorder(BG_PANEL, 2));
        scrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add components with spacing
        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(subtitle);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(dropZone);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(progressPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(scrollPane);

        add(mainPanel);
        log("✅ Ready! Drop an MP3 file to get started.");
    }

    private JPanel createDropZone() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(400, 120));
        panel.setMaximumSize(new Dimension(450, 120));
        panel.setBackground(new Color(74, 85, 104, 50));
        panel.setBorder(BorderFactory.createDashedBorder(new Color(74, 85, 104), 3, 5, 5, true));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        dropLabel = new JLabel("🎵 Drop MP3 here", SwingConstants.CENTER);
        dropLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        dropLabel.setForeground(TEXT_DIM);
        panel.add(dropLabel, BorderLayout.CENTER);

        // Enable drag and drop
        new DropTarget(panel, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                panel.setBackground(new Color(72, 187, 120, 30));
                panel.setBorder(BorderFactory.createDashedBorder(ACCENT_GREEN, 3, 5, 5, true));
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                panel.setBackground(new Color(74, 85, 104, 50));
                panel.setBorder(BorderFactory.createDashedBorder(new Color(74, 85, 104), 3, 5, 5, true));
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        if (file.getName().toLowerCase().endsWith(".mp3")) {
                            selectFile(file);
                        } else {
                            log("❌ Please drop an MP3 file");
                        }
                    }
                } catch (Exception e) {
                    log("❌ Error: " + e.getMessage());
                }
                panel.setBackground(new Color(74, 85, 104, 50));
                panel.setBorder(BorderFactory.createDashedBorder(new Color(74, 85, 104), 3, 5, 5, true));
            }
        });

        return panel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(TEXT_LIGHT);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(160, 40));
        return button;
    }

    private void browseForFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".mp3");
            }

            @Override
            public String getDescription() {
                return "MP3 Audio Files (*.mp3)";
            }
        });

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectFile(chooser.getSelectedFile());
        }
    }

    private void selectFile(File file) {
        selectedFile = file;
        dropLabel.setText("🎵 " + file.getName());
        processButton.setEnabled(true);
        statusLabel.setText("Ready to process: " + file.getName());
        log("📂 Selected: " + file.getName());
    }

    private void processAudio() {
        if (selectedFile == null || isProcessing) {
            return;
        }

        isProcessing = true;
        processButton.setEnabled(false);
        browseButton.setEnabled(false);
        progressBar.setValue(0);

        // Run processing in background thread
        new Thread(() -> {
            try {
                runPipeline();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    log("❌ Error: " + e.getMessage());
                    statusLabel.setText("Error: " + e.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isProcessing = false;
                    processButton.setEnabled(true);
                    browseButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void runPipeline() throws IOException {
        Path inputMp3 = selectedFile.toPath();
        String baseName = selectedFile.getName().replace(".mp3", "");

        // Save to Desktop
        String userHome = System.getProperty("user.home");
        Path desktopDir = Path.of(userHome, "Desktop");
        Path outputMp3 = desktopDir.resolve(baseName + "_cleaned.mp3");

        // Create temp directory
        Path tempDir = Files.createTempDirectory("cleanspeech_");

        // Initialize components
        AudioConverter converter = new AudioConverter();
        NoiseReducer noiseReducer = new NoiseReducer();
        GeminiTranscriber transcriber = new GeminiTranscriber();
        EditPlanGenerator editGenerator = new EditPlanGenerator();
        AudioEditor editor = new AudioEditor();

        // Step 1: Convert MP3 to WAV
        updateProgress(10, "Converting MP3 to WAV...");
        Path rawWav = converter.convertMp3ToWav(inputMp3, tempDir);
        log("✅ Converted to WAV");

        // Step 2: Noise reduction
        updateProgress(25, "Reducing background noise...");
        Path denoisedWav = noiseReducer.reduceNoise(rawWav, tempDir);
        log("✅ Noise reduced");

        // Step 3: Transcribe
        updateProgress(40, "Transcribing audio...");
        List<TranscriptWord> words = transcriber.transcribe(denoisedWav, tempDir);
        log("✅ Transcribed: " + words.size() + " words");

        // Step 4: Generate edit plan
        updateProgress(60, "Analyzing for filler words...");
        EditPlan editPlan = editGenerator.generateEditPlan(words);
        log("✅ Found " + editPlan.getSegmentsToRemove().size() + " filler words");

        // Generate transcripts
        try {
            TranscriptWriter transcriptWriter = new TranscriptWriter();
            transcriptWriter.writeTranscripts(words, editPlan, outputMp3);
            log("✅ Transcripts generated");
        } catch (Exception e) {
            log("⚠️ Could not generate transcripts: " + e.getMessage());
        }

        // Step 5: Apply edits
        updateProgress(80, "Editing audio...");
        Path cleanedWav = editor.applyEditPlan(denoisedWav, editPlan, tempDir);
        log("✅ Audio edited");

        // Step 6: Convert back to MP3
        updateProgress(90, "Creating MP3...");
        converter.convertWavToMp3(cleanedWav, outputMp3);

        // Done!
        updateProgress(100, "✅ Complete!");
        log("");
        log("🎉 SUCCESS! Saved: " + outputMp3.getFileName());
        log("   Time saved: " + String.format("%.1f",
                editPlan.getTotalTimeToRemove() + editPlan.getTotalTimeSavedFromPauses()) + "s");

        // Cleanup
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void updateProgress(int progress, String status) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(progress);
            statusLabel.setText(status);
        });
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        // Use system look and feel for better appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            CleanSpeechUI ui = new CleanSpeechUI();
            ui.setVisible(true);
        });
    }
}
