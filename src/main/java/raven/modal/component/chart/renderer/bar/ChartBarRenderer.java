package raven.modal.component.chart.renderer.bar;

import org.jfree.chart.renderer.category.BarRenderer;
import raven.modal.component.chart.themes.ChartDrawingSupplier;

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
