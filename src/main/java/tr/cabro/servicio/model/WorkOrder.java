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
public class WorkOrder {

    private Long id;

    @ColumnName("customer_id")
    private Long customerId;

    @ColumnName("device_id")
    private Long deviceId;

    @ColumnName("technician_id")
    private Long technicianId; // Servisi üzerine alan ana teknisyen

    @ColumnName("reported_fault")
    private String reportedFault;

    @ColumnName("detected_fault")
    private String detectedFault;

    @ColumnName("urgency_status")
    private String urgencyStatus;

    @ColumnName("service_status")
    private ServiceStatus serviceStatus;

    @ColumnName("warranty_end_date")
    private LocalDateTime warrantyEndDate;

    // --- DURUM TARİHLERİ (v_work_orders view'ı durum geçmişinden türetir, bkz. V27) ---
    // Yazma yalnızca work_order_status_history üzerinden yapılır; bu alanlar salt okunurdur.

    /** Teslim alma (ACCEPTED) anı. Yeni kayıtta insert öncesi doldurulursa ilk geçmiş satırına yazılır. */
    @ColumnName("received_at")
    private LocalDateTime receivedAt;

    /** İlk "Tamirde" geçişi; hiç tamire alınmadıysa null. */
    @ColumnName("repair_started_at")
    private LocalDateTime repairStartedAt;

    /** Son "Hazır" geçişi; hiç hazır olmadıysa null. */
    @ColumnName("ready_at")
    private LocalDateTime readyAt;

    /** Teslim ya da iade anı; kayıt kapalı değilse null. */
    @ColumnName("delivery_date")
    private LocalDateTime deliveryDate;

    @ColumnName("is_deleted")
    private boolean isDeleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    /** Durumun son değiştiği an (en son geçmiş satırı). */
    @ColumnName("status_changed_at")
    private LocalDateTime statusChangedAt;

    // --- İLİŞKİSEL VERİLER (DB'ye yazılmaz) ---
    private Customer customer;
    private Device device;

    private List<WorkOrderItem> items = new ArrayList<>();
    private List<Payment> payments = new ArrayList<>();
    private List<WorkOrderNote> technicianNotes = new ArrayList<>();

    // =======================================================================
    // FİNANSAL HESAPLAMA METODLARI
    // =======================================================================

    public BigDecimal getTotalServiceAmount() {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
        return items.stream()
                .map(WorkOrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalPaid() {
        if (payments == null || payments.isEmpty()) return BigDecimal.ZERO;
        return payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getRemainingAmount() {
        return getTotalServiceAmount().subtract(getTotalPaid());
    }
}