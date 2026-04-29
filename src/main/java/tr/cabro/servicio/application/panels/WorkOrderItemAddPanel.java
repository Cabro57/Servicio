package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.ModalBorderAction;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.editors.AddButtonEditor;
import tr.cabro.servicio.application.renderer.AddButtonRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.SourceType;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class WorkOrderItemAddPanel extends JPanel {

    public static final int SELECTED_ITEM = 100;

    private final WorkOrder workOrder;

    private GenericTableModel<Part> partTableModel;
    private GenericTableModel<Labor> laborTableModel;

    @Getter
    private WorkOrderItem item;

    public WorkOrderItemAddPanel(WorkOrder workOrder) {
        this.workOrder = workOrder;
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 0", "[grow]", "[grow]"));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty(FlatClientProperties.STYLE, "tabType: card; tabInsets: 5,20,5,20; focusColor: null;");

        tabbedPane.addTab("Stoktakiler", new Ikon("icons/box.svg", 0.8f), createStockPanel());
        tabbedPane.addTab("Hazır Paketler", new Ikon("icons/sparkles.svg", 0.8f), createLaborPanel());
        tabbedPane.addTab("Manuel Ekle", new Ikon("icons/pencil.svg", 0.8f), createManualPanel());

        add(tabbedPane, "grow");
        loadData();
    }

    private void loadData() {
        ServiceManager.getPartService().getAll().thenAccept(parts -> {
            SwingUtilities.invokeLater(() -> partTableModel.setData(parts));
        });

        ServiceManager.getLaborService().getAll().thenAccept(labors -> {
            SwingUtilities.invokeLater(() -> laborTableModel.setData(labors));
        });
    }

    // --- 1. STOKTAKİ PARÇALAR PANELİ ---
    private JPanel createStockPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 15", "[grow]", "[][grow]"));

        JTextField txtSearch = new JTextField();
        txtSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Parça adı veya barkod ara...");
        txtSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 0.8f));
        panel.add(txtSearch, "growx, wrap");

        List<ColumnDef<Part>> cols = Arrays.asList(
                new ColumnDef<>("Parça Bilgisi", Part.class, p -> p),
                new ColumnDef<>("Fiyat", String.class, p -> Format.formatPrice(p.getSalePrice())),
                new ColumnDef<>("İşlem", String.class, p -> "Ekle")
        );
        partTableModel = new GenericTableModel<>(cols);
        JTable table = new JTable(partTableModel);
        table.setRowHeight(50);

        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Part>(
                Part::getName,
                p -> p.getBarcode() + " • Stok: " + p.getStockQuantity()
        ));

        DefaultTableCellRenderer priceRenderer = new DefaultTableCellRenderer();
        priceRenderer.setHorizontalAlignment(SwingConstants.TRAILING);
        priceRenderer.setFont(priceRenderer.getFont().deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(1).setCellRenderer(priceRenderer);

        table.getColumnModel().getColumn(2).setCellRenderer(new AddButtonRenderer());
        table.getColumnModel().getColumn(2).setCellEditor(new AddButtonEditor(row -> {
            if (table.isEditing()) table.getCellEditor().cancelCellEditing();
            int modelRow = table.convertRowIndexToModel(row);
            Part selectedPart = partTableModel.getItemAt(modelRow);

            if (selectedPart.getStockQuantity() <= 0) {
                Toast.show(WorkOrderItemAddPanel.this, Toast.Type.WARNING, "Bu ürünün stoğu kalmamıştır!");
                return;
            }

            String serialNo = JOptionPane.showInputDialog(WorkOrderItemAddPanel.this,
                    "Kullanılan parçanın Seri Numarasını girin (Opsiyonel):", "Seri No", JOptionPane.QUESTION_MESSAGE);

            item = new WorkOrderItem();
            item.setServiceId(workOrder.getId());
            item.setItemType(ItemType.PART);
            item.setSourceType(SourceType.PRESET);
            item.setPartId(selectedPart.getId());
            item.setItemName(selectedPart.getName());
            item.setPurchasePrice(selectedPart.getPurchasePrice());
            item.setUnitPrice(selectedPart.getSalePrice());
            item.setUsedSerialNo(serialNo != null && !serialNo.trim().isEmpty() ? serialNo.trim() : null);

            ModalBorderAction borderAction = ModalBorderAction.getModalBorderAction(this);
            if (borderAction != null) {
                borderAction.doAction(SELECTED_ITEM);
            }

        }));

        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(2).setMaxWidth(80);

        JScrollPane scroll = new JScrollPane(table);
        panel.add(scroll, "grow");
        return panel;
    }

    // --- 2. HAZIR İŞÇİLİK (LABOR) PANELİ ---
    private JPanel createLaborPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 15", "[grow]", "[][grow]"));

        JTextField txtSearch = new JTextField();
        txtSearch.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İşlem veya paket ara...");
        txtSearch.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 0.8f));
        panel.add(txtSearch, "growx, wrap");

        List<ColumnDef<Labor>> cols = Arrays.asList(
                new ColumnDef<>("İşlem Bilgisi", Labor.class, l -> l),
                new ColumnDef<>("Fiyat", String.class, l -> Format.formatPrice(l.getDefaultPrice())),
                new ColumnDef<>("Ekle", String.class, l -> "Ekle")
        );
        laborTableModel = new GenericTableModel<>(cols);
        JTable table = new JTable(laborTableModel);
        table.setRowHeight(50);

        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Labor>(
                Labor::getName, Labor::getCategory
        ));

        DefaultTableCellRenderer priceRenderer = new DefaultTableCellRenderer();
        priceRenderer.setHorizontalAlignment(SwingConstants.TRAILING);
        priceRenderer.setFont(priceRenderer.getFont().deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(1).setCellRenderer(priceRenderer);

        // Özel Tekil Buton Entegrasyonu
        table.getColumnModel().getColumn(2).setCellRenderer(new AddButtonRenderer());
        table.getColumnModel().getColumn(2).setCellEditor(new AddButtonEditor(row -> {
            Labor labor = laborTableModel.getItemAt(table.convertRowIndexToModel(row));
            WorkOrderItem item = new WorkOrderItem();
            item.setServiceId(workOrder.getId());
            item.setItemType(ItemType.LABOR);
            item.setSourceType(SourceType.PRESET);
            item.setLaborId(labor.getId());
            item.setItemName(labor.getName());
            item.setPurchasePrice(BigDecimal.ZERO); // İşçiliğin alışı yoktur
            item.setUnitPrice(labor.getDefaultPrice());

            ServiceManager.getWorkOrderService().addItem(item).thenAccept(i -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(WorkOrderItemAddPanel.this, Toast.Type.SUCCESS, "İşçilik başarıyla eklendi.");
                });
            });
        }));

        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(2).setMaxWidth(80); // Butonun sığması için biraz genişletildi

        panel.add(new JScrollPane(table), "grow");
        return panel;
    }

    // --- 3. MANUEL EKLEME PANELİ ---
    private JPanel createManualPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 20, gapy 15", "[100!][grow]", "[]"));

        JComboBox<String> cmbType = new JComboBox<>(new String[]{"Parça", "İşçilik"});
        JTextField txtName = new JTextField();
        JFormattedTextField txtPurchasePrice = new CurrencyField();
        JFormattedTextField txtSalePrice = new CurrencyField();
        JTextField txtSerialNo = new JTextField();

        txtSerialNo.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Opsiyonel seri numarası...");

        cmbType.addActionListener(e -> {
            boolean isPart = cmbType.getSelectedIndex() == 0;
            txtPurchasePrice.setEnabled(isPart);
            txtSerialNo.setEnabled(isPart);
            if(!isPart) {
                txtPurchasePrice.setValue(BigDecimal.ZERO);
                txtSerialNo.setText("");
            }
        });

        panel.add(new JLabel("Tür:")); panel.add(cmbType, "w 150!, wrap");
        panel.add(new JLabel("Adı:")); panel.add(txtName, "growx, wrap");
        panel.add(new JLabel("Alış Fiyatı:")); panel.add(txtPurchasePrice, "growx, wrap");
        panel.add(new JLabel("Satış Fiyatı:")); panel.add(txtSalePrice, "growx, wrap");
        panel.add(new JLabel("Seri No:")); panel.add(txtSerialNo, "growx, wrap");

        JButton btnSave = new JButton("Kaydet ve Ekle");
        btnSave.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #fff; arc: 10; font: bold");
        btnSave.addActionListener(e -> {
            if (txtName.getText().trim().isEmpty()) {
                Toast.show(this, Toast.Type.WARNING, "Lütfen bir ad girin.");
                return;
            }

            WorkOrderItem item = new WorkOrderItem();
            item.setServiceId(workOrder.getId());
            item.setItemType(cmbType.getSelectedIndex() == 0 ? ItemType.PART : ItemType.LABOR);
            item.setSourceType(SourceType.MANUAL);
            item.setItemName(txtName.getText().trim());
            item.setPurchasePrice(new BigDecimal(txtPurchasePrice.getValue().toString()));
            item.setUnitPrice(new BigDecimal(txtSalePrice.getValue().toString()));
            item.setUsedSerialNo(txtSerialNo.getText().trim());

            ServiceManager.getWorkOrderService().addItem(item).thenAccept(i -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, "Manuel kalem başarıyla eklendi.");
                    txtName.setText("");
                    txtSerialNo.setText("");
                    txtPurchasePrice.setValue(BigDecimal.ZERO);
                    txtSalePrice.setValue(BigDecimal.ZERO);
                });
            });
        });

        panel.add(btnSave, "span 2, align right, gaptop 15");
        return panel;
    }
}