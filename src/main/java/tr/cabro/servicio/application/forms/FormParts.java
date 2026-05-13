package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.edit.PartEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@SystemForm(name = "Parçalar", description = "Yeni parçalar eklemek ve düzenlemek için kullanılabilir")
public class FormParts extends AbstractTableForm {

    private final PartService partService;
    private GenericTableModel<Part> tableModel;

    public FormParts() {
        this.partService = ServiceManager.getPartService();
    }

    @Override
    protected String getNewButtonText() {
       return "Yeni Parça Ekle";
    }

    @Override
    protected String getNewButtonIconPath() {
       return "icons/package-plus.svg";
    }

    @Override
    protected String getTableTitleText() {
       return "Envanter Listesi";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Parça adı, SKU veya Kategori ara...";
    }

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/package-check.svg", 0.7f), "Parça Çeşidi");
        cardBox.addCardItem(new Ikon("icons/sigma.svg", 0.7f), "Toplam Stok");
        cardBox.addCardItem(new Ikon("icons/circle-alert.svg", 0.7f), "Kritik Stok");
        cardBox.addCardItem(new Ikon("icons/turkish-lira.svg", 0.7f), "Envanter Değeri");
    }

    @Override
    protected void refreshStats() {
        partService.getAll().thenAccept(allParts -> {

            // 1. Parça Çeşidi (Sistemdeki farklı ürün/barkod sayısı)
            long partVarietyCount = allParts.size();

            // 2. Toplam Stok (Tüm parçaların stok adetlerinin toplamı)
            long totalStock = allParts.stream()
                    .mapToLong(Part::getStockQuantity)
                    .sum();

            // 3. Kritik Stok (Stok seviyesi 'minStock' değerinin altına düşen parça sayısı)
            long criticalStockCount = allParts.stream()
                    .filter(p -> p.getStockQuantity() < p.getMinStockLevel())
                    .count();

            // 4. Envanter Değeri (Stoktaki ürünlerin alış fiyatı üzerinden toplam sermaye değeri)
            BigDecimal totalInventoryValue = allParts.stream()
                    .map(Part::getTotalStockValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            SwingUtilities.invokeLater(() -> {
                cardBox.setValueAt(0, String.valueOf(partVarietyCount), "Parça Çeşidi", "", true);
                cardBox.setValueAt(1, String.valueOf(totalStock), "Toplam Stok", "", true);
                cardBox.setValueAt(2, String.valueOf(criticalStockCount), "Kritik Stok", "", true);
                cardBox.setValueAt(3, Format.formatPrice(totalInventoryValue), "Envanter Değeri", "", true);
            });
        }).exceptionally(ex -> {
            Servicio.getLogger().error("İstatistikler çekilirken hata oluştu!", ex);
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, "İstatistikler çekilirken hata oluştu!");
            });
            return null;
        });
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Part>> columns = Arrays.asList(
                new ColumnDef<>("SKU", String.class, Part::getBarcode),
                new ColumnDef<>("Parça Adı", String.class, Part::getName),
                new ColumnDef<>("Kategori",  String.class, Part::getCategory),
                new ColumnDef<>("Uyumlu Model", String.class, Part::getModelCompatibility),
                new ColumnDef<>("Tedarikçi",  Supplier.class, Part::getSupplier),
                new ColumnDef<>("Stok", Integer.class, Part::getStockQuantity),
                new ColumnDef<>("Birim Fiyat", BigDecimal.class, Part::getSalePrice),
                new ColumnDef<>("İşlem", String.class, p -> "Detay")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        configureTableColumns();
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.CENTER,
                SwingConstants.TRAILING,
                SwingConstants.CENTER
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEADING);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: +1");
                return c;
            }
        });

        table.getColumnModel().getColumn(2).setCellRenderer(new TooltipCellRenderer());

        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                return label;
            }
        });

        table.getColumnModel().getColumn(6).setCellRenderer(new CurrencyTableCellRenderer());

        table.getColumnModel().getColumn(7).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(7).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                Part part = tableModel.getItemAt(modelRow);
                openEditModal(part);
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                Part selectedPart = tableModel.getItemAt(modelRow);

                ModalDialog.showModal(FormParts.this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                        "Bu parçayı silmek istediğinizden emin misiniz?", "Silme Onayı",
                        SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {

                    if (action == SimpleModalBorder.YES_OPTION) {
                        partService.delete(selectedPart.getId()).thenAccept(v -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormParts.this, Toast.Type.SUCCESS," Parça silindi.");
                                refreshTable();
                            });
                        }).exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormParts.this, Toast.Type.ERROR, "Silme işlemi başarısız oldu.");
                            });
                            return null;
                        });
                    }
                }));
            }

            @Override
            public void onView(int row) {
                Toast.show(FormParts.this, Toast.Type.WARNING, "Yapım aşamasında ");
            }
        }));

        // Genişlik Ayarları
        table.getColumnModel().getColumn(0).setMinWidth(150); // Barkod
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setMaxWidth(70);
        table.getColumnModel().getColumn(7).setMaxWidth(180);
        table.getColumnModel().getColumn(7).setMinWidth(120);
    }

    @Override
    protected void refreshTable() {

        partService.getAll().thenAccept(parts -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(parts);
                refreshStats();
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

    @Override
    protected void onNew() {
        final String id = "PartNew";
        PartEditPanel panel = new PartEditPanel(new Part());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Yeni Parça Ekle", options,
            (controller, action) -> {
                if (action == SimpleModalBorder.OK_OPTION) {
                    Part updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    updated.setCreatedAt(LocalDateTime.now());
                    partService.save(updated, false).thenAccept(part -> {
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

    private void openEditModal(Part part) {
        final String id = "PartEdit";
        PartEditPanel panel = new PartEditPanel(part);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Parça Düzenle", options,
            (controller, action) -> {
                 if (action == SimpleModalBorder.OK_OPTION) {
                    Part updated = panel.getData();
                    if (updated == null) {
                        controller.consume();
                        return;
                    }

                    partService.save(updated, true).thenAccept(upgrade -> {
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
                }
            })
        , id);
    }
}