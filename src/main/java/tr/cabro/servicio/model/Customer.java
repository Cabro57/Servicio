package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class Customer {
    private int ID;
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

    public Customer (int ID, String name, String surname) {
        this.ID = ID;
        this.name = name;
        this.surname = surname;
    }

    @Override
    public String toString() {
        return name + " " + surname;
    }

}
