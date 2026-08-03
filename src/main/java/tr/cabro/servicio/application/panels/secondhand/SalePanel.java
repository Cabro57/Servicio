package tr.cabro.servicio.application.panels.secondhand;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stoktaki bir ikinci el cihazın satışı formu — cihaz zaten bellidir (salt görüntülenir),
 * alıcı müşteri seçimi ({@link CustomerSelectBox}) + fiyat/tarih/garanti süresi/not.
 */
public class SalePanel extends JPanel {

    private final CustomerSelectBox buyerCombo;
    private final CurrencyField priceField;
    private final DatePicker datePicker;
    private final JSpinner warrantyMonthsSpinner;
    private final JTextArea noteArea;

    public SalePanel(Device device, ActionListener onNewCustomerRequested) {
        setLayout(new MigLayout("fillx, wrap, insets 5 30 5 30, width 400", "[fill]", ""));

        addSectionTitle("Cihaz");
        JLabel deviceLabel = new JLabel(device != null ? device.toString() : "-");
        add(deviceLabel, "growx");

        addSectionTitle("Alıcı (Müşteri)");
        buyerCombo = new CustomerSelectBox(onNewCustomerRequested);
        add(buyerCombo, "growx");

        addSectionTitle("Satış Fiyatı");
        priceField = new CurrencyField();
        add(priceField, "growx");

        addSectionTitle("Satış Tarihi");
        JFormattedTextField dateField = new JFormattedTextField();
        datePicker = new DatePicker();
        datePicker.setDateFormat("dd/MM/yyyy");
        datePicker.setEditor(dateField);
        datePicker.now();
        add(dateField, "growx");

        addSectionTitle("Garanti Süresi (Ay)");
        warrantyMonthsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 60, 1));
        add(warrantyMonthsSpinner, "growx");

        addSectionTitle("Not");
        noteArea = new JTextArea();
        noteArea.setWrapStyleWord(true);
        noteArea.setLineWrap(true);
        add(new JScrollPane(noteArea), "height 100, grow, pushy");
    }

    public void setCustomers(List<Customer> customers) {
        buyerCombo.setCustomers(customers);
    }

    public void appendNewCustomer(Customer customer) {
        buyerCombo.appendCustomer(customer);
    }

    public Customer getBuyer() {
        return buyerCombo.getSelectedItem();
    }

    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceField.getDoubleValue());
    }

    public LocalDateTime getTransactionDate() {
        return datePicker.getSelectedDate() != null ? datePicker.getSelectedDate().atStartOfDay() : LocalDateTime.now();
    }

    public Integer getWarrantyMonths() {
        int months = (Integer) warrantyMonthsSpinner.getValue();
        return months > 0 ? months : null;
    }

    public String getNote() {
        return noteArea.getText().trim();
    }

    public void requestInitialFocus() {
        buyerCombo.grabFocus();
    }

    private void addSectionTitle(String title) {
        JLabel label = new JLabel(title);
        label.putClientProperty(FlatClientProperties.STYLE, "font:+2");
        add(label, "gapy 5 0");
        add(new JSeparator(), "height 2!, gapy 0 0");
    }
}
