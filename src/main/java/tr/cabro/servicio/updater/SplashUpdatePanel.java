package tr.cabro.servicio.updater;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Splash Screen üzerinde gösterilen güncelleme paneli.
 * <p>
 * Splash açıkken güncelleme bulunursa bu panel splash'in üzerine
 * bindirilerek (overlay) kullanıcıya sorulur.
 * <p>
 * Akış:
 *   NOTIFY  → "v2.1.0 mevcut, güncellemek ister misiniz?" + Evet / Hayır / Sürümü Atla
 *   DOWNLOAD → dosya bazlı ilerleme (splash'in progress barı da güncellenir)
 *   DONE    → "Güncelleme tamamlandı, uygulama başlatılıyor…"
 *   ERROR   → "Hata oluştu" + Tekrar Dene / Devam Et
 * <p>
 * Splash ekranınızın layout'u BorderLayout veya null olabilir;
 * bu panel JLayeredPane üzerinde PALETTE_LAYER'da gösterilir.
 */
public class SplashUpdatePanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(SplashUpdatePanel.class);

    // ─── Renkler ──────────────────────────────────────────────────────────────

    private static final Color BG_OVERLAY = new Color(20, 20, 20, 210);   // yarı saydam siyah
    private static final Color BG_CARD    = new Color(38, 40, 42);
    private static final Color ACCENT     = new Color(75, 110, 175);
    private static final Color ACCENT_H   = new Color(90, 130, 200);
    private static final Color TEXT_MAIN  = new Color(220, 220, 220);
    private static final Color TEXT_DIM   = new Color(150, 150, 150);
    private static final Color GREEN      = new Color(98,  151, 85);
    private static final Color RED        = new Color(204, 120, 120);
    private static final Color SEP        = new Color(65,  68,  70);

    // ─── Kart sistemi ─────────────────────────────────────────────────────────

    private final CardLayout cards     = new CardLayout();
    private final JPanel     cardPanel = new JPanel(cards);

    // DOWNLOAD ekranı bileşenleri
    private JLabel       currentFileLabel;
    private JProgressBar currentFileBar;
    private JProgressBar totalBar;
    private JLabel       statusLabel;
    private JPanel       fileListPanel;
    private int          totalFiles = 0;
    private int          doneFiles  = 0;

    // ─── Dışarıya açılan geri çağrılar ───────────────────────────────────────

    /** "Hayır" veya "Sürümü Atla" seçilince → uygulama normal başlar */
    @Setter
    private Runnable onSkip;

    /** İndirme + uygulama tamamlanınca → uygulama başlar */
    @Setter
    private Runnable onUpdateDone;

    /** "Bu sürümü atla" seçilince → sürüm kaydedilir */
    @Setter
    private Consumer<String> onSkipVersion;

    // ─── Bağımlılıklar ────────────────────────────────────────────────────────

    private final UpdateManifest manifest;
    private final UpdateManager  manager;

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    public SplashUpdatePanel(UpdateManifest manifest, UpdateManager manager) {
        this.manifest = manifest;
        this.manager  = manager;

        setOpaque(false);
        setLayout(new GridBagLayout()); // kartı ortalar

        cardPanel.setOpaque(false);
        cardPanel.add(buildNotifyCard(),   "NOTIFY");
        cardPanel.add(buildDownloadCard(), "DOWNLOAD");
        cardPanel.add(buildDoneCard(),     "DONE");
        cardPanel.add(buildErrorCard(),    "ERROR");

        add(cardPanel);
        showCard("NOTIFY");
    }

    // ─── Overlay arka plan çizimi ─────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(BG_OVERLAY);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 1 — Bildirim (Evet / Hayır)
    // ════════════════════════════════════════════════════════════

    private JPanel buildNotifyCard() {
        JPanel card = card(420, 260);

        // İkon + Başlık
        JLabel icon  = lbl("🔔", 28, Font.PLAIN, TEXT_MAIN);
        JLabel title = lbl("Güncelleme Mevcut!", 16, Font.BOLD, TEXT_MAIN);

        JPanel titleRow = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)));
        titleRow.add(icon);
        titleRow.add(title);

        // Sürüm bilgisi
        JLabel verLbl = lbl("Sürüm v" + manifest.getVersion()
                + (manifest.getReleaseDate() != null ? "  —  " + manifest.getReleaseDate() : ""),
                12, Font.PLAIN, ACCENT);

        // Patch notları özeti (ilk sürümün ilk 3 değişikliği)
        JPanel changesPanel = transparent(new JPanel());
        changesPanel.setLayout(new BoxLayout(changesPanel, BoxLayout.Y_AXIS));

        List<UpdateManifest.PatchNote> notes = manifest.getPatchNotes();
        if (notes != null && !notes.isEmpty()) {
            List<String> changes = notes.get(0).getChanges();
            if (changes != null) {
                int limit = Math.min(changes.size(), 3);
                for (int i = 0; i < limit; i++) {
                    JLabel cl = lbl("  • " + changes.get(i), 11, Font.PLAIN, TEXT_MAIN);
                    changesPanel.add(cl);
                }
                if (changes.size() > 3) {
                    changesPanel.add(lbl("  + " + (changes.size() - 3) + " değişiklik daha…",
                            11, Font.PLAIN, TEXT_DIM));
                }
            }
        }

        int fileCount = manifest.getFiles() != null ? manifest.getFiles().size() : 0;
        JLabel hint = lbl(fileCount + " bileşen kontrol edilecek, değişmeyenler atlanır",
                10, Font.PLAIN, TEXT_DIM);

        // Ayırıcı
        JSeparator sep = new JSeparator();
        sep.setForeground(SEP);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        // Butonlar
        JPanel btnRow = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0)));

        final JButton skipVerBtn = ghostBtn("Bu Sürümü Atla");
        final JButton noBtn      = ghostBtn("Şimdi Değil");
        final JButton yesBtn     = accentBtn("Güncelle  ▶");

        skipVerBtn.addActionListener(e -> {
            if (onSkipVersion != null) onSkipVersion.accept(manifest.getVersion());
            if (onSkip != null) onSkip.run();
        });
        noBtn.addActionListener(e -> {
            if (onSkip != null) onSkip.run();
        });
        yesBtn.addActionListener(e -> startDownload());

        btnRow.add(skipVerBtn);
        btnRow.add(noBtn);
        btnRow.add(yesBtn);

        // Kart düzeni
        card.add(Box.createVerticalStrut(18));
        card.add(titleRow);
        card.add(Box.createVerticalStrut(6));
        card.add(verLbl);
        card.add(Box.createVerticalStrut(12));
        card.add(sep);
        card.add(Box.createVerticalStrut(10));
        card.add(changesPanel);
        card.add(Box.createVerticalStrut(6));
        card.add(hint);
        card.add(Box.createVerticalGlue());
        card.add(btnRow);
        card.add(Box.createVerticalStrut(16));

        return card;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 2 — İndirme İlerlemesi
    // ════════════════════════════════════════════════════════════

    private JPanel buildDownloadCard() {
        JPanel card = card(420, 280);

        JLabel title = lbl("Güncelleme İndiriliyor…", 14, Font.BOLD, TEXT_MAIN);

        currentFileLabel = lbl("Hazırlanıyor…", 11, Font.PLAIN, TEXT_DIM);
        currentFileBar   = mkBar(ACCENT, 7);
        totalBar         = mkBar(GREEN,  5);
        statusLabel      = lbl("", 10, Font.PLAIN, TEXT_DIM);

        // Dosya listesi (küçük, kaydırılabilir)
        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(fileListPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(SEP));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(380, 90));
        scroll.getVerticalScrollBar().setUnitIncrement(8);

        final JButton cancelBtn = ghostBtn("İptal");
        cancelBtn.addActionListener(e -> {
            manager.cancel();
            if (onSkip != null) onSkip.run();
        });

        JPanel btnRow = transparent(new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)));
        btnRow.add(cancelBtn);

        card.add(Box.createVerticalStrut(18));
        card.add(title);
        card.add(Box.createVerticalStrut(14));
        card.add(dimLbl("Şu anki dosya:"));
        card.add(Box.createVerticalStrut(3));
        card.add(currentFileBar);
        card.add(Box.createVerticalStrut(2));
        card.add(currentFileLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(dimLbl("Genel ilerleme:"));
        card.add(Box.createVerticalStrut(3));
        card.add(totalBar);
        card.add(Box.createVerticalStrut(2));
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(scroll);
        card.add(Box.createVerticalStrut(8));
        card.add(btnRow);
        card.add(Box.createVerticalStrut(8));

        return card;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 3 — Tamamlandı
    // ════════════════════════════════════════════════════════════

    private JPanel buildDoneCard() {
        JPanel card = card(420, 200);

        JLabel icon = lbl("✔", 40, Font.BOLD, GREEN);
        JLabel msg  = lbl("Güncelleme Hazır!", 16, Font.BOLD, TEXT_MAIN);
        JLabel sub  = lbl("Uygulama başlatılıyor…", 12, Font.PLAIN, TEXT_DIM);

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(200, 4));
        spinner.setBorderPainted(false);
        spinner.setForeground(ACCENT);
        spinner.setMaximumSize(new Dimension(200, 4));

        card.add(Box.createVerticalGlue());
        card.add(center(icon));
        card.add(Box.createVerticalStrut(8));
        card.add(center(msg));
        card.add(Box.createVerticalStrut(4));
        card.add(center(sub));
        card.add(Box.createVerticalStrut(16));
        card.add(center(spinner));
        card.add(Box.createVerticalGlue());

        return card;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 4 — Hata
    // ════════════════════════════════════════════════════════════

    private JPanel buildErrorCard() {
        JPanel card = card(420, 220);

        JLabel icon = lbl("✘", 40, Font.BOLD, RED);
        JLabel msg  = lbl("İndirme Başarısız", 15, Font.BOLD, TEXT_MAIN);
        JLabel sub  = lbl("İnternet bağlantınızı kontrol edin.", 11, Font.PLAIN, TEXT_DIM);

        JButton retryBtn    = accentBtn("Tekrar Dene");
        JButton continueBtn = ghostBtn("Uygulamayı Başlat");

        retryBtn.addActionListener(e -> startDownload());
        continueBtn.addActionListener(e -> {
            if (onSkip != null) onSkip.run();
        });

        JPanel btnRow = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0)));
        btnRow.add(continueBtn);
        btnRow.add(retryBtn);

        card.add(Box.createVerticalGlue());
        card.add(center(icon));
        card.add(Box.createVerticalStrut(6));
        card.add(center(msg));
        card.add(Box.createVerticalStrut(4));
        card.add(center(sub));
        card.add(Box.createVerticalStrut(20));
        card.add(btnRow);
        card.add(Box.createVerticalGlue());

        return card;
    }

    // ─── İndirme Mantığı ──────────────────────────────────────────────────────

    private void startDownload() {
        showCard("DOWNLOAD");

        fileListPanel.removeAll();
        doneFiles  = 0;
        totalFiles = manifest.getFiles() != null ? manifest.getFiles().size() : 1;
        totalBar.setValue(0);
        currentFileBar.setValue(0);
        currentFileLabel.setText("Hazırlanıyor…");
        statusLabel.setText("");

        manager.downloadUpdate(
                manifest,

                // onProgress
                (fileName, pct) -> SwingUtilities.invokeLater(() -> {
                    currentFileLabel.setText(fileName);
                    currentFileBar.setValue((int)(pct * 100));
                }),

                // onFileSkipped
                name -> SwingUtilities.invokeLater(() -> appendRow(name, RowState.SKIPPED)),

                // onFileDone
                name -> SwingUtilities.invokeLater(() -> appendRow(name, RowState.DONE)),

                // onDone — güncellemeyi uygula, sonra uygulamayı başlat
                this::applyAndContinue,

                // onError
                ex -> {
                    log.error("Güncelleme indirme hatası", ex);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Hata: " + ex.getMessage());
                        showCard("ERROR");
                    });
                }
        );
    }

    /** İndirme bitti → dosyaları taşı → DONE ekranını göster → uygulamayı başlat */
    private void applyAndContinue() {
        SwingUtilities.invokeLater(() -> showCard("DONE"));

        new Thread(() -> {
            try {
                manager.applyUpdate();
                log.info("Güncelleme uygulandı. Launcher script yazılıyor…");

                File script = manager.writeLauncherScript("servicio.jar", "");
                manager.launchAndExit(script);

                // Kısa bekleme — kullanıcı DONE ekranını görsün
                Thread.sleep(1500);

                // Servicio.shutdown() yerine System.exit direkt; uygulama henüz tam başlamadı
                log.info("Güncelleme tamamlandı, JVM kapatılıyor.");
                System.exit(0);

            } catch (Exception ex) {
                log.error("Güncelleme uygulama hatası", ex);
                SwingUtilities.invokeLater(() -> showCard("ERROR"));
            }
        }, "servicio-apply-update").start();
    }

    // ─── Dosya Satırı ────────────────────────────────────────────────────────

    private enum RowState { DONE, SKIPPED, ERROR }

    private void appendRow(String name, RowState state) {
        String prefix;
        Color  color;
        if (state == RowState.DONE)    { prefix = " ✔  "; color = GREEN; }
        else if (state == RowState.SKIPPED) { prefix = " ─  "; color = TEXT_DIM; }
        else                           { prefix = " ✘  "; color = RED; }

        JLabel row = lbl(prefix + name, 10, Font.PLAIN, color);
        row.setBorder(new EmptyBorder(1, 6, 1, 6));
        fileListPanel.add(row);
        fileListPanel.revalidate();
        fileListPanel.repaint();

        doneFiles++;
        if (totalFiles > 0) totalBar.setValue((int)((double) doneFiles / totalFiles * 100));
        statusLabel.setText(doneFiles + " / " + totalFiles + " dosya işlendi");
    }

    // ─── UI Yardımcıları ─────────────────────────────────────────────────────

    private void showCard(String name) { cards.show(cardPanel, name); }

    /** Yuvarlak köşeli, hafif gölgeli kart paneli */
    private static JPanel card(int w, int h) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Gölge
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(4, 6, getWidth() - 6, getHeight() - 6, 16, 16);
                // Kart arka planı
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 14, 14);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(0, 24, 0, 24));
        p.setPreferredSize(new Dimension(w, h));
        p.setMaximumSize(new Dimension(w, h));
        return p;
    }

    private static JLabel lbl(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, style, size));
        l.setForeground(color);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JLabel dimLbl(String text) {
        return lbl(text, 10, Font.PLAIN, TEXT_DIM);
    }

    private static JPanel center(JComponent c) {
        JPanel p = transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)));
        p.add(c);
        return p;
    }

    private static <T extends JPanel> T transparent(T p) {
        p.setOpaque(false);
        return p;
    }

    private static JProgressBar mkBar(Color color, int height) {
        JProgressBar b = new JProgressBar(0, 100);
        b.setForeground(color);
        b.setBorderPainted(false);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        b.setPreferredSize(new Dimension(0, height));
        return b;
    }

    private static JButton accentBtn(String text) {
        final JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(ACCENT);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(140, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(ACCENT_H); }
            @Override public void mouseExited (java.awt.event.MouseEvent e) { b.setBackground(ACCENT);   }
        });
        return b;
    }

    private static JButton ghostBtn(String text) {
        final JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        b.setForeground(TEXT_DIM);
        b.setBackground(new Color(55, 57, 59));
        b.setOpaque(true);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createLineBorder(SEP));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(128, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { b.setForeground(TEXT_MAIN); }
            @Override public void mouseExited (java.awt.event.MouseEvent e) { b.setForeground(TEXT_DIM);  }
        });
        return b;
    }
}
