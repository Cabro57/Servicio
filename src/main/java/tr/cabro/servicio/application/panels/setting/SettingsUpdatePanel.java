package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.UpdateModal;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.updater.UpdateService;
import tr.cabro.servicio.updater.UpdateService.State;

import javax.swing.*;
import java.util.List;

/**
 * Ayarlar &gt; Sistem &gt; Güncelleme: güncellemenin "evi". Yüklü sürüm, son denetimin sonucu, elle
 * denetim (başlıktaki "Şimdi denetle"), otomatik denetim anahtarı ve atlanan sürümün geri alınması.
 * Sonuçlar bildirim balonu yerine sayfanın kendi durum satırına yazılır.
 */
public class SettingsUpdatePanel extends JPanel implements SettingsModal.HeaderActions {

    private final UpdateService service = Servicio.getInstance().getUpdateService();
    private final Runnable listener = this::render;

    private final JButton checkNow = SettingsKit.headerButton("Şimdi denetle", "icons/refresh-cw.svg");

    private final JLabel statusLine = new JLabel();
    private final JLabel statusSub = SettingsKit.note("");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton statusAction = DetailKit.secondaryButton("", null, null);

    private final JCheckBox autoCheck = new JCheckBox("Güncellemeleri kendiliğinden denetle");

    private JPanel skippedSection;
    private final JLabel skippedText = new JLabel();

    public SettingsUpdatePanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
        checkNow.addActionListener(e -> service.check(true));
        render();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(checkNow);
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Durum ---
        statusLine.setIconTextGap(8);
        progress.putClientProperty(FlatClientProperties.STYLE, "arc: 6");
        progress.putClientProperty("JProgressBar.largeHeight", Boolean.FALSE);
        statusAction.addActionListener(e -> onStatusAction());

        JPanel status = SettingsKit.stack();
        status.add(statusLine);
        status.add(statusSub, "gaptop -4, gapleft " + UIScale.scale(24));
        status.add(progress, "gapleft " + UIScale.scale(24) + ", wmax 380, h " + UIScale.scale(6) + "!");
        status.add(statusAction, "gapleft " + UIScale.scale(24) + ", growx 0, gaptop 4");

        JPanel rows = SettingsKit.rows();
        rows.add(SettingsKit.label("Yüklü sürüm"));
        JLabel installed = new JLabel(service.getCurrentVersion());
        installed.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        rows.add(installed);

        JPanel content = SettingsKit.stack();
        content.add(status);
        content.add(new JSeparator(), "gaptop 6, gapbottom 6");
        content.add(rows);
        SettingsKit.section(page, "Sürüm", "Kullandığınız sürüm ve son denetimin sonucu.", content);

        // --- Otomatik denetim ---
        autoCheck.setSelected(service.isAutoCheck());
        autoCheck.addActionListener(e -> {
            service.setAutoCheck(autoCheck.isSelected());
            SettingsKit.saved(this);
        });
        JPanel auto = SettingsKit.stack();
        auto.add(SettingsKit.check(autoCheck,
                "Açılışta ve 6 saatte bir sessizce bakılır. Yeni sürüm yalnızca alt çubukta gösterilir; "
                        + "siz istemeden indirilmez, çalışırken hiçbir pencere açılmaz."));
        SettingsKit.section(page, "Otomatik denetim", "İnternet bağlantısı gerekir.", auto);

        // --- Atlanan sürüm ---
        JButton undo = DetailKit.secondaryButton("Geri al", null, () -> {
            service.clearSkippedVersion();
            SettingsKit.saved(this);
        });
        undo.setToolTipText("Atlanan sürüm yeniden bildirilsin");
        JPanel skipped = new JPanel(new MigLayout("insets 0, fillx, gap 12", "[grow][]", "[center]"));
        skipped.setOpaque(false);
        skipped.add(skippedText, "wmin 0");
        skipped.add(undo);
        skippedSection = SettingsKit.section(page, "Atlanan sürüm", "\"Bu sürümü atla\" dediğiniz sürüm.", skipped);

        return page;
    }

    private void onStatusAction() {
        switch (service.getState()) {
            case READY -> UpdateModal.restart(this);
            case CHECK_FAILED -> service.check(true);
            default -> UpdateModal.open();
        }
    }

    // ------------------------------------------------------------------ durum

    private void render() {
        State state = service.getState();
        String version = service.getManifest() != null ? "v" + service.getManifest().getVersion() : "";

        progress.setVisible(state == State.DOWNLOADING);
        statusAction.setVisible(false);
        checkNow.setEnabled(state != State.CHECKING && state != State.DOWNLOADING
                && state != State.READY && state != State.RESTARTING);

        switch (state) {
            case CHECKING -> status("Denetleniyor…", "icons/loader.svg", "Label.disabledForeground", null);
            case UP_TO_DATE -> status("Güncel sürümü kullanıyorsunuz", "icons/check-check.svg", "Servicio.successColor", null);
            case CHECK_FAILED -> {
                status("Denetlenemedi", "icons/circle-x.svg", "Servicio.dangerColor",
                        "İnternet bağlantınızı kontrol edip tekrar deneyin.");
                action("Tekrar dene");
            }
            case AVAILABLE -> {
                boolean hotfix = service.isHotfix();
                status(hotfix ? "Kritik yama: " + version : "Yeni sürüm hazır: " + version, "icons/circle-arrow-up.svg",
                        hotfix ? "Servicio.warningColor" : "Servicio.actionColor", null);
                action("Sürüm notları ve indir");
            }
            case DOWNLOADING -> {
                int value = (int) Math.round(service.getProgress() * 100);
                progress.setValue(value);
                status(version + " indiriliyor", "icons/download.svg", "Label.foreground", "%" + value
                        + " tamamlandı. Çalışmaya devam edebilirsiniz; ilerleme alt çubukta da görünür.");
            }
            case READY -> {
                status(version + " kurulmaya hazır", "icons/package-check.svg", "Servicio.successColor",
                        "Şimdi yapmazsanız uygulamayı kapatırken kurulur.");
                action("Yeniden başlat");
            }
            case RESTARTING -> status("Güncelleme kuruluyor…", "icons/loader.svg", "Label.disabledForeground", null);
            case DOWNLOAD_FAILED -> {
                status("Güncelleme indirilemedi", "icons/circle-x.svg", "Servicio.dangerColor", service.getError());
                action("Ayrıntılar");
            }
            default -> status(service.isAutoCheck() ? "Henüz denetlenmedi" : "Otomatik denetim kapalı",
                    "icons/circle-dashed.svg", "Label.disabledForeground", "\"Şimdi denetle\" ile yeni sürüm olup olmadığına bakabilirsiniz.");
        }

        if (statusSub.getText().isEmpty() && service.getLastChecked() != null) {
            statusSub.setText("Son denetim: " + DateFormats.dateTime().format(service.getLastChecked()));
        }
        statusSub.setVisible(!statusSub.getText().isEmpty());

        String skipped = service.getSkippedVersion();
        skippedText.setText(skipped != null ? "v" + skipped + " kendiliğinden bildirilmiyor" : "");
        setSectionVisible(skippedSection, skipped != null);

        autoCheck.setSelected(service.isAutoCheck());
        revalidate();
        repaint();
    }

    private void status(String text, String icon, String colorKey, String sub) {
        statusLine.setText(text);
        statusLine.setIcon(new Ikon(icon, 16, colorKey));
        statusLine.putClientProperty(FlatClientProperties.STYLE, "font: bold +1; foreground: $" + colorKey);
        statusSub.setText(sub != null ? sub : "");
    }

    private void action(String text) {
        statusAction.setText(text);
        statusAction.setVisible(true);
    }

    /** Bölümü ve üstündeki ayırıcı çizgiyi birlikte gizler/gösterir. */
    private static void setSectionVisible(JPanel section, boolean visible) {
        section.setVisible(visible);
        java.awt.Container page = section.getParent();
        int index = page.getComponentZOrder(section);
        if (index > 0 && page.getComponent(index - 1) instanceof JSeparator separator) {
            separator.setVisible(visible);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        service.addListener(listener);
        render();
    }

    @Override
    public void removeNotify() {
        service.removeListener(listener);
        super.removeNotify();
    }
}
