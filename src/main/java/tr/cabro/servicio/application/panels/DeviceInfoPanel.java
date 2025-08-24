package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.Getter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.FieldPopupEditor;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class DeviceInfoPanel extends JPanel {

    private JLabel device_type_label;
    @Getter
    private JComboBox<String> device_type_combo;
    private JLabel brand_label;
    @Getter
    private JComboBox<String> brand_combo;
    private JLabel model_label;
    @Getter
    private JTextField model_field;
    private JLabel seri_no_label;
    @Getter
    private JTextField seri_no_field;
    private JLabel password_label;
    @Getter
    private JTextField password_field;
    private JLabel accessory_label;
    @Getter
    private JTextField accessory_field;
    private JPanel main_panel;
    private JLabel title;

    private final DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> brandComboBoxModel = new DefaultComboBoxModel<>();

    public DeviceInfoPanel() {
        init();
        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        model_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        model_field.setColumns(10);
        password_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        password_field.setColumns(10);
        seri_no_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        seri_no_field.setColumns(10);

        FieldPopupEditor popupEditor = new FieldPopupEditor(accessory_field);
        accessory_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, popupEditor.getTriggerButton());
        accessory_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        accessory_field.setColumns(10);

        model_field.setPreferredSize(accessory_field.getPreferredSize());

        device_type_combo.setModel(deviceTypeComboBoxModel);
        brand_combo.setModel(brandComboBoxModel);

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
        if (brand != null) {
            brand_combo.setSelectedItem(brand);
        } else {
            brand_combo.setSelectedItem(null);
        }
    }

    public void setDeviceModel(String model) {
        model_field.setText(model != null ? model : "");
    }

    public void setDeviceSerial(String serial) {
        seri_no_field.setText(serial != null ? serial : "");
    }

    public void setDevicePassword(String password) {
        password_field.setText(password != null ? password : "");
    }

    public void setDeviceAccessory(String accessory) {
        accessory_field.setText(accessory != null ? accessory : "");
    }

    public String getDeviceType() {
        return (String) device_type_combo.getSelectedItem();
    }

    public String getDeviceBrand() {
        return (String) brand_combo.getSelectedItem();
    }

    public String getDeviceModel() {
        return model_field.getText().trim();
    }

    public String getDeviceSerial() {
        return seri_no_field.getText().trim();
    }

    public String getDevicePassword() {
        return password_field.getText().trim();
    }

    public String getDeviceAccessory() {
        return accessory_field.getText().trim();
    }

}
