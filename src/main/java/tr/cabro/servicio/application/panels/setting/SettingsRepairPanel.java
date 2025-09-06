package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.ProcessEditPanel;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.tablemodal.ProcessEditTableModal;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class SettingsRepairPanel extends JPanel {

    private final DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>();
    private final ProcessEditTableModal tableModal;

    private final DeviceSettings settings;

    public SettingsRepairPanel() {
        settings = Servicio.getDeviceSettings();

        tableModal = new ProcessEditTableModal(new ArrayList<>());

        init();
    }

    private void init() {
        initComponent();

        initTable();

        device_type_combo.setModel(comboBoxModel);

        add_button.addActionListener(e -> onProcessAdd());
        edit_button.addActionListener(e -> onProcessEdit());
        delete_button.addActionListener(e -> onProcessDel());

        settings.getTypes().forEach(comboBoxModel::addElement);

        device_type_combo.addActionListener((ActionEvent e) -> loadProcesses());

        device_type_combo.setSelectedItem(null);
    }

    private void onProcessAdd() {
        ProcessEditPanel panel = new ProcessEditPanel();

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "İşlem Form"));
    }

    private void onProcessEdit() {

    }

    private void onProcessDel() {

    }

    private void loadProcesses() {
        String selectedType = (String) device_type_combo.getSelectedItem();

        List<Process> processes = settings.getProcesses(selectedType);

        tableModal.setProcesses(processes);
    }

    private void initTable() {
        process_table.setModel(tableModal);

        process_table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(process_table, 0));

        process_table.getColumnModel().getColumn(0).setMaxWidth(50);
        process_table.getColumnModel().getColumn(3).setMaxWidth(100);
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,insets 5,gapy 10", "[grow][pref][pref][pref]", "[][grow]"));

        // Cihaz tipi combobox
        device_type_combo = new JComboBox<>();
        add(device_type_combo, "growx");

        // Butonlar
        add_button = new JButton("Ekle");
        add(add_button, "split 3"); // diğer butonlarla birlikte hizala

        edit_button = new JButton("Düzenle");
        add(edit_button);

        delete_button = new JButton("Sil");
        add(delete_button, "wrap");

        // Tablo + scrollpane
        process_table = new JTable();
        JScrollPane table_scroll = new JScrollPane(process_table);
        add(table_scroll, "span, grow, pushy");
    }

    JTable process_table;
    JComboBox<String> device_type_combo;
    JButton add_button;
    JButton edit_button;
    JButton delete_button;

}
