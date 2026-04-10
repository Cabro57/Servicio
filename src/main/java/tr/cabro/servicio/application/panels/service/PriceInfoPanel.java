package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.renderer.PaymentTypeRenderer;
import tr.cabro.servicio.model.enums.PaymentType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceInfoPanel extends ServicePanel {

    // Veri yüklenirken gereksiz onDataChanged sinyallerini engellemek için bayrak
    private boolean isInitializing = false;

    public PriceInfoPanel() {
        init();
    }

    private void init() {
        initComponent();

        DefaultComboBoxModel<PaymentType> defaultComboBoxModel = new DefaultComboBoxModel<>(PaymentType.values());
        payment_type_combo.setModel(defaultComboBoxModel);
        payment_type_combo.setRenderer(new PaymentTypeRenderer());

        addListeners();
    }

    @Override
    protected void onServiceSet() {
        if (service == null) return;

        // Form doldurulurken tetiklenen Listener'ları susturmak için bayrağı açıyoruz
        isInitializing = true;
        try {
            // Service modeli içindeki verileri arayüze basıyoruz
            setMaterialCost(service.getTotalPartsCost());
            setLaborCost(service.getLaborCost());
            setPaid(service.getPaid());

            if (service.getPaymentType() != null) {
                payment_type_combo.setSelectedItem(service.getPaymentType());
            } else {
                payment_type_combo.setSelectedItem(PaymentType.CASH);
            }

            // Veriler dolduktan sonra matematiksel hesabı bir kez çalıştır
            recalculate();
        } finally {
            // Bayrağı kapat. Bundan sonraki her klavye hareketi "değişiklik" sayılacak.
            isInitializing = false;
        }
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

        // Ödeme türü değiştiğinde de ana formu uyar
        payment_type_combo.addActionListener(e -> notifyDataChanged());
    }

    private void recalculate() {
        BigDecimal material = getFieldValue(material_cost_field);
        BigDecimal labor = getFieldValue(labor_cost_field);
        BigDecimal paid = getFieldValue(paid_field);

        // Toplam = Malzeme + İşçilik
        BigDecimal total = material.add(labor);
        total_field.setValue(total);

        // Kalan = Toplam - Ödenen (Eksiye düşemez)
        BigDecimal remainder = total.subtract(paid);
        remainder_field.setValue(remainder.max(BigDecimal.ZERO));

        // Hesaplama değiştiğine göre ana formu (Güncelle butonunu) uyar
        notifyDataChanged();
    }

    private void notifyDataChanged() {
        // Eğer form program tarafından doldurulmuyorsa ve listener bağlıysa tetikle
        if (!isInitializing && getListener() != null) {
            getListener().onDataChanged();
        }
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

    // Ana formun (FormService) parçalar güncellendiğinde buraya fiyat basması için kullanılır
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

    // Formu toplarken (collectForm) kullanılacak Getter'lar
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

    // --- İşlemler Eklendiğinde Otomatik Artırma/Azaltma Metotları ---
    public void addLaborCost(double amount) {
        BigDecimal current = getFieldValue(labor_cost_field);
        BigDecimal added = current.add(BigDecimal.valueOf(amount));
        labor_cost_field.setValue(added);
        // recalculate() DocumentListener sayesinde otomatik tetiklenecektir.
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap 4, insets 10", "[grow, fill][grow, fill]", ""));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Fiyat Bilgileri");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        material_cost_label = new JLabel("Malzeme Ücreti:");
        material_cost_field = new CurrencyField();
        material_cost_field.setEditable(false); // Sadece Parça tablosu değiştirebilir

        labor_cost_label = new JLabel("İşçilik Ücreti:");
        labor_cost_field = new CurrencyField();

        total_label = new JLabel("Toplam:");
        total_field = new CurrencyField();
        total_field.setEditable(false); // Otomatik Hesaplanır

        paid_label = new JLabel("Ödenen:");
        paid_field = new CurrencyField();

        remainder_label = new JLabel("Kalan:");
        remainder_field = new CurrencyField();
        remainder_field.setEditable(false); // Otomatik Hesaplanır

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
}