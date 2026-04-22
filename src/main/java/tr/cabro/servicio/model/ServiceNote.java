package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

@Getter @Setter
public class ServiceNote {

    private int id;

    @ColumnName("service_id")
    private int serviceId;

    @ColumnName("technician_id")
    private Integer technicianId; // Notu yazan User'ın ID'si

    private String note;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    // --- İLİŞKİSEL VERİLER (Veritabanına yazılmaz, UI'da göstermek için) ---
    private User technician; // Notu yazan kişinin detayları (Ad, Soyad vb.)

    public ServiceNote() {
        this.createdAt = LocalDateTime.now();
    }
}