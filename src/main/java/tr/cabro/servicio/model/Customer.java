package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.CustomerType;

import java.beans.Transient;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    private Long id;

    @ColumnName("customer_type")
    private CustomerType type;

    @ColumnName("business_name")
    private String businessName;

    // Kurumsal standart: first_name / last_name
    @ColumnName("first_name")
    private String firstName;

    @ColumnName("last_name")
    private String lastName;

    @ColumnName("identity_no")
    private String identityNo;

    @ColumnName("tax_number")
    private String taxNumber;

    @ColumnName("tax_office")
    private String taxOffice;

    @ColumnName("phone_number_1")
    private String phoneNumber1;

    @ColumnName("phone_number_2")
    private String phoneNumber2;

    private String email;
    private String address;
    private String note;

    // Denetim (Audit) & Soft Delete Alanları
    // DİKKAT: "deleted" olarak tutulmalı — Lombok, boolean isDeleted için isIsDeleted() üretir (JDBI hatası).
    @ColumnName("is_deleted")
    private boolean deleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    // Geriye dönük uyumluluk için basit Constructor
    public Customer(Long id, String firstName, String lastName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                ", type=" + type +
                ", businessName='" + businessName + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", identityNo='" + identityNo + '\'' +
                ", phoneNumber1='" + phoneNumber1 + '\'' +
                ", deleted=" + deleted +
                ", createdAt=" + createdAt +
                '}';
    }
}