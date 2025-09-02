package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.FieldPopupEditor;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.settings.Settings;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class DeviceInfoPanel extends ServicePanel {

    public DeviceInfoPanel(ServiceContext context) {
        super(context);
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

        device_type_combo.addActionListener((ActionEvent e) -> {
            String selectedType = (String) device_type_combo.getSelectedItem();
            loadBrands(selectedType);
        });

        device_type_combo.setSelectedItem(null);
    }

    private void loadDeviceTypes() {
        deviceTypeComboBoxModel.removeAllElements();
        Settings settings = Servicio.getSettings();
        List<String> types = settings.getDevice_types();
        for (String type : types) {
            deviceTypeComboBoxModel.addElement(type);
        }
    }

    private void loadBrands(String typeName) {
        brandComboBoxModel.removeAllElements();
        if (typeName != null) {
            Settings settings = Servicio.getSettings();
            List<String> brands = settings.getBrands(typeName);
            for (String brand : brands) {
                brandComboBoxModel.addElement(brand);
            }
        }
        brand_combo.setSelectedItem(null);
    }

    // 💡 Eksik setter metodları ekliyoruz
    public void setDeviceType(String type) {
        if (type != null) {
            device_type_combo.setSelectedItem(type);
            loadBrands(type);
        } else {
            device_type_combo.setSelectedItem(null);
        }
    }

    public void setDeviceBrand(String brand) {
        brand_combo.setSelectedItem(brand);
    }

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
        add(device_type_combo);
        add(brand_label);
        add(brand_combo);
        add(model_label);
        add(model_field);
        add(seri_no_label);
        add(seri_no_field);
        add(password_label);
        add(password_field);
        add(accessory_label);
        add(accessory_field);
    }

    JLabel device_type_label;
    JComboBox<String> device_type_combo;
    JLabel brand_label;
    JComboBox<String> brand_combo;
    JLabel model_label;
    public JTextField model_field;
    JLabel seri_no_label;
    public JTextField seri_no_field;
    JLabel password_label;
    public JTextField password_field;
    JLabel accessory_label;
    public JTextField accessory_field;
    JLabel title;

    public final DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    public final DefaultComboBoxModel<String> brandComboBoxModel = new DefaultComboBoxModel<>();
}
