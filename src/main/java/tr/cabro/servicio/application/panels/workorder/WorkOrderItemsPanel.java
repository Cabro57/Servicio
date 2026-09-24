package tr.cabro.servicio.application.panels.workorder;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.simple.SimpleMessageModal;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.component.table.TableActionEvent;
import tr.cabro.servicio.application.panels.WorkOrderItemEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.i18n.Messages;
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
    private final WorkOrderPanelSupport.StepMarker stepMarker =
            new WorkOrderPanelSupport.StepMarker(2, "Kalemler girildi", "Henüz parça veya işçilik yok");

    public WorkOrderItemsPanel(WorkOrder workOrder, Runnable onItemsChanged) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.onItemsChanged = onItemsChanged;
        build();
    }

    private void build() {
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        setLayout(new MigLayout("insets 14 16 12 16, fillx, hidemode 3", "[grow][]", "[]10[]"));
        // Birincil "Parça / İşçilik Ekle" kimlik şeridinde; burada aynı işin ikincil kısayolu.
        JButton btnAddPart = new JButton("Ekle", new Ikon("icons/plus.svg", 14, "Label.foreground"));
        btnAddPart.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10; iconTextGap: 4");
        btnAddPart.setToolTipText("Parça veya işçilik ekle");
        btnAddPart.addActionListener(e -> openItemAddModal());
        add(WorkOrderPanelSupport.createStepHeader(stepMarker, "Parça ve işçilik", btnAddPart), "span 2, growx, wrap");

        // --- Tablo ---

        // Seri no ayrı (çoğunlukla boş) bir kolon değil, kalem adının altında soluk satır.
        List<ColumnDef<WorkOrderItem>> columnDefs = Arrays.asList(
                new ColumnDef<WorkOrderItem>("Tür", ItemType.class, WorkOrderItem::getItemType).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrderItem>("Kalem", WorkOrderItem.class, item -> item).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrderItem>("Adet", Integer.class, WorkOrderItem::getQuantity).alignment(SwingConstants.CENTER),
                new ColumnDef<WorkOrderItem>("Tutar", BigDecimal.class, WorkOrderItem::getTotalPrice).alignment(SwingConstants.TRAILING),
                ColumnDef.<WorkOrderItem>actionColumn("")
        );

        itemsTableModel = new GenericTableModel<>(columnDefs);

        // Düz JTable: düzenle/sil düğmeleri ListTable gibi yalnızca hover satırında değil, her satırda görünür.
        JTable itemsTable = new JTable();
        itemsTable.setModel(itemsTableModel);
        WorkOrderPanelSupport.styleTable(itemsTable);
        tr.cabro.servicio.application.component.table.TableColumnConfigurator.applyColumnRenderers(itemsTable, columnDefs);
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(95);
        itemsTable.getColumnModel().getColumn(0).setMinWidth(85);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        itemsTable.getColumnModel().getColumn(2).setMaxWidth(70);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        itemsTable.getColumnModel().getColumn(3).setMinWidth(100);

        itemsTable.getColumnModel().getColumn(0).setCellRenderer(new ItemTypeBadgeRenderer());
        itemsTable.getColumnModel().getColumn(1).setCellRenderer(new tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer<WorkOrderItem>(
                WorkOrderItem::getItemName,
                item -> item.getUsedSerialNo() != null && !item.getUsedSerialNo().isBlank() ? "SN " + item.getUsedSerialNo() : ""));
        itemsTable.getColumnModel().getColumn(3).setCellRenderer(
                new tr.cabro.servicio.application.renderer.MoneyCellRenderer(tr.cabro.servicio.application.renderer.MoneyCellRenderer.Mode.NEUTRAL));

        // Düzenle/sil düğmeleri her satırda görünür (kalem işlemi sık ve hedefli).
        itemsTable.getColumnModel().getColumn(4).setMaxWidth(96);
        itemsTable.getColumnModel().getColumn(4).setMinWidth(96);
        DynamicActionColumnSupport.install(itemsTable, 4, itemsTableModel, List.of(
                DynamicActionColumnSupport.button("icons/pencil.svg", SemanticColor.info(), "Kalemi düzenle",
                        this::openItemEditModal),
                DynamicActionColumnSupport.button("icons/trash-2.svg", SemanticColor.danger(), "Kalemi sil",
                        this::confirmDeleteItem)
        ));

        // JScrollPane KALDILIRDI. Yerine normal JPanel kullanıyoruz.
        itemsTableContainer = new JPanel(new MigLayout("insets 0, gap 0", "[grow, fill]", "[]0[]"));
        itemsTableContainer.setOpaque(false);
        // JTable JPanel'de kullanıldığında Header'ı manuel eklememiz gerekir.
        itemsTableContainer.add(itemsTable.getTableHeader(), "wrap");
        itemsTableContainer.add(itemsTable);

        itemsEmptyLabel = WorkOrderPanelSupport.createEmptyStatePanel("Henüz parça veya işçilik eklenmedi.");

        populateItemsTable();

        add(itemsEmptyLabel, "span 2, growx, wrap");
        add(itemsTableContainer, "span 2, growx, wrap");
    }

    public WorkOrderPanelSupport.StepMarker getStepMarker() {
        return stepMarker;
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

    /** Kalem ekleme penceresi; kimlik şeridindeki birincil düğme de bunu açar. */
    public void openItemAddModal() {
        WorkOrderItemModal.open(this, workOrder, saved -> {
            workOrder.getItems().addAll(saved);
            populateItemsTable();
            onItemsChanged.run();
            tr.cabro.servicio.util.SoundPlayer.added();
            Toasts.show(this, Toast.Type.SUCCESS, saved.size() == 1
                    ? Messages.get("toast.item.added") : Messages.get("toast.items.added", String.valueOf(saved.size())));
        });
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
                Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.item.nameInvalid"));
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
                Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.item.updated"));
            })).exceptionally(ex -> ErrorHandler.handle(this, "Kalem güncellenemedi", ex));
        }), "itemEditModal");
    }

    private void confirmDeleteItem(WorkOrderItem item) {
        if (item == null) return;

        JPanel panel = new JPanel(new BorderLayout(0, 10));

        JLabel messageLabel = new JLabel(Messages.get("confirm.delete.item.named", item.getItemName()));
        JCheckBox stockCheckBox = new JCheckBox("Silinen parça stoğa eklensin mi?");

        stockCheckBox.setSelected(false);

        // Bileşenleri panele ekliyoruz
        panel.add(messageLabel, BorderLayout.CENTER);
        if (item.getSourceType() == SourceType.PRESET) {
            stockCheckBox.setSelected(true);
            panel.add(stockCheckBox, BorderLayout.SOUTH);
        }

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(Messages.get("dialog.button.yes"), SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option(Messages.get("dialog.button.no"), SimpleModalBorder.NO_OPTION)
        };

        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.WARNING,
                panel, Messages.get("confirm.delete.title"), options, (controller, action) -> {
            if (action != SimpleModalBorder.YES_OPTION) return;

            boolean updateStock = stockCheckBox.isSelected();

            workOrderService.deleteItem(item.getId(), updateStock).thenRun(() -> SwingUtilities.invokeLater(() -> {
                workOrder.getItems().remove(item);

                populateItemsTable();
                onItemsChanged.run();

                tr.cabro.servicio.util.SoundPlayer.removed();
                Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.item.deleted"));
            })).exceptionally(ex -> ErrorHandler.handle(this, "Kalem silinemedi", ex));
        }));
    }

    private static class ItemTypeBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof ItemType) {
                if ((ItemType) value == ItemType.LABOR) {
                    label.setText("İşçilik");
                    // Kalem türü rozetleri de tema token'ından; sabit hex açık temada soluk kalıyordu.
                    String laborHex = BadgePalette.foregroundHex(BadgeColor.PURPLE);
                    label.putClientProperty(FlatClientProperties.STYLE,
                            "border: 1,8,1,8," + laborHex + "; foreground: " + laborHex + "; arc: 15; font: -1");
                } else {
                    label.setText("Parça");
                    String partHex = BadgePalette.foregroundHex(BadgeColor.BLUE);
                    label.putClientProperty(FlatClientProperties.STYLE,
                            "border: 1,8,1,8," + partHex + "; foreground: " + partHex + "; arc: 15; font: -1");
                }
            }
            label.setHorizontalAlignment(SwingConstants.LEADING);
            return label;
        }
    }
}
