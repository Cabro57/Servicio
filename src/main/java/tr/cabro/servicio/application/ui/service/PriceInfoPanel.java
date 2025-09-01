package tr.cabro.servicio.application.ui.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.renderer.PaymentTypeRenderer;
import tr.cabro.servicio.model.PaymentType;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceInfoPanel extends ServicePanel {

    public PriceInfoPanel(ServiceContext context) {
        super(context);
        init();
    }

    private void init() {
        initComponent();

        DefaultComboBoxModel<PaymentType> defaultComboBoxModel = new DefaultComboBoxModel<>(PaymentType.values());
        payment_type_combo.setModel(defaultComboBoxModel);
        payment_type_combo.setRenderer(new PaymentTypeRenderer());

        addListeners();
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

    private void initComponent() {
        setLayout(new MigLayout("wrap 4, insets 10", "[grow, fill][grow, fill]", ""));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Fiyat Bilgileri");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        material_cost_label = new JLabel("Malzeme Ücreti:");
        material_cost_field = new CurrencyField();
        material_cost_field.setEditable(false);

        labor_cost_label = new JLabel("İşçilik Ücreti:");
        labor_cost_field = new CurrencyField();

        total_label = new JLabel("Toplam:");
        total_field = new CurrencyField();
        total_field.setEditable(false);

        paid_label = new JLabel("Ödenen:");
        paid_field = new CurrencyField();

        remainder_label = new JLabel("Kalan:");
        remainder_field = new CurrencyField();
        remainder_field.setEditable(false);

        payment_type_label = new JLabel("Ödeme Türü:");
        payment_type_combo = new JComboBox<>();

        add(title, "span 4, align left, gapbottom 10");
        add(material_cost_label, "alignx trailing");
        add(material_cost_field, "growx");
        add(labor_cost_label, "alignx trailing");
        add(labor_cost_field, "growx, wrap");
        add(total_label, "alignx trailing");
        add(total_field, "growx");
        add(paid_label, "alignx trailing");
        add(paid_field, "growx, wrap");
        add(remainder_label, "alignx trailing");
        add(remainder_field, "growx");
        add(payment_type_label, "alignx trailing");
        add(payment_type_combo, "growx, wrap");

    }

    JLabel material_cost_label;
    JFormattedTextField material_cost_field;
    JLabel labor_cost_label;
    JFormattedTextField labor_cost_field;
    JLabel total_label;
    JFormattedTextField total_field;
    JLabel paid_label;
    JFormattedTextField paid_field;
    JLabel remainder_label;
    JFormattedTextField remainder_field;
    JLabel payment_type_label;
    JComboBox<PaymentType> payment_type_combo;
    JLabel title;
}
