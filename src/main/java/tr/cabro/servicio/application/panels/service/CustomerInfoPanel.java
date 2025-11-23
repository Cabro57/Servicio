package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.datetime.TimePicker;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.SearchField;
import tr.cabro.servicio.application.panels.SearchCustomerPanel;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

public class CustomerInfoPanel extends ServicePanel {

    public Customer selectedCustomer;

    private final CustomerService service;

    public CustomerInfoPanel(ServiceContext context) {
        super(context);

        service = ServiceManager.getCustomerService();

        init();
    }

    private void init() {
        initComponent();

        customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK, (Runnable) () -> {
            if (selectedCustomer != null) selectedCustomer = null;
            customer_field.setText("");
        });

        customer_field.addActionListener(e -> {
            final String id = "CustomerSelect";
            SearchCustomerPanel panel = new SearchCustomerPanel();

            ModalDialog.showModal(this, new SimpleModalBorder(
                            panel, "Parça Formu", null,
                            (controller, action) -> {
                                if (action == SimpleModalBorder.OPENED) {
                                    panel.setOnCustomerSelected(customer -> {
                                        setCustomer(customer);
                                        ModalDialog.closeModal("CustomerSelect");
                                    });
                                }
                            })
                    , id);
        });

        customer_button.addActionListener(e -> {
            final String id = "CustomerNew";
            CustomerEditPanel panel = new CustomerEditPanel();

            SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                    new SimpleModalBorder.Option("Tamam", 0),
                    new SimpleModalBorder.Option("İptal", 2)
            };

            ModalDialog.showModal(this, new SimpleModalBorder(
                            panel, "Müşteri Formu", options,
                            (controller, action) -> {
                                if (action == SimpleModalBorder.OPENED) {
                                    panel.clearForm();

                                } else if (action == SimpleModalBorder.OK_OPTION) {
                                    Customer customer = panel.getData();
                                    if (customer == null) {
                                        controller.consume();
                                        return;
                                    }

                                    try {
                                        customer.setCreatedAt(LocalDateTime.now());
                                        service.save(customer, false);
                                        Toast.show(this, Toast.Type.SUCCESS, customer.getName() + " başarıyla eklendi.");
                                    } catch (Exception ex) {
                                        Toast.show(this, Toast.Type.WARNING, customer.getName() + " zaten mevcut.");
                                        Servicio.getLogger().error(customer.getName() + " zaten mevcut.", e);
                                    }

                                    setCustomer(customer);
                                }
                            })
                    , id);
        });

        recordDatePicker = new DatePicker();
        recordDatePicker.setEditor(record_date_field);
        recordDatePicker.setSelectedDate(LocalDate.now());
        recordTimePicker = new TimePicker();

        deliverDatePicker = new DatePicker();
        deliverDatePicker.setEditor(deliver_date_field);
        deliverTimePicker = new TimePicker();


    }

    public void setCustomer(int serviceId) {
        Optional<Customer> customer = service.get(serviceId);

        customer.ifPresent(this::setCustomer);
    }

    public void setCustomer(Customer customer) {
        this.selectedCustomer = customer;
        if (customer != null) {
            customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, customer.getType().getIcon(22, 22));
            customer_field.setText(customer.toString());
        } else {
            customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, CustomerType.NORMAL.getIcon(22, 22));
            customer_field.setText("");
        }
    }

    public LocalDateTime getRecordDate() {
        LocalDate date = recordDatePicker.getSelectedDate();
        LocalTime time = recordTimePicker.getSelectedTime();
        if (date == null) return null;
        if (time == null) time = LocalTime.now();
        return LocalDateTime.of(date, time);
    }

    public void setRecordDate(LocalDateTime dateTime) {
        if (dateTime == null) return;
        recordTimePicker.setSelectedTime(dateTime.toLocalTime());
        recordDatePicker.setSelectedDate(dateTime.toLocalDate());
    }

    public LocalDateTime getDeliverDate() {
        LocalDate date = deliverDatePicker.getSelectedDate();
        LocalTime time = deliverTimePicker.getSelectedTime();
        if (date == null) return null;
        if (time == null) time = LocalTime.now();
        return LocalDateTime.of(date, time);
    }

    public void setDeliverDate(LocalDateTime dateTime) {
        if (dateTime == null) return;
        deliverTimePicker.setSelectedTime(dateTime.toLocalTime());
        deliverDatePicker.setSelectedDate(dateTime.toLocalDate());
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap 2, insets 10, fillx", "[grow][pref!]", "[][][]"));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Müşteri Bilgileri");
        title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3"); // daha hoş stil için
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));
        title.setHorizontalTextPosition(SwingConstants.LEFT);
        title.setHorizontalAlignment(SwingConstants.LEFT);

        customer_field = new SearchField();
        customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icons/customer.svg", 22, 22));
        customer_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");
        customer_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri adı, telefon, veya TC kimlik no yazıp ENTER tuşuna basın");
        customer_field.setFont(customer_field.getFont().deriveFont(16f));
        customer_field.requestFocusInWindow();

        customer_button = new JButton("Yeni Müşteri");
        customer_button.setFont(customer_button.getFont().deriveFont(java.awt.Font.BOLD, 12f));

        record_date_label = new JLabel("Kayıt Tarihi: ");
        record_date_field = new JFormattedTextField();

        deliver_date_label = new JLabel("Teslim Tarihi:");
        deliver_date_field = new JFormattedTextField();

        JPanel date_panel = new JPanel(new MigLayout("insets 0, fillx", "[][grow][][grow]", "[]"));
        date_panel.setBackground(null);

        date_panel.add(record_date_label);
        date_panel.add(record_date_field, "growx");
        date_panel.add(deliver_date_label);
        date_panel.add(deliver_date_field, "growx");

        add(title, "span 2, align left, gapbottom 10");
        add(customer_field, "growx, height 50::");
        add(customer_button, "growy, align right, wrap");
        add(date_panel, "span 2, growx");
    }

    JLabel title;

    SearchField customer_field;
    JButton customer_button;

    JFormattedTextField record_date_field;
    JFormattedTextField deliver_date_field;
    JLabel record_date_label;
    JLabel deliver_date_label;

    DatePicker recordDatePicker;
    TimePicker recordTimePicker;
    DatePicker deliverDatePicker;
    TimePicker deliverTimePicker;
}