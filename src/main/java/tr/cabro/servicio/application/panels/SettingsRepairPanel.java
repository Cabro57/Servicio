package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.util.Map;

public class SettingsRepairPanel extends JPanel {

    private JTable process_table;
    private JComboBox<String> device_type_combo;
    private JButton add_button;
    private JButton edit_button;
    private JButton delete_button;
    private JScrollPane table_scroll;
    private JPanel main_panel;

    private DefaultComboBoxModel<String> deviceTypeComboBoxModel = new DefaultComboBoxModel<>();
    private DefaultTableModel processTableModel = new DefaultTableModel(new Object[]{"İşlem", "Fiyat"}, 0);

    public SettingsRepairPanel() {
        init();
        add(main_panel);
    }

    private void init() {
        process_table.setModel(processTableModel);

        device_type_combo.setModel(deviceTypeComboBoxModel);

        Settings settings = Servicio.getSettings();
        settings.getDevice_types().forEach(deviceTypeComboBoxModel::addElement);

        device_type_combo.addActionListener((ActionEvent e) -> {
            String selectedType = (String) device_type_combo.getSelectedItem();
            loadProcesses(selectedType);
        });

        device_type_combo.setSelectedItem(null);

        add_button.addActionListener(e -> {
            String selectedType = (String) device_type_combo.getSelectedItem();
            if (selectedType != null) {
                String processName = JOptionPane.showInputDialog(this, "İşlem Adı:");
                if (processName != null && !processName.trim().isEmpty()) {
                    String priceStr = JOptionPane.showInputDialog(this, "Fiyat:");
                    try {
                        Double price = Double.parseDouble(priceStr);
                        if (Servicio.getSettings().addProcess(selectedType, processName, price)) {
                            processTableModel.addRow(new Object[]{processName, price});
                        } else {
                            // Güncellendi
                            loadProcesses(selectedType);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "Geçersiz fiyat girişi!", "Hata", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        delete_button.addActionListener(e -> {
            String selectedType = (String) device_type_combo.getSelectedItem();
            int selectedRow = process_table.getSelectedRow();
            if (selectedType != null && selectedRow != -1) {
                String processName = (String) processTableModel.getValueAt(selectedRow, 0);
                if (Servicio.getSettings().removeProcess(selectedType, processName)) {
                    processTableModel.removeRow(selectedRow);
                }
            }
        });

        edit_button.addActionListener(e -> {
            String selectedType = (String) device_type_combo.getSelectedItem();
            int selectedRow = process_table.getSelectedRow();
            if (selectedType != null && selectedRow != -1) {
                String processName = (String) processTableModel.getValueAt(selectedRow, 0);
                String priceStr = JOptionPane.showInputDialog(this, "Yeni Fiyat:", processTableModel.getValueAt(selectedRow, 1));
                try {
                    Double price = Double.parseDouble(priceStr);
                    Servicio.getSettings().addProcess(selectedType, processName, price);
                    processTableModel.setValueAt(price, selectedRow, 1);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Geçersiz fiyat girişi!", "Hata", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void loadProcesses(String selectedType) {
        processTableModel.setRowCount(0);
        if (selectedType != null) {
            Map<String, Double> processes = Servicio.getSettings().getProcess(selectedType);
            processes.forEach((name, price) -> {
                processTableModel.addRow(new Object[]{name, price});
            });
        }
    }
}
