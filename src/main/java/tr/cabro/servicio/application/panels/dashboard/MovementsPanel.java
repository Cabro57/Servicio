package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.forms.FormCustomer;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Bugünkü kasa hareketleri: gün içindeki ödemeler (tahsilat, satış, iade), en yeni önce.
 * Müşteriye bağlı satır müşteri kartını açar; perakende satış ödemeleri müşterisizdir.
 */
public class MovementsPanel extends JPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JPanel list = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0", "[fill]", ""));

    public MovementsPanel() {
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 10 16, fillx, wrap", "[fill]", "[]6[]");
        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", "[center]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Bugünkü hareketler"));
        card.add(header);

        list.setOpaque(false);
        card.add(list);
        add(card);
    }

    public void setPayments(List<Payment> payments, Map<Long, Customer> customers) {
        list.removeAll();
        if (payments.isEmpty()) {
            list.add(DashboardUi.emptyState("Bugün henüz hareket yok",
                    QuickAction.QUICK_SALE.getShortcutText() + " satış, "
                            + QuickAction.COLLECT.getShortcutText() + " tahsilat"));
        } else {
            for (Payment p : payments) list.add(row(p, p.getCustomerId() != null ? customers.get(p.getCustomerId()) : null));
        }
        list.revalidate();
        list.repaint();
    }

    private JButton row(Payment p, Customer customer) {
        JButton row = DashboardUi.rowButton();
        row.setLayout(new MigLayout("insets 0, fillx, gap 8 1", "[36!][grow,fill][right]", "[][]"));

        PaymentType type = p.getPaymentType() != null ? p.getPaymentType() : PaymentType.OTHER;
        String time = p.getPaymentDate() != null ? p.getPaymentDate().format(TIME) : "";
        row.add(DashboardUi.small(time), "span 1 2, aligny top");

        String who = customer != null ? customer.getFullName()
                : (p.getCustomerId() != null ? "Müşteri #" + p.getCustomerId() : "Perakende satış");
        JLabel name = new JLabel(who);
        row.add(name, "wmin 0");

        BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
        JLabel value = new JLabel(Format.formatPrice(amount));
        DashboardUi.styleMoney(value, amount, "font: bold");
        row.add(value, "span 1 2, aligny center, wrap");

        JLabel sub = new JLabel(type.getDisplayName(), DashboardUi.badgeIcon(type.getIconPath(), 12, type.getBadgeColor()),
                SwingConstants.LEADING);
        sub.setIconTextGap(5);
        sub.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        if (p.getNote() != null && !p.getNote().isBlank()) sub.setText(type.getDisplayName() + "  ·  " + p.getNote());
        row.add(sub, "wmin 0");

        row.getAccessibleContext().setAccessibleName(time + " " + who + " " + Format.formatPrice(amount));
        if (customer != null) {
            row.addActionListener(e -> FormManager.showForm(new FormCustomer(customer)));
        } else {
            row.setCursor(null);
            row.setFocusable(false);
        }
        return row;
    }
}
