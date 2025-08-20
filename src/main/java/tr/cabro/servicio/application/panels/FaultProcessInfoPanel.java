package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.Getter;
import tr.cabro.servicio.application.component.FieldPopupEditor;

import javax.swing.*;

public class FaultProcessInfoPanel extends JPanel {
    private JPanel main_panel;
    private JLabel customer_complaint_label;
    private JLabel detected_fault_label;
    private JLabel action_taken_label;
    @Getter
    private JTextField detected_fault_field;
    @Getter
    private JTextField reported_fault_field;
    @Getter
    private JTextField action_taken_field;
    @Getter
    private JButton action_taken_button;
    private JLabel title;

    public FaultProcessInfoPanel() {
        init();
        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        FieldPopupEditor reportedPopupEditor = new FieldPopupEditor(reported_fault_field);
        reported_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, reportedPopupEditor.getTriggerButton());
        reported_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        reported_fault_field.setColumns(20);

        FieldPopupEditor detectedPopupEditor = new FieldPopupEditor(detected_fault_field);
        detected_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, detectedPopupEditor.getTriggerButton());
        detected_fault_field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        detected_fault_field.setColumns(20);

        action_taken_field.setColumns(20);
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

    public void setReportedFault(String fault) {
        reported_fault_field.setText(fault != null ? fault : "");
    }

    public void setDetectedFault(String fault) {
        detected_fault_field.setText(fault != null ? fault : "");
    }

    public void setActionTaken(String action) {
        action_taken_field.setText(action != null ? action : "");
    }

    public String getReportedFault() {
        return reported_fault_field.getText().trim();
    }

    public String getDetectedFault() {
        return detected_fault_field.getText().trim();
    }

    public String getActionTaken() {
        return action_taken_field.getText().trim();
    }

}
