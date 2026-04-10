package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import raven.datetime.TimePicker;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.SearchField;
import tr.cabro.servicio.application.panels.SearchCustomerPanel;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CustomerInfoPanel extends ServicePanel {

    private final CustomerService customerService;

    public CustomerInfoPanel() {
        this.customerService = ServiceManager.getCustomerService();
        init();
    }

    @Override
    protected void onServiceSet() {
        // Hydration sayesinde customer nesnesi servisin içinde zaten dolu geliyor!
        // Ekstra veritabanı sorgusuna gerek yok.
        if (service != null) {
            updateCustomerUI(service.getCustomer());
            setRecordDate(service.getCreatedAt());
            setDeliverDate(service.getDeliveryAt());
        }
    }

    private void init() {
        initComponent();

        // 1. Müşteri Temizleme (Çarpı İkonuna Basınca)
        customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK, (Runnable) () -> {
            updateCustomerSelection(null);
        });

        // 2. Müşteri Seçme (Enter'a basınca)
        customer_field.addActionListener(e -> {
            final String id = "CustomerSelect";
            SearchCustomerPanel panel = new SearchCustomerPanel();

            ModalDialog.showModal(this, new SimpleModalBorder(
                            panel, "Müşteri Seç", null,
                            (controller, action) -> {
                                if (action == SimpleModalBorder.OPENED) {
                                    panel.setOnCustomerSelected(customer -> {
                                        updateCustomerSelection(customer);
                                        ModalDialog.closeModal("CustomerSelect");
                                    });
                                }
                            })
                    , id);
        });

        // 3. Yeni Müşteri Oluşturma (Tamamen Asenkron)
        customer_button.addActionListener(e -> {
            final String id = "CustomerNew";
            CustomerEditPanel panel = new CustomerEditPanel(new Customer());

            SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                    new SimpleModalBorder.Option("Kaydet", 0),
                    new SimpleModalBorder.Option("İptal", 2)
            };

            ModalDialog.showModal(this, new SimpleModalBorder(
                            panel, "Yeni Müşteri Formu", options,
                            (controller, action) -> {
                                if (action == SimpleModalBorder.OPENED) {
                                    panel.clearForm();

                                } else if (action == SimpleModalBorder.OK_OPTION) {
                                    Customer newCustomer = panel.getData();
                                    if (newCustomer == null) {
                                        controller.consume();
                                        return;
                                    }

                                    newCustomer.setCreatedAt(LocalDateTime.now());

                                    // KRİTİK DÜZELTME: service.save yerine customerService.saveAsync
                                    customerService.save(newCustomer, false).thenAccept(savedCustomer -> {
                                        SwingUtilities.invokeLater(() -> {
                                            updateCustomerSelection(savedCustomer);
                                            Toast.show(this, Toast.Type.SUCCESS, savedCustomer.getName() + " başarıyla eklendi.");
                                        });
                                    }).exceptionally(ex -> {
                                        SwingUtilities.invokeLater(() -> {
                                            Toast.show(this, Toast.Type.ERROR, "Kayıt Hatası: " + ex.getCause().getMessage());
                                        });
                                        return null;
                                    });
                                }
                            })
                    , id);
        });

        // Tarih ve Saat Seçicileri
        recordDatePicker = new DatePicker();
        recordDatePicker.setEditor(record_date_field);
        recordDatePicker.setSelectedDate(LocalDate.now());
        recordTimePicker = new TimePicker();

        deliverDatePicker = new DatePicker();
        deliverDatePicker.setEditor(deliver_date_field);
        deliverTimePicker = new TimePicker();

        // UX İYİLEŞTİRMESİ: Tarih veya saat değiştiğinde de ana formu uyar
        recordDatePicker.addDateSelectionListener(d -> notifyDataChanged());
        recordTimePicker.addTimeSelectionListener(t -> notifyDataChanged());
        deliverDatePicker.addDateSelectionListener(d -> notifyDataChanged());
        deliverTimePicker.addTimeSelectionListener(t -> notifyDataChanged());
    }

    // --- MERKEZİ VERİ YÖNETİMİ ---

    /**
     * Müşteri seçildiğinde, silindiğinde veya yeni eklendiğinde çağrılır.
     * Hem UI'ı, hem Servis modelini günceller, hem de Ana Formu uyarır.
     */
    private void updateCustomerSelection(Customer customer) {
        if (service != null) {
            service.setCustomer(customer);
            service.setCustomerId(customer != null ? customer.getId() : null);
        }
        updateCustomerUI(customer);
        notifyDataChanged();
    }

    private void updateCustomerUI(Customer customer) {
        if (customer != null) {
            // ZIRH: Eğer müşterinin türü NULL gelirse, uygulamanın çökmemesi için varsayılan olarak NORMAL ata
            CustomerType type = customer.getType() != null ? customer.getType() : CustomerType.NORMAL;

            customer_field.setText(customer.toString());
        } else {
            customer_field.setText("");
        }
    }

    private void notifyDataChanged() {
        if (getListener() != null) {
            getListener().onDataChanged();
        }
    }

    // Ana formun collectForm metodu için kullanışlı getter
    public Customer getSelectedCustomer() {
        return service != null ? service.getCustomer() : null;
    }

    // --- TARİH GETTER / SETTER METOTLARI ---

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
        title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));
        title.setHorizontalTextPosition(SwingConstants.LEFT);
        title.setHorizontalAlignment(SwingConstants.LEFT);

        customer_field = new SearchField();
        customer_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSVGIcon("icons/customer.svg", 22, 22));
        customer_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");
        customer_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri adı, telefon veya TC kimlik no yazıp ENTER tuşuna basın");
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
        add(title, "span 2, align left, gapbottom 10");
        date_panel.add(deliver_date_field, "growx");

        add(customer_field, "growx, height 50::");
        add(customer_button, "growy, align right, wrap");
        add(date_panel, "span 2, growx");
    }

    private JLabel title;
    private SearchField customer_field;
    private JButton customer_button;

    private JFormattedTextField record_date_field;
    private JFormattedTextField deliver_date_field;
    private JLabel record_date_label;
    private JLabel deliver_date_label;

    private DatePicker recordDatePicker;
    private TimePicker recordTimePicker;
    private DatePicker deliverDatePicker;
    private TimePicker deliverTimePicker;
}