package tr.cabro.servicio.application.component.scroll;

import javax.swing.*;

/**
 * Bir kaydırma alanının çubuklarının görünürlüğü (0..1). Belirme hızlı ({@value #IN_MS} ms), kaybolma
 * yumuşak ({@value #OUT_MS} ms); son hareketten {@value #HOLD_MS} ms sonra başlar. Fare çubuğun
 * üstündeyken ya da çubuk sürüklenirken çubuk açık kalır.
 * <p>
 * Zamanlayıcı yalnızca animasyon ve bekleme süresince çalışır; çubuk gizliyken hiçbir iş yapılmaz.
 */
final class OverlayFader {

    static final int IN_MS = 120;
    static final int OUT_MS = 320;
    static final int HOLD_MS = 900;

    private static final int FRAME_MS = 16;
    private static final int IDLE_POLL_MS = 120;

    private final JScrollPane scrollPane;
    private final Timer timer;
    private float alpha;
    private long holdUntil;
    private long lastTick;

    OverlayFader(JScrollPane scrollPane) {
        this.scrollPane = scrollPane;
        this.timer = new Timer(FRAME_MS, e -> tick());
        this.timer.setCoalesce(true);
    }

    float alpha() {
        return alpha;
    }

    /** Çubukları gösterir ve bekleme süresini yeniden başlatır. */
    void reveal() {
        holdUntil = System.currentTimeMillis() + HOLD_MS;
        if (!timer.isRunning()) {
            lastTick = System.currentTimeMillis();
            timer.setDelay(FRAME_MS);
            timer.start();
        }
    }

    void dispose() {
        timer.stop();
        alpha = 0;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long dt = Math.max(1, now - lastTick);
        lastTick = now;

        boolean show = now < holdUntil || engaged();
        float target = show ? 1f : 0f;
        float old = alpha;
        if (alpha < target) alpha = Math.min(1f, alpha + dt / (float) IN_MS);
        else if (alpha > target) alpha = Math.max(0f, alpha - dt / (float) OUT_MS);

        if (alpha != old) repaintBars();
        if (alpha == 0f && !show) {
            timer.stop();
        } else {
            // Tam görünür ve beklemedeyken saniyede birkaç kez bakmak yeter.
            timer.setDelay(alpha == target ? IDLE_POLL_MS : FRAME_MS);
        }
    }

    /** Fare çubuğun üstünde ya da çubuk sürükleniyor mu? */
    private boolean engaged() {
        return engaged(scrollPane.getVerticalScrollBar()) || engaged(scrollPane.getHorizontalScrollBar());
    }

    private static boolean engaged(JScrollBar bar) {
        if (bar == null || !bar.isVisible()) return false;
        if (bar.getValueIsAdjusting()) return true;
        return bar.getUI() instanceof OverlayScrollBarUI ui && ui.isHovered();
    }

    private void repaintBars() {
        repaint(scrollPane.getVerticalScrollBar());
        repaint(scrollPane.getHorizontalScrollBar());
    }

    private void repaint(JScrollBar bar) {
        if (bar != null && bar.isVisible()) scrollPane.repaint(bar.getBounds());
    }
}
