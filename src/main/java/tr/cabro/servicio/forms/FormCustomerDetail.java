package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.extras.AvatarIcon;
import raven.modal.component.chart.themes.DefaultChartTheme;
import raven.modal.component.dashboard.CardBox;
import raven.modal.system.Form;
import tr.cabro.servicio.application.tablemodal.CustomerServiceRecordTableModel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;

public class FormCustomerDetail extends Form {

    private final Customer customer;

    public FormCustomerDetail(@NonNull Customer customer) {
        this.customer = customer;

        formInit();
    }

    @Override
    public void formInit() {
        initComponent();

        collectForm();
    }

    @Override
    public void formOpen() {
        super.formOpen();
    }

    @Override
    public void formRefresh() {
        super.formRefresh();
    }


    private void collectForm() {

    }

    private JLabel smallText(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 13f));
        lbl.setForeground(new Color(200, 200, 200));
        return lbl;
    }

    private JPanel createStatCard(String value, String label) {
        JPanel p = new JPanel(new MigLayout("wrap", "[grow]", "[]5[]"));
        p.setBackground(new Color(45, 45, 45));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 18f));
        v.setForeground(Color.WHITE);

        JLabel l = smallText(label);

        p.add(v);
        p.add(l);
        return p;
    }

    private JPanel createOrderItem(String product, String color, String size, int qty, String status) {
        JPanel p = new JPanel(new MigLayout("wrap", "[grow][]", "[]5[]"));
        p.setBackground(new Color(50, 50, 50));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel lblProduct = new JLabel(product);
        lblProduct.setFont(lblProduct.getFont().deriveFont(Font.BOLD, 14f));
        lblProduct.setForeground(Color.WHITE);

        JLabel detail = smallText("Color: " + color + " | Size: " + size + " | Qty: " + qty);

        JLabel badge = new JLabel(status);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        badge.setForeground(Color.WHITE);
        badge.setBackground(
                status.equalsIgnoreCase("Completed")
                        ? new Color(30, 136, 229)
                        : new Color(255, 152, 0)
        );

        p.add(lblProduct, "split 2, growx");
        p.add(badge, "right");
        p.add(detail, "growx");

        return p;
    }

    private JPanel createProfileCard() {
        JPanel panel = new JPanel(new MigLayout("wrap,fillx", "[grow]", "[][]20[][]20[grow]"));
        panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        // Avatar
        AvatarIcon icon = new AvatarIcon(new FlatSVGIcon("drawer/image/avatar_male.svg", 100, 100), 50, 50, 3.5f);
        icon.setType(AvatarIcon.Type.MASK_SQUIRCLE);
        icon.setBorder(2, 2);

        JLabel avatar = new JLabel(icon);

        // Customer name (küçük başlık olarak)
        JLabel lblName = new JLabel(customer.toString());
        lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 16f));



        // Email
        JLabel lblEmail = new JLabel(customer.getEmail());
        JLabel lblPhone = new JLabel(customer.getPhone_number_1());
        JLabel lblAddress = new JLabel(customer.getAddress());
        JLabel lblCreatedAt = new JLabel(Format.formatDate(customer.getCreated_at()));

        panel.add(avatar, "alignx center");
        panel.add(new JButton(customer.getType().getIcon(12, 12)), "split 2, alignx center");
        panel.add(lblName, "alignx center");

        panel.add(smallText("E-Posta"));
        panel.add(lblEmail);
        panel.add(smallText("Telefon"));
        panel.add(lblPhone);
        panel.add(smallText("Adres"));
        panel.add(lblAddress, "wrap");
        panel.add(smallText("Kayıt Tarihi"));
        panel.add(lblCreatedAt);

        return panel;
    }

    private CardBox createStatsCard() {
        CardBox cardBox = new CardBox();

        cardBox.addCardItem(createIcon("icons/dashboard/customer.svg", DefaultChartTheme.getColor(0)), "Toplam Servis");
        cardBox.addCardItem(createIcon("icons/dashboard/income.svg", DefaultChartTheme.getColor(1)), "Toplam Gelir");
        cardBox.addCardItem(createIcon("icons/dashboard/expense.svg", DefaultChartTheme.getColor(2)), "Toplam Gider");

        return cardBox;
    }

    private JPanel createTable() {
        JPanel panel = new JPanel(new MigLayout("wrap,fill", "", ""));
        panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        table = new JTable(new CustomerServiceRecordTableModel(customer));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);

        panel.add(scroll, "push");

        return panel;
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 0.4f).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }

    private void initComponent() {
        setLayout(new MigLayout("fill", "[grow]", ""));

        add(createProfileCard(), "grow, cell 0 0 1 2");
        add(createStatsCard(), "grow, cell 1 0");
        add(createTable(), "grow, cell 1 1");
    }

    private JTable table;
}
