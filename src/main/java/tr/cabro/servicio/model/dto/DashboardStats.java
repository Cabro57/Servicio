package tr.cabro.servicio.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class DashboardStats {
    private int totalRecords;
    private int activeRecords;
    private int completedRecords;
    private double totalRevenue;
}