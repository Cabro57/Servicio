package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.ProcessEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.LaborService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class SettingsRepairPanel extends JPanel {

    private static final DeviceType ALL_TYPES = new DeviceType(null, "Tümü", 0);

    private final DefaultComboBoxModel<DeviceType> comboBoxModel = new DefaultComboBoxModel<>();
    private GenericTableModel<Labor> tableModal;

    private final LaborService laborService;

    public SettingsRepairPanel() {
        laborService = ServiceManager.getLaborService();

        init();
    }

    private void init() {
        initComponent();

        setupTable();

        comboBoxModel.addElement(ALL_TYPES);
        device_type_combo.setModel(comboBoxModel);
        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(types -> {
            SwingUtilities.invokeLater(() -> types.forEach(comboBoxModel::addElement));
        });

        refreshTable();

        device_type_combo.addActionListener(e -> refreshTable());

        add_button.addActionListener(e -> {
            setupEditModal("LABOR_ADD", false, new Labor());
        });
    }

    private void setupTable() {
        List<ColumnDef<Labor>> columns = Arrays.asList(
                new ColumnDef<>("İşlem Adı", String.class, Labor::getName),
                new ColumnDef<>("Tür", String.class, l -> l.getDeviceTypeId() == null ? "Genel" : l.getDeviceTypeName()),
                new ColumnDef<>("Açıklama", String.class, Labor::getDescription),
                new ColumnDef<>("Fiyat", BigDecimal.class, Labor::getDefaultPrice),
                new ColumnDef<>("İşlem", String.class, labor -> "Detay")
        );
        tableModal = new GenericTableModel<>(columns);

        table.setModel(tableModal);

        configureTableColumns();
    }

    private void setupEditModal(String id, boolean updated, Labor labor) {

        ProcessEditPanel panel = new ProcessEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("Çık", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(
                panel, "İşçilik Formu", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        panel.formOpen();
                        panel.formFill(labor);

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        Labor newLabor = panel.getLabor();

                        laborService.save(newLabor, updated)
                                .thenAccept(labor1 -> {
                                   SwingUtilities.invokeLater(() -> {
                                       refreshTable();
                                       Toast.show(this, Toast.Type.SUCCESS, labor1.getName() + " adlı işçilik eklendi.");
                                   });
                                }).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(() -> {
                                        controller.consume();
                                        Toast.show(this, Toast.Type.ERROR, ex.getMessage());
                                    });
                                    return null;
                                });
                    }
                })
        , id);
    }

    private void refreshTable() {
        DeviceType selectedType = (DeviceType) device_type_combo.getSelectedItem();
        boolean filterByType = selectedType != null && selectedType.getId() != null;

        var future = filterByType
                ? laborService.getByTypeId(selectedType.getId())
                : laborService.getAll();

        future.thenAccept(labors -> {
            SwingUtilities.invokeLater(() -> {
                tableModal.setData(labors);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage());
                Servicio.getLogger().error("Tablo yenileme hatası", ex);
            });
            return null;
        });
    }

    private void configureTableColumns() {

        table.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                Labor l = tableModal.getItemAt(modelRow);

                setupEditModal("LABOR_EDIT", true, l);
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
                            Toast.show(SettingsRepairPanel.this, Toast.Type.SUCCESS, "İşçilik silindi.");
                            refreshTable();
                        });
                    });
                }

            }

            @Override
            public void onView(int row) {

            }
        }));

        table.getColumnModel().getColumn(3).setMaxWidth(100);
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,insets 5,gapy 10", "[grow][pref]", "[][grow]"));

        // Cihaz tipi combobox
        device_type_combo = new JComboBox<>();
        add(device_type_combo, "growx");

        // Butonlar
        add_button = new JButton("Ekle");
        add(add_button, "wrap");

        // Tablo + scrollpane
        table = new JTable();
        JScrollPane table_scroll = new JScrollPane(table);
        add(table_scroll, "span, grow, pushy");
    }

    JTable table;
    JComboBox<DeviceType> device_type_combo;
    JButton add_button;

}
