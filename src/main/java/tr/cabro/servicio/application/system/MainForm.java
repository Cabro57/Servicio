package tr.cabro.servicio.application.system;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import raven.modal.Toast;
import raven.modal.toast.ToastPromise;
import raven.modal.toast.option.ToastLocation;
import raven.modal.toast.option.ToastOption;
import tr.cabro.servicio.application.component.FormSearchButton;
import tr.cabro.servicio.application.component.MemoryBar;
import tr.cabro.servicio.application.component.RefreshLine;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.updater.UpdateChecker;
import tr.cabro.servicio.updater.UpdateManager;
import tr.cabro.servicio.updater.UpdateManifest;
import tr.cabro.servicio.updater.UpdateNotifier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.util.prefs.Preferences;

public class MainForm extends JPanel {

    private static final String TOAST_CHECK_ID   = "servicio-update-check";
    private static final String PREF_NODE        = "tr/cabro/servicio";
    private static final String PREF_SKIPPED_VER = "update.skipped_version";

    private boolean startupUpdateCheckScheduled = false;

    public MainForm() {
        init();

        // MainForm oluşturulduğu anda henüz frame'e eklenmemiş olabilir
        // (örn. kullanıcı PIN ekranındayken). Toast'ın parent penceresi
        // bulabilmesi için gerçekten ekranda görünür olana kadar bekle.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing() && !startupUpdateCheckScheduled) {
                startupUpdateCheckScheduled = true;
                scheduleStartupUpdateCheck();
            }
        });
    }

    private void init() {
        setLayout(new MigLayout("fillx,wrap,insets 0,gap 0", "[fill]", "[][][fill,grow][]"));
        add(createHeader());
        add(createRefreshLine(), "height 3!");
        add(createMain());
        add(new JSeparator(), "height 2!");
        add(createFooter());
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private JPanel createHeader() {
        JPanel panel = new JPanel(new MigLayout("insets 3", "[]push[]push", "[fill]"));
        JToolBar toolBar = new JToolBar();

        JButton buttonDrawer = new JButton(new Ikon("icons/menu.svg", 1f));
        buttonUndo    = new JButton(new Ikon("icons/arrow-left.svg", 1f));
        buttonRedo    = new JButton(new Ikon("icons/arrow-right.svg", 1f));
        buttonRefresh = new JButton(new Ikon("icons/refresh-cw.svg", 1f));

        buttonDrawer.putClientProperty(FlatClientProperties.STYLE, "arc:10;");
        buttonUndo.putClientProperty(FlatClientProperties.STYLE,   "arc:10;");
        buttonRedo.putClientProperty(FlatClientProperties.STYLE,   "arc:10;");
        buttonRefresh.putClientProperty(FlatClientProperties.STYLE,"arc:10;");

        buttonDrawer.addActionListener(e -> {
            if (Drawer.isOpen()) Drawer.showDrawer();
            else Drawer.toggleMenuOpenMode();
        });
        buttonUndo.addActionListener(e    -> FormManager.undo());
        buttonRedo.addActionListener(e    -> FormManager.redo());
        buttonRefresh.addActionListener(e -> FormManager.refresh());

        toolBar.add(buttonDrawer);
        toolBar.add(buttonUndo);
        toolBar.add(buttonRedo);
        toolBar.add(buttonRefresh);

        panel.add(toolBar);
        panel.add(createSearchBox(), "gapx n 135");
        return panel;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────

    private JPanel createFooter() {
        JPanel panel = new JPanel(new MigLayout(
                "insets 1 n 1 n,al trailing center,gapx 10,height 30!",
                "[]push[][][]", "fill"));
        panel.putClientProperty(FlatClientProperties.STYLE,
                "[light]background:tint($Panel.background,20%);" +
                        "[dark]background:tint($Panel.background,5%);");

        // Sürüm etiketi
        JLabel lbVersion = new JLabel("Sürüm: " + Servicio.getInstance().getAppVersion());
        lbVersion.putClientProperty(FlatClientProperties.STYLE,
                "foreground:$Label.disabledForeground;");
        lbVersion.setIcon(new Ikon("icons/git-merge.svg", 0.7f, "Label.disabledForeground"));
        panel.add(lbVersion);

        // Java sürümü
        String javaVendor = System.getProperty("java.vendor", "");
        if ("Oracle Corporation".equals(javaVendor)) javaVendor = "";
        String java = javaVendor + " v" + System.getProperty("java.version", "").trim();
        JLabel lbJava = new JLabel(String.format("Running on: Java %s", java));
        lbJava.putClientProperty(FlatClientProperties.STYLE,
                "foreground:$Label.disabledForeground;");
        lbJava.setIcon(new Ikon("icons/java.svg", 1f, "Label.disabledForeground"));
        panel.add(lbJava);

        panel.add(new JSeparator(JSeparator.VERTICAL));
        panel.add(new MemoryBar());

        // ── Helper butonu ──────────────────────────────────────────────────────
        helperButton = new JButton(new Ikon("icons/circle-alert.svg", 0.7f));
        helperButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");
        helperButton.setToolTipText("Yardım & Güncelleme");
        helperButton.addActionListener(e -> showHelperPopup());

        panel.add(helperButton);
        return panel;
    }

    // ─── Helper Popup ─────────────────────────────────────────────────────────

    /**
     * Helper butonuna tıklandığında popup menüyü gösterir.
     * <p>
     * Menü içeriği:
     *   • Güncel değilse → "Güncelleme Mevcut" başlığı + "Şimdi Güncelle" öğesi
     *   • Her zaman  → "Güncellemeleri Kontrol Et" öğesi (Toast promise)
     */
    private void showHelperPopup() {
        UpdateChecker checker  = Servicio.getInstance().getUpdateChecker();
        JFrame        frame    = (JFrame) SwingUtilities.getWindowAncestor(this);
        JPopupMenu    popup    = new JPopupMenu();

        // ── Güncelleme varsa üstte vurgulu öğe ────────────────────────────────
        if (checker.isSplashCheckDone() && checker.hasPendingUpdate()) {
            UpdateManifest manifest = checker.getPendingUpdate();
            boolean isHotfix = UpdateManager.sameVersion(
                    manifest.getVersion(), Servicio.getInstance().getAppVersion());

            String label = isHotfix
                    ? "🔧  Yama Mevcut — v" + manifest.getVersion() + "  (Şimdi Uygula)"
                    : "🚀  Güncelleme Mevcut — v" + manifest.getVersion() + "  (İndir)";

            JMenuItem menuUpdate = new JMenuItem(label);
            menuUpdate.putClientProperty(FlatClientProperties.STYLE,
                    "foreground:$Component.warningForeground;");
            menuUpdate.setIcon(new Ikon("icons/download.svg", 0.7f));
            menuUpdate.addActionListener(ae -> {
                popup.setVisible(false);
                UpdateNotifier.openUpdateDialog(frame, manifest, isHotfix);
                checker.clearPendingUpdate();
                resetHelperBadge();
            });

            popup.add(menuUpdate);
            popup.addSeparator();
        }

        // ── Güncellemeleri Kontrol Et ──────────────────────────────────────────
        JMenuItem menuCheck = new JMenuItem("Güncellemeleri Kontrol Et");
        menuCheck.setIcon(new Ikon("icons/refresh-cw.svg", 0.7f));
        menuCheck.addActionListener(ae -> {
            popup.setVisible(false);
            checkUpdateWithToast();
        });
        popup.add(menuCheck);

        // Butonun sol üst köşesine, yukarı doğru aç
        int popupH = popup.getPreferredSize().height;
        int popupW = popup.getPreferredSize().width;
        popup.show(helperButton,
                helperButton.getWidth() - popupW,
                -popupH - 4);
    }

    // ─── Toast: "Güncellemeleri Kontrol Et" ──────────────────────────────────

    /**
     * Promise Toast ile güncelleme kontrolü:
     *   Kontrol ediliyor… → başarı / bilgi / hata
     * Güncelleme bulunursa UpdateDialog otomatik açılır.
     */
    private void checkUpdateWithToast() {
        if (Toast.isToastAvailable(TOAST_CHECK_ID)) return;

        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);

        ToastOption option = Toast.createOption();
        option.setHtmlEnabled(true)
                .setCloseOnClick(false)
                .getLayoutOption()
                .setLocation(ToastLocation.TOP_TRAILING);

        Toast.showPromise(this, "🔍 Güncelleme kontrol ediliyor…", option,
                new ToastPromise(TOAST_CHECK_ID) {
                    @Override
                    public void execute(PromiseCallback callback) {
                        // Async kontrol — sonuç için polling (max 15sn)
                        boolean[]        found  = {false};
                        boolean[]        done   = {false};
                        UpdateManifest[] result = {null};
                        Exception[]      err    = {null};

                        Servicio.getInstance().getUpdateChecker()
                                .getManager().checkForUpdates(
                                        manifest -> {
                                            result[0] = manifest;
                                            found[0]  = true;
                                            done[0]   = true;
                                        },
                                        () -> done[0] = true,
                                        ex -> { err[0] = ex; done[0] = true; }
                                );

                        long deadline = System.currentTimeMillis() + 15_000L;
                        while (!done[0] && System.currentTimeMillis() < deadline) {
                            try { Thread.sleep(100); }
                            catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        if (found[0] && result[0] != null) {
                            final UpdateManifest manifest = result[0];
                            boolean isHotfix = UpdateManager.sameVersion(
                                    manifest.getVersion(), Servicio.getInstance().getAppVersion());

                            // Toast başarı mesajı
                            callback.done(Toast.Type.SUCCESS,
                                    (isHotfix ? "🔧 " : "🚀 ")
                                            + "<html><b>v" + manifest.getVersion() + " hazır!</b>"
                                            + "<br><font color='#888888' size='-1'>"
                                            + (isHotfix ? "Yama güncelleme mevcut."
                                            : "Yeni sürüm indirilebilir.")
                                            + "</font></html>");

                            // UpdateDialog EDT'de aç
                            SwingUtilities.invokeLater(() ->
                                    UpdateNotifier.openUpdateDialog(frame, manifest, isHotfix));

                        } else if (err[0] != null) {
                            callback.done(Toast.Type.ERROR,
                                    "<html>⚠ <b>Kontrol başarısız.</b>"
                                            + "<br><font color='#888888' size='-1'>"
                                            + "İnternet bağlantınızı kontrol edin.</font></html>");
                        } else {
                            callback.done(Toast.Type.INFO,
                                    "<html>✔ <b>En güncel sürümdesiniz.</b>"
                                            + "<br><font color='#888888' size='-1'>v"
                                            + Servicio.getInstance().getAppVersion() + "</font></html>");
                        }
                    }
                });
    }

    // ─── Açılış Güncelleme Kontrolü ──────────────────────────────────────────

    /**
     * MainForm oluşturulunca başlar.
     * Splash kontrolü tamamlanana kadar 300ms aralıklarla bekler,
     * ardından güncelleme varsa:
     *   1. Helper butonuna badge ekler
     *   2. Sağ üstte Toast bildirimi gösterir
     */
    private void scheduleStartupUpdateCheck() {
        Timer timer = new Timer(300, null);
        timer.addActionListener(e -> {
            UpdateChecker checker = Servicio.getInstance().getUpdateChecker();
            if (!checker.isSplashCheckDone()) return;

            timer.stop(); // Kontrol tamamlandı

            if (checker.hasPendingUpdate()) {
                UpdateManifest manifest = checker.getPendingUpdate();
                boolean isHotfix = UpdateManager.sameVersion(
                        manifest.getVersion(), Servicio.getInstance().getAppVersion());

                applyUpdateBadge(manifest.getVersion());
                showStartupToast(manifest, isHotfix);
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    /**
     * Açılış Toast'ı — sağ üstte, kapatılana kadar kalır.
     * "Güncelle" için helper butona yönlendirir (basit toast, action yok).
     */
    private void showStartupToast(UpdateManifest manifest, boolean isHotfix) {
        ToastOption option = Toast.createOption();
        option.setHtmlEnabled(true)
                .setAutoClose(true)
                .setCloseOnClick(true)
                .getLayoutOption()
                .setLocation(ToastLocation.TOP_TRAILING);

        String msg = (isHotfix ? "<html>🔧 <b>Kritik Güncelleme</b>" : "🚀 <b>Güncelleme Hazır</b>")
                + " — v" + manifest.getVersion()
                + "<br><font color='#888888' size='-1'>"
                + "Güncellemek için sağ alttaki "
                + "<b>❕</b> butonuna tıklayın.</font>" +
                "</html>";

        Toast.show(this, isHotfix ? Toast.Type.WARNING : Toast.Type.INFO, msg, option);
    }

    // ─── Badge Yönetimi ───────────────────────────────────────────────────────

    /**
     * Helper butonuna güncelleme badge'i ekler.
     * İkon rengi accent rengine döner, tooltip güncellenir.
     */
    private void applyUpdateBadge(String version) {
        if (helperButton == null) return;
        helperButton.setIcon(new Ikon("icons/circle-alert.svg", 0.7f, "Component.accentColor"));
        helperButton.setToolTipText("Güncelleme mevcut: v" + version + " — tıklayın");

        // Kısa yanıp sönme animasyonu (3 kez)
        Timer pulse = new Timer(450, null);
        final int[] count = {0};
        pulse.addActionListener(ev -> {
            if (count[0] % 2 == 0) {
                helperButton.putClientProperty(FlatClientProperties.STYLE,
                        "background:@accentColor; arc:8;");
            } else {
                helperButton.putClientProperty(FlatClientProperties.STYLE, "arc:8;");
            }
            if (++count[0] >= 6) ((Timer) ev.getSource()).stop();
        });
        pulse.start();
    }

    /** Badge'i normal haline döndürür (güncelleme atlandı / uygulandı). */
    public void resetHelperBadge() {
        if (helperButton == null) return;
        helperButton.setIcon(new Ikon("icons/circle-alert.svg", 0.7f));
        helperButton.setToolTipText("Yardım & Güncelleme");
        helperButton.putClientProperty(FlatClientProperties.STYLE, "");
    }

    // ─── Form Yönetimi ────────────────────────────────────────────────────────

    private JPanel createSearchBox() {
        JPanel panel = new JPanel(new MigLayout("fill", "[fill,center,200:250:]", "[fill]"));
        FormSearchButton button = new FormSearchButton();
        button.addActionListener(e -> FormSearch.getInstance().showSearch());
        panel.add(button);
        return panel;
    }

    private JPanel createRefreshLine() {
        refreshLine = new RefreshLine();
        return refreshLine;
    }

    private Component createMain() {
        mainPanel = new JPanel(new BorderLayout());
        return mainPanel;
    }

    public void setForm(Form form) {
        mainPanel.removeAll();
        mainPanel.add(form);
        mainPanel.repaint();
        mainPanel.revalidate();

        buttonUndo.setEnabled(FormManager.FORMS.isUndoAble());
        buttonRedo.setEnabled(FormManager.FORMS.isRedoAble());

        if (mainPanel.getComponentOrientation().isLeftToRight()
                != form.getComponentOrientation().isLeftToRight()) {
            applyComponentOrientation(mainPanel.getComponentOrientation());
        }
    }

    public void refresh() { refreshLine.refresh(); }

    // ─── Alanlar ──────────────────────────────────────────────────────────────

    private JPanel      mainPanel;
    private RefreshLine refreshLine;
    private JButton     buttonUndo;
    private JButton     buttonRedo;
    private JButton     buttonRefresh;
    private JButton     helperButton;
}