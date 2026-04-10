package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.panels.ServicePanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class FaultProcessInfoPanel extends ServicePanel {

    // Veri yüklenirken sahte tetiklemeleri engellemek için
    private boolean isInitializing = false;

    public FaultProcessInfoPanel() {
        init();
    }

    private void init() {
        initComponent();
        addListeners();
    }

    @Override
    protected void onServiceSet() {
        if (service == null) return;

        isInitializing = true;
        try {
            reported_fault_field.setText(service.getReportedFault() != null ? service.getReportedFault() : "");
            detected_fault_field.setText(service.getDetectedFault() != null ? service.getDetectedFault() : "");
            action_taken_field.setText(service.getActionTaken() != null ? service.getActionTaken() : "");
        } finally {
            isInitializing = false;
        }
    }

    private void addListeners() {
        // Metin kutularında klavye ile yapılan her değişikliği dinle
        DocumentListener documentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { notifyDataChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { notifyDataChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { notifyDataChanged(); }
        };

        reported_fault_field.getDocument().addDocumentListener(documentListener);
        detected_fault_field.getDocument().addDocumentListener(documentListener);
        action_taken_field.getDocument().addDocumentListener(documentListener);
    }

    private void notifyDataChanged() {
        if (!isInitializing && getListener() != null) {
            getListener().onDataChanged(); // Ana formdaki 'Güncelle' butonunu uyandır
        }
    }

    public void appendAction(String newAction) {
        if (newAction == null || newAction.trim().isEmpty()) {
            return;
        }

        String current = action_taken_field.getText().trim();
        if (current.isEmpty()) {
            action_taken_field.setText(newAction.trim());
        } else {
            action_taken_field.setText(current + ", " + newAction.trim());
        }
        // Not: setText metodu DocumentListener'ı otomatik tetikleyeceği için
        // notifyDataChanged() dememize gerek yok, kendi kendine çalışır.
    }

    // --- FormService'in verileri toplaması (collectForm) için güvenli Getter'lar ---
    public String getReportedFault() {
        return reported_fault_field.getText().trim();
    }

    public String getDetectedFault() {
        return detected_fault_field.getText().trim();
    }

    public String getActionTaken() {
        return action_taken_field.getText().trim();
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap 3, insets 5, fillx", "[right][grow][pref!]"));

        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Arıza ve İşlem Bilgileri");
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));

        customer_complaint_label = new JLabel("Müşteri Şikayeti:");
        reported_fault_field = new JTextField();
        reported_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        detected_fault_label = new JLabel("Tespit:");
        detected_fault_field = new JTextField();
        detected_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        action_taken_label = new JLabel("Yapılan İşlem:");
        action_taken_field = new JTextField();
        action_taken_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        action_taken_button = new JButton("Seç");

        add(title, "span 3, align left, gapbottom 10");
//        add(customer_complaint_label);
//        add(reported_fault_field, "growx, span 2");
        add(detected_fault_label);
        add(detected_fault_field, "growx, span 2");
        add(action_taken_label);
        add(action_taken_field, "growx");
        add(action_taken_button, "align right");
    }

    private JLabel title;
    private JLabel customer_complaint_label;
    private JLabel detected_fault_label;
    private JLabel action_taken_label;

    // UI Bileşenleri (Getter'lar eklendiği için bunları private yapmak daha güvenli olurdu ama
    // FormService içinden butona EventListener eklediğin için şimdilik public/package-private kalıyor).
    public JTextField detected_fault_field;
    public JTextField reported_fault_field;
    public JTextField action_taken_field;
    public JButton action_taken_button;
}