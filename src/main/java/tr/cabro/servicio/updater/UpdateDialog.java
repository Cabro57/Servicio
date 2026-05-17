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
 * Güncelleme diyaloğu — FlatLaf temasına uyumlu, 4 ekranlı.
 * <p>
 * Ekranlar:
 *   INFO     → GitHub Releases API'sinden asenkron yüklenen patch notları
 *   DOWNLOAD → Dosya bazlı ilerleme çubukları, canlı dosya listesi
 *   DONE     → Başarı, "Yeniden Başlat" → launcher script + shutdown()
 *   ERROR    → Hata mesajı, Tekrar Dene / Kapat
 * <p>
 * PatchNote / getPatchNotes() kullanılmaz — bunlar UpdateManifest'ten kaldırıldı.
 * Patch notları UpdateManager.fetchReleaseInfo() ile GitHub API'sinden çekilir.
 */
public class UpdateDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(UpdateDialog.class);

    // ─── Renkler (FlatLaf uyumlu) ─────────────────────────────────────────────

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

    /**
     * true  → aynı sürüm numarası, dosya içeriği değişmiş (hotfix/yama).
     * false → yeni sürüm numarası.
     */
    private final boolean isHotfix;

    // ─── Geri Çağrılar ────────────────────────────────────────────────────────

    @Setter
    private Consumer<String> onSkipVersion;

    // ─── Kart sistemi ─────────────────────────────────────────────────────────

    private final CardLayout cards     = new CardLayout();
    private final JPanel     cardPanel = new JPanel(cards);

    // INFO ekranı — asenkron doldurulur
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

    /** Geriye dönük uyumluluk — isHotfix=false. */
    public UpdateDialog(JFrame owner, UpdateManifest manifest, UpdateManager manager) {
        this(owner, manifest, manager, false);
    }

    public UpdateDialog(JFrame owner, UpdateManifest manifest,
                        UpdateManager manager, boolean isHotfix) {
        super(owner,
                isHotfix
                        ? "Kritik Güncelleme (Yama) — v" + manifest.getVersion()
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

        // Diyalog gösterilirken arka planda patch notlarını çek
        fetchReleaseNotesAsync();
    }

    // ─── Kart Kurulumu ────────────────────────────────────────────────────────

    private void buildCards() {
        cardPanel.add(buildInfoCard(),     "INFO");
        cardPanel.add(buildDownloadCard(), "DOWNLOAD");
        cardPanel.add(buildDoneCard(),     "DONE");
        cardPanel.add(buildErrorCard(),    "ERROR");
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 1 — Bilgi + Patch Notları
    // ════════════════════════════════════════════════════════════

    private JPanel buildInfoCard() {
        JPanel root = new JPanel(new BorderLayout(0, 0));

        // ── Başlık ──
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBorder(new EmptyBorder(22, 26, 18, 26));
        header.setOpaque(false);

        String titleText = (isHotfix ? "🔧  Kritik Güncelleme — " : "🚀  Yeni Sürüm — ")
                + "v" + manifest.getVersion();
        JLabel title = styledLabel(titleText, 17, Font.BOLD);

        String dateStr = manifest.getReleaseDate() != null && !manifest.getReleaseDate().isEmpty()
                ? "Yayın tarihi: " + manifest.getReleaseDate() : "";
        JLabel sub = styledLabel(dateStr, 12, Font.PLAIN);
        sub.setForeground(fgDim());

        header.add(title, BorderLayout.CENTER);
        header.add(sub,   BorderLayout.SOUTH);

        JSeparator sep1 = new JSeparator();

        // ── Patch Notları alanı (GitHub API'sinden asenkron doldurulur) ──
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

        // ── Alt bilgi ──
        int fileCount = manifest.getFiles() != null ? manifest.getFiles().size() : 0;
        JLabel hint = styledLabel(
                "  " + fileCount + " bileşen kontrol edilecek — yalnızca değişenler indirilir",
                11, Font.PLAIN);
        hint.setForeground(fgDim());
        hint.setBorder(new EmptyBorder(8, 10, 6, 10));

        // ── Butonlar ──
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnRow.setOpaque(false);

        final JButton skipBtn   = ghostButton("Bu Sürümü Atla");
        final JButton laterBtn  = ghostButton("Sonra Hatırlat");
        final JButton updateBtn = accentButton("Güncelle  ▶");

        skipBtn.setVisible(!isHotfix); // Hotfix'te "atla" anlamsız

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
        topBlock.add(header, BorderLayout.CENTER);
        topBlock.add(sep1,   BorderLayout.SOUTH);

        root.add(topBlock, BorderLayout.NORTH);
        root.add(scroll,   BorderLayout.CENTER);
        root.add(south,    BorderLayout.SOUTH);

        return root;
    }

    // ─── GitHub Release Notları Asenkron Yükleme ─────────────────────────────

    private void fetchReleaseNotesAsync() {
        manager.fetchReleaseInfo(
                manifest,
                info -> SwingUtilities.invokeLater(() -> populateReleaseNotes(info)),
                ex   -> {
                    log.warn("Patch notları yüklenemedi: {}", ex.getMessage());
                    SwingUtilities.invokeLater(this::showNotesError);
                }
        );
    }

    /** Release bilgisini notesWrapper'a doldurur. EDT'de çağrılmalıdır. */
    private void populateReleaseNotes(UpdateManifest.GitHubReleaseInfo info) {
        notesWrapper.removeAll();

        // Release başlığı
        String releaseName = (info.name != null && !info.name.isEmpty()) ? info.name : info.tagName;
        JLabel titleLbl = styledLabel(releaseName, 13, Font.BOLD);
        titleLbl.setForeground(accent());
        titleLbl.setBorder(new EmptyBorder(4, 0, 4, 0));
        notesWrapper.add(titleLbl);

        // Tarih
        if (info.publishedAt != null && !info.publishedAt.isEmpty()) {
            JLabel dateLbl = styledLabel("  " + info.publishedAt, 11, Font.PLAIN);
            dateLbl.setForeground(fgDim());
            dateLbl.setBorder(new EmptyBorder(0, 0, 8, 0));
            notesWrapper.add(dateLbl);
        }

        // Değişiklik satırları
        List<String> lines = info.changeLines;
        if (lines != null && !lines.isEmpty()) {
            for (String line : lines) {
                if (line.startsWith("**") && line.endsWith("**")) {
                    // Başlık satırı
                    String heading = line.substring(2, line.length() - 2);
                    JLabel hl = styledLabel("  " + heading, 12, Font.BOLD);
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
            notesWrapper.add(styledLabel("  Bu sürüm için açıklama bulunmuyor.", 12, Font.PLAIN));
        }

        notesWrapper.revalidate();
        notesWrapper.repaint();
    }

    private void showNotesError() {
        notesWrapper.removeAll();
        JLabel err = styledLabel(
                "  Sürüm notları yüklenemedi. GitHub bağlantısını kontrol edin.",
                12, Font.PLAIN);
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

        JPanel barsPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        barsPanel.setOpaque(false);
        barsPanel.add(dimLabel("Şu anki dosya:"));
        barsPanel.add(currentFileBar);
        barsPanel.add(currentFileLabel);
        barsPanel.add(Box.createVerticalStrut(4));
        barsPanel.add(dimLabel("Genel ilerleme:"));
        barsPanel.add(totalBar);
        barsPanel.add(statusLabel);

        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);
        fileListPanel.setBorder(new EmptyBorder(6, 4, 6, 4));

        JScrollPane listScroll = new JScrollPane(fileListPanel);
        listScroll.setBorder(BorderFactory.createLineBorder(sep()));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getVerticalScrollBar().setUnitIncrement(8);

        final JButton cancelBtn = ghostButton("İptal");
        cancelBtn.addActionListener(e -> { manager.cancel(); dispose(); });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        btnRow.setOpaque(false);
        btnRow.add(cancelBtn);

        JPanel topSection = new JPanel(new BorderLayout(0, 12));
        topSection.setOpaque(false);
        topSection.add(title,     BorderLayout.NORTH);
        topSection.add(barsPanel, BorderLayout.CENTER);

        root.add(topSection, BorderLayout.NORTH);
        root.add(listScroll, BorderLayout.CENTER);
        root.add(btnRow,     BorderLayout.SOUTH);

        return root;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 3 — Tamamlandı
    // ════════════════════════════════════════════════════════════

    private JPanel buildDoneCard() {
        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;

        JLabel icon = styledLabel("✔", 52, Font.BOLD);
        icon.setForeground(green());

        JLabel msg = styledLabel("Güncelleme Hazır!", 20, Font.BOLD);

        JLabel sub = styledLabel(
                "<html><center>Değişikliklerin uygulanması için<br>" +
                        "uygulama yeniden başlatılacak.</center></html>",
                13, Font.PLAIN);
        sub.setForeground(fgDim());
        sub.setHorizontalAlignment(SwingConstants.CENTER);

        final JButton restartBtn = accentButton("Yeniden Başlat  ↺");
        JButton       laterBtn   = ghostButton("Daha Sonra");

        restartBtn.addActionListener(e -> {
            restartBtn.setEnabled(false);
            restartBtn.setText("Uygulanıyor…");
            applyAndRestart();
        });
        laterBtn.addActionListener(e -> dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setOpaque(false);
        btnRow.add(laterBtn);
        btnRow.add(restartBtn);

        c.gridy = 0; c.insets = new Insets(0, 0, 12, 0); root.add(icon,   c);
        c.gridy = 1; c.insets = new Insets(0, 0, 8,  0); root.add(msg,    c);
        c.gridy = 2; c.insets = new Insets(0, 0, 32, 0); root.add(sub,    c);
        c.gridy = 3; c.insets = new Insets(0, 0, 0,  0); root.add(btnRow, c);

        return root;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 4 — Hata
    // ════════════════════════════════════════════════════════════

    private JPanel buildErrorCard() {
        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;

        JLabel icon = styledLabel("✘", 52, Font.BOLD);
        icon.setForeground(red());

        JLabel msg = styledLabel("İndirme Başarısız", 20, Font.BOLD);

        JLabel sub = styledLabel(
                "<html><center>Lütfen internet bağlantınızı kontrol edip<br>" +
                        "tekrar deneyin.</center></html>",
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
                (fileName, pct) -> SwingUtilities.invokeLater(() -> {
                    currentFileLabel.setText(fileName);
                    currentFileBar.setValue((int)(pct * 100));
                }),
                fileName -> SwingUtilities.invokeLater(() -> appendFileRow(fileName, RowState.SKIPPED)),
                fileName -> SwingUtilities.invokeLater(() -> appendFileRow(fileName, RowState.DONE)),
                ()       -> SwingUtilities.invokeLater(() -> showCard("DONE")),
                ex       -> SwingUtilities.invokeLater(() -> {
                    log.error("İndirme hatası", ex);
                    statusLabel.setText("Hata: " + ex.getMessage());
                    showCard("ERROR");
                })
        );
    }

    private void applyAndRestart() {
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
                    statusLabel.setText("Uygulama hatası: " + e.getMessage());
                    showCard("ERROR");
                });
            }
        }, "servicio-apply-update").start();
    }

    // ─── Dosya Listesi ────────────────────────────────────────────────────────

    private enum RowState { DONE, SKIPPED, ERROR }

    private void appendFileRow(String name, RowState state) {
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
        b.setPreferredSize(new Dimension(140, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}