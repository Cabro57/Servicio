package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    private int Id;
    private String businessName;
    private String name;
    private String surname;
    private String phoneNumber1;
    private String phoneNumber2;
    private String idNo;
    private String address;
    private String email;
    private CustomerType type;
    private String note;

    private LocalDateTime createdAt;

    public Customer (int Id, String name, String surname) {
        this.Id = Id;
        this.name = name;
        this.surname = surname;
    }

    @Override
    public String toString() {
        return name + " " + surname;
    }

}
