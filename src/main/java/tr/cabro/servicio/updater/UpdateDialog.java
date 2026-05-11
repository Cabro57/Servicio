package tr.cabro.servicio.updater;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.Servicio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Güncelleme diyaloğu — FlatLaf temasına uyumlu, 4 ekranlı.
 *
 * Ekranlar:
 *   INFO     → Patch notları, dosya sayısı özeti, Güncelle / Atla / Sonra
 *   DOWNLOAD → Dosya bazlı ilerleme çubukları, canlı dosya listesi
 *   DONE     → Başarı, "Yeniden Başlat" → launcher script çalıştır + shutdown()
 *   ERROR    → Hata mesajı, Tekrar Dene / Kapat
 *
 * FlatLaf renkleri UIManager üzerinden okunur; ayrı renk sabiti tanımlanmamıştır.
 * Bu sayede kullanıcının seçtiği tema (koyu/açık) otomatik yansır.
 */
public class UpdateDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(UpdateDialog.class);

    // ─── Renkler (FlatLaf uyumlu) ─────────────────────────────────────────────

    // Buton renkleri: UIManager'dan alınır, fallback sabitler sadece UIManager boşsa kullanılır
    private static Color accent()   { return getUI("Button.default.background", new Color(75, 110, 175)); }
    private static Color accentHov(){ return getUI("Button.default.background", new Color(75, 110, 175)).brighter(); }
    private static Color fg()       { return getUI("Label.foreground",           new Color(220, 220, 220)); }
    private static Color fgDim()    { return getUI("Label.disabledForeground",   new Color(140, 140, 140)); }
    private static Color bgPanel()  { return getUI("Panel.background",           new Color(60, 63, 65)); }
    private static Color green()    { return new Color(98, 151, 85); }
    private static Color red()      { return new Color(204, 120, 120); }
    private static Color sep()      { return getUI("Separator.foreground",       new Color(80, 80, 80)); }

    private static Color getUI(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    // ─── Model / Bağımlılıklar ────────────────────────────────────────────────

    private final UpdateManifest manifest;
    private final UpdateManager  manager;

    // ─── Geri Çağrılar ────────────────────────────────────────────────────────

    @Setter
    private Consumer<String> onSkipVersion;

    // ─── Kart sistemi ─────────────────────────────────────────────────────────

    private final CardLayout cards     = new CardLayout();
    private final JPanel     cardPanel = new JPanel(cards);

    // Download ekranı bileşenleri
    private JLabel       currentFileLabel;
    private JProgressBar currentFileBar;
    private JProgressBar totalBar;
    private JLabel       statusLabel;
    private JPanel       fileListPanel;

    private int totalFiles = 0;
    private int doneFiles  = 0;

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    public UpdateDialog(JFrame owner, UpdateManifest manifest, UpdateManager manager) {
        super(owner, "Güncelleme Mevcut — v" + manifest.getVersion(), true);
        this.manifest = manifest;
        this.manager  = manager;

        setSize(620, 540);
        setMinimumSize(new Dimension(500, 420));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // FlatLaf getRootPane rounding
        getRootPane().putClientProperty("JRootPane.titleBarBackground",
                UIManager.getColor("TitlePane.background"));

        buildCards();
        add(cardPanel);
        showCard("INFO");
    }

    // ─── Kartları Oluştur ─────────────────────────────────────────────────────

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

        JLabel title = styledLabel("Yeni Sürüm Hazır — v" + manifest.getVersion(), 17, Font.BOLD);
        JLabel sub   = styledLabel(manifest.getReleaseDate() != null
                ? "Yayın tarihi: " + manifest.getReleaseDate() : "", 12, Font.PLAIN);
        sub.setForeground(fgDim());

        header.add(title, BorderLayout.CENTER);
        header.add(sub,   BorderLayout.SOUTH);

        JSeparator sep1 = new JSeparator();

        // ── Patch Notları (kaydırılabilir) ──
        JPanel notesWrapper = new JPanel();
        notesWrapper.setLayout(new BoxLayout(notesWrapper, BoxLayout.Y_AXIS));
        notesWrapper.setBorder(new EmptyBorder(10, 22, 10, 22));
        notesWrapper.setOpaque(false);

        List<UpdateManifest.PatchNote> patchNotes = manifest.getPatchNotes();
        if (patchNotes != null) {
            for (UpdateManifest.PatchNote note : patchNotes) {
                JLabel versionLabel = styledLabel(
                        "▸  " + note.getVersion() + "   " + (note.getDate() != null ? note.getDate() : ""),
                        12, Font.BOLD);
                versionLabel.setForeground(accent());
                versionLabel.setBorder(new EmptyBorder(8, 0, 4, 0));
                notesWrapper.add(versionLabel);

                List<String> changes = note.getChanges();
                if (changes != null) {
                    for (String change : changes) {
                        JLabel cl = styledLabel("     " + change, 12, Font.PLAIN);
                        cl.setBorder(new EmptyBorder(1, 10, 1, 0));
                        notesWrapper.add(cl);
                    }
                }
                notesWrapper.add(Box.createVerticalStrut(6));
            }
        } else {
            notesWrapper.add(styledLabel("Yenilik bilgisi bulunamadı.", 12, Font.PLAIN));
        }

        JScrollPane scroll = new JScrollPane(notesWrapper);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, sep()));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(10);

        // ── Alt Bilgi ──
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

        skipBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onSkipVersion != null) onSkipVersion.accept(manifest.getVersion());
                dispose();
            }
        });
        laterBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { dispose(); }
        });
        updateBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { startDownload(); }
        });

        btnRow.add(skipBtn);
        btnRow.add(laterBtn);
        btnRow.add(updateBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(hint,   BorderLayout.NORTH);
        south.add(btnRow, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(sep1,   BorderLayout.NORTH);   // replaced below
        JPanel topBlock = new JPanel(new BorderLayout());
        topBlock.setOpaque(false);
        topBlock.add(header, BorderLayout.CENTER);
        topBlock.add(sep1,   BorderLayout.SOUTH);
        root.add(topBlock, BorderLayout.NORTH);
        root.add(scroll,   BorderLayout.CENTER);
        root.add(south,    BorderLayout.SOUTH);

        return root;
    }

    // ════════════════════════════════════════════════════════════
    // EKRAN 2 — İndirme İlerlemesi
    // ════════════════════════════════════════════════════════════

    private JPanel buildDownloadCard() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(22, 26, 14, 26));

        JLabel title = styledLabel("Güncelleme İndiriliyor…", 16, Font.BOLD);

        // İlerleme çubukları
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

        // Dosya listesi
        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);
        fileListPanel.setBorder(new EmptyBorder(6, 4, 6, 4));

        JScrollPane listScroll = new JScrollPane(fileListPanel);
        listScroll.setBorder(BorderFactory.createLineBorder(sep()));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getVerticalScrollBar().setUnitIncrement(8);

        // İptal butonu
        final JButton cancelBtn = ghostButton("İptal");
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                manager.cancel();
                dispose();
            }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        btnRow.setOpaque(false);
        btnRow.add(cancelBtn);

        JPanel topSection = new JPanel(new BorderLayout(0, 12));
        topSection.setOpaque(false);
        topSection.add(title,    BorderLayout.NORTH);
        topSection.add(barsPanel,BorderLayout.CENTER);

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
                "<html><center>Değişikliklerin uygulanması için<br>uygulama yeniden başlatılacak.</center></html>",
                13, Font.PLAIN);
        sub.setForeground(fgDim());
        sub.setHorizontalAlignment(SwingConstants.CENTER);

        final JButton restartBtn = accentButton("Yeniden Başlat  ↺");
        JButton laterBtn         = ghostButton("Daha Sonra");

        restartBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restartBtn.setEnabled(false);
                restartBtn.setText("Uygulanıyor…");
                applyAndRestart();
            }
        });
        laterBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { dispose(); }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setOpaque(false);
        btnRow.add(laterBtn);
        btnRow.add(restartBtn);

        c.gridy = 0; c.insets = new Insets(0, 0, 12, 0); root.add(icon,    c);
        c.gridy = 1; c.insets = new Insets(0, 0, 8,  0); root.add(msg,     c);
        c.gridy = 2; c.insets = new Insets(0, 0, 32, 0); root.add(sub,     c);
        c.gridy = 3; c.insets = new Insets(0, 0, 0,  0); root.add(btnRow,  c);

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
                "<html><center>Lütfen internet bağlantınızı kontrol edip<br>tekrar deneyin.</center></html>",
                13, Font.PLAIN);
        sub.setForeground(fgDim());
        sub.setHorizontalAlignment(SwingConstants.CENTER);

        JButton retryBtn = accentButton("Tekrar Dene");
        JButton closeBtn = ghostButton("Kapat");

        retryBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { startDownload(); }
        });
        closeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { dispose(); }
        });

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

                // onProgress
                new java.util.function.BiConsumer<String, Double>() {
                    @Override
                    public void accept(final String fileName, final Double pct) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                currentFileLabel.setText(fileName);
                                currentFileBar.setValue((int)(pct * 100));
                            }
                        });
                    }
                },

                // onFileSkipped (hash aynı)
                new Consumer<String>() {
                    @Override
                    public void accept(final String fileName) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() { appendFileRow(fileName, RowState.SKIPPED); }
                        });
                    }
                },

                // onFileDone
                new Consumer<String>() {
                    @Override
                    public void accept(final String fileName) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() { appendFileRow(fileName, RowState.DONE); }
                        });
                    }
                },

                // onDone
                new Runnable() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() { showCard("DONE"); }
                        });
                    }
                },

                // onError
                new Consumer<Exception>() {
                    @Override
                    public void accept(final Exception ex) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                log.error("İndirme hatası", ex);
                                statusLabel.setText("Hata: " + ex.getMessage());
                                showCard("ERROR");
                            }
                        });
                    }
                }
        );
    }

    /** Güncellemeyi uygular ve launcher script aracılığıyla yeniden başlatır. */
    private void applyAndRestart() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Dosyaları uygulama köküne taşı
                    manager.applyUpdate();

                    // 2. Launcher script yaz (.bat / .sh)
                    File script = manager.writeLauncherScript("servicio.jar", "");

                    // 3. Script'i başlat (mevcut PID'i parametre olarak geçer)
                    manager.launchAndExit(script);

                    // 4. Uygulamayı kapat (Servicio.shutdown() kayıt + DB + yedek yapar)
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            dispose();
                            Servicio.getInstance().shutdown();
                        }
                    });

                } catch (Exception e) {
                    log.error("Uygulama sırasında hata", e);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            statusLabel.setText("Uygulama hatası: " + e.getMessage());
                            showCard("ERROR");
                        }
                    });
                }
            }
        }, "servicio-apply-update").start();
    }

    // ─── Dosya Listesi Satırı ─────────────────────────────────────────────────

    private enum RowState { DONE, SKIPPED, ERROR }

    private void appendFileRow(String name, RowState state) {
        String prefix;
        Color  color;

        if (state == RowState.DONE) {
            prefix = "  ✔  "; color = green();
        } else if (state == RowState.SKIPPED) {
            prefix = "  ─  "; color = fgDim();
        } else {
            prefix = "  ✘  "; color = red();
        }

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
        final JButton b = new JButton(text);
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

    // ─── Setter ───────────────────────────────────────────────────────────────

}