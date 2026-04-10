package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.CustomerType;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    private int id;

    @ColumnName("business_name")
    private String businessName;

    private String name;
    private String surname;

    @ColumnName("phone_number_1")
    private String phoneNumber1;

    @ColumnName("phone_number_2")
    private String phoneNumber2;

    @ColumnName("id_no")
    private String idNo;

    private String address;
    private String email;

    @ColumnName("status")
    private CustomerType type;
    private String note;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    public Customer (int Id, String name, String surname) {
        this.id = Id;
        this.name = name;
        this.surname = surname;
    }

    public CustomerType getStatus() {
        return type;
    }
    public void setStatus(CustomerType status) {
        this.type = status;
    }

    @Override
    public String toString() {
        return name + " " + surname;
    }

}
