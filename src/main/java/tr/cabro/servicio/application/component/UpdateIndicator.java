package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.updater.UpdateService;
import tr.cabro.servicio.updater.UpdateService.State;

import javax.swing.*;
import java.awt.*;

/**
 * Alt çubuğun sağındaki güncelleme göstergesi (IntelliJ tarzı). Güncel, denetleniyor ya da sessiz
 * denetim hatası durumlarında hiç görünmez; yalnızca kullanıcının yapabileceği bir şey olduğunda çıkar:
 * <ul>
 *   <li>Yeni sürüm var → "v2.8.0 hazır" (tık: güncelleme penceresi)</li>
 *   <li>İndiriliyor → metin + ince ilerleme çubuğu + yüzde + iptal</li>
 *   <li>İndirildi → "v2.8.0 kurulmaya hazır" + "Yeniden başlat"</li>
 *   <li>İndirme/kurma başarısız → "Güncelleme indirilemedi" + "Tekrar dene"</li>
 * </ul>
 * Hiçbir durumda kendiliğinden pencere açmaz.
 */
public class UpdateIndicator extends JPanel {

    private static final String ITEM_STYLE = "margin: 1,6,1,6; iconTextGap: 6";

    private final UpdateService service = Servicio.getInstance().getUpdateService();
    private final Runnable listener = this::render;

    private final JButton status = new JButton();
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel percent = new JLabel();
    private final JButton cancel = new JButton(new Ikon("icons/x.svg", 12, "Label.disabledForeground"));
    private final JButton action = new JButton();

    public UpdateIndicator() {
        setLayout(new MigLayout("insets 0, gapx 6, hidemode 3", "[][][][][]", "[center]"));
        setOpaque(false);

        status.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        status.setFocusable(false);
        status.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        status.addActionListener(e -> UpdateModal.open());

        bar.putClientProperty(FlatClientProperties.STYLE, "arc: 4");
        bar.putClientProperty("JProgressBar.largeHeight", Boolean.FALSE);
        bar.setPreferredSize(new Dimension(UIScale.scale(120), UIScale.scale(4)));

        percent.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");

        cancel.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        cancel.putClientProperty(FlatClientProperties.STYLE, "margin: 2,2,2,2; arc: 999");
        cancel.setToolTipText("İndirmeyi iptal et");
        cancel.getAccessibleContext().setAccessibleName("İndirmeyi iptal et");
        cancel.setFocusable(false);
        cancel.addActionListener(e -> service.cancelDownload());

        action.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        action.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.linkColor; font: bold; margin: 1,6,1,6");
        action.setFocusable(false);
        action.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        action.addActionListener(e -> runAction());

        add(status);
        add(bar, "w " + UIScale.scale(120) + "!, h " + UIScale.scale(4) + "!");
        add(percent, "w 32!");
        add(cancel);
        add(action);
        render();
    }

    private void runAction() {
        switch (service.getState()) {
            case READY -> UpdateModal.restart(this);
            case DOWNLOAD_FAILED -> service.download();
            default -> { }
        }
    }

    private void render() {
        State state = service.getState();
        String version = service.getManifest() != null ? "v" + service.getManifest().getVersion() : "";
        boolean hotfix = service.isHotfix();

        boolean visible = true;
        boolean downloading = state == State.DOWNLOADING;
        bar.setVisible(downloading);
        percent.setVisible(downloading);
        cancel.setVisible(downloading);
        action.setVisible(false);

        switch (state) {
            case AVAILABLE -> {
                String color = hotfix ? "Servicio.warningColor" : "Servicio.actionColor";
                status(hotfix ? "Kritik yama: " + version : version + " hazır", "icons/circle-arrow-up.svg", color, true,
                        "Yeni sürüm yayınlandı: sürüm notlarını gör ve indir");
            }
            case DOWNLOADING -> {
                status("Güncelleme indiriliyor", "icons/download.svg", "Label.disabledForeground", false,
                        "İndirme arka planda sürüyor; ayrıntılar için tıklayın");
                int value = (int) Math.round(service.getProgress() * 100);
                bar.setValue(value);
                percent.setText("%" + value);
            }
            case READY -> {
                status(version + " kurulmaya hazır", "icons/package-check.svg", "Servicio.successColor", false,
                        "Yeniden başlatınca kurulur. Şimdi yapmazsanız uygulamayı kapatırken kurulur.");
                action.setText("Yeniden başlat");
                action.setVisible(true);
            }
            case RESTARTING -> status("Güncelleme kuruluyor…", "icons/loader.svg", "Label.disabledForeground", false, null);
            case DOWNLOAD_FAILED -> {
                status("Güncelleme indirilemedi", "icons/circle-x.svg", "Servicio.dangerColor", false,
                        service.getError() != null ? service.getError() : "Ayrıntılar için tıklayın");
                action.setText("Tekrar dene");
                action.setVisible(true);
            }
            default -> visible = false;
        }
        setVisible(visible);
        revalidate();
        repaint();
    }

    private void status(String text, String icon, String colorKey, boolean colored, String tooltip) {
        status.setText(text);
        status.setIcon(new Ikon(icon, 14, colorKey));
        status.setToolTipText(tooltip);
        status.putClientProperty(FlatClientProperties.STYLE, ITEM_STYLE
                + (colored ? "; font: bold; foreground: $" + colorKey : "; foreground: $Label.foreground"));
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
