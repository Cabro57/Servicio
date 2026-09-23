package tr.cabro.servicio.application.panels.dashboard;

import net.miginfocom.swing.MigLayout;
import org.jfree.data.general.DefaultPieDataset;
import tr.cabro.servicio.application.component.chart.PieChart;
import tr.cabro.servicio.application.component.chart.themes.DefaultChartTheme;
import tr.cabro.servicio.model.dto.ChartDataDto;

import javax.swing.*;
import java.util.List;

/**
 * Seçili dönemde servise gelen cihazların tür ve marka dağılımı (iki pasta grafik, başlıklı).
 * %3'ün altındaki dilimler "Diğer" altında toplanır.
 */
public class DistributionPanel extends JPanel {

    private static final double OTHER_THRESHOLD_PERCENT = 3.0;

    private final PieChart deviceTypeChart = new PieChart();
    private final PieChart brandChart = new PieChart();

    public DistributionPanel() {
        setLayout(new MigLayout("insets 0, fillx, gap 14", "[fill,sg p][fill,sg p]", "[fill, 280!]"));
        setOpaque(false);
        add(titled(deviceTypeChart, "Cihaz türleri"));
        add(titled(brandChart, "Markalar"));
    }

    private static PieChart titled(PieChart chart, String title) {
        DefaultChartTheme.applyTheme(chart.getFreeChart());
        chart.add(DashboardUi.title(title), "gap 16 16 14 0", 0);
        return chart;
    }

    public void setDeviceTypes(List<ChartDataDto> data) {
        deviceTypeChart.setDataset(grouped(data));
    }

    public void setBrands(List<ChartDataDto> data) {
        brandChart.setDataset(grouped(data));
    }

    private static DefaultPieDataset grouped(List<ChartDataDto> data) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        if (data == null || data.isEmpty()) return dataset;

        double total = data.stream().filter(d -> d.getValue() != null).mapToDouble(d -> d.getValue().doubleValue()).sum();
        if (total == 0) return dataset;

        double otherSum = 0;
        for (ChartDataDto dto : data) {
            if (dto.getValue() == null) continue;
            double value = dto.getValue().doubleValue();
            if (value / total * 100.0 < OTHER_THRESHOLD_PERCENT) {
                otherSum += value;
            } else {
                dataset.setValue(dto.getLabel() != null ? dto.getLabel() : "Bilinmeyen", value);
            }
        }
        if (otherSum > 0) dataset.setValue("Diğer", otherSum);
        return dataset;
    }
}
