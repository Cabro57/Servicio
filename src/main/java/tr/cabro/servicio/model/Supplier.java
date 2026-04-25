package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    private Long id;

    private String name;

    @ColumnName("business_name")
    private String businessName;

    @ColumnName("tax_number")
    private String taxNumber;

    @ColumnName("tax_office")
    private String taxOffice;

    private String email;
    private String phone;
    private String address;

    @ColumnName("note")
    private String note;

    // Denetim (Audit) & Soft Delete Alanları
    @ColumnName("is_deleted")
    private boolean isDeleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    // Geriye dönük uyumluluk için eski Constructor
    public Supplier(Long id, String name, String businessName) {
        this.id = id;
        this.name = name;
        this.businessName = businessName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return businessName != null && !businessName.isEmpty() ? businessName : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Supplier)) return false;
        Supplier other = (Supplier) o;
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}