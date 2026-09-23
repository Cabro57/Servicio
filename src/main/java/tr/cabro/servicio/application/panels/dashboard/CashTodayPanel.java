package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.forms.FormAccounts;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dto.DailyCashReportDto;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Bugünkü kasa: seçili dönemden bağımsız, her zaman bugünü gösterir. Net tutar işaretine göre
 * renklenir, altında ödeme yöntemi dökümü ve açık alacak (borçlu müşterilerin toplamı) durur.
 */
public class CashTodayPanel extends JPanel {

    private final JLabel lblDate = DashboardUi.muted("");
    private final JLabel lblNet = new JLabel("—");
    private final JLabel lblCounts = DashboardUi.small(" ");
    private final JPanel breakdown = new JPanel(new MigLayout("insets 0, fillx, wrap, gapy 4", "[grow][right]", ""));
    private final JLabel lblReceivables = new JLabel("—");

    public CashTodayPanel() {
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 12 16, fillx, wrap", "[fill]", "[]10[]0[]12[]10[]10[]");

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", "[center]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Bugünkü kasa"));
        header.add(lblDate);
        card.add(header);

        lblNet.putClientProperty(FlatClientProperties.STYLE, "font: bold +14");
        lblNet.getAccessibleContext().setAccessibleName("Bugünkü net kasa");
        card.add(lblNet);
        card.add(lblCounts);

        breakdown.setOpaque(false);
        card.add(breakdown);

        card.add(new JSeparator());

        JButton receivables = DashboardUi.rowButton();
        receivables.setLayout(new MigLayout("insets 0, fillx", "[grow][right]", "[center]"));
        JLabel caption = new JLabel("Açık alacak", new Ikon("icons/hand-coins.svg", 16, "Label.disabledForeground"),
                SwingConstants.LEADING);
        caption.setIconTextGap(8);
        receivables.add(caption);
        lblReceivables.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        receivables.add(lblReceivables);
        receivables.setToolTipText("Borçlu müşterilerin toplam bakiyesi — cari hesapları aç");
        receivables.getAccessibleContext().setAccessibleName("Açık alacak");
        receivables.addActionListener(e -> FormManager.showForm(AllForms.getForm(FormAccounts.class)));
        card.add(receivables, "gapx -8 -8");

        JButton report = new JButton(QuickAction.CASH_REPORT.getLabel(),
                new Ikon(QuickAction.CASH_REPORT.getIconPath(), 16, "Label.foreground"));
        report.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,12,6,12");
        report.setToolTipText(QuickAction.CASH_REPORT.getDescription() + " (" + QuickAction.CASH_REPORT.getShortcutText() + ")");
        report.addActionListener(e -> QuickAction.CASH_REPORT.run());
        card.add(report, "growx 0, al left");

        add(card);
    }

    public void setReport(DailyCashReportDto report) {
        LocalDate date = report.getDate() != null ? report.getDate() : LocalDate.now();
        lblDate.setText(Format.formatDate(date));

        BigDecimal total = report.getTotal() != null ? report.getTotal() : BigDecimal.ZERO;
        lblNet.setText(Format.formatPrice(total));
        DashboardUi.styleMoney(lblNet, total, "font: bold +14");

        lblCounts.setText(report.getSaleCount() + " satış  ·  " + report.getReturnCount() + " iade  ·  net tahsilat");

        breakdown.removeAll();
        Map<PaymentType, BigDecimal> map = report.getBreakdown();
        boolean any = false;
        for (PaymentType type : PaymentType.values()) {
            BigDecimal amount = map != null ? map.get(type) : null;
            if (amount == null || amount.signum() == 0) continue;
            any = true;
            JLabel name = new JLabel(type.getDisplayName(), DashboardUi.badgeIcon(type.getIconPath(), 16, type.getBadgeColor()),
                    SwingConstants.LEADING);
            name.setIconTextGap(8);
            breakdown.add(name);
            JLabel value = new JLabel(Format.formatPrice(amount));
            DashboardUi.styleMoney(value, amount.signum() < 0 ? amount : BigDecimal.ZERO, "font: bold");
            breakdown.add(value);
        }
        if (!any) {
            breakdown.add(DashboardUi.small("Bugün henüz ödeme alınmadı."), "span 2");
        }
        breakdown.revalidate();
        breakdown.repaint();
    }

    public void setReceivables(BigDecimal amount) {
        lblReceivables.setText(Format.formatPrice(amount));
        lblReceivables.putClientProperty(FlatClientProperties.STYLE, amount != null && amount.signum() > 0
                ? "font: bold; foreground: $Servicio.warningColor"
                : "font: bold; foreground: $Label.disabledForeground");
    }
}
