package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.FormAccounts;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Alt çubuk: tezgâhın defter satırı. Solda iş nabzı (bugünkü kasa, atölyedeki cihaz, teslime hazır,
 * borçlu müşteri), sağda sistem durumu (güncelleme göstergesi, son yedeğin yaşı, sürüm).
 * <p>
 * Her değer "soluk ad + kalın değer" çiftidir; değer yalnızca bir anlam taşıdığında renklenir
 * (kasa işaretine göre, teslime hazır bekleyen iş, borç), sıfır soluk kalır. Her parça tıklanınca
 * ilgili listeyi ya da ayarı açar. Kısayol ipuçları ve "Yeni" menüsü burada değil: kısayollar
 * Ayarlar &gt; Klavye kısayolları'nda, sık işlemler Ctrl+K komut paletinde.
 * <p>
 * Ekran açıkken dakikada bir ve her form geçişinde ({@link #refresh()}) tazelenir.
 */
public class StatusBar extends JPanel {

    private static final int REFRESH_MS = 60_000;

    /** Bu kadar günden eski son yedek uyarı rengiyle gösterilir (Ayarlar &gt; Yedekleme ile aynı eşik). */
    private static final int BACKUP_STALE_DAYS = 7;

    private final Segment cash = new Segment("Kasa", "Bugünkü kasa raporunu aç");
    private final Segment open = new Segment("Atölyede", "Atölyedeki servisleri listele");
    private final Segment ready = new Segment("Teslime hazır", "Teslime hazır servisleri listele");
    private final Segment debt = new Segment("Borçlu", "Borçlu müşterileri (cari hesaplar) aç");

    private final JButton backup = systemItem("Yedekleme ayarlarını aç");
    private final JButton version = systemItem(null);

    private final Timer timer = new Timer(REFRESH_MS, e -> refresh());
    private boolean loading;

    public StatusBar() {
        setLayout(new MigLayout("insets 0 4 0 4, gap 0, filly, hidemode 3",
                "[][][][][][][]push[][][][][]", "[center]"));
        setOpaque(false);

        cash.addActionListener(e -> QuickAction.CASH_REPORT.run());
        open.addActionListener(e -> workOrders().showOpen());
        ready.addActionListener(e -> workOrders().showStatus(ServiceStatus.READY));
        debt.addActionListener(e -> FormManager.showForm(AllForms.getForm(FormAccounts.class)));

        add(cash);
        add(divider());
        add(open);
        add(divider());
        add(ready);
        add(divider());
        add(debt);

        UpdateIndicator update = new UpdateIndicator();
        add(update);
        add(divider(update));
        add(backup);
        add(divider());
        add(version);

        backup.addActionListener(e -> FormManager.showSettings("Sistem/Yedekleme"));
        version.setText("v" + Servicio.getInstance().getAppVersion());
        version.setToolTipText("Servicio " + Servicio.getInstance().getAppVersion()
                + "  ·  Java " + System.getProperty("java.version") + "  —  Hakkında");
        version.addActionListener(e -> FormManager.showAbout());
    }

    private static FormWorkOrders workOrders() {
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        return form;
    }

    // ------------------------------------------------------------------ parçalar

    /**
     * İş nabzı parçası: soluk ad ve kalın değer, tümü tek tıklanabilir satır. Üzerine gelince
     * satır zemini belirir (liste satırlarıyla aynı dil); simge yok, ad zaten ne olduğunu söylüyor.
     */
    private static final class Segment extends JButton {
        private final JLabel caption;
        private final JLabel value = new JLabel("—");

        Segment(String captionText, String tooltip) {
            setLayout(new MigLayout("insets 0, gap 6", "[][]", "[center]"));
            caption = new JLabel(captionText);
            caption.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            add(caption);
            add(value);
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,10,2,10;"
                    + " toolbar.hoverBackground: fade($Label.foreground,6%); toolbar.pressedBackground: fade($Label.foreground,10%)");
            setToolTipText(tooltip);
            getAccessibleContext().setAccessibleName(captionText);
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setValue("—", null);
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = getLayout().preferredLayoutSize(this);
            Insets m = getInsets();
            return new Dimension(d.width + m.left + m.right, d.height + m.top + m.bottom);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        /** @param colorKey anlamı olan değerin tema rengi; {@code null} → soluk (sıfır / bilinmiyor). */
        void setValue(String text, String colorKey) {
            value.setText(text);
            value.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: "
                    + (colorKey != null ? "$" + colorKey : "$Label.disabledForeground"));
            getAccessibleContext().setAccessibleDescription(caption.getText() + " " + text);
        }
    }

    /** Sağdaki sistem öğesi: küçük soluk metin, isteğe bağlı simge; tıklanınca ilgili ayar/pencere. */
    private static JButton systemItem(String tooltip) {
        JButton b = new JButton();
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,8,2,8; iconTextGap: 6; font: -1;"
                + " foreground: $Label.disabledForeground; toolbar.hoverBackground: fade($Label.foreground,6%)");
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Parçalar arasında ince dikey çizgi. */
    private static JComponent divider() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, UIScale.scale(14)));
        JPanel wrap = new JPanel(new MigLayout("insets 0 4 0 4", "[1!]", "[center]"));
        wrap.setOpaque(false);
        wrap.add(s, "h 14!");
        return wrap;
    }

    /** Yalnızca {@code owner} görünürken görünen ayırıcı (güncelleme göstergesi çoğu zaman gizli). */
    private static JComponent divider(JComponent owner) {
        JComponent d = divider();
        d.setVisible(owner.isVisible());
        owner.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { d.setVisible(true); }
            @Override public void componentHidden(java.awt.event.ComponentEvent e) { d.setVisible(false); }
        });
        return d;
    }

    // ------------------------------------------------------------------ veri

    public void refresh() {
        refreshBackup();
        if (loading) return;
        loading = true;
        LocalDate today = LocalDate.now();

        var cashF = ServiceManager.getSaleService().getDailyCashReport(today);
        var countsF = ServiceManager.getWorkOrderService().getOpenStatusCounts();
        var debtF = ServiceManager.getPaymentService().getCustomersWithBalancePaged(null, 1, 1);

        CompletableFuture.allOf(cashF, countsF, debtF).whenComplete((v, ex) -> SwingUtilities.invokeLater(() -> {
            loading = false;
            if (ex != null) {
                Servicio.getLogger().warn("Durum çubuğu tazelenemedi", ex);
                return;
            }
            BigDecimal total = cashF.join().getTotal() != null ? cashF.join().getTotal() : BigDecimal.ZERO;
            cash.setValue(Format.formatPrice(total), total.signum() > 0 ? "Servicio.successColor"
                    : total.signum() < 0 ? "Servicio.dangerColor" : null);

            Map<ServiceStatus, Long> counts = countsF.join();
            long openCount = counts.values().stream().mapToLong(Long::longValue).sum();
            long readyCount = counts.getOrDefault(ServiceStatus.READY, 0L);
            open.setValue(String.valueOf(openCount), openCount > 0 ? "Label.foreground" : null);
            ready.setValue(String.valueOf(readyCount), readyCount > 0 ? "Servicio.actionColor" : null);

            long debtors = debtF.join().getTotalItems();
            debt.setValue(String.valueOf(debtors), debtors > 0 ? "Servicio.warningColor" : null);
        }));
    }

    /** Son yedeğin yaşı: dosya listesi ucuz olduğu için doğrudan okunur. */
    private void refreshBackup() {
        File dir = AppSettings.getBackupDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db") || name.endsWith(".sql"));
        long latest = files == null ? 0 : Arrays.stream(files).mapToLong(File::lastModified).max().orElse(0);

        String text;
        String color;
        String icon;
        if (latest == 0) {
            text = "Yedek yok";
            color = "Servicio.dangerColor";
            icon = "icons/triangle-alert.svg";
        } else {
            Duration age = Duration.between(Instant.ofEpochMilli(latest), Instant.now());
            boolean stale = age.toDays() >= BACKUP_STALE_DAYS;
            text = "Yedek " + relative(age);
            color = stale ? "Servicio.warningColor" : null;
            icon = stale ? "icons/triangle-alert.svg" : "icons/database-backup.svg";
        }
        backup.setText(text);
        backup.setIcon(new Ikon(icon, 13, color != null ? color : "Label.disabledForeground"));
        backup.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,8,2,8; iconTextGap: 6; font: -1;"
                + " toolbar.hoverBackground: fade($Label.foreground,6%); foreground: "
                + (color != null ? "$" + color : "$Label.disabledForeground"));
    }

    private static String relative(Duration age) {
        long minutes = age.toMinutes();
        if (minutes < 1) return "az önce";
        if (minutes < 60) return minutes + " dk önce";
        long hours = age.toHours();
        if (hours < 24) return hours + " sa önce";
        long days = age.toDays();
        return days == 1 ? "dün" : days + " gün önce";
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
        refresh();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }
}
