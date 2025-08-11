package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Setter;
import tr.cabro.servicio.application.compenents.CurrencyField;
import tr.cabro.servicio.application.editors.DoubleEditor;
import tr.cabro.servicio.application.editors.SpinnerEditor;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.ui.PartEditUI;
import tr.cabro.servicio.application.ui.PartSearchUI;
import tr.cabro.servicio.application.listeners.PartsChangeListener;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.application.tablemodal.ServicePartTableModel;

import javax.swing.*;
import java.awt.*;
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
    private JTextField notes_field;
    private JButton part_add_button;
    private JTextField series_no_field;
    private JFormattedTextField sale_price_field;
    private JSpinner amount_spinner;
    private JFormattedTextField purchase_price_field;
    private JPanel manual_add_panel;
    private JLabel series_no_label;
    private JButton manual_add_button;
    private JLabel sale_price_label;
    private JLabel pruchase_price_label;
    private JLabel amount_label;
    private JLabel part_name_label;
    private JTextField part_name_field;
    private JPanel manual_content_panel;

    private final PartService partService;

    private ServicePartTableModel tableModel;

    private final List<PartsChangeListener> partsChangeListeners = new ArrayList<>();

    @Setter
    private int serviceId = 0;

    public PartsNotesInfoPanel() {
        this.partService = ServiceManager.getPartService();
        init();

        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        part_search_panel.setBackground(null);
        product_search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Eklenen ürünlerde ara...");
        product_search_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");

        // Search Button Setup
        FlatSVGIcon search_icon = new FlatSVGIcon("icon/search.svg");
        JButton search_button = new JButton(search_icon);
        search_button.setMargin(new Insets(1,1,1,2));
        product_search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, search_button);

        part_add_button.addActionListener(e -> add_parts_cmd());

        new_part_add_button.addActionListener(e -> new_part_cmd());

        tableModel = new ServicePartTableModel(partService.getPartsByServiceId(serviceId));
        tableModel.addPriceChangeListener(this::updateMaterialCost);

        parts_table.setModel(tableModel);
        parts_table.setRowHeight(30);
        parts_table.getColumnModel().getColumn(2).setCellEditor(new SpinnerEditor(0, Integer.MAX_VALUE, 1));
        parts_table.getColumnModel().getColumn(3).setCellRenderer(new CurrencyTableCellRenderer());
        parts_table.getColumnModel().getColumn(3).setCellEditor(new DoubleEditor());
        applyPartsTableDoubleClickListener();

        manual_add_panel.setBackground(null);
        manual_content_panel.setBackground(null);
        manual_add_button.addActionListener(e -> manualAddPart());
    }

    private void new_part_cmd() {
        PartEditUI dialog = new PartEditUI();
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    private void add_parts_cmd() {
        PartSearchUI productSearchUI = new PartSearchUI();
        productSearchUI.setModal(true);
        productSearchUI.setVisible(true);

        List<Part> parts = productSearchUI.getSelectedParts();
        if (parts != null && !parts.isEmpty()) {
            for (Part part : parts) {
                boolean alreadyExists = false;

                for (AddedPart existing : tableModel.getAddedParts()) {
                    if (existing.getBarcode().equals(part.getBarcode())) {
                        existing.setAmount(existing.getAmount() + 1);
                        alreadyExists = true;
                        break;
                    }
                }

                if (!alreadyExists) {
                    AddedPart addedPart = new AddedPart(part.getBarcode(), 1, part.getSale_price(), serviceId);
                    tableModel.addAddedPart(addedPart);
                }
            }

            updateMaterialCost();
        }
    }

    private void manualAddPart() {
        String serialNo = series_no_field.getText().trim();

        double sellingPrice;
        try {
            // Eğer sale_price_field JFormattedTextField ise:
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

        int amount = (Integer) amount_spinner.getValue();

        if (amount <= 0) {
            JOptionPane.showMessageDialog(this, "Adet 0 veya negatif olamaz.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        AddedPart newPart = new AddedPart("", amount, sellingPrice, serviceId); // barcode boş
        newPart.setSerial_no(serialNo);

        tableModel.addAddedPart(newPart);
        updateMaterialCost();

        // İstersen form alanlarını temizleyebilirsin:
        series_no_field.setText("");
        sale_price_field.setValue(0.0);
        purchase_price_field.setValue(0.0);
        amount_spinner.setValue(1);
    }


    private void updateMaterialCost() {
        double total = tableModel.getTotalPrice();

        for (PartsChangeListener listener : partsChangeListeners) {
            listener.onPartsChanged(total);
        }
    }


    private void applyPartsTableDoubleClickListener() {
        parts_table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && parts_table.getSelectedRow() != -1) {
                    int viewRow = parts_table.getSelectedRow();

                    int result = JOptionPane.showConfirmDialog(
                            PartsNotesInfoPanel.this,
                            "Seçili parçayı silmek istediğinize emin misiniz?",
                            "Parça Sil",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

                    if (result == JOptionPane.YES_OPTION) {
                        int modelRow = parts_table.convertRowIndexToModel(viewRow);
                        AddedPart addedPart = tableModel.getAddedParts().get(modelRow);
                        tableModel.removeAddedPart(addedPart);
                        updateMaterialCost(); // Malzeme maliyetini güncelle
                    }
                }
            }
        });
    }

    public void addPartsChangeListener(PartsChangeListener listener) {
        partsChangeListeners.add(listener);
    }

    // Mevcut parça listesini döner (örneğin Service nesnesine kaydetmek için)
    public List<AddedPart> getAddedParts() {
        return tableModel.getAddedParts();
    }

    // Parça listesiyle tabloyu doldurur (örneğin Service'den yüklerken)
    public void setAddedParts(List<AddedPart> parts) {
        for (AddedPart part : parts) {
            tableModel.addAddedPart(part);
        }
        updateMaterialCost();
    }

    // Not alanı getter/setter
    public String getNotes() {
        return notes_field.getText().trim();
    }

    public void setNotes(String notes) {
        notes_field.setText(notes);
    }


    private void createUIComponents() {
        // TODO: place custom component creation code here
        sale_price_field = new CurrencyField();
        purchase_price_field = new CurrencyField();
    }
}
