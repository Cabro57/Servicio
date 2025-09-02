package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.editors.*;
import tr.cabro.servicio.application.events.EventCellInputChange;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.ui.PartEditUI;
import tr.cabro.servicio.application.ui.PartSearchUI;
import tr.cabro.servicio.application.listeners.PartsChangeListener;
import tr.cabro.servicio.application.tablemodal.ServicePartTableModel;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PartsNotesInfoPanel extends JPanel {

    private JPanel main_panel;
    private JScrollPane table_scroll;
    private JTable parts_table;
    private JLabel notes_label;
    private JPanel part_search_panel;
    private JButton new_part_add_button;
    private JTextField product_search_field;
    private JTextArea notes_field;
    private JButton part_add_button;
    private JTextField series_no_field;
    private JFormattedTextField purchase_price_field;
    private JSpinner amount_spinner;
    private JFormattedTextField sale_price_field;
    private JPanel manual_add_panel;
    private JLabel series_no_label;
    private JButton manual_add_button;
    private JLabel purchase_price_label;
    private JLabel sale_price_label;
    private JLabel amount_label;
    private JLabel part_name_label;
    private JTextField part_name_field;
    private JPanel manual_content_panel;

    private final RepairService partService;
    private ServicePartTableModel tableModel;
    private final List<PartsChangeListener> partsChangeListeners = new ArrayList<>();

    private int serviceId = 0;

    public PartsNotesInfoPanel() {
        this.partService = ServiceManager.getRepairService();
        initUI();
        add(main_panel);
    }

    private void initUI() {
        // Temel stil ayarları
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        // Arama paneli ayarları
        part_search_panel.setBackground(null);
        product_search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Eklenen ürünlerde ara...");
        product_search_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");

        // Arama butonu ekle
        FlatSVGIcon searchIcon = new FlatSVGIcon("icon/search.svg");
        JButton searchButton = new JButton(searchIcon);
        searchButton.setMargin(new Insets(1, 1, 1, 2));
        product_search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, searchButton);

        // Buton eventleri
        part_add_button.addActionListener(e -> addPartsCmd());
        new_part_add_button.addActionListener(e -> newPartCmd());
        manual_add_button.addActionListener(e -> manualAddPart());

        // Tablo modeli ve görünümü
        tableModel = new ServicePartTableModel(partService.getParts(serviceId));
        tableModel.addPriceChangeListener(this::updateMaterialCost);
        parts_table.setModel(tableModel);
        parts_table.setRowHeight(30);

        // Kolon düzenlemeleri - kolon indekslerini değişken olarak tutmak iyi olur
        final int COL_SERIAL = 0;
        final int COL_NAME = 1;
        final int COL_AMOUNT = 2;
        final int COL_PURCHASE_PRICE = 3;
        final int COL_SALE_PRICE = 4;

        EventCellInputChange eventCellInputChange = this::updateMaterialCost;

        parts_table.getColumnModel().getColumn(COL_SERIAL).setCellEditor(new SerialCellEditor());

        parts_table.getColumnModel().getColumn(COL_NAME).setCellEditor(new NameCellEditor());

        parts_table.getColumnModel().getColumn(COL_AMOUNT).setCellEditor(new AmountCellEditor(eventCellInputChange));
        parts_table.getColumnModel().getColumn(COL_AMOUNT).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                return this;
            }
        });

        parts_table.getColumnModel().getColumn(COL_PURCHASE_PRICE).setCellEditor(new PriceCellEditor(eventCellInputChange, true));
        parts_table.getColumnModel().getColumn(COL_PURCHASE_PRICE).setCellRenderer(new CurrencyTableCellRenderer());

        parts_table.getColumnModel().getColumn(COL_SALE_PRICE).setCellEditor(new PriceCellEditor(eventCellInputChange, false));
        parts_table.getColumnModel().getColumn(COL_SALE_PRICE).setCellRenderer(new CurrencyTableCellRenderer());


        parts_table.getColumnModel().getColumn(5).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onAction(int row) {
                if (row != -1) {
                    tableModel.getAddedParts().remove(row);
                    tableModel.fireTableRowsDeleted(row, row);
                    updateMaterialCost();
                }
            }
        }));
        parts_table.getColumnModel().getColumn(5).setCellRenderer(new ActionButtonRenderer());

        parts_table.getColumnModel().getColumn(0).setMinWidth(100);
        parts_table.getColumnModel().getColumn(1).setPreferredWidth(60);
        parts_table.getColumnModel().getColumn(2).setMinWidth(80);
        parts_table.getColumnModel().getColumn(2).setMaxWidth(80);
        parts_table.getColumnModel().getColumn(3).setMinWidth(100);
        parts_table.getColumnModel().getColumn(3).setMaxWidth(100);
        parts_table.getColumnModel().getColumn(4).setMinWidth(100);
        parts_table.getColumnModel().getColumn(4).setMaxWidth(100);
        parts_table.getColumnModel().getColumn(5).setMaxWidth(50);

        addTableDoubleClickListener();
        amount_spinner.setModel(new SpinnerNumberModel(1, 1, 999, 1));

        // Arka planlar
        manual_add_panel.setBackground(null);
        manual_content_panel.setBackground(null);
    }

    private void newPartCmd() {
        PartEditUI dialog = new PartEditUI();
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    private void addPartsCmd() {
        PartSearchUI productSearchUI = new PartSearchUI();
        productSearchUI.setModal(true);
        productSearchUI.setVisible(true);

        List<Part> selectedParts = productSearchUI.getSelectedParts();
        if (selectedParts != null && !selectedParts.isEmpty()) {
            for (Part part : selectedParts) {
                boolean exists = false;

                for (AddedPart existing : tableModel.getAddedParts()) {
                    if (existing.getBarcode().equals(part.getBarcode())) {
                        existing.setAmount(existing.getAmount() + 1);
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    AddedPart addedPart = new AddedPart(part.getBarcode(), 1, part.getSale_price());
                    addedPart.setName(part.getName());
                    addedPart.setPurchasePrice(part.getPurchase_price());
                    addedPart.setAddedDate(LocalDateTime.now());
                    tableModel.addAddedPart(addedPart);
                }
            }

            tableModel.fireTableDataChanged();
            updateMaterialCost();
        }
    }

    private void manualAddPart() {
        String serialNo = series_no_field.getText().trim();
        String partName = part_name_field.getText().trim();

        if (partName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Parça ismi boş olamaz.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double sellingPrice;
        try {
            Object val = sale_price_field.getValue();
            if (val instanceof Number) {
                sellingPrice = ((Number) val).doubleValue();
            } else {
                sellingPrice = Double.parseDouble(sale_price_field.getText().trim());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Geçerli bir satış fiyatı giriniz.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double purchasePrice = 0.0;
        Object purchaseVal = purchase_price_field.getValue();
        if (purchaseVal instanceof Number) {
            purchasePrice = ((Number) purchaseVal).doubleValue();
        }

        int amount = (Integer) amount_spinner.getValue();
        if (amount <= 0) {
            JOptionPane.showMessageDialog(this, "Adet 0 veya negatif olamaz.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        AddedPart newPart = new AddedPart("", amount, sellingPrice);
        newPart.setName(partName);
        newPart.setSerial_no(serialNo);
        newPart.setPurchasePrice(purchasePrice);

        tableModel.addAddedPart(newPart);
        tableModel.fireTableDataChanged();
        updateMaterialCost();

        // Formu temizle
        series_no_field.setText("");
        purchase_price_field.setValue(0.0);
        sale_price_field.setValue(0.0);
        amount_spinner.setValue(1);
        part_name_field.setText("");
    }

    private void updateMaterialCost() {
        double total = tableModel.getTotalPrice();
        partsChangeListeners.forEach(listener -> listener.onPartsChanged(total));
    }

    private void addTableDoubleClickListener() {
        parts_table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2 && !parts_table.getSelectionModel().isSelectionEmpty()) {
                    int viewRow = parts_table.getSelectedRow();
                    int modelRow = parts_table.convertRowIndexToModel(viewRow);
                    AddedPart addedPart = tableModel.getAddedParts().get(modelRow);

                    int result = JOptionPane.showConfirmDialog(
                            PartsNotesInfoPanel.this,
                            "Seçili parçayı silmek istediğinize emin misiniz?",
                            "Parça Sil",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

                    if (result == JOptionPane.YES_OPTION) {
                        tableModel.removeAddedPart(addedPart);
                        tableModel.fireTableDataChanged();
                        updateMaterialCost();
                    }
                }
            }
        });
    }

    public void addPartsChangeListener(PartsChangeListener listener) {
        partsChangeListeners.add(listener);
    }

    public List<AddedPart> getAddedParts() {
        return new ArrayList<>(tableModel.getAddedParts());
    }

    public void setAddedParts(List<AddedPart> parts) {
        for (AddedPart part : parts) {
            tableModel.addAddedPart(part);
        }
        tableModel.fireTableDataChanged();
        updateMaterialCost();
    }

    public String getNotes() {
        return notes_field.getText().trim();
    }

    public void setNotes(String notes) {
        notes_field.setText(notes);
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
        List<AddedPart> addedParts = partService.getParts(serviceId);
        tableModel.setParts(addedParts);
        updateMaterialCost();
    }

    private void createUIComponents() {
        purchase_price_field = new CurrencyField();
        sale_price_field = new CurrencyField();
    }
}
