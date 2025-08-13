package tr.cabro.servicio.application.ui;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.importer.ImportManager;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImporterUI extends JDialog {

    public enum LogLevel {
        INFO, SUCCESS, WARNING, ERROR
    }

    private JEditorPane path_field;
    private JTextPane logger_area;
    private JButton import_button;
    private JScrollPane text_scroll;
    private JPanel main_panel;
    private JPanel button_panel;
    private JButton skip_button;

    private final ImportManager importManager;
    private File savedLogFile;
    private String folderPath;

    public ImporterUI() {
        super((Frame) null, "Servicio - Veri Tabanı Aktarma", true);
        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.25);
        int height = (int) (screen_size.height * 0.4);
        setSize(width, height);
        setLocationRelativeTo(null);

        // ImportManager log callback'i
        this.importManager = new ImportManager(line -> appendLog(line, LogLevel.INFO));

        init();

        setContentPane(main_panel);
    }

    private void init() {
        // logger_area ayarları
        logger_area.setEditable(false);
        logger_area.setBackground(Color.BLACK);
        logger_area.setForeground(Color.WHITE);
        logger_area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        File folder = detectOldAppData();
        if (folder != null) {
            folderPath = folder.getAbsolutePath();
            appendLog("Eski uygulamadan kalan veriler bulundu.", LogLevel.INFO);
            appendLog("Bulunan klasör: " + folderPath, LogLevel.INFO);
            appendLog("Aktarmak için 'Başlat' butonuna tıklayın.", LogLevel.INFO);
        } else {
            appendLog("Eski uygulama verisi bulunamadı.", LogLevel.WARNING);
        }

        import_button.addActionListener(e -> {
            if (savedLogFile != null) {
                openLogFile();
                dispose();
            } else {
                importCmd();
            }
        });

        skip_button.addActionListener(e -> dispose());
    }

    private void importCmd() {
        Path path = Paths.get(folderPath);
        File folder = path.toFile();
        if (!folder.exists() || !folder.isDirectory()) {
            appendLog("Geçerli bir klasör seçiniz.", LogLevel.ERROR);
            return;
        }

        clearLog();
        appendLog("İçe aktarma işlemi başlatılıyor...", LogLevel.INFO);

        import_button.setEnabled(false);
        skip_button.setEnabled(false);

        new Thread(() -> {
            try {
                importManager.importAll(folder.getAbsolutePath());
                saveLogToFile();

                SwingUtilities.invokeLater(() -> {
                    appendLog("İçe aktarma işlemi başarıyla tamamlandı.", LogLevel.SUCCESS);
                    appendLog("Log kaydedildi: " + savedLogFile.getAbsolutePath(), LogLevel.INFO);

                    import_button.setText("Logu Görüntüle & Kapat");
                    import_button.setEnabled(true);
                    skip_button.setText("Kapat");
                    skip_button.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendLog("İçe aktarma sırasında hata oluştu: " + e.getMessage(), LogLevel.ERROR);
                    skip_button.setEnabled(true);
                });
            }
        }).start();
    }

    private void saveLogToFile() {
        File dataFolder = Servicio.getInstance().getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        savedLogFile = new File(dataFolder, "import_log.txt");
        try (FileWriter writer = new FileWriter(savedLogFile)) {
            writer.write(importManager.getLog());
        } catch (IOException ex) {
            Servicio.getLogger().error("Log kaydedilemedi: {}", ex.getMessage());
        }
    }

    private void openLogFile() {
        if (savedLogFile != null && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(savedLogFile);
            } catch (IOException e) {
                Servicio.getLogger().error("Log açılamadı: {}", e.getMessage());
            }
        }
    }

    public boolean hasDetectedFolder() {
        return detectOldAppData() != null;
    }

    private File detectOldAppData() {
        String userHome = System.getProperty("user.home");
        File appDataDir;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            appDataDir = new File(System.getenv("APPDATA"), ".technical-service-application");
        } else {
            appDataDir = new File(userHome, ".technical-service-application");
        }
        return appDataDir.exists() ? appDataDir : null;
    }

    // Renkli + ikonlu log
    private void appendLog(String message, LogLevel level) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = logger_area.getStyledDocument();
                Style style = logger_area.addStyle("Style", null);

                switch (level) {
                    case SUCCESS:
                        StyleConstants.setForeground(style, new Color(0, 128, 0));
                        break;
                    case WARNING:
                        StyleConstants.setForeground(style, new Color(255, 140, 0));
                        break;
                    case ERROR:
                        StyleConstants.setForeground(style, Color.RED);
                        break;
                    default:
                        StyleConstants.setForeground(style, Color.WHITE);
                }

                StyleConstants.setBold(style, level == LogLevel.ERROR || level == LogLevel.SUCCESS);

                String icon;
                switch (level) {
                    case SUCCESS:
                        icon = "✔ ";
                        break;
                    case WARNING:
                        icon = "⚠ ";
                        break;
                    case ERROR:
                        icon = "❌ ";
                        break;
                    default:
                        icon = "ℹ ";
                }

                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                doc.insertString(doc.getLength(), icon + "[" + time + "] " + message + "\n", style);
                logger_area.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                Servicio.getLogger().error(e.getMessage());
            }
        });
    }

    private void clearLog() {
        SwingUtilities.invokeLater(() -> logger_area.setText(""));
    }
}
