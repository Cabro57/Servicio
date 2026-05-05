package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.util.Ikon;
import raven.modal.Toast;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

/**
 * Cihaz bilgilerini (tür, marka, model, seri no, şifre, aksesuar) yöneten bağımsız panel.
 * <p>
 * Sorumlulukları:
 * <ul>
 *   <li>Cihaz alanlarını göstermek ve kullanıcı girişini almak</li>
 *   <li>Seri numarasına göre sistemde cihaz aramak ve alanları doldurmak</li>
 *   <li>Mevcut bir cihazın ID'sini saklamak (düzenleme modunda üzerine yazılmayı önler)</li>
 * </ul>
 * <p>
 * Bu panel kendi içinde tutarlı bir duruma sahiptir; {@link QuickIntakePanel} sadece
 * {@link #getDevice()} ve {@link #setDevice(Device)} ile etkileşime girer.
 */
public class DeviceFormPanel extends JPanel {

    // --- Alan bileşenleri ---
    private JComboBox<DeviceType> deviceTypeCombo;
    private JComboBox<DeviceBrand> brandCombo;
    private JTextField modelField;
    private JTextField serialNoField;
    private JTextField passwordField;
    private JTextField accessoryField;

    private DefaultComboBoxModel<DeviceType> deviceTypeModel;
    private DefaultComboBoxModel<DeviceBrand> brandModel;

    /**
     * Mevcut cihazın veritabanı ID'si. Null ise bu kayıt yeni bir cihazdır;
     * dolu ise güncelleme yapılacak demektir.
     */
    private Long currentDeviceId;

    /**
     * Populasyon sırasında (setDevice çağrıldığında) device_type_combo'nun
     * ActionListener'ının markaları boşaltmasını engelleyen koruyucu bayrak.
     */
    private boolean isPopulating;

    private final DeviceDictionaryManager dictionaryManager;

    public DeviceFormPanel() {
        this.dictionaryManager = ServiceManager.getDeviceDictionaryManager();
        initComponents();
        initEvents();
        loadDeviceTypes();
    }

    // -------------------------------------------------------------------------
    // Kurulum
    // -------------------------------------------------------------------------

    private void initComponents() {
        setLayout(new MigLayout("insets 0, fillx, wrap 2", "[fill,grow][fill,grow]", "[]2[]8[]2[]8[]2[]8[]2[]"));

        deviceTypeModel = new DefaultComboBoxModel<>();
        brandModel = new DefaultComboBoxModel<>();

        deviceTypeCombo = new JComboBox<>(deviceTypeModel);
        AutoCompleteDecorator.decorate(deviceTypeCombo);

        brandCombo = new JComboBox<>(brandModel);
        AutoCompleteDecorator.decorate(brandCombo);

        modelField = buildClearableField();
        serialNoField = buildClearableField();
        passwordField = buildClearableField();
        accessoryField = buildClearableField();

        // Seri no alanına arama butonu yerleştir
        JButton btnSearch = new JButton(new Ikon("icons/search.svg", 0.7f));
        btnSearch.setToolTipText("Seri numarasına göre cihaz ara");
        btnSearch.addActionListener(e -> searchBySerialNo());
        serialNoField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnSearch);

        add(new JLabel("IMEI / Seri No:"), "span 2");
        add(serialNoField, "span 2, growx");

        add(new JLabel("Cihaz Türü:"), "span 2");
        add(deviceTypeCombo, "span 2");

        add(new JLabel("Marka:"),          "sg col");
        add(new JLabel("Model:"),          "sg col");
        add(brandCombo,                    "sg col");
        add(modelField,                    "sg col");

        add(new JLabel("Kozmetik Durumu:"), "sg col");
        add(new JLabel("Ekran Şifresi:"),   "sg col");
        add(accessoryField,                 "sg col");
        add(passwordField,                  "sg col");
    }

    private void initEvents() {
        deviceTypeCombo.addActionListener(e -> {
            // Populasyon sırasında bu event tetiklenir ama marka listesini boşaltmamamız gerekir.
            if (isPopulating) return;
            DeviceType selected = (DeviceType) deviceTypeCombo.getSelectedItem();
            loadBrands(selected, null);
        });
    }

    // -------------------------------------------------------------------------
    // Veri yükleme
    // -------------------------------------------------------------------------

    private void loadDeviceTypes() {
        dictionaryManager.getAllTypes().thenAccept(types -> SwingUtilities.invokeLater(() -> {
            deviceTypeModel.removeAllElements();
            types.forEach(deviceTypeModel::addElement);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Cihaz türleri yüklenemedi", ex);
            return null;
        });
    }

    /**
     * Seçili türe göre marka listesini yükler.
     *
     * @param type           Filtrelenecek cihaz türü; null ise liste temizlenir.
     * @param brandToSelect  Yükleme tamamlandığında seçilecek marka; null ise ilk eleman seçilir.
     */
    private void loadBrands(DeviceType type, DeviceBrand brandToSelect) {
        brandModel.removeAllElements();
        if (type == null) return;

        dictionaryManager.getBrandsByTypeId(type.getId()).thenAccept(brands -> {
            SwingUtilities.invokeLater(() -> {
                brands.forEach(brandModel::addElement);
                if (brandToSelect != null) {
                    brandCombo.setSelectedItem(brandToSelect);
                }
            });
        }).exceptionally(ex -> {
            Servicio.getLogger().error("Markalar yüklenemedi", ex);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Seri no ile cihaz arama
    // -------------------------------------------------------------------------

    private void searchBySerialNo() {
        String serial = serialNoField.getText().trim();
        if (serial.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Lütfen sorgulamak için bir seri numarası girin.");
            return;
        }

        ServiceManager.getDeviceService().getBySerialNo(serial).thenAccept(deviceOpt -> {
            SwingUtilities.invokeLater(() -> {
                if (deviceOpt.isPresent()) {
                    setDevice(deviceOpt.get());
                    Toast.show(this, Toast.Type.SUCCESS, "Cihaz sistemde bulundu. Bilgiler getirildi.");
                } else {
                    Toast.show(this, Toast.Type.INFO, "Bu seri numarasına sahip cihaz bulunamadı. Yeni cihaz olarak kaydedilecek.");
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.WARNING, "Seri numarası ile arama sırasında bir hata oluştu."));
            Servicio.getLogger().error("Seri no araması başarısız", ex);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Dışa açık API
    // -------------------------------------------------------------------------

    /**
     * Formu verilen cihaz bilgileriyle doldurur.
     * Null geçilirse form sıfırlanır (yeni kayıt modu).
     */
    public void setDevice(Device device) {
        if (device == null) {
            clear();
            return;
        }

        isPopulating = true;
        try {
            currentDeviceId = device.getId();
            serialNoField.setText(nullToEmpty(device.getSerialNo()));
            modelField.setText(nullToEmpty(device.getModel()));
            passwordField.setText(nullToEmpty(device.getPassword()));
            accessoryField.setText(nullToEmpty(device.getAccessory()));

            DeviceType type = device.getDeviceType();
            deviceTypeCombo.setSelectedItem(type);

            // Markaları yükle; yükleme tamamlanınca ilgili markayı seç.
            loadBrands(type, device.getBrand());
        } finally {
            isPopulating = false;
        }
    }

    /**
     * Form alanlarından bir {@link Device} nesnesi oluşturur.
     * Zorunlu alanlar (tür ve marka) boşsa null döner.
     */
    public Device getDevice() {
        DeviceType type = (DeviceType) deviceTypeCombo.getSelectedItem();
        DeviceBrand brand = (DeviceBrand) brandCombo.getSelectedItem();
        String model = modelField.getText().trim();

        if (type == null || brand == null || model.isEmpty()) {
            return null;
        }

        Device device = new Device();
        device.setId(currentDeviceId);
        device.setDeviceType(type);
        device.setBrand(brand);
        device.setModel(model);
        String serialNo = serialNoField.getText().trim();
        if (!serialNo.isEmpty()) {
            device.setSerialNo(serialNoField.getText().trim());
        }
        device.setPassword(passwordField.getText().trim());
        device.setAccessory(accessoryField.getText().trim());
        return device;
    }

    /**
     * Tüm alanları ve iç durumu sıfırlar.
     */
    public void clear() {
        isPopulating = true;
        try {
            currentDeviceId = null;
            deviceTypeCombo.setSelectedItem(null);
            brandModel.removeAllElements();
            modelField.setText("");
            serialNoField.setText("");
            passwordField.setText("");
            accessoryField.setText("");
        } finally {
            isPopulating = false;
        }
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    private JTextField buildClearableField() {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        return field;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}