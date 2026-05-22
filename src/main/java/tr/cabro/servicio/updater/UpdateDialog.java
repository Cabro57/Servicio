package tr.cabro.servicio.updater;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.Servicio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Güncelleme diyaloğu — FlatLaf + IntelliJ IDEA tarzı akış.
 * <p>
 * Ekranlar:
 *   INFO     → GitHub Releases API'sinden asenkron patch notları
 *   DOWNLOAD → Dosya bazlı ilerleme çubukları
 *   ERROR    → Hata + Tekrar Dene / Kapat
 * <p>
 * İndirme tamamlanınca DONE ekranı yerine doğrudan JOptionPane:
 *   [Şimdi Yeniden Başlat] → applyUpdate + launchAndExit + shutdown
 *   [Daha Sonra]           → dialog kapanır, .update-tmp bir sonraki açılışta uygulanır
 */
public class UpdateDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(UpdateDialog.class);

    // ─── Renkler ─────────────────────────────────────────────────────────────

    private static Color accent() { return getUI("Button.default.background", new Color(75, 110, 175)); }
    private static Color fg()     { return getUI("Label.foreground",           new Color(220, 220, 220)); }
    private static Color fgDim()  { return getUI("Label.disabledForeground",   new Color(140, 140, 140)); }
    private static Color green()  { return new Color(98, 151, 85); }
    private static Color red()    { return new Color(204, 120, 120); }
    private static Color sep()    { return getUI("Separator.foreground",       new Color(80, 80, 80)); }

    private static Color getUI(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    // ─── Model ────────────────────────────────────────────────────────────────

    private final UpdateManifest manifest;
    private final UpdateManager  manager;
    private final boolean        isHotfix;

    @Setter
    private Consumer<String> onSkipVersion;

    // ─── Kart sistemi ─────────────────────────────────────────────────────────

    private final CardLayout cards     = new CardLayout();
    private final JPanel     cardPanel = new JPanel(cards);

    // INFO ekranı
    private JPanel notesWrapper;

    // DOWNLOAD ekranı
    private JLabel       currentFileLabel;
    private JProgressBar currentFileBar;
    private JProgressBar totalBar;
    private JLabel       statusLabel;
    private JPanel       fileListPanel;

    private int totalFiles = 0;
    private int doneFiles  = 0;

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    public UpdateDialog(JFrame owner, UpdateManifest manifest, UpdateManager manager) {
        this(owner, manifest, manager, false);
    }

    public UpdateDialog(JFrame owner, UpdateManifest manifest,
                        UpdateManager manager, boolean isHotfix) {
        super(owner,
                isHotfix ? "Kritik Güncelleme (Yama) — v" + manifest.getVersion()
                        : "Güncelleme Mevcut — v" + manifest.getVersion(),
                true);
        this.manifest = manifest;
        this.manager  = manager;
        this.isHotfix = isHotfix;

        setSize(620, 540);
        setMinimumSize(new Dimension(500, 420));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground",
                UIManager.getColor("TitlePane.background"));

        buildCards();
        add(cardPanel);
        showCard("INFO");
        fetchReleaseNotesAsync();
    }

    // ─── Kartlar ─────────────────────────────────────────────────────────────

    private void buildCards() {
        cardPanel.add(buildInfoCard(),     "INFO");
        cardPanel.add(buildDownloadCard(), "DOWNLOAD");
        cardPanel.add(buildErrorCard(),    "ERROR");
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 1 — Bilgi + Patch Notları
    // ════════════════════════════════════════════════════════════

    private JPanel buildInfoCard() {
        JPanel root = new JPanel(new BorderLayout());

        // Başlık
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBorder(new EmptyBorder(22, 26, 18, 26));
        header.setOpaque(false);

        JLabel title = styledLabel(
                (isHotfix ? "🔧  Kritik Güncelleme — " : "🚀  Yeni Sürüm — ")
                        + "v" + manifest.getVersion(), 17, Font.BOLD);

        String dateStr = (manifest.getReleaseDate() != null && !manifest.getReleaseDate().isEmpty())
                ? "Yayın tarihi: " + manifest.getReleaseDate() : "";
        JLabel sub = styledLabel(dateStr, 12, Font.PLAIN);
        sub.setForeground(fgDim());

        header.add(title, BorderLayout.CENTER);
        header.add(sub,   BorderLayout.SOUTH);

        // Patch notları (asenkron)
        notesWrapper = new JPanel();
        notesWrapper.setLayout(new BoxLayout(notesWrapper, BoxLayout.Y_AXIS));
        notesWrapper.setBorder(new EmptyBorder(10, 22, 10, 22));
        notesWrapper.setOpaque(false);
        JLabel loadingLbl = styledLabel("  Sürüm notları yükleniyor…", 12, Font.PLAIN);
        loadingLbl.setForeground(fgDim());
        notesWrapper.add(loadingLbl);

        JScrollPane scroll = new JScrollPane(notesWrapper);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, sep()));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(10);

        // Alt bilgi
        int fileCount = manifest.getFiles() != null ? manifest.getFiles().size() : 0;
        JLabel hint = styledLabel(
                "  " + fileCount + " bileşen kontrol edilecek — yalnızca değişenler indirilir",
                11, Font.PLAIN);
        hint.setForeground(fgDim());
        hint.setBorder(new EmptyBorder(8, 10, 6, 10));

        // Butonlar
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnRow.setOpaque(false);

        JButton skipBtn   = ghostButton("Bu Sürümü Atla");
        JButton laterBtn  = ghostButton("Sonra");
        JButton updateBtn = accentButton("İndir ve Kur  ▶");

        skipBtn.setVisible(!isHotfix);
        skipBtn.addActionListener(e -> {
            if (onSkipVersion != null) onSkipVersion.accept(manifest.getVersion());
            dispose();
        });
        laterBtn.addActionListener(e -> dispose());
        updateBtn.addActionListener(e -> startDownload());

        btnRow.add(skipBtn);
        btnRow.add(laterBtn);
        btnRow.add(updateBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(hint,   BorderLayout.NORTH);
        south.add(btnRow, BorderLayout.CENTER);

        JPanel topBlock = new JPanel(new BorderLayout());
        topBlock.setOpaque(false);
        topBlock.add(header,           BorderLayout.CENTER);
        topBlock.add(new JSeparator(), BorderLayout.SOUTH);

        root.add(topBlock, BorderLayout.NORTH);
        root.add(scroll,   BorderLayout.CENTER);
        root.add(south,    BorderLayout.SOUTH);
        return root;
    }

    // ─── Patch Notları Asenkron ───────────────────────────────────────────────

    private void fetchReleaseNotesAsync() {
        manager.fetchReleaseInfo(
                manifest,
                info -> SwingUtilities.invokeLater(() -> populateNotes(info)),
                ex   -> {
                    log.warn("Patch notları yüklenemedi: {}", ex.getMessage());
                    SwingUtilities.invokeLater(this::showNotesError);
                }
        );
    }

    private void populateNotes(UpdateManifest.GitHubReleaseInfo info) {
        notesWrapper.removeAll();

        String relName = (info.name != null && !info.name.isEmpty()) ? info.name : info.tagName;
        JLabel titleLbl = styledLabel(relName, 13, Font.BOLD);
        titleLbl.setForeground(accent());
        titleLbl.setBorder(new EmptyBorder(4, 0, 4, 0));
        notesWrapper.add(titleLbl);

        if (info.publishedAt != null && !info.publishedAt.isEmpty()) {
            JLabel date = styledLabel("  " + info.publishedAt, 11, Font.PLAIN);
            date.setForeground(fgDim());
            date.setBorder(new EmptyBorder(0, 0, 8, 0));
            notesWrapper.add(date);
        }

        List<String> lines = info.changeLines;
        if (lines != null && !lines.isEmpty()) {
            for (String line : lines) {
                if (line.startsWith("**") && line.endsWith("**")) {
                    JLabel hl = styledLabel(
                            "  " + line.substring(2, line.length() - 2), 12, Font.BOLD);
                    hl.setForeground(fg());
                    hl.setBorder(new EmptyBorder(8, 0, 3, 0));
                    notesWrapper.add(hl);
                } else {
                    JLabel cl = styledLabel("     • " + line, 12, Font.PLAIN);
                    cl.setBorder(new EmptyBorder(1, 10, 1, 0));
                    notesWrapper.add(cl);
                }
            }
        } else {
            notesWrapper.add(styledLabel(
                    "  Bu sürüm için açıklama bulunmuyor.", 12, Font.PLAIN));
        }
        notesWrapper.revalidate();
        notesWrapper.repaint();
    }

    private void showNotesError() {
        notesWrapper.removeAll();
        JLabel err = styledLabel(
                "  Sürüm notları yüklenemedi.", 12, Font.PLAIN);
        err.setForeground(fgDim());
        notesWrapper.add(err);
        notesWrapper.revalidate();
        notesWrapper.repaint();
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 2 — İndirme İlerlemesi
    // ════════════════════════════════════════════════════════════

    private JPanel buildDownloadCard() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(22, 26, 14, 26));

        JLabel title = styledLabel("Güncelleme İndiriliyor…", 16, Font.BOLD);

        currentFileLabel = styledLabel("Hazırlanıyor…", 11, Font.PLAIN);
        currentFileLabel.setForeground(fgDim());
        currentFileBar = makeProgressBar(accent());
        totalBar       = makeProgressBar(green());
        statusLabel    = styledLabel("", 11, Font.PLAIN);
        statusLabel.setForeground(fgDim());

        JPanel bars = new JPanel(new GridLayout(0, 1, 0, 4));
        bars.setOpaque(false);
        bars.add(dimLabel("Şu anki dosya:"));
        bars.add(currentFileBar);
        bars.add(currentFileLabel);
        bars.add(Box.createVerticalStrut(4));
        bars.add(dimLabel("Genel ilerleme:"));
        bars.add(totalBar);
        bars.add(statusLabel);

        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);
        fileListPanel.setBorder(new EmptyBorder(6, 4, 6, 4));

        JScrollPane listScroll = new JScrollPane(fileListPanel);
        listScroll.setBorder(BorderFactory.createLineBorder(sep()));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getVerticalScrollBar().setUnitIncrement(8);

        JButton cancelBtn = ghostButton("İptal");
        cancelBtn.addActionListener(e -> { manager.cancel(); dispose(); });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        btnRow.setOpaque(false);
        btnRow.add(cancelBtn);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(title, BorderLayout.NORTH);
        top.add(bars,  BorderLayout.CENTER);

        root.add(top,        BorderLayout.NORTH);
        root.add(listScroll, BorderLayout.CENTER);
        root.add(btnRow,     BorderLayout.SOUTH);
        return root;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 3 — Hata
    // ════════════════════════════════════════════════════════════

    private JPanel buildErrorCard() {
        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;

        JLabel icon = styledLabel("✘", 52, Font.BOLD);
        icon.setForeground(red());

        JLabel msg = styledLabel("İndirme Başarısız", 20, Font.BOLD);
        JLabel sub = styledLabel(
                "<html><center>Lütfen internet bağlantınızı kontrol edip<br>tekrar deneyin.</center></html>",
                13, Font.PLAIN);
        sub.setForeground(fgDim());
        sub.setHorizontalAlignment(SwingConstants.CENTER);

        JButton retryBtn = accentButton("Tekrar Dene");
        JButton closeBtn = ghostButton("Kapat");
        retryBtn.addActionListener(e -> startDownload());
        closeBtn.addActionListener(e -> dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setOpaque(false);
        btnRow.add(closeBtn);
        btnRow.add(retryBtn);

        c.gridy = 0; c.insets = new Insets(0, 0, 12, 0); root.add(icon,   c);
        c.gridy = 1; c.insets = new Insets(0, 0, 8,  0); root.add(msg,    c);
        c.gridy = 2; c.insets = new Insets(0, 0, 32, 0); root.add(sub,    c);
        c.gridy = 3; c.insets = new Insets(0, 0, 0,  0); root.add(btnRow, c);
        return root;
    }

    // ─── İndirme Mantığı ──────────────────────────────────────────────────────

    private void startDownload() {
        showCard("DOWNLOAD");
        fileListPanel.removeAll();
        fileListPanel.revalidate();
        doneFiles  = 0;
        totalFiles = manifest.getFiles() != null ? manifest.getFiles().size() : 1;
        totalBar.setValue(0);
        currentFileBar.setValue(0);
        currentFileLabel.setText("Hazırlanıyor…");
        statusLabel.setText("");

        manager.downloadUpdate(
                manifest,
                (name, pct) -> SwingUtilities.invokeLater(() -> {
                    currentFileLabel.setText(name);
                    currentFileBar.setValue((int)(pct * 100));
                }),
                name -> SwingUtilities.invokeLater(() -> appendRow(name, RowState.SKIPPED)),
                name -> SwingUtilities.invokeLater(() -> appendRow(name, RowState.DONE)),
                ()   -> SwingUtilities.invokeLater(this::onDownloadComplete),
                ex   -> SwingUtilities.invokeLater(() -> {
                    log.error("İndirme hatası", ex);
                    statusLabel.setText("Hata: " + ex.getMessage());
                    showCard("ERROR");
                })
        );
    }

    // ─── İndirme Tamamlandı — IntelliJ IDEA tarzı ────────────────────────────

    /**
     * İndirme bitti → "Yeniden Başlat?" JOptionPane sorusu.
     * <p>
     * [Şimdi Yeniden Başlat] → applyUpdate + launchAndExit + shutdown
     * [Daha Sonra]           → dialog kapanır, .update-tmp korunur;
     *                          bir sonraki açılışta launcher dosyaları taşır
     */
    private void onDownloadComplete() {
        totalBar.setValue(100);
        statusLabel.setText("İndirme tamamlandı!");

        // IntelliJ IDEA tarzı mesaj paneli
        JPanel msg = new JPanel(new BorderLayout(12, 0));
        msg.setOpaque(false);

        JLabel iconLbl = new JLabel("✔");
        iconLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        iconLbl.setForeground(green());
        iconLbl.setVerticalAlignment(SwingConstants.TOP);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);

        JLabel titleLbl = new JLabel("Güncelleme indirildi!");
        titleLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        JLabel subLbl = new JLabel(
                "<html>v" + manifest.getVersion() + " kurulmaya hazır.<br>"
                        + "<font color='#888888'>Uygulamayı şimdi yeniden başlatmak ister misiniz?<br>"
                        + "<i>\"Daha Sonra\" seçerseniz bir sonraki açılışta otomatik uygulanır.</i>"
                        + "</font></html>");
        subLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        text.add(titleLbl);
        text.add(Box.createVerticalStrut(6));
        text.add(subLbl);

        msg.add(iconLbl, BorderLayout.WEST);
        msg.add(text,    BorderLayout.CENTER);

        String[] options = {"Şimdi Yeniden Başlat", "Daha Sonra"};

        int choice = JOptionPane.showOptionDialog(
                this, msg,
                "Güncelleme Hazır",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]   // Varsayılan: yeniden başlat
        );

        if (choice == JOptionPane.YES_OPTION) {
            applyAndRestart();
        } else {
            // Daha sonra — launcher script yaz, .update-tmp koru
            scheduleLauncherForNextStart();
            dispose();
        }
    }

    /**
     * "Daha Sonra" seçildi:
     * Launcher script oluşturulur. Bir sonraki JVM başlangıcında
     * update-restart.bat/sh mevcut ise çalışır ve dosyaları taşır.
     * <p>
     * Bu davranışı etkinleştirmek için ApplicationBootstrap.launch() içine
     * aşağıdaki kontrolü ekleyin:
     * <pre>
     *   File launcherScript = new File(".", "update-restart.bat"); // veya .sh
     *   if (launcherScript.exists()) {
     *       // .update-tmp mevcut — uygulama başlamadan önce uygula
     *       // Veya bildirim göster: "Bekleyen güncelleme var"
     *   }
     * </pre>
     */
    private void scheduleLauncherForNextStart() {
        new Thread(() -> {
            try {
                manager.writeLauncherScript("servicio.jar", "");
                log.info("Launcher script yazıldı. Güncelleme bir sonraki başlatmada uygulanacak.");
            } catch (Exception e) {
                log.warn("Launcher script yazılamadı: {}", e.getMessage());
            }
        }, "servicio-schedule-launcher").start();
    }

    private void applyAndRestart() {
        statusLabel.setText("Uygulanıyor…");
        currentFileBar.setIndeterminate(true);
        currentFileLabel.setText("");

        new Thread(() -> {
            try {
                manager.applyUpdate();
                File script = manager.writeLauncherScript("servicio.jar", "");
                manager.launchAndExit(script);
                SwingUtilities.invokeLater(() -> {
                    dispose();
                    Servicio.getInstance().shutdown();
                });
            } catch (Exception e) {
                log.error("Güncelleme uygulama hatası", e);
                SwingUtilities.invokeLater(() -> {
                    currentFileBar.setIndeterminate(false);
                    statusLabel.setText("Hata: " + e.getMessage());
                    showCard("ERROR");
                });
            }
        }, "servicio-apply-update").start();
    }

    // ─── Dosya Listesi ────────────────────────────────────────────────────────

    private enum RowState { DONE, SKIPPED, ERROR }

    private void appendRow(String name, RowState state) {
        String prefix;
        Color  color;
        if      (state == RowState.DONE)    { prefix = "  ✔  "; color = green(); }
        else if (state == RowState.SKIPPED) { prefix = "  ─  "; color = fgDim(); }
        else                                { prefix = "  ✘  "; color = red();   }

        JLabel row = styledLabel(prefix + name, 11, Font.PLAIN);
        row.setForeground(color);
        row.setBorder(new EmptyBorder(2, 4, 2, 4));
        fileListPanel.add(row);
        fileListPanel.revalidate();
        fileListPanel.repaint();

        doneFiles++;
        if (totalFiles > 0) totalBar.setValue((int)((double) doneFiles / totalFiles * 100));
        statusLabel.setText(doneFiles + " / " + totalFiles + " dosya işlendi");
    }

    // ─── UI Yardımcıları ──────────────────────────────────────────────────────

    private void showCard(String name) { cards.show(cardPanel, name); }

    private static JLabel styledLabel(String text, int size, int style) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, style, size));
        return l;
    }

    private static JLabel dimLabel(String text) {
        JLabel l = styledLabel(text, 11, Font.PLAIN);
        l.setForeground(fgDim());
        return l;
    }

    private static JProgressBar makeProgressBar(Color color) {
        JProgressBar b = new JProgressBar(0, 100);
        b.setForeground(color);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(0, 7));
        b.putClientProperty("JProgressBar.largeHeight", Boolean.FALSE);
        return b;
    }

    private static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        b.putClientProperty("JButton.buttonType", "default");
        b.setPreferredSize(new Dimension(180, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton ghostButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        b.putClientProperty("JButton.buttonType", "borderless");
        b.setPreferredSize(new Dimension(120, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}