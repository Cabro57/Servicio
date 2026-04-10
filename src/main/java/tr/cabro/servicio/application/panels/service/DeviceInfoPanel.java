package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.FieldPopupEditor;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.util.List;

public class DeviceInfoPanel extends ServicePanel {

    // Form yüklenirken sahte "Veri Değişti" sinyallerini engellemek için
    private boolean isInitializing = false;

    public DeviceInfoPanel() {
        init();
    }

    private void init() {
        initComponent();

        FieldPopupEditor popupEditor = new FieldPopupEditor(accessory_field);
        accessory_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, popupEditor.getTriggerButton());

        device_type_combo.setModel(deviceTypeComboBoxModel);
        AutoCompleteDecorator.decorate(device_type_combo);

        brand_combo.setModel(brandComboBoxModel);
        AutoCompleteDecorator.decorate(brand_combo);

        loadDeviceTypes();
        device_type_combo.setSelectedItem(null);

        // Dinleyicileri (Listeners) aktifleştir
        addListeners();
    }

    @Override
    protected void onServiceSet() {
        if (service == null) return;

        isInitializing = true; // Dinleyicileri (Listeners) geçici olarak sağır et
        try {
            // DİKKAT: Sıralama çok önemlidir! Önce Tür, Sonra Marka seçilmeli.
            setDeviceType(service.getDeviceType());
            setDeviceBrand(service.getDeviceBrand());

            model_field.setText(service.getDeviceModel() != null ? service.getDeviceModel() : "");
            seri_no_field.setText(service.getDeviceSerial() != null ? service.getDeviceSerial() : "");
            password_field.setText(service.getDevicePassword() != null ? service.getDevicePassword() : "");
            accessory_field.setText(service.getDeviceAccessory() != null ? service.getDeviceAccessory() : "");

        } finally {
            isInitializing = false; // Form doldu, dinleyicileri tekrar aç
        }
    }

    private void addListeners() {
        // Metin kutuları için ortak dinleyici (Klavye ile bir harf bile yazılsa tetiklenir)
        DocumentListener documentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { notifyDataChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { notifyDataChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { notifyDataChanged(); }
        };

        model_field.getDocument().addDocumentListener(documentListener);
        seri_no_field.getDocument().addDocumentListener(documentListener);
        password_field.getDocument().addDocumentListener(documentListener);
        accessory_field.getDocument().addDocumentListener(documentListener);

        // Cihaz Türü değiştiğinde markaları yükle ve ana formu uyar
        device_type_combo.addActionListener((ActionEvent e) -> {
            if (!isInitializing) {
                String selectedType = (String) device_type_combo.getSelectedItem();
                loadBrands(selectedType);
                notifyDataChanged();
            }
        });

        // Marka değiştiğinde ana formu uyar
        brand_combo.addActionListener(e -> {
            if (!isInitializing) notifyDataChanged();
        });
    }

    private void notifyDataChanged() {
        if (!isInitializing && getListener() != null) {
            getListener().onDataChanged(); // Ana formdaki 'Güncelle' butonunu aktif et
        }
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        DeviceSettings settings = Servicio.getDeviceSettings();
        List<String> types = settings.getTypes();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
    }

    private void loadBrands(String typeName) {
        brandComboBoxModel.removeAllElements();
        if (typeName != null) {
            DeviceSettings settings = Servicio.getDeviceSettings();
            List<String> brands = settings.getBrands(typeName);
            for (String brand : brands) {
                brandComboBoxModel.addElement(brand);
            }
        }
        brand_combo.setSelectedItem(null);
    }

    public void setDeviceType(String type) {
        if (type != null) {
            device_type_combo.setSelectedItem(type);
            // Eğer isInitializing True ise Listener tetiklenmeyeceği için markaları manuel yüklüyoruz
            if (isInitializing) loadBrands(type);
        } else {
            device_type_combo.setSelectedItem(null);
            brandComboBoxModel.removeAllElements();
        }
    }

    public void setDeviceBrand(String brand) {
        brand_combo.setSelectedItem(brand);
    }

    // --- FormService'in verileri toplaması (collectForm) için güvenli Getter'lar ---
    public String getSelectedDeviceType() { return (String) device_type_combo.getSelectedItem(); }
    public String getSelectedBrand() { return (String) brand_combo.getSelectedItem(); }
    public String getDeviceModel() { return model_field.getText().trim(); }
    public String getDeviceSerial() { return seri_no_field.getText().trim(); }
    public String getDevicePassword() { return password_field.getText().trim(); }
    public String getDeviceAccessory() { return accessory_field.getText().trim(); }

    private void initComponent() {
        setLayout(new MigLayout("insets 5, wrap 4", "[][grow,fill][][grow,fill]", "[]"));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Cihaz Bilgileri");
        title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");

        device_type_label = new JLabel("Cihaz Türü:");
        device_type_combo = new JComboBox<>();

        brand_label = new JLabel("Marka:");
        brand_combo = new JComboBox<>();

        model_label = new JLabel("Model:");
        model_field = new JTextField();
        model_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        seri_no_label = new JLabel("IMEI/Seri No:");
        seri_no_field = new JTextField();
        seri_no_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        password_label = new JLabel("Ekran Şifresi:");
        password_field = new JTextField();
        password_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        accessory_label = new JLabel("Kozmetik Durumu:");
        accessory_field = new JTextField();
        accessory_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        add(title, "span 4, align left, gapbottom 10");

        add(device_type_label);
        add(device_type_combo, "sg combos");
        add(brand_label);
        add(brand_combo, "sg combos");
        add(model_label);
        add(model_field, "sg combos");
        add(seri_no_label);
        add(seri_no_field, "sg combos");
        add(password_label);
        add(password_field, "sg combos");
        add(accessory_label);
        add(accessory_field, "sg combos");
    }

    private JLabel device_type_label;
    public JComboBox<String> device_type_combo;
    private JLabel brand_label;
    public JComboBox<String> brand_combo;
    private JLabel model_label;
    public JTextField model_field;
    private JLabel seri_no_label;
    public JTextField seri_no_field;
    private JLabel password_label;
    public JTextField password_field;
    private JLabel accessory_label;
    public JTextField accessory_field;
    private JLabel title;

    public final DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    public final DefaultComboBoxModel<String> brandComboBoxModel = new DefaultComboBoxModel<>();
}