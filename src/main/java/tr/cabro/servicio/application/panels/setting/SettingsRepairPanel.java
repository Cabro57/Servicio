package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.forms.FormCustomers;
import tr.cabro.servicio.application.panels.ProcessEditPanel;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.LaborService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsRepairPanel extends JPanel {

    private final DefaultComboBoxModel<DeviceType> comboBoxModel = new DefaultComboBoxModel<>();
    private GenericTableModel<Labor> tableModal;

    private final LaborService laborService;

    public SettingsRepairPanel() {
        laborService = ServiceManager.getLaborService();

        List<ColumnDef<Labor>> columns = Arrays.asList(
                new ColumnDef<>("İşlem Adı", String.class, Labor::getName),
                new ColumnDef<>("Açıklama", String.class, Labor::getDescription),
                new ColumnDef<>("Fiyat", BigDecimal.class, Labor::getDefaultPrice),
                new ColumnDef<>("İşlem", String.class, labor -> "Detay")
        );
        tableModal = new GenericTableModel<>(columns);

        init();
    }

    private void init() {
        initComponent();

        initTable();

        add_button.addActionListener(e -> onProcessAdd());
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
                            // Tabloyu güncel işlem listesiyle yenile
                            tableModal.setData(settings.getProcesses(t));
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

        List<Process> processes = tableModal.getSelectedItems(table.getSelectedRows());
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

                        Process oldProcess = processes.get(0);
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
//        String type = (String) comboBoxModel.getSelectedItem();

//        if (type == null || type.isEmpty()) {
//            Toast.show(this, Toast.Type.WARNING, "Cihaz türü seçili değil.");
//            return;
//        }



        List<Process> selected = tableModal.getSelectedItems(process_table.getSelectedRows());
        if (selected.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Seçili işlem yok.");
            return;
        }

        int errorCount = 0;
        for (Process process : selected) {
            if (!.removeProcess(type, process.getName())) {
                Toast.show(this, Toast.Type.ERROR, process.getName() + " adlı işlem silinemedi.");
                errorCount++;
            }
        }

        if (errorCount > 0) {
            Toast.show(this, Toast.Type.WARNING, errorCount + " işlem silinemedi.");
        }

        // Tabloyu güncel listeyle yenile
//        loadProcesses();
        Toast.show(this, Toast.Type.SUCCESS, "Tüm işlemler başarılı şekilde silindi.");
    }

//    private void loadProcesses() {
//        DeviceType selectedType = (DeviceType) comboBoxModel.getSelectedItem();
//
//        deviceDictService.getBrandsByTypeId(selectedType.getId()).thenAccept(brands -> {
//
//        })
//        List<Process> processes = .getProcesses(selectedType);
//        tableModal.setData(processes);
//    }

    private void initTable() {
        table.setModel(tableModal);

        table.getColumnModel().getColumn(3).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(3).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {

            }

            @Override
            public void onDelete(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                Labor l = tableModal.getItemAt(modelRow);

                int confirm = JOptionPane.showConfirmDialog(
                        SettingsRepairPanel.this,
                        l.getName() + " adlı işçiliği silmek istediğinize emin misiniz?",
                        "İşçilik Sil",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    laborService.delete(l.getId()).thenAccept(response -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(SettingsRepairPanel.this, Toast.Type.SUCCESS, "Müşteri silindi.");
                            refreshTable();
                        });
                    });
                }

            }

            @Override
            public void onView(int row) {

            }
        }));

        table.getColumnModel().getColumn(2).setMaxWidth(100);
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,insets 5,gapy 10", "[grow][pref][pref][pref]", "[][grow]"));

        // Cihaz tipi combobox
        device_type_combo = new JComboBox<>();
        add(device_type_combo, "growx");

        // Butonlar
        add_button = new JButton("Ekle");
        add(add_button, "split 3");

        edit_button = new JButton("Düzenle");
        add(edit_button);

        delete_button = new JButton("Sil");
        add(delete_button, "wrap");

        // Tablo + scrollpane
        table = new JTable();
        JScrollPane table_scroll = new JScrollPane(table);
        add(table_scroll, "span, grow, pushy");
    }

    JTable table;
    JComboBox<DeviceType> device_type_combo;
    JButton add_button;
    JButton edit_button;
    JButton delete_button;

}
