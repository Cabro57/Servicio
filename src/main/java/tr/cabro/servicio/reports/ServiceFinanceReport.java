package tr.cabro.servicio.reports;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ServiceFinanceReport extends OkaeriConfig {

    private final List<ServiceFinanceRecord> records = new ArrayList<>();

    public void add(ServiceFinanceRecord record) {
        records.add(record);
    }

    public List<ServiceFinanceRecord> getMonthlyRows() {
        return records.stream()
                .filter(r -> !"GENEL TOPLAM".equals(r.getMonth()))
                .collect(Collectors.toList());
    }

    public ServiceFinanceRecord getLatestMonth() {
        return records.stream()
                .filter(r -> !"GENEL TOPLAM".equals(r.getMonth()))
                .max(Comparator.comparing(ServiceFinanceRecord::getMonth))
                .orElse(null);
    }

    public void sortByMonth() {
        records.sort(Comparator.comparing(ServiceFinanceRecord::getMonth));
    }

    // ---- Toplam ve ortalama hesaplamaları ---- //

    public int getTotalServiceCount() {
        return records.stream()
                .filter(r -> r.getServiceCount() != null)
                .mapToInt(ServiceFinanceRecord::getServiceCount)
                .sum();
    }

    public double getTotalRevenue() {
        return records.stream()
                .filter(r -> r.getTotalRevenue() != null)
                .mapToDouble(ServiceFinanceRecord::getTotalRevenue)
                .sum();
    }

    public double getTotalExpense() {
        return records.stream()
                .filter(r -> r.getTotalExpense() != null)
                .mapToDouble(ServiceFinanceRecord::getTotalExpense)
                .sum();
    }

    public double getTotalProfit() {
        return records.stream()
                .filter(r -> r.getTotalProfit() != null)
                .mapToDouble(ServiceFinanceRecord::getTotalProfit)
                .sum();
    }

    public double getAverageProfit() {
        return records.stream()
                .filter(r -> r.getTotalProfit() != null)
                .mapToDouble(ServiceFinanceRecord::getTotalProfit)
                .average()
                .orElse(0.0);
    }

    // ---- Mevcut ay metrikleri ---- //

    public int getCurrentMonthServices() {
        ServiceFinanceRecord latest = getLatestMonth();
        return latest != null && latest.getServiceCount() != null ? latest.getServiceCount() : 0;
    }

    public double getIncomeThisMonth() {
        ServiceFinanceRecord latest = getLatestMonth();
        return latest != null && latest.getTotalRevenue() != null ? latest.getTotalRevenue() : 0.0;
    }

    public double getExpenseThisMonth() {
        ServiceFinanceRecord latest = getLatestMonth();
        return latest != null && latest.getTotalExpense() != null ? latest.getTotalExpense() : 0.0;
    }

    public double getProfitThisMonth() {
        ServiceFinanceRecord latest = getLatestMonth();
        return latest != null && latest.getTotalProfit() != null ? latest.getTotalProfit() : 0.0;
    }
}
