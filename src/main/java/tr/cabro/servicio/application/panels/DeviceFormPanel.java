package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import raven.modal.Toast;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Cihaz bilgilerini (tür, marka, model, seri no, aksesuar) yöneten bağımsız panel.
 * <p>
 * NOT: Ekran kilidi (PIN/şifre/desen) bilgisi artık burada değil, servis kaydına (WorkOrder)
 * bağlı olarak {@code DeviceAccessField} ile ayrıca toplanır — bkz. {@link QuickIntakePanel}.
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
    private JTextField accessoryField;

    private DefaultComboBoxModel<DeviceType> deviceTypeModel;
    private DefaultComboBoxModel<DeviceBrand> brandModel;

    /**
     * Mevcut cihazın veritabanı ID'si. Null ise bu kayıt yeni bir cihazdır;
     * dolu ise güncelleme yapılacak demektir.
     */
    private Long currentDeviceId;

    private Device pendingDevice;

    /**
     * Populasyon sırasında (setDevice çağrıldığında) device_type_combo'nun
     * ActionListener'ının markaları boşaltmasını engelleyen koruyucu bayrak.
     */
    private boolean isPopulating;

    private final DeviceDictionaryManager dictionaryManager;

    // --- Müşteriye özel cihaz önerileri (seri no alanına yazarken/odaklanırken gösterilir) ---
    private final List<Device> suggestedDevices = new ArrayList<>();
    private JPopupMenu suggestionPopup;
    private DefaultListModel<Device> suggestionListModel;
    private JList<Device> suggestionList;

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

        add(new JLabel("Kozmetik Durumu:"), "span 2");
        add(accessoryField,                 "span 2, growx");

        initSuggestionPopup();
    }

    private void initEvents() {
        deviceTypeCombo.addActionListener(e -> {
            // Populasyon sırasında bu event tetiklenir ama marka listesini boşaltmamamız gerekir.
            if (isPopulating) return;
            DeviceType selected = (DeviceType) deviceTypeCombo.getSelectedItem();
            loadBrands(selected, null);
        });

        serialNoField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (isPopulating) return;
                filterSuggestions(serialNoField.getText());
                if (suggestionListModel.getSize() > 0) showSuggestionPopup();
            }

            @Override
            public void focusLost(FocusEvent e) {
                suggestionPopup.setVisible(false);
            }
        });
        serialNoField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onSerialTextChanged(); }
            public void removeUpdate(DocumentEvent e) { onSerialTextChanged(); }
            public void changedUpdate(DocumentEvent e) { onSerialTextChanged(); }
        });
    }

    private void onSerialTextChanged() {
        if (isPopulating) return; // applyDevice() alanları doldururken tetiklenmesin
        filterSuggestions(serialNoField.getText());
        if (serialNoField.isFocusOwner() && suggestionListModel.getSize() > 0) {
            showSuggestionPopup();
        } else if (suggestionPopup.isVisible()) {
            suggestionPopup.setVisible(false);
        }
    }

    // -------------------------------------------------------------------------
    // Müşteriye özel cihaz önerileri
    // -------------------------------------------------------------------------

    private void initSuggestionPopup() {
        suggestionPopup = new JPopupMenu();
        suggestionPopup.setFocusable(false); // odak serialNoField'de kalsın

        suggestionListModel = new DefaultListModel<>();
        suggestionList = new JList<>(suggestionListModel);
        suggestionList.setFocusable(false); // serialNoField'in odağını kaybetmesini (focusLost) önler
        suggestionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Device) {
                    Device d = (Device) value;
                    String brand = d.getBrand() != null ? d.getBrand().getName() : "-";
                    setText(brand + " " + nullToEmpty(d.getModel()) + " — " + nullToEmpty(d.getSerialNo()));
                }
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(suggestionList);
        suggestionPopup.add(scroll);
        suggestionPopup.setPreferredSize(new Dimension(320, 150));

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = suggestionList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    applyDevice(suggestionListModel.getElementAt(index));
                    suggestionPopup.setVisible(false);
                }
            }
        });
    }

    private void showSuggestionPopup() {
        suggestionPopup.setPopupSize(Math.max(serialNoField.getWidth(), 250),
                Math.min(200, suggestionListModel.getSize() * 36 + 10));
        suggestionPopup.show(serialNoField, 0, serialNoField.getHeight() + 2);
    }

    private void filterSuggestions(String text) {
        suggestionListModel.clear();
        String lower = text == null ? "" : text.toLowerCase();
        for (Device d : suggestedDevices) {
            String brand = d.getBrand() != null ? d.getBrand().getName().toLowerCase() : "";
            String model = d.getModel() != null ? d.getModel().toLowerCase() : "";
            String serial = d.getSerialNo() != null ? d.getSerialNo().toLowerCase() : "";
            if (brand.contains(lower) || model.contains(lower) || serial.contains(lower)) {
                suggestionListModel.addElement(d);
            }
        }
    }

    /** Seçili müşterinin daha önce sistemde kayıtlı cihazlarını önerilecek listeye yükler. */
    public void setSuggestedDevices(List<Device> devices) {
        suggestedDevices.clear();
        if (devices != null) suggestedDevices.addAll(devices);
        if (suggestionPopup != null && suggestionPopup.isVisible()) {
            filterSuggestions(serialNoField.getText());
        }
    }

    // -------------------------------------------------------------------------
    // Veri yükleme
    // -------------------------------------------------------------------------

    private void loadDeviceTypes() {
        dictionaryManager.getAllTypes().thenAccept(types -> SwingUtilities.invokeLater(() -> {
            deviceTypeModel.removeAllElements();
            types.forEach(deviceTypeModel::addElement);

            // Türler yüklendi — bekleyen device varsa şimdi uygula
            if (pendingDevice != null) {
                applyDevice(pendingDevice);
                pendingDevice = null;
            }
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
                    brands.stream()
                            .filter(b -> b.getId().equals(brandToSelect.getId()))
                            .findFirst()
                            .ifPresent(brandCombo::setSelectedItem);
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
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.device.serialRequired"));
            return;
        }

        ServiceManager.getDeviceService().getBySerialNo(serial).thenAccept(deviceOpt -> {
            SwingUtilities.invokeLater(() -> {
                if (deviceOpt.isPresent()) {
                    setDevice(deviceOpt.get());
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.device.foundInSystem"));
                } else {
                    Toast.show(this, Toast.Type.INFO, Messages.get("toast.device.notFoundWillCreate"));
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.WARNING, Messages.get("toast.device.serialSearchFailed")));
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
        if (device == null) { clear(); return; }

        // Türler henüz yüklenmediyse beklet
        if (deviceTypeModel.getSize() == 0) {
            pendingDevice = device;
            return;
        }
        applyDevice(device);
    }


    private void applyDevice(Device device) {
        isPopulating = true;
        try {
            currentDeviceId = device.getId();
            serialNoField.setText(nullToEmpty(device.getSerialNo()));
            modelField.setText(nullToEmpty(device.getModel()));
            accessoryField.setText(nullToEmpty(device.getAccessory()));

            // ID bazlı tür eşleştir
            DeviceType matchedType = null;
            if (device.getDeviceType() != null) {
                for (int i = 0; i < deviceTypeModel.getSize(); i++) {
                    DeviceType t = deviceTypeModel.getElementAt(i);
                    if (t.getId().equals(device.getDeviceType().getId())) {
                        matchedType = t;
                        break;
                    }
                }
            }
            deviceTypeCombo.setSelectedItem(matchedType);
            loadBrands(matchedType, device.getBrand());

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
            accessoryField.setText("");
            suggestedDevices.clear();
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