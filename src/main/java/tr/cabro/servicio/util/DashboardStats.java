package tr.cabro.servicio.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardStats {
    private long totalServices;
    private long currentMonthServices;
    private double serviceChangePercent;

    private double totalIncome;
    private double incomeThisMonth;
    private double incomeChangePercent;

    private double totalExpense;
    private double expenseThisMonth;
    private double expenseChangePercent;

    private double totalProfit;
    private double profitThisMonth;
    private double profitChangePercent;
}



