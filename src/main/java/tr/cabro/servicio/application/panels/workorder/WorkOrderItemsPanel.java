package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.WorkOrderItemAddPanel;
import tr.cabro.servicio.application.panels.WorkOrderItemEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.SourceType;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * `FormWorkOrder`'ın sağ kolonundaki "Kullanılan Parçalar ve Ücretlendirme" kartı — eskiden
 * {@code FormWorkOrder.buildPartsCard()}/{@code populateItemsTable()}/{@code openItemAddModal()}/
 * {@code openItemEditModal()}/{@code confirmDeleteItem()} olarak tek sınıfta duruyordu.
 * Kalem ekleme/düzenleme/silme sonrası kalan bakiye değişebildiği için {@code onItemsChanged}
 * callback'i çağırılır (orkestratör bunu ödeme panelinin {@code refresh()}'ine bağlar).
 */
public class WorkOrderItemsPanel extends JPanel {

    private final WorkOrder workOrder;
    private final WorkOrderService workOrderService;
    private final Runnable onItemsChanged;

    private GenericTableModel<WorkOrderItem> itemsTableModel;
    private JPanel itemsTableContainer;
    private JPanel itemsEmptyLabel;

    public WorkOrderItemsPanel(WorkOrder workOrder, Runnable onItemsChanged) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.onItemsChanged = onItemsChanged;
        build();
    }

    private void build() {
        putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 15;");
        setLayout(new MigLayout("insets 20, fillx", "[grow][]", "[]15[]10[]"));

        JLabel title = new JLabel("Kullanılan Parçalar ve Ücretlendirme");
        title.setIcon(new Ikon("icons/wrench.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        add(title, "span 2, wrap");

        JLabel subtitle = new JLabel("İşlemler ve Parçalar");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "font: bold");

        JButton btnAddPart = new JButton("+ Parça / İşlem Ekle");
        btnAddPart.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: #ffffff; arc: 10; font: bold");
        btnAddPart.addActionListener(e -> openItemAddModal());
        add(subtitle, "aligny center");
        add(btnAddPart, "align right, wrap");

        // --- Tablo ---

        List<ColumnDef<WorkOrderItem>> columnDefs = Arrays.asList(
                new ColumnDef<>("Tür", ItemType.class, WorkOrderItem::getItemType),
                new ColumnDef<>("İşçilik / Parça Adı", String.class, WorkOrderItem::getItemName),
                new ColumnDef<>("Seri No", String.class, WorkOrderItem::getUsedSerialNo),
                new ColumnDef<>("Fiyat", BigDecimal.class, WorkOrderItem::getTotalPrice),
                new ColumnDef<>("İşlem", WorkOrderItem.class, workOrderItem -> "Detay")
        );

        itemsTableModel = new GenericTableModel<>(columnDefs);

        JTable itemsTable = new JTable(itemsTableModel);
        WorkOrderPanelSupport.styleTable(itemsTable);
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(85);
        itemsTable.getColumnModel().getColumn(0).setMinWidth(85);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        itemsTable.getColumnModel().getColumn(4).setMaxWidth(180);
        itemsTable.getColumnModel().getColumn(4).setMinWidth(120);

        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.TRAILING);
        itemsTable.getColumnModel().getColumn(3).setCellRenderer(new CurrencyTableCellRenderer());
        itemsTable.getColumnModel().getColumn(0).setCellRenderer(new ItemTypeBadgeRenderer());

        itemsTable.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
        itemsTable.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (itemsTable.isEditing()) itemsTable.getCellEditor().cancelCellEditing();
                int modelRow = itemsTable.convertRowIndexToModel(row);
                WorkOrderItem selectedWoItem = itemsTableModel.getItemAt(modelRow);
                openItemEditModal(selectedWoItem);
            }

            @Override
            public void onDelete(int row) {
                if (itemsTable.isEditing()) itemsTable.getCellEditor().cancelCellEditing();
                int modelRow = itemsTable.convertRowIndexToModel(row);
                WorkOrderItem selectedWoItem = itemsTableModel.getItemAt(modelRow);
                confirmDeleteItem(selectedWoItem);
            }

            @Override
            public void onView(int row) {

            }
        }));

        // JScrollPane KALDILIRDI. Yerine normal JPanel kullanıyoruz.
        itemsTableContainer = new JPanel(new MigLayout("insets 0, gap 0", "[grow, fill]", "[]0[]"));
        itemsTableContainer.setOpaque(false);
        // JTable JPanel'de kullanıldığında Header'ı manuel eklememiz gerekir.
        itemsTableContainer.add(itemsTable.getTableHeader(), "wrap");
        itemsTableContainer.add(itemsTable);

        itemsEmptyLabel = WorkOrderPanelSupport.createEmptyStatePanel("Henüz işlem veya parça eklenmedi.");

        populateItemsTable();

        add(itemsEmptyLabel, "span 2, growx, wrap");
        add(itemsTableContainer, "span 2, growx, wrap");
    }

    private void populateItemsTable() {
        List<WorkOrderItem> items = workOrder.getItems();
        if (items == null || items.isEmpty()) {
            itemsEmptyLabel.setVisible(true);
            itemsTableContainer.setVisible(false);
            return;
        }
        itemsEmptyLabel.setVisible(false);
        itemsTableContainer.setVisible(true);

        itemsTableModel.setData(items);

        itemsTableContainer.revalidate();
        itemsTableContainer.repaint();
    }

    // ---- Item CRUD ----

    private void openItemAddModal() {
        WorkOrderItemAddPanel addPanel = new WorkOrderItemAddPanel(workOrder);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Ekle", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        AppModal.showModal(this, new SimpleModalBorder(addPanel, "Parça veya İşlem Ekle", null, (controller, action) -> {

            if (action == WorkOrderItemAddPanel.CREAT_ITEM) {
                WorkOrderItem newItem = addPanel.getItem();
                if (newItem == null) {
                    Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bilgiler girin.");
                    controller.consume();
                    return;
                }

                workOrderService.addItem(newItem).thenAccept(saved -> {
                    workOrder.getItems().add(saved);

                    SwingUtilities.invokeLater(() -> {
                        populateItemsTable();

                        onItemsChanged.run();
                        Toast.show(this, Toast.Type.SUCCESS, "Kalem eklendi.");
                    });
                }).exceptionally(ex -> ErrorHandler.handle(this, "Kalem eklenemedi", ex));

            }
            else if (action == WorkOrderItemAddPanel.SELECTED_ITEM) {
                WorkOrderItem newItem = addPanel.getItem();
                if (newItem == null) {
                    Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bilgiler girin.");
                    controller.consume();
                    return;
                }

                controller.consume();

                AppModal.pushModalDeferred(() -> {
                    WorkOrderItemEditPanel editPanel = new WorkOrderItemEditPanel(newItem);
                    return new SimpleModalBorder(editPanel, "Kalem Ekle", options, (controller1, action1) -> {
                        if (action1 != SimpleModalBorder.YES_OPTION) return;

                        WorkOrderItem updated = editPanel.getUpdatedItem();

                        workOrderService.addItem(updated).thenAccept(saved -> {
                            workOrder.getItems().add(saved);

                            SwingUtilities.invokeLater(() -> {
                                populateItemsTable();

                                onItemsChanged.run();
                                Toast.show(this, Toast.Type.SUCCESS, "Kalem eklendi.");
                            });
                        }).exceptionally(ex -> ErrorHandler.handle(this, "Kalem eklenemedi", ex));
                    });
                }, "itemAddModal");
            }
        }), "itemAddModal");
    }

    private void openItemEditModal(WorkOrderItem item) {
        if (item == null) return;
        WorkOrderItemEditPanel editPanel = new WorkOrderItemEditPanel(item);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        AppModal.showModal(tr.cabro.servicio.application.system.FormManager.getFrame(), new SimpleModalBorder(editPanel, "Kalemi Düzenle", options, (controller, action) -> {
            if (action != SimpleModalBorder.YES_OPTION) return;

            WorkOrderItem updated = editPanel.getUpdatedItem();
            if (updated == null) {
                Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bir isim girin.");
                controller.consume();
                return;
            }

            workOrderService.updateItem(updated).thenRun(() -> SwingUtilities.invokeLater(() -> {
                List<WorkOrderItem> currentItems = workOrder.getItems();

                int index = currentItems.indexOf(item);
                if (index != -1) {
                    currentItems.set(index, updated);

                    workOrder.setItems(currentItems);
                }

                populateItemsTable();

                onItemsChanged.run();
                Toast.show(this, Toast.Type.SUCCESS, "Kalem güncellendi.");
            })).exceptionally(ex -> ErrorHandler.handle(this, "Kalem güncellenemedi", ex));
        }), "itemEditModal");
    }

    private void confirmDeleteItem(WorkOrderItem item) {
        if (item == null) return;

        JPanel panel = new JPanel(new BorderLayout(0, 10));

        JLabel messageLabel = new JLabel("\"" + item.getItemName() + "\" kalemi silinecek. Emin misiniz?");
        JCheckBox stockCheckBox = new JCheckBox("Silinen parça stoğa eklensin mi?");

        stockCheckBox.setSelected(false);

        // Bileşenleri panele ekliyoruz
        panel.add(messageLabel, BorderLayout.CENTER);
        if (item.getSourceType() == SourceType.PRESET) {
            stockCheckBox.setSelected(true);
            panel.add(stockCheckBox, BorderLayout.SOUTH);
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Silme Onayı",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        boolean updateStock = stockCheckBox.isSelected();

        workOrderService.deleteItem(item.getId(), updateStock).thenRun(() -> SwingUtilities.invokeLater(() -> {
            workOrder.getItems().remove(item);

            populateItemsTable();
            onItemsChanged.run();

            Toast.show(this, Toast.Type.SUCCESS, "Kalem silindi.");
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kalem silinemedi", ex));
    }

    private static class ItemTypeBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof ItemType) {
                if ((ItemType) value == ItemType.LABOR) {
                    label.setText("İşçilik");
                    label.putClientProperty(FlatClientProperties.STYLE,
                            "border: 1,8,1,8,#9b59b6; foreground: #9b59b6; arc: 15; font: -1");
                } else {
                    label.setText("Parça");
                    label.putClientProperty(FlatClientProperties.STYLE,
                            "border: 1,8,1,8,#3498db; foreground: #3498db; arc: 15; font: -1");
                }
            }
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        }
    }
}
