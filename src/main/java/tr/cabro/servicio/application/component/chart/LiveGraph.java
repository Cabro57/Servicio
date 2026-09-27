package tr.cabro.servicio.application.component.chart;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Kayan zaman grafiği: son {@code capacity} ölçüm soldan sağa, en yeni sağ kenarda.
 * <p>
 * Kaynak Kullanımı sayfasında işlemci ve bellek için. ColumnChart'la aynı dil: ince ızgara,
 * nötr mürekkep, renk yalnızca anlam taşıdığında ({@link #setAlert}). İlk seri alanı doldurulmuş
 * çizgi (uygulamanın kendisi), sonrakiler ince soluk çizgi (karşılaştırma: tüm sistem).
 * Değerler 0..{@code max} aralığındadır; renkler her çizimde temadan okunur.
 */
public final class LiveGraph extends JComponent {

    private static final class Series {
        final Supplier<Color> ink;
        final boolean filled;
        final double[] values;

        Series(Supplier<Color> ink, boolean filled, int capacity) {
            this.ink = ink;
            this.filled = filled;
            this.values = new double[capacity];
            java.util.Arrays.fill(values, Double.NaN);
        }
    }

    private final int capacity;
    private final List<Series> series = new ArrayList<>();
    private double max = 1.0;
    private int count;
    private boolean alert;

    public LiveGraph(int capacity) {
        this.capacity = capacity;
        setOpaque(false);
        setPreferredSize(new Dimension(UIScale.scale(320), UIScale.scale(76)));
        setMinimumSize(new Dimension(UIScale.scale(120), UIScale.scale(56)));
    }

    /** Seri ekler: ilk eklenen dolgulu ana seri olur. */
    public LiveGraph addSeries(Supplier<Color> ink) {
        series.add(new Series(ink, series.isEmpty(), capacity));
        return this;
    }

    /** Dikey eksenin üst sınırı (ör. yüzde için 1.0, bellek için bayt cinsinden en çok). */
    public void setMax(double max) {
        this.max = max > 0 ? max : 1.0;
    }

    /** Ana seri eşiği aştığında uyarı renginde çizilir. */
    public void setAlert(boolean alert) {
        this.alert = alert;
    }

    /** Her seri için bir değer ekler (sıra addSeries sırası); eksik değer NaN. */
    public void push(double... values) {
        for (int i = 0; i < series.size(); i++) {
            double[] v = series.get(i).values;
            System.arraycopy(v, 1, v, 0, capacity - 1);
            v[capacity - 1] = i < values.length ? values[i] : Double.NaN;
        }
        count = Math.min(capacity, count + 1);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth();
            int h = getHeight();
            int top = UIScale.scale(4);
            int bottom = h - 1;
            float plot = bottom - top;

            // Izgara: taban ve orta çizgisi (ince, kenarlık rengi).
            Color grid = UIManager.getColor("Component.borderColor");
            g2.setColor(grid != null ? grid : Color.LIGHT_GRAY);
            g2.drawLine(0, bottom, w, bottom);
            Stroke dash = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                    new float[]{UIScale.scale(3f), UIScale.scale(3f)}, 0f);
            Stroke old = g2.getStroke();
            g2.setStroke(dash);
            g2.drawLine(0, Math.round(top + plot / 2), w, Math.round(top + plot / 2));
            g2.setStroke(old);

            if (count < 2) return;
            float step = (float) w / (capacity - 1);

            // Arkadaki soluk seriler önce, ana seri en üstte.
            for (int s = series.size() - 1; s >= 0; s--) {
                Series ser = series.get(s);
                Path2D.Float line = new Path2D.Float();
                boolean started = false;
                float firstX = 0, lastX = 0;
                for (int i = capacity - count; i < capacity; i++) {
                    double v = ser.values[i];
                    if (Double.isNaN(v)) continue;
                    float x = i * step;
                    float y = (float) (bottom - Math.max(0, Math.min(1, v / max)) * plot);
                    if (!started) {
                        line.moveTo(x, y);
                        firstX = x;
                        started = true;
                    } else {
                        line.lineTo(x, y);
                    }
                    lastX = x;
                }
                if (!started) continue;

                Color ink = ser.filled && alert ? UIManager.getColor("Servicio.warningColor") : ser.ink.get();
                if (ser.filled) {
                    Path2D.Float area = new Path2D.Float(line);
                    area.lineTo(lastX, bottom);
                    area.lineTo(firstX, bottom);
                    area.closePath();
                    g2.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 34));
                    g2.fill(area);
                    g2.setStroke(new BasicStroke(UIScale.scale(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                } else {
                    g2.setStroke(new BasicStroke(UIScale.scale(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                }
                g2.setColor(ink);
                g2.draw(line);
            }
        } finally {
            g2.dispose();
        }
    }
}
