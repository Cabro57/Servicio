package tr.cabro.servicio.application.component.chart;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Temaya bağlı, elle çizilen sütun grafiği: zaman kovaları boyunca bir ya da birkaç seri,
 * yığılı ya da yan yana; isteğe bağlı bir çizgi serisi (ör. net kâr) sütunların üstünde.
 * <p>
 * Renkler her çizimde tema anahtarlarından okunur ({@link Supplier}), tema ya da vurgu değişince
 * grafik kendiliğinden döner. Fare bir sütunun üstüne gelince o kova hafifçe boyanır ve tüm
 * serilerin değerini gösteren küçük bir kart açılır; tıklama dinleyicisi verilmişse sütun
 * tıklanabilir olur (el imleci). Negatif değerler (iade, zarar) sıfır çizgisinin altına iner.
 */
public class ColumnChart extends JComponent {

    /** Bir seri: ad, kova başına değerler ve rengi veren tema okuyucu. */
    public record Series(String name, double[] values, Supplier<Color> color) {}

    private List<String> axisLabels = Collections.emptyList();
    private List<String> tipLabels = Collections.emptyList();
    private List<Series> bars = Collections.emptyList();
    private Series line;
    private boolean stacked;
    private boolean loading = true;

    private DoubleFunction<String> axisFormat = ColumnChart::compact;
    private DoubleFunction<String> valueFormat = v -> compact(v);
    private IntConsumer onClick;
    private String clickHint;
    private String emptyText = "Bu dönemde hareket yok";

    private int hover = -1;
    /** Son çizimde hesaplanan çizim alanı; fare konumunu kovaya çevirmek için. */
    private Rectangle plot = new Rectangle();

    public ColumnChart() {
        setOpaque(false);
        setFocusable(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(indexAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (index >= 0 && onClick != null && SwingUtilities.isLeftMouseButton(e)) onClick.accept(index);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ── Veri ────────────────────────────────────────────────────────────────

    /**
     * @param axisLabels kısa eksen etiketleri (kova başına)
     * @param tipLabels  ipucu kartının başlığı (kova başına, tam tarih)
     * @param bars       sütun serileri
     * @param line       sütunların üstüne çizilen seri; yoksa {@code null}
     * @param stacked    serileri üst üste yığ (aksi halde yan yana)
     */
    public void setData(List<String> axisLabels, List<String> tipLabels, List<Series> bars, Series line, boolean stacked) {
        this.axisLabels = axisLabels;
        this.tipLabels = tipLabels;
        this.bars = bars;
        this.line = line;
        this.stacked = stacked;
        this.loading = false;
        this.hover = -1;
        repaint();
    }

    /** Veri beklenirken eski grafiği değil sade bir "Yükleniyor…" satırını gösterir. */
    public void setLoading() {
        loading = true;
        hover = -1;
        repaint();
    }

    public void setAxisFormat(DoubleFunction<String> format) {
        this.axisFormat = format;
    }

    public void setValueFormat(DoubleFunction<String> format) {
        this.valueFormat = format;
    }

    public void setEmptyText(String text) {
        this.emptyText = text;
    }

    /** Sütun tıklanınca çağrılır; {@code hint} ipucu kartının altında soluk yazılır ("Tıkla: listeyi aç"). */
    public void setOnClick(IntConsumer onClick, String hint) {
        this.onClick = onClick;
        this.clickHint = hint;
    }

    private boolean isEmpty() {
        if (axisLabels.isEmpty()) return true;
        for (Series s : bars) for (double v : s.values()) if (v != 0) return false;
        if (line != null) for (double v : line.values()) if (v != 0) return false;
        return true;
    }

    private void setHover(int index) {
        if (index == hover) return;
        hover = index;
        setCursor(index >= 0 && onClick != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }

    private int indexAt(int x, int y) {
        if (loading || isEmpty() || plot.width <= 0) return -1;
        if (x < plot.x || x >= plot.x + plot.width || y < plot.y - UIScale.scale(4) || y > plot.y + plot.height + UIScale.scale(24)) return -1;
        int n = axisLabels.size();
        int index = (int) ((x - plot.x) / (plot.width / (double) n));
        return Math.max(0, Math.min(n - 1, index));
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return new Dimension(UIScale.scale(320), UIScale.scale(200));
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        return new Dimension(UIScale.scale(120), UIScale.scale(120));
    }

    // ── Çizim ───────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            paintChart(g2);
        } finally {
            g2.dispose();
        }
    }

    private void paintChart(Graphics2D g2) {
        Font base = UIManager.getFont("Label.font");
        Font small = base.deriveFont(base.getSize2D() - 1f);
        Color muted = color("Label.disabledForeground", Color.GRAY);
        Color grid = color("Component.borderColor", Color.LIGHT_GRAY);
        int w = getWidth();
        int h = getHeight();

        int legendH = paintLegend(g2, small, muted);
        FontMetrics fm = g2.getFontMetrics(small);

        if (loading || isEmpty()) {
            plot = new Rectangle();
            g2.setFont(base);
            g2.setColor(muted);
            String text = loading ? "Yükleniyor…" : emptyText;
            int tw = g2.getFontMetrics().stringWidth(text);
            int top = legendH;
            // Boş durumda da taban çizgisi kalsın; kartta grafik alanı kaybolmasın.
            int baseY = h - fm.getHeight() - UIScale.scale(6);
            g2.setColor(grid);
            g2.draw(new Line2D.Float(0, baseY + 0.5f, w, baseY + 0.5f));
            g2.setColor(muted);
            g2.drawString(text, (w - tw) / 2, top + (baseY - top) / 2 + g2.getFontMetrics().getAscent() / 2);
            return;
        }

        int n = axisLabels.size();
        double[] lo = new double[n];
        double[] hi = new double[n];
        double min = 0;
        double max = 0;
        for (int i = 0; i < n; i++) {
            double pos = 0, neg = 0;
            for (Series s : bars) {
                double v = s.values()[i];
                if (stacked) {
                    if (v >= 0) pos += v;
                    else neg += v;
                } else {
                    pos = Math.max(pos, v);
                    neg = Math.min(neg, v);
                }
            }
            lo[i] = neg;
            hi[i] = pos;
            min = Math.min(min, neg);
            max = Math.max(max, pos);
            if (line != null) {
                min = Math.min(min, line.values()[i]);
                max = Math.max(max, line.values()[i]);
            }
        }
        if (max == min) max = min + 1;

        double step = niceStep((max - min) / 4.0);
        double axisMin = Math.floor(min / step) * step;
        double axisMax = Math.ceil(max / step) * step;
        if (axisMax == axisMin) axisMax = axisMin + step;

        // Sol eksen etiketlerinin genişliği kadar boşluk.
        int labelW = 0;
        for (double t = axisMin; t <= axisMax + step / 2; t += step) {
            labelW = Math.max(labelW, fm.stringWidth(axisFormat.apply(t)));
        }
        int left = labelW + UIScale.scale(10);
        int bottom = fm.getHeight() + UIScale.scale(6);
        int top = legendH + UIScale.scale(6);
        plot = new Rectangle(left, top, Math.max(1, w - left - UIScale.scale(2)), Math.max(1, h - top - bottom));

        double range = axisMax - axisMin;
        java.util.function.DoubleUnaryOperator yOf = v -> plot.y + plot.height - (v - axisMin) / range * plot.height;

        // Yatay kılavuz çizgileri ve değerleri.
        g2.setFont(small);
        for (double t = axisMin; t <= axisMax + step / 2; t += step) {
            float y = (float) yOf.applyAsDouble(t);
            boolean zero = Math.abs(t) < step / 1000;
            g2.setColor(zero ? mix(grid, muted, 0.45f) : grid);
            g2.setStroke(new BasicStroke(UIScale.scale(1f)));
            g2.draw(new Line2D.Float(plot.x, y, plot.x + plot.width, y));
            g2.setColor(muted);
            String label = axisFormat.apply(t);
            g2.drawString(label, left - UIScale.scale(8) - fm.stringWidth(label), y + fm.getAscent() / 2f - 1);
        }

        double slot = plot.width / (double) n;
        float groupW = (float) Math.min(slot * (n > 20 ? 0.72 : 0.62), UIScale.scale(34));
        float zeroY = (float) yOf.applyAsDouble(0);

        // Üzerine gelinen kova.
        if (hover >= 0) {
            Color band = color("Servicio.rowHoverBackground", grid);
            g2.setColor(band);
            float bx = (float) (plot.x + hover * slot + slot * 0.08);
            g2.fill(new RoundRectangle2D.Float(bx, plot.y - UIScale.scale(4), (float) (slot * 0.84),
                    plot.height + UIScale.scale(4), UIScale.scale(8), UIScale.scale(8)));
        }

        float arc = UIScale.scale(6f);
        float gap = UIScale.scale(1f);
        for (int i = 0; i < n; i++) {
            float cx = (float) (plot.x + i * slot + slot / 2);
            if (stacked) {
                float x = cx - groupW / 2;
                paintStack(g2, i, x, groupW, zeroY, yOf, arc, gap, true, hi[i]);
                paintStack(g2, i, x, groupW, zeroY, yOf, arc, gap, false, lo[i]);
            } else {
                int k = Math.max(1, bars.size());
                float inner = UIScale.scale(2f);
                float bw = Math.max(UIScale.scale(2f), (groupW - inner * (k - 1)) / k);
                float x = cx - (bw * k + inner * (k - 1)) / 2;
                for (Series s : bars) {
                    double v = s.values()[i];
                    if (v != 0) {
                        float y = (float) yOf.applyAsDouble(v);
                        g2.setColor(s.color().get());
                        g2.fill(roundedBar(x, Math.min(y, zeroY), bw, Math.abs(zeroY - y), Math.min(arc, bw), v >= 0));
                    }
                    x += bw + inner;
                }
            }
        }

        if (line != null) paintLine(g2, slot, yOf);

        // Alt eksen etiketleri: sığmayan etiketler atlanır, ilk ve son her zaman kalır.
        g2.setFont(small);
        int maxLabel = 0;
        for (String s : axisLabels) maxLabel = Math.max(maxLabel, fm.stringWidth(s));
        int every = Math.max(1, (int) Math.ceil((maxLabel + UIScale.scale(10)) / slot));
        float labelY = plot.y + plot.height + UIScale.scale(4) + fm.getAscent();
        for (int i = 0; i < n; i++) {
            boolean show = i % every == 0 || i == hover;
            if (!show) continue;
            String s = axisLabels.get(i);
            float cx = (float) (plot.x + i * slot + slot / 2);
            float x = Math.max(plot.x - left + UIScale.scale(2), Math.min(w - fm.stringWidth(s), cx - fm.stringWidth(s) / 2f));
            g2.setColor(i == hover ? color("Label.foreground", Color.BLACK) : muted);
            g2.drawString(s, x, labelY);
        }

        if (hover >= 0) paintTip(g2, base, small, hover, slot);
    }

    /** Yığılı sütunun artı ya da eksi yarısı; yalnızca dış uç yuvarlanır, dilimler arasında ince boşluk. */
    private void paintStack(Graphics2D g2, int i, float x, float bw, float zeroY,
                            java.util.function.DoubleUnaryOperator yOf, float arc, float gap, boolean positive, double total) {
        if (total == 0) return;
        float end = (float) yOf.applyAsDouble(total);
        Shape outer = roundedBar(x, Math.min(end, zeroY), bw, Math.abs(zeroY - end), Math.min(arc, bw), positive);
        Shape oldClip = g2.getClip();
        g2.clip(outer);
        double acc = 0;
        List<Series> order = new ArrayList<>(bars);
        for (Series s : order) {
            double v = s.values()[i];
            if (positive ? v <= 0 : v >= 0) continue;
            float y0 = (float) yOf.applyAsDouble(acc);
            acc += v;
            float y1 = (float) yOf.applyAsDouble(acc);
            g2.setColor(s.color().get());
            float top = Math.min(y0, y1);
            float height = Math.abs(y1 - y0);
            // Sonraki dilimle aradaki ince boşluk zemini gösterir, renkler birbirine akmaz.
            float trim = Math.min(gap, height / 3);
            if (positive) g2.fill(new Rectangle2D.Float(x, top, bw, Math.max(0, height - trim)));
            else g2.fill(new Rectangle2D.Float(x, top + trim, bw, Math.max(0, height - trim)));
        }
        g2.setClip(oldClip);
    }

    /** Taban tarafı düz, uç tarafı yuvarlak sütun. */
    private static Shape roundedBar(float x, float y, float w, float h, float arc, boolean up) {
        if (h <= 0) return new Rectangle2D.Float(x, y, w, 0);
        float a = Math.min(arc, h * 2);
        Area area = new Area(new RoundRectangle2D.Float(x, y, w, h, a, a));
        float half = Math.min(h, a / 2);
        area.add(new Area(up ? new Rectangle2D.Float(x, y + h - half, w, half) : new Rectangle2D.Float(x, y, w, half)));
        return area;
    }

    private void paintLine(Graphics2D g2, double slot, java.util.function.DoubleUnaryOperator yOf) {
        int n = axisLabels.size();
        Color c = line.color().get();
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < n; i++) {
            float x = (float) (plot.x + i * slot + slot / 2);
            float y = (float) yOf.applyAsDouble(line.values()[i]);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        g2.setColor(c);
        g2.setStroke(new BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);

        // Noktalar yalnızca seyrek grafikte ve üzerine gelinen kovada; sık grafikte çizgiyi boğmasın.
        Color ground = color("Panel.background", Color.WHITE);
        float r = UIScale.scale(3.5f);
        for (int i = 0; i < n; i++) {
            if (n > 16 && i != hover) continue;
            float x = (float) (plot.x + i * slot + slot / 2);
            float y = (float) yOf.applyAsDouble(line.values()[i]);
            float rr = i == hover ? r + UIScale.scale(1f) : r;
            g2.setColor(ground);
            g2.fill(new java.awt.geom.Ellipse2D.Float(x - rr, y - rr, rr * 2, rr * 2));
            g2.setColor(c);
            g2.setStroke(new BasicStroke(UIScale.scale(2f)));
            g2.draw(new java.awt.geom.Ellipse2D.Float(x - rr, y - rr, rr * 2, rr * 2));
        }
    }

    /** Üstte seri adları; kare işaret sütun serisi, kısa çizgi çizgi serisi. Yüksekliğini döndürür. */
    private int paintLegend(Graphics2D g2, Font font, Color muted) {
        List<Series> all = new ArrayList<>(bars);
        if (line != null) all.add(line);
        if (all.size() < 2) return 0;
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int x = 0;
        int y = fm.getAscent();
        int mark = UIScale.scale(9);
        for (Series s : all) {
            boolean isLine = s == line;
            g2.setColor(s.color().get());
            float my = y - fm.getAscent() / 2f - mark / 2f + UIScale.scale(1);
            if (isLine) {
                g2.setStroke(new BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Float(x, my + mark / 2f, x + mark + UIScale.scale(3), my + mark / 2f));
                x += mark + UIScale.scale(3);
            } else {
                g2.fill(new RoundRectangle2D.Float(x, my, mark, mark, UIScale.scale(3), UIScale.scale(3)));
                x += mark;
            }
            x += UIScale.scale(6);
            g2.setColor(muted);
            g2.drawString(s.name(), x, y);
            x += fm.stringWidth(s.name()) + UIScale.scale(16);
        }
        return fm.getHeight();
    }

    /** Üzerine gelinen kovanın kartı: tarih, seri değerleri, varsa toplam ve tıklama ipucu. */
    private void paintTip(Graphics2D g2, Font base, Font small, int i, double slot) {
        List<Series> all = new ArrayList<>(bars);
        if (line != null) all.add(line);

        Font bold = base.deriveFont(Font.BOLD);
        FontMetrics fb = g2.getFontMetrics(bold);
        FontMetrics fs = g2.getFontMetrics(small);
        FontMetrics fn = g2.getFontMetrics(base);
        String title = i < tipLabels.size() ? tipLabels.get(i) : axisLabels.get(i);

        List<String[]> rows = new ArrayList<>();
        for (Series s : all) rows.add(new String[]{s.name(), valueFormat.apply(s.values()[i])});
        boolean showTotal = stacked && bars.size() > 1;
        String total = null;
        if (showTotal) {
            double sum = 0;
            for (Series s : bars) sum += s.values()[i];
            total = valueFormat.apply(sum);
        }

        int pad = UIScale.scale(10);
        int mark = UIScale.scale(8);
        int rowH = fn.getHeight() + UIScale.scale(2);
        int nameW = 0, valueW = 0;
        for (String[] r : rows) {
            nameW = Math.max(nameW, fn.stringWidth(r[0]));
            valueW = Math.max(valueW, fb.stringWidth(r[1]));
        }
        if (total != null) {
            nameW = Math.max(nameW, fn.stringWidth("Toplam"));
            valueW = Math.max(valueW, fb.stringWidth(total));
        }
        int contentW = Math.max(fb.stringWidth(title), mark + UIScale.scale(6) + nameW + UIScale.scale(18) + valueW);
        if (onClick != null && clickHint != null) contentW = Math.max(contentW, fs.stringWidth(clickHint));
        int boxW = contentW + pad * 2;
        int boxH = pad + fb.getHeight() + UIScale.scale(4) + rows.size() * rowH
                + (total != null ? rowH + UIScale.scale(4) : 0)
                + (onClick != null && clickHint != null ? fs.getHeight() + UIScale.scale(4) : 0) + pad - UIScale.scale(2);

        float cx = (float) (plot.x + i * slot + slot / 2);
        float bx = cx + (float) slot / 2 + UIScale.scale(8);
        if (bx + boxW > getWidth()) bx = cx - (float) slot / 2 - UIScale.scale(8) - boxW;
        if (bx < 0) bx = Math.max(0, Math.min(getWidth() - boxW, cx - boxW / 2f));
        float by = plot.y + UIScale.scale(2);

        float arc = UIScale.scale(10f);
        Shape box = new RoundRectangle2D.Float(bx, by, boxW, boxH, arc, arc);
        g2.setColor(color("Table.background", Color.WHITE));
        g2.fill(box);
        g2.setColor(color("Component.borderColor", Color.LIGHT_GRAY));
        g2.setStroke(new BasicStroke(UIScale.scale(1f)));
        g2.draw(box);

        Color fg = color("Label.foreground", Color.BLACK);
        float x = bx + pad;
        float y = by + pad + fb.getAscent() - UIScale.scale(1);
        g2.setFont(bold);
        g2.setColor(fg);
        g2.drawString(title, x, y);
        y += fb.getDescent() + UIScale.scale(4);

        for (int r = 0; r < rows.size(); r++) {
            Series s = all.get(r);
            float baseline = y + fn.getAscent();
            g2.setColor(s.color().get());
            if (s == line) {
                g2.setStroke(new BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float ly = baseline - fn.getAscent() / 2f + UIScale.scale(1);
                g2.draw(new Line2D.Float(x, ly, x + mark, ly));
            } else {
                g2.fill(new RoundRectangle2D.Float(x, baseline - fn.getAscent() / 2f - mark / 2f + UIScale.scale(1),
                        mark, mark, UIScale.scale(3), UIScale.scale(3)));
            }
            g2.setFont(base);
            g2.setColor(fg);
            g2.drawString(rows.get(r)[0], x + mark + UIScale.scale(6), baseline);
            g2.setFont(bold);
            g2.drawString(rows.get(r)[1], bx + boxW - pad - fb.stringWidth(rows.get(r)[1]), baseline);
            y += rowH;
        }
        if (total != null) {
            y += UIScale.scale(2);
            g2.setColor(color("Component.borderColor", Color.LIGHT_GRAY));
            g2.draw(new Line2D.Float(x, y, bx + boxW - pad, y));
            y += UIScale.scale(2);
            float baseline = y + fn.getAscent();
            g2.setFont(base);
            g2.setColor(fg);
            g2.drawString("Toplam", x + mark + UIScale.scale(6), baseline);
            g2.setFont(bold);
            g2.drawString(total, bx + boxW - pad - fb.stringWidth(total), baseline);
            y += rowH;
        }
        if (onClick != null && clickHint != null) {
            y += UIScale.scale(4);
            g2.setFont(small);
            g2.setColor(color("Label.disabledForeground", Color.GRAY));
            g2.drawString(clickHint, x, y + fs.getAscent());
        }
    }

    // ── Yardımcılar ─────────────────────────────────────────────────────────

    /** 1, 2, 2.5, 5 × 10ⁿ adımlarından büyüklüğe en yakın olanı. */
    private static double niceStep(double raw) {
        if (raw <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / exp;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * exp;
    }

    /** Eksen için kısa sayı: 950, 12,5 B, 1,2 Mn. */
    public static String compact(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(tr.cabro.servicio.i18n.AppLocale.uiLocale());
        nf.setMaximumFractionDigits(1);
        double a = Math.abs(v);
        if (a >= 1_000_000) return nf.format(v / 1_000_000) + " Mn";
        if (a >= 1_000) return nf.format(v / 1_000) + " B";
        nf.setMaximumFractionDigits(a < 10 && a != Math.rint(a) ? 1 : 0);
        return nf.format(v);
    }

    private static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ── Hazır renkler ───────────────────────────────────────────────────────

    /**
     * Grafik mürekkepleri. Hacim (ciro, adet) nötr metin renginin tonlarıyla çizilir; renk yalnızca
     * anlam taşıyan seride kullanılır (kâr/teslim başarı, iade/zarar tehlike) — uygulamanın
     * "renk yalnızca anlam taşır" kuralı grafikte de geçerli.
     */
    public static final class Ink {
        private Ink() {}

        /** Nötr mürekkep; {@code strength} 0..1 arası koyuluk. */
        public static Supplier<Color> neutral(float strength) {
            return () -> {
                Color fg = color("Label.foreground", Color.DARK_GRAY);
                Color bg = color("Panel.background", Color.WHITE);
                return mix(bg, fg, strength);
            };
        }

        public static Supplier<Color> key(String uiKey) {
            return () -> color(uiKey, Color.GRAY);
        }
    }
}
