package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CustomerStats {
    // --- Getters & Setters ---
    private int totalCustomers;
    private int individualCustomers;
    private int corporateCustomers;
    private double totalRevenue;

}