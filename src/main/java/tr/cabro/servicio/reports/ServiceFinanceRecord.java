package tr.cabro.servicio.reports;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class ServiceFinanceRecord extends OkaeriConfig {

    private String month;
    private Integer serviceCount;
    private Double serviceChangeRate;
    private Double totalRevenue;
    private Double revenueChangeRate;
    private Double totalExpense;
    private Double expenseChangeRate;
    private Double totalProfit;
    private Double profitChangeRate;
}
