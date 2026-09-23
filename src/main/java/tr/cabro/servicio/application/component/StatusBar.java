package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
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
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Alt durum çubuğunun iş bilgisi bölümü: bugünkü net kasa, atölyedeki cihaz sayısı,
 * teslime hazır cihazlar ve borçlu müşteriler — her biri tıklanınca ilgili listeyi açar.
 * Sağda en sık kullanılan klavye kısayolları hatırlatılır.
 * <p>
 * Ekran açıkken dakikada bir ve her form geçişinde ({@link #refresh()}) tazelenir.
 */
public class StatusBar extends JPanel {

    private static final int REFRESH_MS = 60_000;

    private final JButton btnCash = item("icons/banknote.svg", "Bugünkü kasa raporunu aç");
    private final JButton btnOpen = item("icons/wrench.svg", "Atölyedeki servisleri listele");
    private final JButton btnReady = item("icons/thumbs-up.svg", "Teslime hazır servisleri listele");
    private final JButton btnDebt = item("icons/hand-coins.svg", "Borçlu müşterileri (cari hesaplar) aç");
    private final Timer timer = new Timer(REFRESH_MS, e -> refresh());
    private boolean loading;

    public StatusBar() {
        setLayout(new MigLayout("insets 0, gapx 4, filly", "[][][][]12[]push[]", "[center]"));
        setOpaque(false);

        btnCash.addActionListener(e -> QuickAction.CASH_REPORT.run());
        btnOpen.addActionListener(e -> workOrders().showOpen());
        btnReady.addActionListener(e -> workOrders().showStatus(ServiceStatus.READY));
        btnDebt.addActionListener(e -> FormManager.showForm(AllForms.getForm(FormAccounts.class)));

        add(btnCash, "w pref!");
        add(btnOpen, "w pref!");
        add(btnReady, "w pref!");
        add(btnDebt, "w pref!");
        add(createNewMenuButton());
        add(createHints(), "w pref!");
    }

    private static FormWorkOrders workOrders() {
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        return form;
    }

    private static JButton item(String icon, String tooltip) {
        JButton button = new JButton(" ", new Ikon(icon, 14, "Label.disabledForeground"));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.putClientProperty(FlatClientProperties.STYLE, "margin: 1,6,1,6; iconTextGap: 6");
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Sağdaki kısayol hatırlatıcıları: tuş etiketi + ne yaptığı. */
    private static JPanel createHints() {
        JPanel panel = new JPanel(new MigLayout("insets 0, gapx 4", "", "[center]"));
        panel.setOpaque(false);
        addHint(panel, "Ctrl+K", "Ara", false);
        addHint(panel, QuickAction.NEW_SERVICE.getShortcutText(), "Servis", true);
        addHint(panel, QuickAction.QUICK_SALE.getShortcutText(), "Satış", true);
        addHint(panel, QuickAction.COLLECT.getShortcutText(), "Tahsilat", true);
        return panel;
    }

    private static void addHint(JPanel panel, String key, String text, boolean gap) {
        JLabel keyLabel = new JLabel(key);
        keyLabel.putClientProperty(FlatClientProperties.STYLE,
                "font: -2; foreground: $Label.disabledForeground; border: 0,4,0,4,$Component.borderColor,1,6");
        JLabel textLabel = new JLabel(text);
        textLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        panel.add(keyLabel, gap ? "gapleft 10, w pref!" : "w pref!");
        panel.add(textLabel, "w pref!");
    }

    /**
     * "Yeni" düğmesi: sık işlemleri ({@link QuickAction}) kısayollarıyla listeler, menü yukarı açılır.
     * Başlığın sağında pencere düğmelerinin altında kalıyordu; alt çubukta her ekranda erişilebilir.
     */
    private static JButton createNewMenuButton() {
        JButton button = new JButton("Yeni", new Ikon("icons/plus.svg", 14, "Label.foreground"));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.putClientProperty(FlatClientProperties.STYLE, "margin: 1,8,1,8; iconTextGap: 6; font: bold");
        button.setToolTipText("Yeni kayıt ve sık işlemler");
        button.setFocusable(false);
        button.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            for (QuickAction action : QuickAction.values()) {
                JMenuItem item = new JMenuItem(action.getLabel(), new Ikon(action.getIconPath(), 0.7f));
                // Kısayol sağda görünsün diye; asıl bağlama QuickAction.installKeyMap'te pencere genelinde.
                item.setAccelerator(action.getKeyStroke());
                item.setToolTipText(action.getDescription());
                item.addActionListener(ae -> action.run());
                popup.add(item);
            }
            popup.show(button, 0, -popup.getPreferredSize().height - 4);
        });
        return button;
    }

    public void refresh() {
        if (loading) return;
        loading = true;
        LocalDate today = LocalDate.now();

        var cashF = ServiceManager.getSaleService().getDailyCashReport(today);
        var countsF = ServiceManager.getWorkOrderService().getOpenStatusCounts();
        var debtF = ServiceManager.getPaymentService().getCustomersWithBalancePaged(null, 1, 1);

        java.util.concurrent.CompletableFuture.allOf(cashF, countsF, debtF).whenComplete((v, ex) -> SwingUtilities.invokeLater(() -> {
            loading = false;
            if (ex != null) {
                Servicio.getLogger().warn("Durum çubuğu tazelenemedi", ex);
                return;
            }
            BigDecimal cash = cashF.join().getTotal() != null ? cashF.join().getTotal() : BigDecimal.ZERO;
            btnCash.setText("Kasa " + Format.formatPrice(cash));
            btnCash.putClientProperty(FlatClientProperties.STYLE, "margin: 1,6,1,6; iconTextGap: 6; font: bold; foreground: "
                    + (cash.signum() > 0 ? "$Servicio.successColor" : cash.signum() < 0 ? "$Servicio.dangerColor" : "$Label.disabledForeground"));

            Map<ServiceStatus, Long> counts = countsF.join();
            long open = counts.values().stream().mapToLong(Long::longValue).sum();
            long ready = counts.getOrDefault(ServiceStatus.READY, 0L);
            btnOpen.setText("Atölyede " + open);
            btnReady.setText(ready + " teslime hazır");
            btnReady.putClientProperty(FlatClientProperties.STYLE, "margin: 1,6,1,6; iconTextGap: 6; foreground: "
                    + (ready > 0 ? "$Servicio.actionColor; font: bold" : "$Label.disabledForeground"));

            long debtors = debtF.join().getTotalItems();
            btnDebt.setText(debtors + " borçlu");
            btnDebt.putClientProperty(FlatClientProperties.STYLE, "margin: 1,6,1,6; iconTextGap: 6; foreground: "
                    + (debtors > 0 ? "$Servicio.warningColor" : "$Label.disabledForeground"));
        }));
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
