package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.ProcessEditPanel;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.tablemodal.ProcessEditTableModel;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class SettingsRepairPanel extends JPanel {

    private final DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>();
    private final ProcessEditTableModel tableModal;

    private final DeviceSettings settings;

    public SettingsRepairPanel() {
        settings = Servicio.getDeviceSettings();

        tableModal = new ProcessEditTableModel(new ArrayList<>());

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
        final String id = "ProcessAdd";
        String type = (String) comboBoxModel.getSelectedItem();

        ProcessEditPanel panel = new ProcessEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                panel, "İşlem Kayıt Formu", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        panel.formOpen();
                        panel.setType(settings.getTypes());
                        panel.setSelectedType(type);

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        Process process = panel.getProcess();
                        String t = panel.getSelectedType();

                        if (t == null || t.equals("Seçiniz...")) {
                            Toast.show(panel, Toast.Type.WARNING, "Lütfen cihaz türü seçiniz.");
                            return;
                        }

                        boolean added = settings.addProcess(t, process);

                        if (added) {
                            tableModal.setProcesses(settings.getProcesses(t)); // tabloyu güncelle
                            Toast.show(this, Toast.Type.SUCCESS, process.getName() + " adlı işlem eklendi.");
                        } else {
                            Toast.show(this, Toast.Type.WARNING, process.getName() + " adlı işlem zaten mevcut.");
                        }
                    }
                })
        , id);
    }

    private void onProcessEdit() {
        final String id = "ProcessEdit";
        String type = (String) comboBoxModel.getSelectedItem();
        if (type == null || type.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Cihaz türü seçili değil.");
            return;
        }

        List<Process> processes = tableModal.getSelectedProcess();
        if (processes.size() != 1) {
            Toast.show(this, Toast.Type.WARNING, "Birden fazla işlem seçili.");
            return;
        }

        ProcessEditPanel panel = new ProcessEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                panel, "İşlem Kayıt Formu", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        panel.formOpen();
                        panel.setType(settings.getTypes());
                        panel.formFill(type, processes.get(0));

                    } else if (action == SimpleModalBorder.OK_OPTION) {

                        Process process = panel.getProcess();
                        String t = panel.getSelectedType();

                        if (t == null || t.equals("Seçiniz...")) {
                            Toast.show(panel, Toast.Type.WARNING, "Lütfen cihaz türü seçiniz.");
                            return;
                        }

                        Process oldProcess = processes.get(0); // seçilen ilk (ve tek) işlem
                        boolean updated = settings.updateProcess(t, oldProcess.getName(), process);

                        if (updated) {
                            Toast.show(this, Toast.Type.SUCCESS, "İşlem güncellendi.");
                            loadProcesses(); // tabloyu yenile
                        } else {
                            Toast.show(this, Toast.Type.ERROR, "İşlem güncellenemedi (aynı isimli işlem olabilir).");
                        }
                    }
                })
        , id);
    }

    private void onProcessDel() {
        String type = (String) comboBoxModel.getSelectedItem();

        if (type == null || type.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Cihaz türü seçili değil.");
            return;
        }

        List<Process> selected = tableModal.getSelectedProcess();
        if (selected.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Seçili işlem yok.");
            return;
        }

        int errorCount = 0;
        for (Process process : selected) {
            if (!settings.removeProcess(type, process.getName())) {
                Toast.show(this, Toast.Type.ERROR, process.getName() + " adlı işlem silinemedi.");
                errorCount++;
                continue;
            }
            tableModal.removeProcess(process);
        }

        if (errorCount > 0) {
            Toast.show(this, Toast.Type.WARNING, errorCount + " işlem silinemedi.");
        }

        Toast.show(this, Toast.Type.SUCCESS, "Tüm işlemler başarılı şekilde silindi.");
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
