package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.FieldPopupEditor;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;

public class FaultProcessInfoPanel extends ServicePanel {

    public FaultProcessInfoPanel(ServiceContext context) {
        super(context);
        init();
    }

    private void init() {
        initComponent();
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
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap 3, insets 5, fillx", "[right][grow][pref!]"));

        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Arıza ve İşlem Bilgileri");
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));

        customer_complaint_label = new JLabel("Müşteri Şikayeti:");
        reported_fault_field = new JTextField();
        FieldPopupEditor reportedPopupEditor = new FieldPopupEditor(reported_fault_field);
        reported_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, reportedPopupEditor.getTriggerButton());
        reported_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        detected_fault_label = new JLabel("Tespit:");
        detected_fault_field = new JTextField();
        FieldPopupEditor detectedPopupEditor = new FieldPopupEditor(detected_fault_field);
        detected_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, detectedPopupEditor.getTriggerButton());
        detected_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        action_taken_label = new JLabel("Yapılan İşlem:");
        action_taken_field = new JTextField();
        action_taken_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        action_taken_button = new JButton("Seç");

        add(title, "span 3, align left, gapbottom 10");
        add(customer_complaint_label);
        add(reported_fault_field, "growx, span 2");
        add(detected_fault_label);
        add(detected_fault_field, "growx 0, span 2");
        add(action_taken_label);
        add(action_taken_field, "growx");
        add(action_taken_button, "align right");
    }

    JLabel title;

    JLabel customer_complaint_label;
    JLabel detected_fault_label;
    JLabel action_taken_label;
    public JTextField detected_fault_field;
    public JTextField reported_fault_field;
    public JTextField action_taken_field;
    public JButton action_taken_button;
}
