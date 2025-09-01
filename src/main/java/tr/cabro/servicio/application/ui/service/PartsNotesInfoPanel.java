package tr.cabro.servicio.application.ui.service;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.editors.*;
import tr.cabro.servicio.application.events.EventCellInputChange;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.tablemodal.ServicePartTableModel;
import tr.cabro.servicio.application.ui.PartEditUI;
import tr.cabro.servicio.application.ui.PartSearchUI;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PartsNotesInfoPanel extends ServicePanel {

    private final PartService partService;

    public ServicePartTableModel tableModel;

    public PartsNotesInfoPanel(ServiceContext context) {
        super(context);
        this.partService = ServiceManager.getPartService();
        init();
    }

    private void init() {
        initComponent();

        // Buton eventleri
        part_add_button.addActionListener(e -> addPartsCmd());
        new_part_add_button.addActionListener(e -> newPartCmd());
        manual_add_button.addActionListener(e -> manualAddPart());

        tableModel.addPriceChangeListener(this::updateMaterialCost);
        parts_table.setModel(tableModel);

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
                    Service service = context.getService();
                    AddedPart addedPart = new AddedPart(part.getBarcode(), 1, part.getSale_price(), service.getId());
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

        Service service = context.getService();
        AddedPart newPart = new AddedPart("", amount, sellingPrice, service.getId());
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
        listener.onPartChange(total);
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
        List<AddedPart> addedParts = partService.getPartsByServiceId(serviceId);
        tableModel.setParts(addedParts);
        updateMaterialCost();
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 5", "[grow]", "[][grow][][pref!]"));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        JLabel title = new JLabel("Arıza ve İşlem Bilgileri");
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));

        product_search_field = new JTextField();
        product_search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Eklenen ürünlerde ara...");
        product_search_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");

        FlatSVGIcon searchIcon = new FlatSVGIcon("icon/search.svg");
        JButton searchButton = new JButton(searchIcon);
        searchButton.setMargin(new Insets(1, 1, 1, 2));
        product_search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, searchButton);

        part_add_button = new JButton("Parça Ekle");


        new_part_add_button = new JButton("Yeni Parça Ekle");

        parts_table = new JTable();
        parts_table.setRowHeight(30);

        JScrollPane table_scroll = new JScrollPane(parts_table);

        JPanel manual_add_panel = new JPanel(new MigLayout("insets 5", "[pref!][100!][pref!][100!][pref!][50!][pref!]", "[][]"));
        manual_add_panel.setBorder(BorderFactory.createTitledBorder("Manuel Parça Ekle"));

        JPanel manual_content_panel = new JPanel(new MigLayout("insets 0", "[pref!][100!][pref!][100!]", "[]"));
        series_no_field = new JTextField();
        part_name_field = new JTextField();

        purchase_price_field = new CurrencyField();
        sale_price_field = new CurrencyField();
        amount_spinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        manual_add_button = new JButton("Ekle");


        notes_field = new JTextArea(3, 20);
        notes_field.setLineWrap(true);
        notes_field.setWrapStyleWord(true);
        JScrollPane notes_scroll = new JScrollPane(notes_field);

        manual_content_panel.add(new JLabel("Seri No:"));
        manual_content_panel.add(series_no_field, "growx");
        manual_content_panel.add(new JLabel("Parça Adı:"));
        manual_content_panel.add(part_name_field, "growx");

        manual_add_panel.add(new JLabel("Alış Fiyatı:"));
        manual_add_panel.add(purchase_price_field, "growx");
        manual_add_panel.add(new JLabel("Satış Fiyatı:"));
        manual_add_panel.add(sale_price_field, "growx");
        manual_add_panel.add(new JLabel("Adet:"));
        manual_add_panel.add(amount_spinner, "growx");
        manual_add_panel.add(manual_add_button, "spany 2, wrap");

        manual_add_panel.add(manual_content_panel, "span 6, growx, wrap");

        add(title, "span 3, align left, gapbottom 10, wrap");
        add(product_search_field, "growx, split 3");
        add(part_add_button, "gapleft 5");
        add(new_part_add_button, "gapleft 5, wrap");
        add(table_scroll, "grow, wrap");
        add(manual_add_panel, "growx, wrap");
        add(new JLabel("Notlar:"), "aligny top, split 2");
        add(notes_scroll, "growx, growy, wrap");
    }

    JScrollPane table_scroll;
    JTable parts_table;
    JLabel notes_label;
    JPanel part_search_panel;
    JButton new_part_add_button;
    JTextField product_search_field;
    JTextArea notes_field;
    JButton part_add_button;
    JTextField series_no_field;
    JFormattedTextField purchase_price_field;
    JSpinner amount_spinner;
    JFormattedTextField sale_price_field;
    JPanel manual_add_panel;
    JLabel series_no_label;
    JButton manual_add_button;
    JLabel purchase_price_label;
    JLabel sale_price_label;
    JLabel amount_label;
    JLabel part_name_label;
    JTextField part_name_field;
    JPanel manual_content_panel;
}
