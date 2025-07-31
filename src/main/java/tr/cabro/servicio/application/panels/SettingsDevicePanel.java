package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import java.util.List;

public class SettingsDevicePanel extends JPanel {
    private JPanel device_type_panel;
    private JLabel device_type_label;
    private JTextField device_type_field;
    private JButton device_type_add_button;
    private JList<String> device_type_list;
    private JButton device_type_del_button;
    private JPanel device_brand_panel;
    private JLabel brand_label;
    private JTextField brand_field;
    private JButton brand_add_button;
    private JList<String> brand_list;
    private JButton brand_del_button;
    private JPanel main_panel;
    private JScrollPane brand_scroll;
    private JScrollPane type_scroll;

    private final DefaultListModel<String> deviceTypeListModel = new DefaultListModel<>();
    private final DefaultListModel<String> brandListModel = new DefaultListModel<>();

    public SettingsDevicePanel() {
        init();
        add(main_panel);
    }

    private void init() {
        main_panel.setBackground(null);

        Settings settings = Servicio.getSettings();

        // Device types yükle
        settings.getDevice_types().forEach(deviceTypeListModel::addElement);

        device_type_list.setModel(deviceTypeListModel);
        brand_list.setModel(brandListModel);

        // Tür ekle
        device_type_add_button.addActionListener(e -> {
            String typeName = device_type_field.getText().trim();
            if (!typeName.isEmpty() && !deviceTypeListModel.contains(typeName)) {
                if (settings.addDeviceType(typeName)) {
                    deviceTypeListModel.addElement(typeName);
                    device_type_field.setText("");
                }
            }
        });

        // Tür sil
        device_type_del_button.addActionListener(e -> {
            String selectedType = device_type_list.getSelectedValue();
            if (selectedType != null) {
                if (settings.removeDeviceType(selectedType)) {
                    deviceTypeListModel.removeElement(selectedType);
                    brandListModel.clear();
                }
            }
        });

        // Tür seçildiğinde markaları yükle
        device_type_list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedType = device_type_list.getSelectedValue();
                loadBrands(selectedType);
            }
        });

        // Marka ekle
        brand_add_button.addActionListener(e -> {
            String selectedType = device_type_list.getSelectedValue();
            String brandName = brand_field.getText().trim();
            if (selectedType != null && !brandName.isEmpty()) {
                if (settings.addBrand(selectedType, brandName)) {
                    brandListModel.addElement(brandName);
                    brand_field.setText("");
                }
            }
        });

        // Marka sil
        brand_del_button.addActionListener(e -> {
            String selectedType = device_type_list.getSelectedValue();
            String selectedBrand = brand_list.getSelectedValue();
            if (selectedType != null && selectedBrand != null) {
                if (settings.removeBrand(selectedType, selectedBrand)) {
                    brandListModel.removeElement(selectedBrand);
                    // Eğer o türde artık hiç marka kalmadıysa, listeyi güncelle
                    if (settings.getBrands(selectedType).isEmpty()) {
                        deviceTypeListModel.removeElement(selectedType);
                    }
                }
            }
        });
    }

    private void loadBrands(String typeName) {
        brandListModel.clear();
        if (typeName != null) {
            List<String> brands = Servicio.getSettings().getBrands(typeName);
            brands.forEach(brandListModel::addElement);
        }
    }
}
