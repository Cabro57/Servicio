package tr.cabro.servicio.application.panels;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.model.enums.ItemType;

import javax.swing.*;
import java.math.BigDecimal;

public class WorkOrderItemEditPanel extends JPanel {

    private final WorkOrderItem item;

    private JTextField txtName;
    private JFormattedTextField txtPurchasePrice;
    private JFormattedTextField txtSalePrice;
    private JTextField txtSerialNo;

    public WorkOrderItemEditPanel(WorkOrderItem item) {
        this.item = item;
        init();
    }

    private void init() {
        setLayout(new MigLayout("fillx, insets 20, gapy 15", "[100!][grow]", ""));

        txtName = new JTextField(item.getItemName());
        txtPurchasePrice = new CurrencyField();
        txtPurchasePrice.setValue(item.getPurchasePrice() != null ? item.getPurchasePrice() : BigDecimal.ZERO);
        txtSalePrice = new CurrencyField();
        txtSalePrice.setValue(item.getUnitPrice());
        txtSerialNo = new JTextField(item.getUsedSerialNo());

        // İşçilik ise alış fiyatı ve seri no mantıksızdır, kapatalım
        boolean isLabor = item.getItemType() == ItemType.LABOR;
        if (isLabor) {
            txtPurchasePrice.setEnabled(false);
            txtSerialNo.setEnabled(false);
        }

        add(new JLabel("Adı:")); add(txtName, "growx, wrap");
        add(new JLabel("Alış Fiyatı:")); add(txtPurchasePrice, "growx, wrap");
        add(new JLabel("Satış Fiyatı:")); add(txtSalePrice, "growx, wrap");
        add(new JLabel("Seri No:")); add(txtSerialNo, "growx, wrap");
    }

    public WorkOrderItem getUpdatedItem() {
        if (txtName.getText().trim().isEmpty()) return null;

        item.setItemName(txtName.getText().trim());
        item.setPurchasePrice(new BigDecimal(txtPurchasePrice.getValue().toString()));
        item.setUnitPrice(new BigDecimal(txtSalePrice.getValue().toString()));
        item.setUsedSerialNo(txtSerialNo.getText().trim());

        return item;
    }
}