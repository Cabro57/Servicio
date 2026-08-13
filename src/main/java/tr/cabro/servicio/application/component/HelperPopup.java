package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.updater.UpdateChecker;
import tr.cabro.servicio.updater.UpdateDialog;
import tr.cabro.servicio.updater.UpdateManifest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Helper butonuna tıklandığında açılan bilgi popup'ı.
 * <p>
 * İçerik:
 *   - Uygulama sürümü
 *   - Güncelleme durumu:
 *       • "Güncel" → yeşil tik
 *       • "Kontrol ediliyor…" → spinner
 *       • "Güncelleme mevcut: vX.Y.Z" → mavi "Güncelle" butonu
 *   - Manuel "Güncellemeleri Kontrol Et" bağlantısı
 * <p>
 * Popup, helper butonunun sol üst köşesine hizalanır.
 */
public class HelperPopup {

    // ─── Renkler (FlatLaf uyumlu) ─────────────────────────────────────────────

    private static Color accent()  { return getUI("Button.default.background", new Color(75, 110, 175)); }
    private static Color fg()      { return getUI("Label.foreground",           new Color(220, 220, 220)); }
    private static Color fgDim()   { return getUI("Label.disabledForeground",   new Color(140, 140, 140)); }
    private static Color bgPanel() { return getUI("Panel.background",           new Color(60, 63, 65)); }
    private static Color green()   { return new Color(98, 151, 85); }
    private static Color orange()  { return new Color(200, 140, 60); }

    private static Color getUI(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    private HelperPopup() {}

    /**
     * Helper butonu için popup oluşturur ve gösterir.
     *
     * @param owner  Popup'ın bağlandığı buton.
     * @param frame  UpdateDialog'un sahibi.
     */
    public static void show(final JButton owner, final JFrame frame) {
        JPopupMenu popup = buildPopup(owner, frame);

        // Butonun sol üst köşesine hizala, yukarı aç
        Dimension popupSize = popup.getPreferredSize();
        popup.show(owner, 0, -popupSize.height - 4);
    }

    // ─── Popup İnşası ─────────────────────────────────────────────────────────

    private static JPopupMenu buildPopup(final JButton owner, final JFrame frame) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(12, 16, 12, 16));
        content.setPreferredSize(new Dimension(260, -1));

        // ── Başlık satırı ──
        JPanel titleRow = row();
        JLabel appIcon  = new JLabel(new Ikon("icons/git-merge.svg", 0.85f));
        JLabel appName  = styledLabel("Servicio", 13, Font.BOLD);
        JLabel version  = styledLabel(
                "v" + Servicio.getInstance().getAppVersion(), 11, Font.PLAIN);
        version.setForeground(fgDim());
        version.setBorder(new EmptyBorder(0, 6, 0, 0));

        titleRow.add(appIcon);
        titleRow.add(Box.createHorizontalStrut(6));
        titleRow.add(appName);
        titleRow.add(version);
        titleRow.add(Box.createHorizontalGlue());

        content.add(titleRow);
        content.add(vspace(10));
        content.add(separator());
        content.add(vspace(10));

        // ── Güncelleme durumu ──
        JPanel updateRow = buildUpdateRow(popup, owner, frame);
        content.add(updateRow);

        content.add(vspace(10));
        content.add(separator());
        content.add(vspace(8));

        // ── "Güncellemeleri Kontrol Et" linki ──
        JLabel checkLink = styledLabel(Messages.get("updater.helper.checkbutton"), 11, Font.PLAIN);
        checkLink.setForeground(accent());
        checkLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        checkLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                UpdateChecker checker = Servicio.getInstance().getUpdateChecker();
                checker.checkNow();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                checkLink.setText("<html><u>" + Messages.get("updater.helper.checkbutton") + "</u></html>");
            }
            @Override
            public void mouseExited(MouseEvent e) {
                checkLink.setText(Messages.get("updater.helper.checkbutton"));
            }
        });

        content.add(checkLink);

        popup.add(content, BorderLayout.CENTER);
        popup.pack();
        return popup;
    }

    /**
     * Güncelleme durumuna göre uygun satırı üretir.
     * <p>
     * Durum:
     *   A) splashCheckDone=false  → "Kontrol ediliyor…"
     *   B) hasPendingUpdate=true  → "Güncelleme mevcut: vX" + Güncelle butonu
     *   C) güncel                 → "Güncel ✔"
     */
    private static JPanel buildUpdateRow(final JPopupMenu popup,
                                         final JButton owner,
                                         final JFrame frame) {
        UpdateChecker checker = Servicio.getInstance().getUpdateChecker();
        JPanel row = row();

        if (!checker.isSplashCheckDone()) {
            // Hâlâ kontrol ediliyor
            JLabel spinner = styledLabel(Messages.get("updater.checking"), 12, Font.PLAIN);
            spinner.setForeground(fgDim());
            spinner.setIcon(new Ikon("icons/loader.svg", 0.75f, "Label.disabledForeground"));
            row.add(spinner);

        } else if (checker.hasPendingUpdate()) {
            // Güncelleme mevcut
            final UpdateManifest manifest = checker.getPendingUpdate();
            boolean isHotfix = tr.cabro.servicio.updater.UpdateManager
                    .sameVersion(manifest.getVersion(), Servicio.getInstance().getAppVersion());

            JLabel icon = new JLabel(new Ikon(
                    isHotfix ? "icons/wrench.svg" : "icons/arrow-up-circle.svg",
                    0.85f, "Component.accentColor"));
            JLabel text = styledLabel(
                    isHotfix ? Messages.get("updater.helper.patchAvailable") : Messages.get("updater.helper.updateAvailableShort"),
                    12, Font.PLAIN);
            text.setForeground(fg());

            JLabel verBadge = styledLabel("v" + manifest.getVersion(), 10, Font.BOLD);
            verBadge.setForeground(Color.WHITE);
            verBadge.setBackground(accent());
            verBadge.setOpaque(true);
            verBadge.setBorder(new EmptyBorder(1, 6, 1, 6));
            verBadge.putClientProperty(FlatClientProperties.STYLE, "arc:8;");

            JButton updateBtn = new JButton(isHotfix ? Messages.get("updater.helper.applyPatch") : Messages.get("updater.helper.update"));
            updateBtn.putClientProperty(FlatClientProperties.STYLE,
                    "arc:8; font: bold 11;");
            updateBtn.putClientProperty("JButton.buttonType", "default");
            updateBtn.setPreferredSize(new Dimension(100, 24));
            updateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            updateBtn.addActionListener(e -> {
                popup.setVisible(false);
                SwingUtilities.invokeLater(() -> {
                    UpdateDialog dialog = new UpdateDialog(frame, manifest,
                            checker.getManager(), isHotfix);
                    dialog.setOnSkipVersion(v ->
                            Servicio.getInstance().getUpdateChecker()
                                    .clearPendingUpdate());
                    dialog.setVisible(true);
                });
            });

            row.add(icon);
            row.add(Box.createHorizontalStrut(6));
            row.add(text);
            row.add(Box.createHorizontalStrut(6));
            row.add(verBadge);
            row.add(Box.createHorizontalGlue());
            row.add(updateBtn);

        } else {
            // Güncel
            JLabel icon = new JLabel(new Ikon("icons/check-circle.svg", 0.85f));
            icon.setForeground(green());
            JLabel text = styledLabel(Messages.get("updater.helper.uptodate"), 12, Font.PLAIN);
            text.setForeground(green());

            row.add(icon);
            row.add(Box.createHorizontalStrut(6));
            row.add(text);
        }

        return row;
    }

    // ─── UI Yardımcıları ──────────────────────────────────────────────────────

    private static JPanel row() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return p;
    }

    private static JLabel styledLabel(String text, int size, int style) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, style, size));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static Component vspace(int height) {
        return Box.createRigidArea(new Dimension(0, height));
    }

    private static JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }
}
