package tr.cabro.servicio.application.component.scroll;

import tr.cabro.servicio.util.EdtStallMonitor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Üstüne çubuk binmiş içeriğin yeniden çizimlerini kaydırma alanına yönlendirir.
 * <p>
 * Swing, opak bir bileşeni (tablo, liste) yeniden çizerken yalnızca o bileşeni çizer; üstündeki kardeş
 * bileşen (kaydırma çubuğu) silinir. Çubuk görünürken bu tür bir kirli bölge kaydırma alanına taşınır;
 * kaydırma alanı çocuklarını katman sırasıyla çizdiği için çubuk içeriğin üstünde kalır. Çubuk gizliyken
 * hiçbir şey yönlendirilmez, normal çizim yolu aynen çalışır.
 * <p>
 * Uygulamanın tek repaint yöneticisi olduğu için çizim isteklerini de kaydeder: bir çizim turu
 * {@link EdtStallMonitor} eşiğini aşarsa, o turda hangi bileşenin ne kadar alanı kimin isteğiyle
 * kirlettiği rapora eklenir.
 */
final class OverlayRepaintManager extends RepaintManager {

    private static final int MAX_DIRTY_RECORDS = 400;

    /** Bir sonraki çizim turuna kadar biriken istekler; EDT dışından da eklenebildiği için kilitli. */
    private final List<DirtyRecord> pending = new ArrayList<>();

    private record DirtyRecord(String component, int w, int h, String caller) {}

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        if (EdtStallMonitor.isInstalled() && w > 0 && h > 0) record(c, w, h);
        JScrollPane sp = overlayOwner(c);
        if (sp != null) {
            Rectangle r = SwingUtilities.convertRectangle(c, new Rectangle(x, y, w, h), sp);
            super.addDirtyRegion(sp, r.x, r.y, r.width, r.height);
            return;
        }
        super.addDirtyRegion(c, x, y, w, h);
    }

    @Override
    public void paintDirtyRegions() {
        List<DirtyRecord> records;
        synchronized (pending) {
            records = pending.isEmpty() ? List.of() : new ArrayList<>(pending);
            pending.clear();
        }
        if (!records.isEmpty()) EdtStallMonitor.annotate(() -> summarize(records));
        super.paintDirtyRegions();
    }

    private void record(JComponent c, int w, int h) {
        synchronized (pending) {
            if (pending.size() >= MAX_DIRTY_RECORDS) return;
        }
        DirtyRecord r = new DirtyRecord(c.getClass().getName(), w, h, caller());
        synchronized (pending) {
            pending.add(r);
        }
    }

    /** repaint'i isteyen ilk çerçeve: Swing/AWT'nin kendi repaint zinciri atlanır. */
    private static String caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> {
                    String cls = f.getClassName();
                    return !cls.startsWith("javax.swing.RepaintManager")
                            && !cls.equals("javax.swing.JComponent")
                            && !cls.equals("java.awt.Component")
                            && !cls.equals(OverlayRepaintManager.class.getName());
                })
                .limit(3)
                .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                .reduce((a, b) -> a + " < " + b)
                .orElse("?"));
    }

    /** Aynı bileşen + istek yeri birleştirilir, en büyük alan önce yazılır. */
    private static String summarize(List<DirtyRecord> records) {
        Map<String, long[]> groups = new LinkedHashMap<>(); // {adet, toplam alan, en büyük w, en büyük h}
        for (DirtyRecord r : records) {
            long[] g = groups.computeIfAbsent(r.component() + "  <=  " + r.caller(), k -> new long[4]);
            g[0]++;
            g[1] += (long) r.w() * r.h();
            if ((long) r.w() * r.h() > g[2] * g[3]) {
                g[2] = r.w();
                g[3] = r.h();
            }
        }
        StringBuilder sb = new StringBuilder("çizim istekleri (" + records.size() + "):");
        groups.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(6)
                .forEach(e -> sb.append("\n    x").append(e.getValue()[0])
                        .append(" en büyük ").append(e.getValue()[2]).append('x').append(e.getValue()[3])
                        .append("  ").append(e.getKey()));
        return sb.toString();
    }

    /** {@code c} çubuğu şu an görünen bir kaydırma alanının görünüm ya da başlık alanındaysa o alan. */
    private static JScrollPane overlayOwner(JComponent c) {
        Component child = c;
        for (Container p = c.getParent(); p != null; child = p, p = p.getParent()) {
            if (p instanceof JScrollPane sp) {
                if (child != sp.getViewport() && child != sp.getColumnHeader() && child != sp.getRowHeader()) return null;
                OverlayFader fader = OverlayScrollPaneUI.fader(sp);
                return fader != null && fader.alpha() > 0f ? sp : null;
            }
            if (p instanceof Window) return null;
        }
        return null;
    }
}
