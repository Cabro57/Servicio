package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import raven.modal.Toast;
import raven.modal.component.ModalBorderAction;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CustomerCellRenderer;
import tr.cabro.servicio.application.component.EmbeddedComboBox;
import tr.cabro.servicio.application.panels.edit.AbstractEditPanel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.util.List;

public class QuickIntakePanel extends AbstractEditPanel<Service> {

    private final String MODAL_ID;
    public static final int NEW_CUSTOMER_ACTION = 50;

    private EmbeddedComboBox<Customer> customerCombo;
    private JTextArea reportedFaultArea;
    private JComboBox<String> device_type_combo;
    private JComboBox<String> brand_combo;
    private JTextField model_field;
    private JTextField seri_no_field;
    private JTextField password_field;
    private JTextField accessory_field;

    // Sadece deklare ediyoruz. "new" anahtar kelimesi ile oluşturmayı initComponent'in içine alacağız.
    private DefaultComboBoxModel<String> deviceTypeComboBoxModel;
    private DefaultComboBoxModel<String> brandComboBoxModel;
    private DefaultComboBoxModel<Customer> listModel;

    public QuickIntakePanel(String modalId, Service data) {
        // DIKKAT: super(data) cagrildiginda dogrudan initComponent() tetiklenecek.
        super(data);
        this.MODAL_ID = modalId;

        initData();
        initEvents();
    }

    @Override
    protected void initComponent() {
        // HATA ÇÖZÜMÜ BURASI: Abstract class'tan buraya gelindiğinde değişkenler henüz null'dır.
        // Onları hemen burada, ilk satırlarda bellekte yaratıyoruz.
        deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
        brandComboBoxModel = new DefaultComboBoxModel<>();
        listModel = new DefaultComboBoxModel<>();

        setLayout(new MigLayout("fillx,wrap,insets 5 30 5 30,width 400", "[fill]", ""));

        // 1. Müşteri Seçimi
        customerCombo = createEmbeddedComboBox(e -> {
            ModalBorderAction borderAction = ModalBorderAction.getModalBorderAction(this);
            if (borderAction != null) {
                borderAction.doAction(NEW_CUSTOMER_ACTION);
            }
        });

        add(new JLabel("Müşteri"), "gapy 5 0");
        add(customerCombo, "");

        // 2. Cihaz Bilgileri
        createTitle("Cihaz Bilgileri");
        JPanel device_content = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[fill, grow][fill, grow]", "[]2[]8[]2[]8[]2[]8[]2[]"));

        device_type_combo = new JComboBox<>(deviceTypeComboBoxModel);
        AutoCompleteDecorator.decorate(device_type_combo);

        brand_combo = new JComboBox<>(brandComboBoxModel);
        AutoCompleteDecorator.decorate(brand_combo);

        model_field = new JTextField();
        model_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        seri_no_field = new JTextField();
        seri_no_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        password_field = new JTextField();
        password_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        accessory_field = new JTextField();
        accessory_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        // Dizilim
        device_content.add(new JLabel("Cihaz Türü:"), "span 2");
        device_content.add(device_type_combo, "span 2");

        device_content.add(new JLabel("Marka:"), "sg 1");
        device_content.add(new JLabel("Model:"), "sg 1");
        device_content.add(brand_combo, "sg 1");
        device_content.add(model_field, "sg 1");

        device_content.add(new JLabel("IMEI/Seri No:"), "span 2");
        device_content.add(seri_no_field, "span 2");

        device_content.add(new JLabel("Kozmetik Durumu:"), "sg 1");
        device_content.add(new JLabel("Ekran Şifresi:"), "sg 1");
        device_content.add(accessory_field, "sg 1");
        device_content.add(password_field, "sg 1");

        add(device_content, "growx");

        // 3. Şikayet
        createTitle("Müşteri Şikayeti");

        reportedFaultArea = new JTextArea();
        reportedFaultArea.setWrapStyleWord(true);
        reportedFaultArea.setLineWrap(true);
        JScrollPane scroll = new JScrollPane(reportedFaultArea);

        add(new JLabel("Şikayet/Arıza:"), "gapy 5 0");
        add(scroll, "height 150,grow,pushy");

        reportedFaultArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.isControlDown() && e.getKeyChar() == 10) {
                    ModalBorderAction modalBorderAction = ModalBorderAction.getModalBorderAction(QuickIntakePanel.this);
                    if (modalBorderAction != null) {
                        modalBorderAction.doAction(SimpleModalBorder.YES_OPTION);
                    }
                }
            }
        });

        loadDeviceTypes();
    }

    @Override
    protected Service collectFormData(Service data) {
        Customer selectedCustomer = (Customer) customerCombo.getSelectedItem();

        if (selectedCustomer == null) {
            showValidationError(Toast.Type.WARNING, "Lütfen bir müşteri seçin.");
            return null;
        }

        String dType = (String) device_type_combo.getSelectedItem();
        String dBrand = (String) brand_combo.getSelectedItem();

        if (dType == null || dType.trim().isEmpty() || dBrand == null || dBrand.trim().isEmpty()) {
            showValidationError(Toast.Type.WARNING, "Cihaz türü ve markası seçmek zorunludur.");
            return null;
        }

        data.setCustomer(selectedCustomer);
        data.setCustomerId(selectedCustomer.getId());

        data.setDeviceType(dType);
        data.setDeviceBrand(dBrand);
        data.setDeviceModel(model_field.getText().trim());
        data.setDeviceSerial(seri_no_field.getText().trim());
        data.setDevicePassword(password_field.getText().trim());
        data.setDeviceAccessory(accessory_field.getText().trim());
        data.setReportedFault(reportedFaultArea.getText().trim());

        data.setCreatedAt(LocalDateTime.now());
        data.setServiceStatus(ServiceStatus.UNDER_REPAIR);

        return data;
    }

    @Override
    protected void populateFormWith(Service data) {
        if (data == null) return;

        if (data.getCustomer() != null) {
            listModel.addElement(data.getCustomer());
            customerCombo.setSelectedItem(data.getCustomer());
        }

        // Eğer veritabanından gelen dolu bir Service ise türü ve markayı seç
        if (data.getDeviceType() != null && !data.getDeviceType().isEmpty()) {
            device_type_combo.setSelectedItem(data.getDeviceType());
            loadBrands(data.getDeviceType());

            if (data.getDeviceBrand() != null) {
                brand_combo.setSelectedItem(data.getDeviceBrand());
            }
        } else {
            // DÜZELTME: Eğer YENİ BİR KAYIT açılıyorsa (data boşsa),
            // kullanıcıyı seçim yapmaya zorlamak için kutuları boşalt.
            device_type_combo.setSelectedItem(null);
            brand_combo.setSelectedItem(null);
        }

        model_field.setText(data.getDeviceModel() != null ? data.getDeviceModel() : "");
        seri_no_field.setText(data.getDeviceSerial() != null ? data.getDeviceSerial() : "");
        password_field.setText(data.getDevicePassword() != null ? data.getDevicePassword() : "");
        accessory_field.setText(data.getDeviceAccessory() != null ? data.getDeviceAccessory() : "");
        reportedFaultArea.setText(data.getReportedFault() != null ? data.getReportedFault() : "");
    }

    @Override
    protected void clearForm() {
        customerCombo.setSelectedItem(null);

        // Form temizlenirken ComboBox seçimlerini iptal et
        device_type_combo.setSelectedItem(null);
        brandComboBoxModel.removeAllElements(); // Tür seçili olmadığı için markaları da temizle

        model_field.setText("");
        seri_no_field.setText("");
        password_field.setText("");
        accessory_field.setText("");
        reportedFaultArea.setText("");
    }

    @Override
    protected Service createEmptyObject() {
        return new Service();
    }

    // --- YARDIMCI METODLAR ---

    private void initData() {
        ServiceManager.getCustomerService().getAll().thenAccept(customers -> {
            SwingUtilities.invokeLater(() -> {
                // Seçili müşteriyi hafızaya al
                Customer selectedCustomer = (Customer) customerCombo.getSelectedItem();

                listModel.removeAllElements();

                if (customers != null) {
                    for (Customer c : customers) {
                        listModel.addElement(c);
                        // Veritabanından gelen referans ile hafızadaki nesneyi ID üzerinden eşleştir
                        if (selectedCustomer != null && c.getId() == selectedCustomer.getId()) {
                            selectedCustomer = c;
                        }
                    }
                }

                // Müşteriyi listeye geri oturt
                if (selectedCustomer != null) {
                    customerCombo.setSelectedItem(selectedCustomer);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Müşteriler yüklenemedi."));
            return null;
        });
    }

    private void initEvents() {
        device_type_combo.addActionListener((ActionEvent e) -> {
            String selectedType = (String) device_type_combo.getSelectedItem();
            loadBrands(selectedType);
        });
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        DeviceSettings settings = Servicio.getDeviceSettings();
        if (settings != null) {
            List<String> types = settings.getTypes();
            for (String type : types) {
                deviceTypeComboBoxModel.addElement(type);
            }

            String selectedType = (String) device_type_combo.getSelectedItem();
            if (selectedType != null) {
                loadBrands(selectedType);
            }
        }
    }

    private void loadBrands(String typeName) {
        brandComboBoxModel.removeAllElements();
        if (typeName != null) {
            DeviceSettings settings = Servicio.getDeviceSettings();
            if (settings != null) {
                List<String> brands = settings.getBrands(typeName);
                for (String brand : brands) {
                    brandComboBoxModel.addElement(brand);
                }
            }
        }
    }

    public void appendNewCustomer(Customer newCustomer) {
        listModel.addElement(newCustomer);
        customerCombo.setSelectedItem(newCustomer);
    }

    private EmbeddedComboBox<Customer> createEmbeddedComboBox(ActionListener event) {
        EmbeddedComboBox<Customer> embeddedComboBox = new EmbeddedComboBox<>();
        embeddedComboBox.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İsim veya Telefon yazın...");
        embeddedComboBox.setRenderer(new CustomerCellRenderer());

        // Model artık burada null gelmeyecek çünkü initComponent başında yaratıldı.
        embeddedComboBox.setModel(listModel);

        embeddedComboBox.setEditable(true);
        AutoCompleteDecorator.decorate(embeddedComboBox);
        JToolBar toolbar = new JToolBar();
        JButton button = new JButton(new Ikon("icons/user-plus.svg"));
        button.addActionListener(event);
        toolbar.add(button);
        toolbar.addSeparator();
        embeddedComboBox.setEmbedded(toolbar);
        return embeddedComboBox;
    }

    private void createTitle(String title) {
        JLabel lb = new JLabel(title);
        lb.putClientProperty(FlatClientProperties.STYLE, "font:+2");
        add(lb, "gapy 5 0");
        add(new JSeparator(), "height 2!,gapy 0 0");
    }

    public void formOpen() {
        customerCombo.grabFocus();
    }
}