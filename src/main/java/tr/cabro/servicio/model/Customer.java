package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class Customer {
    private int Id;
    private String business_name;
    private String name;
    private String surname;
    private String phone_number_1;
    private String phone_number_2;
    private String id_no;
    private String address;
    private String email;
    private CustomerType type;
    private String note;

    private LocalDateTime created_at;

    public Customer() {

    }

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
