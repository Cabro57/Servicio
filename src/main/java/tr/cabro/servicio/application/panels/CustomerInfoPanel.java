package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import lombok.Setter;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.ui.CustomerEditUI;
import tr.cabro.servicio.application.ui.CustomerSearchUI;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.CustomerService;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomerInfoPanel extends JPanel {
    private JPanel main_panel;

    private JLabel title;

    @Getter
    private tr.cabro.servicio.application.compenents.SearchField customer_field;
    private JButton new_customer_button;

    private JPanel date_info_panel;
    @Getter
    private JFormattedTextField record_date_field;
    @Getter @Setter
    private JFormattedTextField deliver_date_field;
    private JLabel record_date_label;
    private JLabel deliver_date_label;

    @Getter
    private Customer selected_customer;

    public CustomerInfoPanel() {
        init();
        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icon/customer.svg", 22, 22));
        customer_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");
        customer_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri adı, telefon, veya TC kimlik no yazıp ENTER tuşuna basın");
        customer_field.addActionListener(e -> {
            String s = customer_field.getText().trim();
            CustomerSearchUI customerSearchUI = new CustomerSearchUI(s);
            customerSearchUI.setModal(true);
            customerSearchUI.setVisible(true);

            Customer cs = customerSearchUI.getSelectedCustomer();

            if (cs != null) {
                setCustomer(cs);
            }
        });

        new_customer_button.addActionListener(e -> new_customer_cmd());

        date_info_panel.setBackground(null);

        DatePicker recordDatePicker = new DatePicker();
        recordDatePicker.setSelectedDate(LocalDate.now());
        recordDatePicker.setEditor(record_date_field);

        DatePicker deliverDatePicker = new DatePicker();
        deliverDatePicker.setEditor(deliver_date_field);
    }

    private void new_customer_cmd() {
        CustomerEditUI customerEditUI = new CustomerEditUI(null);
        customerEditUI.setModal(true);
        customerEditUI.setVisible(true);

        if (customerEditUI.isConfirmed()) {
            Customer customer = customerEditUI.getCustomerFromForm();
            if (customer != null) {
                setCustomer(customer);
            }
        }

        customer_field.requestFocusInWindow();
    }

    /**
     * Eksik olan metodları ekliyoruz
     */
    public int getCustomerId() {
        return selected_customer != null ? selected_customer.getID() : -1;
    }

    public void loadCustomer(int customerId) {
        CustomerService service = ServiceManager.getCustomerService();
        service.get(customerId).ifPresent(this::setCustomer);
    }

    public void setCustomer(Customer customer) {
        this.selected_customer = customer;
        if (customer != null) {
            customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, customer.getType().getIcon(22, 22));
            customer_field.setText(customer.toString());
        } else {
            customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icon/customer.svg", 22, 22));
            customer_field.setText("");
        }
    }

    public LocalDateTime getRecordDate() {
        try {
            String text = record_date_field.getText();
            if (text != null && !text.trim().isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDateTime.parse(text.trim(), formatter);
            }
        } catch (DateTimeParseException e) {
            // Geçersiz tarih varsa null döndür
        }
        return null;
    }

    public void setRecordDate(LocalDateTime date) {
        if (date != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            record_date_field.setText(date.format(formatter));
        } else {
            record_date_field.setText("");
        }
    }

    public LocalDateTime getDeliverDate() {
        try {
            String text = deliver_date_field.getText();
            if (text != null && !text.trim().isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDateTime.parse(text.trim(), formatter);
            }
        } catch (DateTimeParseException e) {
            // Geçersiz tarih varsa null döndür
        }
        return null;
    }

    public void setDeliverDate(LocalDateTime date) {
        if (date != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            deliver_date_field.setText(date.format(formatter));
        } else {
            deliver_date_field.setText("");
        }
    }


}
