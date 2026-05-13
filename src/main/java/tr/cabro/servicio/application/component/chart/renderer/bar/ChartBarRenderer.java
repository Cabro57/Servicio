package tr.cabro.servicio.application.component.chart.renderer.bar;

import org.jfree.chart.renderer.category.BarRenderer;
import tr.cabro.servicio.application.component.chart.themes.ChartDrawingSupplier;

public class ChartBarRenderer extends BarRenderer {

    public ChartBarRenderer() {
        initStyle();
    }

    private void initStyle() {
        setDefaultLegendShape(ChartDrawingSupplier.getDefaultShape());
    }

    @Override
    public String toString() {
        return "Bar";
    }
}
