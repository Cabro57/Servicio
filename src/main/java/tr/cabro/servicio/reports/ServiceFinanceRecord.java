package tr.cabro.servicio.reports;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Aylık finans raporunun tek satırı — yalnızca bellekte kullanılan bir taşıyıcı. */
@Getter @Setter @NoArgsConstructor
public class ServiceFinanceRecord {

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
