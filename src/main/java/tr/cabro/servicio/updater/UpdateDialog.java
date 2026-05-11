//package tr.cabro.servicio.updater;
//
//import lombok.Setter;
//
//import javax.swing.*;
//import javax.swing.border.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.util.List;
//import java.util.function.Consumer;
//
///**
// * IntelliJ IDEA tarzı güncelleme diyaloğu.
// *
// * Ekranlar:
// *  1. INFO   → Sürüm bilgisi + patch notları + Güncelle / Atla / Sonra Hatırlat butonları
// *  2. DOWNLOAD → Dosya bazında ilerleme çubukları + iptal
// *  3. DONE   → Başarı mesajı + Yeniden Başlat / Daha Sonra
// *  4. ERROR  → Hata mesajı + Tekrar Dene / Kapat
// */
//public class UpdateDialog extends JDialog {
//
//    // ─── Renkler (koyu tema) ──────────────────────────────────────────────────
//
//    private static final Color BG          = new Color(43,  43,  43);
//    private static final Color BG_PANEL    = new Color(60,  63,  65);
//    private static final Color BG_NOTES    = new Color(49,  51,  53);
//    private static final Color ACCENT      = new Color(75, 110, 175);
//    private static final Color ACCENT_HOV  = new Color(90, 130, 200);
//    private static final Color TEXT_MAIN   = new Color(220, 220, 220);
//    private static final Color TEXT_DIM    = new Color(140, 140, 140);
//    private static final Color TEXT_GREEN  = new Color(98,  151, 85);
//    private static final Color TEXT_RED    = new Color(204, 120, 120);
//    private static final Color SEPARATOR   = new Color(80,  80,  80);
//
//    // ─── Model ────────────────────────────────────────────────────────────────
//
//    private final UpdateManifest manifest;
//    private final UpdateManager  manager;
//
//    // ─── Geri Çağrılar ────────────────────────────────────────────────────────
//
//    @Setter
//    private Consumer<String> onSkipVersion;
//
//    // ─── UI Bileşenleri ───────────────────────────────────────────────────────
//
//    private final CardLayout   cardLayout  = new CardLayout();
//    private final JPanel       cardPanel   = new JPanel(cardLayout);
//
//    // Download ekranı bileşenleri
//    private JLabel             currentFileLabel;
//    private JProgressBar       currentFileBar;
//    private JProgressBar       totalBar;
//    private JLabel             statusLabel;
//    private JButton            cancelBtn;
//    private JPanel             fileListPanel;
//
//    private int  totalFiles     = 0;
//    private int  doneFiles      = 0;
//
//    // ─── Oluşturucu ───────────────────────────────────────────────────────────
//
//    public UpdateDialog(JFrame owner, UpdateManifest manifest, UpdateManager manager) {
//        super(owner, "Güncelleme Mevcut — v" + manifest.getVersion(), true);
//        this.manifest = manifest;
//        this.manager  = manager;
//
//        setSize(600, 500);
//        setLocationRelativeTo(owner);
//        setResizable(false);
//        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
//
//        getContentPane().setBackground(BG);
//        getContentPane().setLayout(new BorderLayout());
//
//        buildCards();
//        getContentPane().add(cardPanel, BorderLayout.CENTER);
//
//        showCard("INFO");
//    }
//
//    // ─── Kart Oluşturucular ───────────────────────────────────────────────────
//
//    private void buildCards() {
//        cardPanel.setBackground(BG);
//        cardPanel.add(buildInfoCard(),     "INFO");
//        cardPanel.add(buildDownloadCard(), "DOWNLOAD");
//        cardPanel.add(buildDoneCard(),     "DONE");
//        cardPanel.add(buildErrorCard(),    "ERROR");
//    }
//
//    /** ── Ekran 1: Güncelleme bilgisi + patch notları ── */
//    private JPanel buildInfoCard() {
//        JPanel root = dark(new JPanel(new BorderLayout(0, 0)));
//
//        // Başlık
//        JPanel header = dark(new JPanel(new BorderLayout()));
//        header.setBorder(new EmptyBorder(20, 24, 16, 24));
//
//        JLabel title = label("🚀  Yeni Güncelleme Hazır", 18, Font.BOLD, TEXT_MAIN);
//        JLabel sub   = label("v" + manifest.getVersion() + "  •  " + manifest.getReleaseDate(),
//                             13, Font.PLAIN, TEXT_DIM);
//        header.add(title, BorderLayout.NORTH);
//        header.add(sub,   BorderLayout.SOUTH);
//
//        // Ayırıcı
//        JSeparator sep = new JSeparator();
//        sep.setForeground(SEPARATOR);
//
//        // Patch notları (kaydırılabilir)
//        JPanel notesPanel = new JPanel();
//        notesPanel.setLayout(new BoxLayout(notesPanel, BoxLayout.Y_AXIS));
//        notesPanel.setBackground(BG_NOTES);
//        notesPanel.setBorder(new EmptyBorder(12, 20, 12, 20));
//
//        List<UpdateManifest.PatchNote> notes = manifest.getPatchNotes();
//        for (UpdateManifest.PatchNote note : notes) {
//            JLabel verLabel = label("▸  " + note.getVersion() + "   " + note.getDate(),
//                    12, Font.BOLD, ACCENT);
//            verLabel.setBorder(new EmptyBorder(6, 0, 4, 0));
//            notesPanel.add(verLabel);
//
//            for (String change : note.getChanges()) {
//                JLabel cl = label("   " + change, 12, Font.PLAIN, TEXT_MAIN);
//                cl.setBorder(new EmptyBorder(1, 8, 1, 0));
//                notesPanel.add(cl);
//            }
//            notesPanel.add(Box.createVerticalStrut(8));
//        }
//
//        JScrollPane scroll = new JScrollPane(notesPanel);
//        scroll.setBorder(BorderFactory.createLineBorder(SEPARATOR, 1));
//        scroll.getViewport().setBackground(BG_NOTES);
//        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
//
//        // Dosya sayısı özeti
//        int fileCount = manifest.getFiles() != null ? manifest.getFiles().size() : 0;
//        JLabel fileInfo = label("Toplam " + fileCount + " bileşen kontrol edilecek " +
//                "(değişmeyenler atlanır)", 11, Font.PLAIN, TEXT_DIM);
//        fileInfo.setBorder(new EmptyBorder(8, 24, 0, 24));
//
//        // Butonlar
//        JPanel btnPanel = dark(new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12)));
//
//        JButton skipBtn   = ghostBtn("Bu Sürümü Atla");
//        JButton laterBtn  = ghostBtn("Sonra Hatırlat");
//        JButton updateBtn = accentBtn("Güncelle");
//
//        skipBtn.addActionListener(e -> {
//            if (onSkipVersion != null) onSkipVersion.accept(manifest.getVersion());
//            dispose();
//        });
//        laterBtn.addActionListener(e -> dispose());
//        updateBtn.addActionListener(e -> startDownload());
//
//        btnPanel.add(skipBtn);
//        btnPanel.add(laterBtn);
//        btnPanel.add(updateBtn);
//
//        root.add(header,   BorderLayout.NORTH);
//        root.add(sep,      BorderLayout.NORTH); // override → sadece görsel
//        JPanel center = dark(new JPanel(new BorderLayout()));
//        center.add(scroll,    BorderLayout.CENTER);
//        center.add(fileInfo,  BorderLayout.SOUTH);
//        center.setBorder(new EmptyBorder(0, 16, 0, 16));
//        root.add(center,   BorderLayout.CENTER);
//        root.add(btnPanel, BorderLayout.SOUTH);
//
//        // Başlık + ayırıcı birlikte
//        JPanel top = dark(new JPanel(new BorderLayout()));
//        top.add(header, BorderLayout.CENTER);
//        top.add(sep,    BorderLayout.SOUTH);
//        root.add(top, BorderLayout.NORTH);
//
//        return root;
//    }
//
//    /** ── Ekran 2: İndirme ilerleme ── */
//    private JPanel buildDownloadCard() {
//        JPanel root = dark(new JPanel(new BorderLayout(0, 0)));
//        root.setBorder(new EmptyBorder(20, 24, 16, 24));
//
//        JLabel title = label("İndiriliyor…", 16, Font.BOLD, TEXT_MAIN);
//
//        currentFileLabel = label("Hazırlanıyor…", 12, Font.PLAIN, TEXT_DIM);
//        currentFileBar   = progressBar(ACCENT);
//        totalBar         = progressBar(TEXT_GREEN);
//        statusLabel      = label("", 11, Font.PLAIN, TEXT_DIM);
//
//        fileListPanel = new JPanel();
//        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
//        fileListPanel.setBackground(BG_NOTES);
//        fileListPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
//
//        JScrollPane fileScroll = new JScrollPane(fileListPanel);
//        fileScroll.setBorder(BorderFactory.createLineBorder(SEPARATOR));
//        fileScroll.getViewport().setBackground(BG_NOTES);
//
//        cancelBtn = ghostBtn("İptal");
//        cancelBtn.addActionListener(e -> {
//            manager.cancel();
//            dispose();
//        });
//
//        JPanel btnPanel = dark(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
//        btnPanel.add(cancelBtn);
//
//        JPanel bars = dark(new JPanel(new GridLayout(0, 1, 0, 4)));
//        bars.add(label("Şu anki dosya:", 11, Font.PLAIN, TEXT_DIM));
//        bars.add(currentFileBar);
//        bars.add(currentFileLabel);
//        bars.add(Box.createVerticalStrut(6));
//        bars.add(label("Genel ilerleme:", 11, Font.PLAIN, TEXT_DIM));
//        bars.add(totalBar);
//        bars.add(statusLabel);
//
//        JPanel top = dark(new JPanel(new BorderLayout(0, 12)));
//        top.add(title, BorderLayout.NORTH);
//        top.add(bars,  BorderLayout.CENTER);
//
//        root.add(top,        BorderLayout.NORTH);
//        root.add(fileScroll, BorderLayout.CENTER);
//        root.add(btnPanel,   BorderLayout.SOUTH);
//
//        return root;
//    }
//
//    /** ── Ekran 3: Tamamlandı ── */
//    private JPanel buildDoneCard() {
//        JPanel root = dark(new JPanel(new GridBagLayout()));
//
//        JLabel icon = label("✔", 48, Font.BOLD, TEXT_GREEN);
//        JLabel msg  = label("Güncelleme İndirildi!", 18, Font.BOLD, TEXT_MAIN);
//        JLabel sub  = label("Değişikliklerin uygulanması için uygulamayı yeniden başlatın.",
//                13, Font.PLAIN, TEXT_DIM);
//
//        JButton restartBtn = accentBtn("Şimdi Yeniden Başlat");
//        JButton laterBtn   = ghostBtn("Daha Sonra");
//
//        restartBtn.addActionListener(e -> {
//            try {
//                manager.applyUpdate();
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//            // Uygulamayı yeniden başlatmak için launcher/script gerekir.
//            // Basit yöntem: JVM'i kapat, launcher tekrar açar.
//            System.exit(0);
//        });
//        laterBtn.addActionListener(e -> dispose());
//
//        GridBagConstraints c = new GridBagConstraints();
//        c.gridx = 0; c.gridy = 0; c.insets = new Insets(0, 0, 8,  0);
//        root.add(icon, c);
//        c.gridy = 1; c.insets = new Insets(0, 0, 6,  0);
//        root.add(msg, c);
//        c.gridy = 2; c.insets = new Insets(0, 0, 24, 0);
//        root.add(sub, c);
//
//        JPanel btns = dark(new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0)));
//        btns.add(laterBtn);
//        btns.add(restartBtn);
//        c.gridy = 3; c.insets = new Insets(0, 0, 0, 0);
//        root.add(btns, c);
//
//        return root;
//    }
//
//    /** ── Ekran 4: Hata ── */
//    private JPanel buildErrorCard() {
//        JPanel root = dark(new JPanel(new GridBagLayout()));
//
//        JLabel icon = label("✘", 48, Font.BOLD, TEXT_RED);
//        JLabel msg  = label("İndirme Başarısız", 18, Font.BOLD, TEXT_MAIN);
//        JLabel sub  = label("Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
//                13, Font.PLAIN, TEXT_DIM);
//
//        JButton retryBtn = accentBtn("Tekrar Dene");
//        JButton closeBtn = ghostBtn("Kapat");
//
//        retryBtn.addActionListener(e -> startDownload());
//        closeBtn.addActionListener(e -> dispose());
//
//        GridBagConstraints c = new GridBagConstraints();
//        c.gridx = 0; c.gridy = 0; c.insets = new Insets(0, 0, 8,  0);
//        root.add(icon, c);
//        c.gridy = 1; c.insets = new Insets(0, 0, 6,  0);
//        root.add(msg, c);
//        c.gridy = 2; c.insets = new Insets(0, 0, 24, 0);
//        root.add(sub, c);
//
//        JPanel btns = dark(new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0)));
//        btns.add(closeBtn);
//        btns.add(retryBtn);
//        c.gridy = 3;
//        root.add(btns, c);
//
//        return root;
//    }
//
//    // ─── İndirme Mantığı ──────────────────────────────────────────────────────
//
//    private void startDownload() {
//        showCard("DOWNLOAD");
//
//        fileListPanel.removeAll();
//        doneFiles  = 0;
//        totalFiles = manifest.getFiles() != null ? manifest.getFiles().size() : 0;
//        totalBar.setValue(0);
//
//        manager.downloadUpdate(
//                manifest,
//
//                // onProgress: (dosyaAdı, 0.0-1.0)
//                (fileName, progress) -> SwingUtilities.invokeLater(() -> {
//                    currentFileLabel.setText(fileName);
//                    currentFileBar.setValue((int)(progress * 100));
//                }),
//
//                // onFileSkipped: aynı hash → atlandı
//                (fileName) -> SwingUtilities.invokeLater(() -> {
//                    addFileRow(fileName, "SKIPPED");
//                    doneFiles++;
//                    totalBar.setValue((int)((double) doneFiles / totalFiles * 100));
//                    statusLabel.setText(doneFiles + " / " + totalFiles + " dosya işlendi");
//                }),
//
//                // onDone
//                SwingUtilities.invokeLater(() -> showCard("DONE")),
//
//                // onError
//                (ex) -> SwingUtilities.invokeLater(() -> {
//                    statusLabel.setText("Hata: " + ex);
//                    showCard("ERROR");
//                })
//        );
//
//        // İndirilen her dosyayı listeye ekle
//        manager.setCompletionCallback(bytes ->
//                SwingUtilities.invokeLater(() -> addFileRow("Tamamlandı", "DONE"))
//        );
//    }
//
//    private void addFileRow(String name, String state) {
//        Color stateColor = switch (state) {
//            case "DONE"    -> TEXT_GREEN;
//            case "SKIPPED" -> TEXT_DIM;
//            default        -> TEXT_RED;
//        };
//        String icon = switch (state) {
//            case "DONE"    -> "✔ ";
//            case "SKIPPED" -> "─ ";
//            default        -> "✘ ";
//        };
//
//        JLabel row = label(icon + name, 11, Font.PLAIN, stateColor);
//        row.setBorder(new EmptyBorder(2, 4, 2, 4));
//        fileListPanel.add(row);
//        fileListPanel.revalidate();
//        fileListPanel.repaint();
//
//        doneFiles++;
//        totalBar.setValue((int)((double) doneFiles / totalFiles * 100));
//        statusLabel.setText(doneFiles + " / " + totalFiles + " dosya işlendi");
//    }
//
//
//    // ─── UI Yardımcıları ──────────────────────────────────────────────────────
//
//    private void showCard(String name) { cardLayout.show(cardPanel, name); }
//
//    private static JLabel label(String text, int size, int style, Color color) {
//        JLabel l = new JLabel(text);
//        l.setFont(new Font("Segoe UI", style, size));
//        l.setForeground(color);
//        return l;
//    }
//
//    private static JProgressBar progressBar(Color color) {
//        JProgressBar b = new JProgressBar(0, 100);
//        b.setForeground(color);
//        b.setBackground(new Color(30, 30, 30));
//        b.setBorderPainted(false);
//        b.setPreferredSize(new Dimension(0, 6));
//        return b;
//    }
//
//    private static JButton accentBtn(String text) {
//        JButton b = new JButton(text);
//        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
//        b.setForeground(Color.WHITE);
//        b.setBackground(ACCENT);
//        b.setFocusPainted(false);
//        b.setBorderPainted(false);
//        b.setOpaque(true);
//        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
//        b.setPreferredSize(new Dimension(160, 32));
//        b.addMouseListener(new MouseAdapter() {
//            public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT_HOV); }
//            public void mouseExited (MouseEvent e) { b.setBackground(ACCENT); }
//        });
//        return b;
//    }
//
//    private static JButton ghostBtn(String text) {
//        JButton b = new JButton(text);
//        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
//        b.setForeground(TEXT_DIM);
//        b.setBackground(BG_PANEL);
//        b.setFocusPainted(false);
//        b.setBorderPainted(true);
//        b.setBorder(BorderFactory.createLineBorder(SEPARATOR));
//        b.setOpaque(true);
//        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
//        b.setPreferredSize(new Dimension(130, 32));
//        b.addMouseListener(new MouseAdapter() {
//            public void mouseEntered(MouseEvent e) { b.setForeground(TEXT_MAIN); }
//            public void mouseExited (MouseEvent e) { b.setForeground(TEXT_DIM); }
//        });
//        return b;
//    }
//
//    private static <T extends JComponent> T dark(T c) {
//        c.setBackground(BG);
//        return c;
//    }
//}
