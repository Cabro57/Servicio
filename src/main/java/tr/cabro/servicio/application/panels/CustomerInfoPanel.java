package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import lombok.Setter;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.compenents.SearchField;
import tr.cabro.servicio.application.ui.CustomerEditUI;
import tr.cabro.servicio.application.ui.CustomerSearchUI;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.CustomerService;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CustomerInfoPanel extends JPanel {
    private JPanel main_panel;

    private JLabel title;

    @Getter
    private SearchField customer_field;
    private JButton new_customer_button;

    private JPanel date_info_panel;
    private JFormattedTextField record_date_field;
    private JFormattedTextField deliver_date_field;
    private JLabel record_date_label;
    private JLabel deliver_date_label;

    private DatePicker recordDatePicker;
    private DatePicker deliverDatePicker;

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

        recordDatePicker = new DatePicker();
        recordDatePicker.setSelectedDate(LocalDate.now());
        recordDatePicker.setEditor(record_date_field);

        deliverDatePicker = new DatePicker();
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
        LocalDate date = recordDatePicker.getSelectedDate();

        return LocalDateTime.of(date, LocalTime.now());
    }

    public void setRecordDate(LocalDateTime date) {
        recordDatePicker.setSelectedDate(date.toLocalDate());
    }

    public LocalDateTime getDeliverDate() {
        LocalDate date = deliverDatePicker.getSelectedDate();
        return LocalDateTime.of(date, LocalTime.now());
    }

    public void setDeliverDate(LocalDateTime date) {
        deliverDatePicker.setSelectedDate(date.toLocalDate());
    }


}
