package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class Supplier {

    private int id;

    private String name;
    private String businessName;
    private String idNo;
    private String taxNo;
    private String taxOffice;
    private String email;
    private String phone;
    private String address;
    private String notes;

    private LocalDateTime created_at;

    public Supplier(int id, String name, String businessName) {
        this.id = id;
        this.name = name;
        this.businessName = businessName;
        this.created_at = LocalDateTime.now();
    }

    public Supplier() {
        this.created_at = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return businessName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Supplier)) return false;
        Supplier other = (Supplier) o;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
