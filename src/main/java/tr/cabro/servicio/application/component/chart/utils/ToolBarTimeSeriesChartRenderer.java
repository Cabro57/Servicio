package tr.cabro.servicio.application.component.chart.utils;

import org.jfree.chart.renderer.xy.XYItemRenderer;
import tr.cabro.servicio.application.component.ToolBarSelection;
import tr.cabro.servicio.application.component.chart.TimeSeriesChart;
import tr.cabro.servicio.application.component.chart.renderer.*;

public class ToolBarTimeSeriesChartRenderer extends ToolBarSelection<XYItemRenderer> {

    public ToolBarTimeSeriesChartRenderer(TimeSeriesChart chart) {
        super(getRenderers(), renderer -> {
            chart.setRenderer(renderer);
        });
    }

    private static XYItemRenderer[] getRenderers() {
        XYItemRenderer[] renderers = new XYItemRenderer[]{
                new ChartXYCurveRenderer(),
                new ChartXYLineRenderer(),
                new ChartXYBarRenderer(),
                new ChartStackedXYBarRenderer(),
                new ChartDeviationStepRenderer(),
                new ChartXYDifferenceRenderer()
        };
        return renderers;
    }
}
