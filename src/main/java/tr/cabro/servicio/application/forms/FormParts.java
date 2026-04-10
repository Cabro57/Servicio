package tr.cabro.servicio.forms;

import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.edit.PartEditPanel;
import tr.cabro.servicio.application.renderer.AlignedRenderer;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.forms.base.AbstractTableForm;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SystemForm(name = "Parçalar", description = "Yeni parçalar eklemek ve düzenlemek için kullanılabilir")
public class FormParts extends AbstractTableForm<Part> {

    private final PartService service;
    private GenericTableModel<Part> partTableModel;

    public FormParts() {
        this.service = ServiceManager.getPartService();
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Part>> columns = Arrays.asList(
                new ColumnDef<>("Barkod", String.class, Part::getBarcode),
                new ColumnDef<>("Marka", String.class, Part::getBrand),
                new ColumnDef<>("Ürün Adı", String.class, Part::getName),
                new ColumnDef<>("Cihaz Türü", String.class, Part::getDeviceType),
                new ColumnDef<>("Uyumlu Model", String.class, Part::getModel),
                new ColumnDef<>("Stok", Integer.class, Part::getStock),
                new ColumnDef<>("Alış Fiyatı", String.class, p -> Format.formatPrice(p.getPurchasePrice())),
                new ColumnDef<>("Satış Fiyatı", String.class, p -> Format.formatPrice(p.getSalePrice())),
                new ColumnDef<>("Alış Tarihi", String.class, p -> Format.formatDate(p.getPurchaseDate()))
        );
        partTableModel = new GenericTableModel<>(columns);
        setTableModel(partTableModel);
        configureTableColumns();
    }

    @Override
    protected void refreshTable() {

        service.getAll().thenAccept(parts -> {
            SwingUtilities.invokeLater(() -> {
                partTableModel.setData(parts);
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, "Veriler yüklenirken hata oluştu: " + ex.getMessage());
                Servicio.getLogger().error("Parts refresh error", ex);
                resetKeyboardActions();
            });
            return  null;
        });

    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.CENTER, SwingConstants.TRAILING, SwingConstants.TRAILING,
                SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        if (table.getColumnCount() > 0) {
            if (table.getColumnCount() > 4) {
                table.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());
                table.getColumnModel().getColumn(5).setCellRenderer(new AlignedRenderer(table, 5, SwingConstants.CENTER));
            }
            table.getColumnModel().getColumn(0).setMinWidth(150); // Barkod
        }
    }

    /** Tabloda seçili satırlardaki parça nesnelerini döndürür. */
    private List<Part> getSelectedParts() {
        return partTableModel.getSelectedItems(table.getSelectedRows());
    }

    @Override
    protected void onNew() {
        final String id = "PartNew";
        PartEditPanel panel = new PartEditPanel(new Part());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Yeni Parça Ekle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.clearForm();
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Part updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setCreatedAt(LocalDateTime.now());
                                service.save(updated, false).thenAccept(part -> {
                                    SwingUtilities.invokeLater(() -> {
                                        Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
                                        refreshTable();
                                    });
                                }).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(() -> {
                                        controller.consume();
                                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                                    });
                                    Servicio.getLogger().error("Parça ekleme hatası", ex);
                                    return  null;
                                });
                            }
                        })
                , id);
    }

    @Override
    protected void onEdit() {
        List<Part> selected = getSelectedParts();

        if (selected.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen düzenlemek için bir parça seçin.");
            return;
        }
        if (selected.size() > 1) {
            Toast.show(this, Toast.Type.INFO, "Düzenlemek için sadece 1 parça seçin.");
            return;
        }

        final String id = "PartEdit";
        Part part = selected.get(0);
        PartEditPanel panel = new PartEditPanel(part);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Parça Düzenle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.populateFormWith(part);
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Part updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                service.save(updated, true).thenAccept(updare -> {
                                    SwingUtilities.invokeLater(() -> {
                                        Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
                                        refreshTable();
                                    });
                                }).exceptionally(ex -> {
                                    SwingUtilities.invokeLater(() -> {
                                        controller.consume();
                                        Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + ex.getMessage());

                                    });
                                    Servicio.getLogger().error("Parça güncelleme hatası", ex);
                                    return  null;
                                });
                                try {


                                } catch (Exception e) {
                                }
                            }
                        })
                , id);
    }

    @Override
    protected void onDelete() {
        List<Part> selects = getSelectedParts();

        if (selects.isEmpty()) {
            Toast.show(this, Toast.Type.INFO, "Lütfen silmek için bir parça seçin.");
            return;
        }

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Seçilen " + selects.size() + " parçayı silmek istediğinizden emin misiniz?", "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {


                List<String> idsToDelete = selects.stream().map(Part::getBarcode).collect(Collectors.toList());

                service.deleteMultiple(idsToDelete).thenAccept(v -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, selects.size() + " adet müşteri silindi.");
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.ERROR, "Silme işlemi başarısız oldu.");
                    });
                    return null;
                });
        }));
    }
}