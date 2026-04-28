package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import raven.modal.Toast;
import raven.modal.component.ModalBorderAction;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.panels.edit.AbstractEditPanel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public class QuickIntakePanel extends AbstractEditPanel<WorkOrder> {

    public static final int NEW_CUSTOMER_ACTION = 50;

    private CustomerSelectBox customerCombo;
    private JTextArea reportedFaultArea;
    private JComboBox<DeviceType> device_type_combo;
    private JComboBox<DeviceBrand> brand_combo;
    private JTextField model_field;
    private JTextField seri_no_field;
    private JTextField password_field;
    private JTextField accessory_field;
    private JButton btnCheckSerial; // YENİ: Sorgulama Butonu

    private DefaultComboBoxModel<DeviceType> deviceTypeComboBoxModel;
    private DefaultComboBoxModel<DeviceBrand> brandComboBoxModel;
    private DefaultComboBoxModel<Customer> listModel;

    // YENİ: Eğer cihaz sistemde varsa, aynı ID'yi korumak için tutuyoruz
    private Long currentDeviceId = 0L;


    private DeviceDictionaryManager deviceDictService;
    public QuickIntakePanel(WorkOrder data) {
        super(data);
        initData();
        initEvents();
    }

    @Override
    protected void initComponent() {
        deviceDictService = ServiceManager.getDeviceDictionaryManager();

        deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
        brandComboBoxModel = new DefaultComboBoxModel<>();
        listModel = new DefaultComboBoxModel<>();

        setLayout(new MigLayout("fillx,wrap,insets 5 30 5 30,width 400", "[fill]", ""));

        // 1. Müşteri Seçimi
        customerCombo = new CustomerSelectBox(e -> {
            ModalBorderAction borderAction = ModalBorderAction.getModalBorderAction(this);
            if (borderAction != null) {
                borderAction.doAction(NEW_CUSTOMER_ACTION);
            }
        });

        add(new JLabel("Müşteri"), "gapy 5 0");
        add(customerCombo, "growx");

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

        // Sorgulama Butonu
        btnCheckSerial = new JButton("Sorgula", new Ikon("icons/search.svg"));
        btnCheckSerial.addActionListener(e -> checkDeviceBySerial());

        password_field = new JTextField();
        password_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        accessory_field = new JTextField();
        accessory_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        // Dizilim
        device_content.add(new JLabel("IMEI/Seri No:"), "span 2");
        device_content.add(seri_no_field, "split 2, growx, pushx"); // Split ile butonu yanına alıyoruz
        device_content.add(btnCheckSerial, "shrink");

        device_content.add(new JLabel("Cihaz Türü:"), "span 2");
        device_content.add(device_type_combo, "span 2");

        device_content.add(new JLabel("Marka:"), "sg 1");
        device_content.add(new JLabel("Model:"), "sg 1");
        device_content.add(brand_combo, "sg 1");
        device_content.add(model_field, "sg 1");

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

    // YENİ: Cihaz Sorgulama Mantığı
    private void checkDeviceBySerial() {
        String serial = seri_no_field.getText().trim();
        if (serial.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Lütfen sorgulamak için bir seri numarası girin.");
            return;
        }

        // TODO: Kendi mimarinize göre çağrıyı düzenleyin. Örn: ServiceManager.getDeviceService().findBySerialNo()
        ServiceManager.getDeviceService().getBySerialNo(serial).thenAccept(deviceOpt -> {
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                currentDeviceId = device.getId();

                SwingUtilities.invokeLater(() -> {
                    device_type_combo.setSelectedItem(device.getDeviceType());
                    loadBrands(device.getDeviceType()); // Markaları yükle
                    brand_combo.setSelectedItem(device.getBrand());
                    model_field.setText(device.getModel());

                    Toast.show(this, Toast.Type.SUCCESS, "Cihaz sistemde bulundu. Bilgiler getirildi.");
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    currentDeviceId = 0L; // Yeni cihaz olarak işaretle
                    Toast.show(this, Toast.Type.INFO, "Bu seri numarasına sahip cihaz bulunamadı. Yeni cihaz olarak kaydedilecek.");
                });
            }
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.WARNING, "Bu seri numarasıile arama yaparken bir hata oluştu.");
            });
            return null;
        });
    }

    @Override
    protected WorkOrder collectFormData(WorkOrder data) {
        Customer selectedCustomer = customerCombo.getSelectedItem();

        if (selectedCustomer == null) {
            showValidationError(Toast.Type.WARNING, "Lütfen bir müşteri seçin.");
            return null;
        }

        DeviceType dType = (DeviceType) device_type_combo.getSelectedItem();
        DeviceBrand dBrand = (DeviceBrand) brand_combo.getSelectedItem();

        if (dType == null || dBrand == null ) {
            showValidationError(Toast.Type.WARNING, "Cihaz türü ve markası seçmek zorunludur.");
            return null;
        }

        data.setCustomer(selectedCustomer);
        data.setCustomerId(selectedCustomer.getId());

        // YENİ: Bağımsız Cihaz Nesnesi Oluşturma
        Device device = new Device();
        device.setId(currentDeviceId); // Eğer sorgulanıp bulunduysa mevcut ID, yoksa 0 (yeni kayıt)
        device.setDeviceType(dType);
        device.setBrand(dBrand);
        device.setModel(model_field.getText().trim());
        device.setSerialNo(seri_no_field.getText().trim());
        device.setPassword(password_field.getText().trim());
        device.setAccessory(accessory_field.getText().trim());

        // Cihazı Servise Bağla
        data.setDevice(device);

        data.setReportedFault(reportedFaultArea.getText().trim());
        data.setCreatedAt(LocalDateTime.now());
        data.setServiceStatus(ServiceStatus.UNDER_REPAIR);

        return data;
    }

    @Override
    protected void populateFormWith(WorkOrder data) {
        if (data == null) return;

        if (data.getCustomer() != null) {
            listModel.addElement(data.getCustomer());
            customerCombo.setSelectedItem(data.getCustomer());
        }

        // YENİ: Servisin içindeki cihaza (Device) ulaşıyoruz
        Device device = data.getDevice();
        if (device != null) {
            currentDeviceId = device.getId(); // Düzenleme yapılıyorsa ID'yi koru

            if (device.getDeviceType() != null) {
                device_type_combo.setSelectedItem(device.getDeviceType());
                loadBrands(device.getDeviceType());

                if (device.getBrand() != null) {
                    brand_combo.setSelectedItem(device.getBrand());
                }
            }
            model_field.setText(device.getModel() != null ? device.getModel() : "");
            seri_no_field.setText(device.getSerialNo() != null ? device.getSerialNo() : "");
            password_field.setText(device.getPassword() != null ? device.getPassword() : "");
            accessory_field.setText(device.getAccessory() != null ? device.getAccessory() : "");
        } else {
            currentDeviceId = 0L;
            device_type_combo.setSelectedItem(null);
            brand_combo.setSelectedItem(null);
        }

        reportedFaultArea.setText(data.getReportedFault() != null ? data.getReportedFault() : "");
    }

    @Override
    protected void clearForm() {
        customerCombo.setSelectedItem(null);

        currentDeviceId = 0L; // Sıfırla
        device_type_combo.setSelectedItem(null);
        brandComboBoxModel.removeAllElements();

        model_field.setText("");
        seri_no_field.setText("");
        password_field.setText("");
        accessory_field.setText("");
        reportedFaultArea.setText("");
    }

    @Override
    protected WorkOrder createEmptyObject() {
        return new WorkOrder();
    }

    // --- YARDIMCI METODLAR ---

    private void initData() {
        ServiceManager.getCustomerService().getAll().thenAccept(customers -> {
            SwingUtilities.invokeLater(() -> {
                Customer currentSelection = customerCombo.getSelectedItem();
                customerCombo.setCustomers(customers);

                if (currentSelection != null) {
                    for (Customer c : customers) {
                        if (Objects.equals(c.getId(), currentSelection.getId())) {
                            customerCombo.setSelectedItem(c);
                            break;
                        }
                    }
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Müşteriler yüklenemedi."));
            return null;
        });
    }

    private void initEvents() {
        device_type_combo.addActionListener((ActionEvent e) -> {
            DeviceType selectedType = (DeviceType) device_type_combo.getSelectedItem();
            loadBrands(selectedType);
        });
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();

        deviceDictService.getAllTypes().thenAccept(deviceTypes -> {
            SwingUtilities.invokeLater(() -> {
                deviceTypes.forEach(deviceTypeComboBoxModel::addElement);

                DeviceType selectedType = (DeviceType) device_type_combo.getSelectedItem();
                loadBrands(selectedType);
            });
        });
    }

    private void loadBrands(DeviceType type) {
        brandComboBoxModel.removeAllElements();

        if (type == null) {
            return;
        }

        deviceDictService.getBrandsByTypeId(type.getId()).thenAccept(deviceBrands -> {
            deviceBrands.forEach(brandComboBoxModel::addElement);
        });
    }

    public void appendNewCustomer(Customer newCustomer) {
        customerCombo.appendCustomer(newCustomer);
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