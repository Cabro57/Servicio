package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.TemplateEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.DocumentTemplate;
import tr.cabro.servicio.model.enums.TemplateType;
import tr.cabro.servicio.service.DocumentTemplateService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class SettingsTemplatesPanel extends JPanel {

    private GenericTableModel<DocumentTemplate> tableModal;

    private final DocumentTemplateService templateService;

    public SettingsTemplatesPanel() {
        templateService = ServiceManager.getDocumentTemplateService();

        init();
    }

    private void init() {
        initComponent();

        setupTable();

        refreshTable();

        add_button.addActionListener(e -> {
            setupEditModal("TEMPLATE_ADD", false, new DocumentTemplate());
        });
    }

    private void setupTable() {
        List<ColumnDef<DocumentTemplate>> columns = Arrays.asList(
                new ColumnDef<>("Ad", String.class, DocumentTemplate::getName),
                new ColumnDef<>("İşlem", String.class, t -> "Detay")
        );
        tableModal = new GenericTableModel<>(columns);

        table.setModel(tableModal);

        configureTableColumns();
    }

    private void setupEditModal(String id, boolean updated, DocumentTemplate template) {

        TemplateEditPanel panel = new TemplateEditPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("Çık", 2)
        };

        AppModal.showModal(this, new SimpleModalBorder(
                panel, "Şablon Formu", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        panel.formOpen();
                        panel.formFill(template);

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        DocumentTemplate newTemplate = panel.getTemplate();
                        if (updated) newTemplate.setId(template.getId());

                        templateService.save(newTemplate, updated)
                                .thenAccept(saved -> {
                                    SwingUtilities.invokeLater(() -> {
                                        refreshTable();
                                        Toast.show(this, Toast.Type.SUCCESS, saved.getName() + " adlı şablon kaydedildi.");
                                    });
                                }).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(controller::consume);
                                    return ErrorHandler.handle(this, "Şablon kaydedilemedi", ex);
                                });
                    }
                })
        , id);
    }

    private void refreshTable() {
        templateService.getByType(TemplateType.WHATSAPP_MESSAGE).thenAccept(templates -> {
            SwingUtilities.invokeLater(() -> {
                tableModal.setData(templates);
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Şablon tablosu yenilenemedi", ex));
    }

    private void configureTableColumns() {

        table.getColumnModel().getColumn(1).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(1).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                DocumentTemplate t = tableModal.getItemAt(modelRow);

                setupEditModal("TEMPLATE_EDIT", true, t);
            }

            @Override
            public void onDelete(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                DocumentTemplate t = tableModal.getItemAt(modelRow);

                int confirm = JOptionPane.showConfirmDialog(
                        SettingsTemplatesPanel.this,
                        t.getName() + " adlı şablonu silmek istediğinize emin misiniz?",
                        "Şablon Sil",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    templateService.delete(t.getId()).thenAccept(response -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(SettingsTemplatesPanel.this, Toast.Type.SUCCESS, "Şablon silindi.");
                            refreshTable();
                        });
                    }).exceptionally(ex -> ErrorHandler.handle(SettingsTemplatesPanel.this, "Şablon silinemedi", ex));
                }
            }

            @Override
            public void onView(int row) {
            }
        }));
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,insets 5,gapy 10", "[grow][pref]", "[][grow]"));

        add_button = new JButton("Ekle");
        add(add_button, "span, align right, wrap");

        table = new JTable();
        JScrollPane table_scroll = new JScrollPane(table);
        add(table_scroll, "span, grow, pushy");
    }

    JTable table;
    JButton add_button;

}
