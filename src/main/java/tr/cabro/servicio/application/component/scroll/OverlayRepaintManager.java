package tr.cabro.servicio.application.component.scroll;

import javax.swing.*;
import java.awt.*;

/**
 * Üstüne çubuk binmiş içeriğin yeniden çizimlerini kaydırma alanına yönlendirir.
 * <p>
 * Swing, opak bir bileşeni (tablo, liste) yeniden çizerken yalnızca o bileşeni çizer; üstündeki kardeş
 * bileşen (kaydırma çubuğu) silinir. Çubuk görünürken bu tür bir kirli bölge kaydırma alanına taşınır;
 * kaydırma alanı çocuklarını katman sırasıyla çizdiği için çubuk içeriğin üstünde kalır. Çubuk gizliyken
 * hiçbir şey yönlendirilmez, normal çizim yolu aynen çalışır.
 */
final class OverlayRepaintManager extends RepaintManager {

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        JScrollPane sp = overlayOwner(c);
        if (sp != null) {
            Rectangle r = SwingUtilities.convertRectangle(c, new Rectangle(x, y, w, h), sp);
            super.addDirtyRegion(sp, r.x, r.y, r.width, r.height);
            return;
        }
        super.addDirtyRegion(c, x, y, w, h);
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
