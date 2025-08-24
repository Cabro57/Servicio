package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import raven.datetime.DatePicker;
import raven.datetime.PanelDateOptionLabel;

import javax.swing.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class WarrantyInfoPanel extends JPanel {
    private JLabel warranty_period_label;
    private JFormattedTextField warranty_period_field;
    private JLabel warranty_status_label;
    private JLabel warranty_status_info;
    private JLabel maintenance_period_label;
    private JFormattedTextField maintenance_period_field;
    private JLabel maintenance_status_label;
    private JLabel maintenance_status_info;
    private JPanel main_panel;
    private JLabel title;

    private DatePicker warrantyDatePicker;
    private DatePicker maintenanceDatePicker;

    public WarrantyInfoPanel() {
        init();
        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        PanelDateOptionLabel panelDateOptionLabel = getPanelDateOptionLabel();

        warranty_period_field.setColumns(10);
        maintenance_period_field.setColumns(10);

        warrantyDatePicker = new DatePicker();
        warrantyDatePicker.setUsePanelOption(true);
        warrantyDatePicker.setPanelDateOptionLabel(panelDateOptionLabel);
        warrantyDatePicker.setEditor(warranty_period_field);
        warrantyDatePicker.addDateSelectionListener(d -> updateWarrantyStatus());

        maintenanceDatePicker = new DatePicker();
        maintenanceDatePicker.setUsePanelOption(true);
        maintenanceDatePicker.setPanelDateOptionLabel(panelDateOptionLabel);
        maintenanceDatePicker.setEditor(maintenance_period_field);
        maintenanceDatePicker.addDateSelectionListener(d -> updateMaintenanceStatus());
    }

    private static PanelDateOptionLabel getPanelDateOptionLabel() {
        PanelDateOptionLabel panelDateOptionLabel = new PanelDateOptionLabel();

        panelDateOptionLabel.add("3 Gün", () -> new LocalDate[] {
                LocalDate.now().plusDays(2)
        });

        panelDateOptionLabel.add("1 Hafta", () -> new LocalDate[] {
                LocalDate.now().plusDays(6)
        });

        panelDateOptionLabel.add("2 Hafta", () -> new LocalDate[] {
                LocalDate.now().plusDays(13)
        });

        panelDateOptionLabel.add("1 Ay", () -> new LocalDate[] {
                LocalDate.now().plusMonths(1).minusDays(1)
        });

        panelDateOptionLabel.add("3 Ay", () -> new LocalDate[] {
                LocalDate.now().plusMonths(3).minusDays(1)
        });
        return panelDateOptionLabel;
    }

    private void updateWarrantyStatus() {
        LocalDate today = LocalDate.now();
        LocalDate selected = warrantyDatePicker.getSelectedDate();

        if (selected != null) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, selected);
            if (daysLeft < 0) {
                warranty_status_info.setText("Süre doldu");
            } else {
                warranty_status_info.setText(daysLeft + " gün kaldı");
            }
        } else {
            warranty_status_info.setText("Tarih seçilmedi");
        }
    }

    private void updateMaintenanceStatus() {
        LocalDate today = LocalDate.now();
        LocalDate selected = maintenanceDatePicker.getSelectedDate();

        if (selected != null) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, selected);
            if (daysLeft < 0) {
                maintenance_status_info.setText("Süre doldu");
            } else {
                maintenance_status_info.setText(daysLeft + " gün kaldı");
            }
        } else {
            maintenance_status_info.setText("Tarih seçilmedi");
        }
    }

    // ----------------------------
    // Getter / Setter metodları
    // ----------------------------


    public LocalDateTime getWarrantyDate() {
        LocalDate date = warrantyDatePicker.getSelectedDate();
        return date != null ? LocalDateTime.of(date, LocalTime.now()) : null;
    }

    public void setWarrantyDate(LocalDateTime date) {
        if (date != null) {
            warrantyDatePicker.setSelectedDate(date.toLocalDate());
        } else {
            // Eğer tarih null ise, tarih seçim alanını temizlemek için uygun bir yöntem varsa kullan
            warrantyDatePicker.clearSelectedDate(); // veya başka bir temizleme metodu yoksa, boş bırakabilirsin
        }
        updateWarrantyStatus();
    }

    public LocalDateTime getMaintenanceDate() {
        LocalDate date = maintenanceDatePicker.getSelectedDate();
        return date != null ? LocalDateTime.of(date, LocalTime.now()) : null;
    }

    public void setMaintenanceDate(LocalDateTime date) {
        if (date != null) {
            maintenanceDatePicker.setSelectedDate(date.toLocalDate());
        } else {
            maintenanceDatePicker.clearSelectedDate(); // Varsa
        }
        updateMaintenanceStatus();
    }
}
