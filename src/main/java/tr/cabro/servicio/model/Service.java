package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class Service {

    private int id;

    @ColumnName("customer_id")
    private Integer customerId;

    @ColumnName("device_id")
    private int deviceId;

    @ColumnName("technician_id")
    private Integer technicianId; // Servisi üzerine alan ana teknisyen

    @ColumnName("reported_fault")
    private String reportedFault;

    @ColumnName("detected_fault")
    private String detectedFault;

    @ColumnName("action_taken")
    private String actionTaken;

    @ColumnName("urgency_status")
    private String urgencyStatus;

    @ColumnName("service_status")
    private ServiceStatus serviceStatus;

    @ColumnName("warranty_end_date")
    private LocalDateTime warrantyEndDate;

    @ColumnName("delivery_date")
    private LocalDateTime deliveryDate;

    // DİKKAT: 'private String note;' BURADAN TAMAMEN SİLİNDİ!

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    // --- İLİŞKİSEL VERİLER (DB'ye yazılmaz) ---
    private Customer customer;
    private Device device;

    private List<ServiceItem> items = new ArrayList<>();
    private List<ServicePayment> payments = new ArrayList<>();

    // YENİ: Teknisyen Notları Listesi
    private List<ServiceNote> technicianNotes = new ArrayList<>();

    // =======================================================================
    // FİNANSAL HESAPLAMA METODLARI
    // =======================================================================

    public BigDecimal getTotalServiceAmount() {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
        return items.stream()
                .map(ServiceItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalPaid() {
        if (payments == null || payments.isEmpty()) return BigDecimal.ZERO;
        return payments.stream()
                .map(ServicePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRemainingAmount() {
        return getTotalServiceAmount().subtract(getTotalPaid());
    }
}