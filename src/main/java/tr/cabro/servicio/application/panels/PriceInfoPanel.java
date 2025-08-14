package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.compenents.CurrencyField;
import tr.cabro.servicio.application.renderer.PaymentTypeRenderer;
import tr.cabro.servicio.model.PaymentType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceInfoPanel extends JPanel {
    private JPanel main_panel;

    private JLabel material_cost_label;
    private JFormattedTextField material_cost_field;
    private JLabel labor_cost_label;
    private JFormattedTextField labor_cost_field;
    private JLabel total_label;
    private JFormattedTextField total_field;
    private JLabel paid_label;
    private JFormattedTextField paid_field;
    private JLabel remainder_label;
    private JFormattedTextField remainder_field;
    private JLabel payment_type_label;
    private JComboBox<PaymentType> payment_type_combo;
    private JLabel title;

    public PriceInfoPanel() {
        init();
        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        DefaultComboBoxModel<PaymentType> defaultComboBoxModel = new DefaultComboBoxModel<>(PaymentType.values());
        payment_type_combo.setModel(defaultComboBoxModel);
        payment_type_combo.setRenderer(new PaymentTypeRenderer());

        addListeners();
    }

    private void createUIComponents() {
        material_cost_field = new CurrencyField();
        labor_cost_field = new CurrencyField();
        total_field = new CurrencyField();
        paid_field = new CurrencyField();
        remainder_field = new CurrencyField();
    }

    private void addListeners() {
        DocumentListener recalculateListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { recalculate(); }
            @Override
            public void removeUpdate(DocumentEvent e) { recalculate(); }
            @Override
            public void changedUpdate(DocumentEvent e) { recalculate(); }
        };

        material_cost_field.getDocument().addDocumentListener(recalculateListener);
        labor_cost_field.getDocument().addDocumentListener(recalculateListener);
        paid_field.getDocument().addDocumentListener(recalculateListener);
    }

    private void recalculate() {
        BigDecimal material = getFieldValue(material_cost_field);
        BigDecimal labor = getFieldValue(labor_cost_field);
        BigDecimal paid = getFieldValue(paid_field);

        BigDecimal total = material.add(labor);
        total_field.setValue(total);

        BigDecimal remainder = total.subtract(paid);
        remainder_field.setValue(remainder.max(BigDecimal.ZERO));
    }

    private BigDecimal getFieldValue(JFormattedTextField field) {
        try {
            Object value = field.getValue();
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
            } else {
                return new BigDecimal(field.getText().replace(",", "").replace("₺", "").trim()).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public void setMaterialCost(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        material_cost_field.setValue(value);
        recalculate();
    }

    public void setLaborCost(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        labor_cost_field.setValue(value);
        recalculate();
    }

    public void setPaid(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        paid_field.setValue(value);
        recalculate();
    }

    public void setPaymentType(String type) {
        if (type != null) {
            payment_type_combo.setSelectedItem(PaymentType.of(type));
        } else {
            payment_type_combo.setSelectedItem(null);
        }
    }

    public double getMaterialCost() {
        return getFieldValue(material_cost_field).doubleValue();
    }

    public double getLaborCost() {
        return getFieldValue(labor_cost_field).doubleValue();
    }

    public double getPaid() {
        return getFieldValue(paid_field).doubleValue();
    }

    public PaymentType getPaymentType() {
        PaymentType selected = (PaymentType) payment_type_combo.getSelectedItem();
        return selected != null ? selected : PaymentType.CASH;
    }

    // Diğer mevcut add/subtract metodları korunuyor
    public void addMaterialCost(double amount) {
        BigDecimal current = getFieldValue(material_cost_field);
        BigDecimal added = current.add(BigDecimal.valueOf(amount));
        material_cost_field.setValue(added);
        recalculate();
    }

    public void subtractMaterialCost(double amount) {
        BigDecimal current = getFieldValue(material_cost_field);
        BigDecimal result = current.subtract(BigDecimal.valueOf(amount)).max(BigDecimal.ZERO);
        material_cost_field.setValue(result);
        recalculate();
    }

    public void addLaborCost(double amount) {
        BigDecimal current = getFieldValue(labor_cost_field);
        BigDecimal added = current.add(BigDecimal.valueOf(amount));
        labor_cost_field.setValue(added);
        recalculate();
    }

    public void subtractLaborCost(double amount) {
        BigDecimal current = getFieldValue(labor_cost_field);
        BigDecimal result = current.subtract(BigDecimal.valueOf(amount)).max(BigDecimal.ZERO);
        labor_cost_field.setValue(result);
        recalculate();
    }
}
