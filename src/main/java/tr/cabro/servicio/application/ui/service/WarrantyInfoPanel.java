package tr.cabro.servicio.application.ui.service;

import com.formdev.flatlaf.FlatClientProperties;
import raven.datetime.DatePicker;
import raven.datetime.PanelDateOptionLabel;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class WarrantyInfoPanel extends ServicePanel {

    private DatePicker warrantyDatePicker;
    private DatePicker maintenanceDatePicker;

    public WarrantyInfoPanel(ServiceContext context) {
        super(context);
        init();
    }

    private void init() {
        initComponent();

        PanelDateOptionLabel panelDateOptionLabel = getPanelDateOptionLabel();

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

    private void initComponent() {
        setLayout(new net.miginfocom.swing.MigLayout("wrap 4, insets 10", "[grow][grow]", ""));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Garanti ve Bakım Bilgileri");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        warranty_period_label = new JLabel("Garanti Tarihi:");
        warranty_period_field = new JFormattedTextField();
        warranty_status_label = new JLabel("Garanti Süresi:");
        warranty_status_info = new JLabel("Tarih seçilmedi");
        warranty_status_info.setFont(warranty_status_info.getFont().deriveFont(Font.BOLD, 14f));

        maintenance_period_label = new JLabel("Bakım Tarihi:");
        maintenance_period_field = new JFormattedTextField();
        maintenance_status_label = new JLabel("Bakım Süresi:");
        maintenance_status_info = new JLabel("Tarih seçilmedi");
        maintenance_status_info.setFont(maintenance_status_info.getFont().deriveFont(Font.BOLD, 14f));

        add(title, "span 4, wrap, gapbottom 10");
        add(warranty_period_label, "alignx leading");
        add(warranty_period_field, "growx");
        add(warranty_status_label, "alignx leading");
        add(warranty_status_info, "growx, wrap");
        add(maintenance_period_label, "alignx leading");
        add(maintenance_period_field, "growx");
        add(maintenance_status_label, "alignx leading");
        add(maintenance_status_info, "growx, wrap");
    }

    private JLabel warranty_period_label;
    private JFormattedTextField warranty_period_field;
    private JLabel warranty_status_label;
    private JLabel warranty_status_info;
    private JLabel maintenance_period_label;
    private JFormattedTextField maintenance_period_field;
    private JLabel maintenance_status_label;
    private JLabel maintenance_status_info;
    private JLabel title;
}
