package tr.cabro.servicio.application.panels.secondhand;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.panels.DeviceFormPanel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * İkinci el alım kaydı formu — satıcı müşteri seçimi ({@link CustomerSelectBox}) ve cihaz
 * bilgileri ({@link DeviceFormPanel}, var olan/yeni cihaz aynı bileşenle) + fiyat/tarih/ekspertiz
 * notları. {@code QuickIntakePanel}'deki müşteri/cihaz seçim desenini yeniden kullanır.
 */
public class PurchasePanel extends JPanel {

    private final CustomerSelectBox sellerCombo;
    private final DeviceFormPanel deviceFormPanel;
    private final CurrencyField priceField;
    private final DatePicker datePicker;
    private final JTextArea expertiseNotesArea;

    public PurchasePanel(ActionListener onNewCustomerRequested) {
        setLayout(new MigLayout("fillx, wrap, insets 5 30 5 30, width 400", "[fill]", ""));

        addSectionTitle("Satıcı (Müşteri)");
        sellerCombo = new CustomerSelectBox(onNewCustomerRequested);
        add(sellerCombo, "growx");

        addSectionTitle("Cihaz Bilgileri");
        deviceFormPanel = new DeviceFormPanel();
        add(deviceFormPanel, "growx");

        addSectionTitle("Alım Fiyatı");
        priceField = new CurrencyField();
        add(priceField, "growx");

        addSectionTitle("Alım Tarihi");
        JFormattedTextField dateField = new JFormattedTextField();
        datePicker = new DatePicker();
        datePicker.setDateFormat("dd/MM/yyyy");
        datePicker.setEditor(dateField);
        datePicker.now();
        add(dateField, "growx");

        addSectionTitle("Ekspertiz Notları");
        expertiseNotesArea = new JTextArea();
        expertiseNotesArea.setWrapStyleWord(true);
        expertiseNotesArea.setLineWrap(true);
        add(new JScrollPane(expertiseNotesArea), "height 120, grow, pushy");
    }

    public void setCustomers(List<Customer> customers) {
        sellerCombo.setCustomers(customers);
    }

    public void appendNewCustomer(Customer customer) {
        sellerCombo.appendCustomer(customer);
    }

    public Customer getSeller() {
        return sellerCombo.getSelectedItem();
    }

    public Device getDevice() {
        return deviceFormPanel.getDevice();
    }

    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceField.getDoubleValue());
    }

    public LocalDateTime getTransactionDate() {
        return datePicker.getSelectedDate() != null ? datePicker.getSelectedDate().atStartOfDay() : LocalDateTime.now();
    }

    public String getExpertiseNotes() {
        return expertiseNotesArea.getText().trim();
    }

    public void requestInitialFocus() {
        sellerCombo.grabFocus();
    }

    private void addSectionTitle(String title) {
        JLabel label = new JLabel(title);
        label.putClientProperty(FlatClientProperties.STYLE, "font:+2");
        add(label, "gapy 5 0");
        add(new JSeparator(), "height 2!, gapy 0 0");
    }
}
