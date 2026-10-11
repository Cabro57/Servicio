package tr.cabro.servicio.application.component.scroll;

import com.formdev.flatlaf.ui.FlatScrollPaneUI;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;

/**
 * Tüm kaydırma alanlarının arayüzü (temada {@code ScrollPaneUI} olarak kayıtlı). Kaydırma çubukları
 * içerikten yer çalmaz: görünüm alanı tüm genişliği kaplar, çubuklar içeriğin ÜSTÜNE biner ve yalnızca
 * kullanılırken görünür. Kaydırınca, çubuğun kenarına yaklaşınca ya da çubuk tutulurken belirir;
 * {@link OverlayFader#HOLD_MS} sonra kendiliğinden kaybolur.
 * <p>
 * Üst üste binen bileşenlerde Swing'in bir sorunu var: tablo tek başına yeniden çizilirse (ör. satır
 * hover'ı) üstündeki çubuğu siler. {@link OverlayRepaintManager} bu çizimleri çubuk görünürken kaydırma
 * alanının kendisine yönlendirir; kaydırma alanı çocuklarını doğru sırayla (içerik, sonra çubuk) çizer.
 * Aynı sebeple görünüm alanı piksel kopyalayarak (blit) kaydırmaz; kopya, çubuğun izini de taşırdı.
 */
public class OverlayScrollPaneUI extends FlatScrollPaneUI {

    static final String FADER_KEY = "Servicio.overlayFader";

    private static boolean globalsInstalled;

    private ChangeListener viewportListener;
    private Point lastViewPosition;

    public static ComponentUI createUI(JComponent c) {
        installGlobals();
        return new OverlayScrollPaneUI();
    }

    /** Uygulama başına bir kez: yönlendiren repaint yöneticisi ve kenara yaklaşmayı izleyen dinleyici. */
    private static void installGlobals() {
        if (globalsInstalled) return;
        globalsInstalled = true;
        RepaintManager.setCurrentManager(new OverlayRepaintManager());
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_DRAGGED) {
                revealNearEdge((MouseEvent) e);
            }
        }, AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        JScrollPane sp = (JScrollPane) c;
        sp.putClientProperty(FADER_KEY, new OverlayFader(sp));
        sp.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, null);
        viewportListener = e -> onViewportChanged();
        attachViewport(sp.getViewport());
    }

    @Override
    public void uninstallUI(JComponent c) {
        JScrollPane sp = (JScrollPane) c;
        detachViewport(sp.getViewport());
        OverlayFader fader = fader(sp);
        if (fader != null) fader.dispose();
        sp.putClientProperty(FADER_KEY, null);
        super.uninstallUI(c);
    }

    @Override
    protected void updateViewport(PropertyChangeEvent e) {
        super.updateViewport(e);
        if (e.getOldValue() instanceof JViewport old) detachViewport(old);
        if (e.getNewValue() instanceof JViewport vp) attachViewport(vp);
    }

    private void attachViewport(JViewport vp) {
        if (vp == null) return;
        vp.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        vp.addChangeListener(viewportListener);
        lastViewPosition = vp.getViewPosition();
    }

    private void detachViewport(JViewport vp) {
        if (vp != null && viewportListener != null) vp.removeChangeListener(viewportListener);
    }

    /** Yalnızca konum değişince belirir: veri yenilenip boyut değiştiğinde çubuk yanıp sönmesin. */
    private void onViewportChanged() {
        JViewport vp = scrollpane.getViewport();
        if (vp == null) return;
        Point p = vp.getViewPosition();
        if (lastViewPosition != null && !p.equals(lastViewPosition)) {
            OverlayFader fader = fader(scrollpane);
            if (fader != null) fader.reveal();
        }
        lastViewPosition = p;
    }

    @Override
    protected FlatScrollPaneLayout createScrollPaneLayout() {
        return new OverlayLayout();
    }

    static OverlayFader fader(Component sp) {
        return sp instanceof JScrollPane s && s.getClientProperty(FADER_KEY) instanceof OverlayFader f ? f : null;
    }

    /** Fare bir kaydırma çubuğunun kenarına yaklaştıysa (gizliyken bile) çubuğu gösterir. */
    private static void revealNearEdge(MouseEvent e) {
        Component src = e.getComponent();
        JScrollPane sp = src instanceof JScrollPane s ? s
                : (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, src);
        OverlayFader fader = fader(sp);
        if (fader == null) return;
        Point p = SwingUtilities.convertPoint(src, e.getPoint(), sp);
        int reach = UIScale.scale(12);
        if (near(sp.getVerticalScrollBar(), p, reach) || near(sp.getHorizontalScrollBar(), p, reach)) fader.reveal();
    }

    private static boolean near(JScrollBar bar, Point p, int reach) {
        if (bar == null || !bar.isVisible() || bar.getWidth() <= 0) return false;
        Rectangle r = bar.getBounds();
        r.grow(reach, reach);
        return r.contains(p);
    }

    /**
     * FlatLaf yerleşimini çalıştırır, sonra görünüm alanını ve başlıkları çubukların altına uzatır.
     * Çubuklar en üstteki katmana alınır; sağ alt köşe kutusu çizilmez.
     */
    private static final class OverlayLayout extends FlatScrollPaneLayout {
        @Override
        public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            JScrollPane sp = (JScrollPane) parent;
            Rectangle vp = viewport != null ? viewport.getBounds() : null;
            if (vp == null) return;
            // Swing çubuğu görünüm alanı çerçevesinin (viewportBorder) dışına koyar. Alanı çubuğun yerine
            // uzatırken bu çerçeve korunmalı; yoksa combo listesinin sağ iç boşluğu yutuluyor, seçili
            // satır sağda kenara yapışıp solda boşluklu kalıyordu.
            javax.swing.border.Border vpb = sp.getViewportBorder();
            Insets vpbi = vpb != null ? vpb.getBorderInsets(sp) : new Insets(0, 0, 0, 0);

            if (vsb != null && vsb.isVisible() && vsb.getWidth() > 0) {
                Rectangle b = vsb.getBounds();
                if (b.x >= vp.x + vp.width) {
                    vp.width = b.x + b.width - vp.x - vpbi.right;
                } else if (b.x + b.width <= vp.x) {
                    vp.width += vp.x - b.x - vpbi.left;
                    vp.x = b.x + vpbi.left;
                }
                if (colHead != null) {
                    Rectangle h = colHead.getBounds();
                    colHead.setBounds(vp.x, h.y, vp.width, h.height);
                }
                toTop(sp, vsb);
            }
            if (hsb != null && hsb.isVisible() && hsb.getHeight() > 0) {
                Rectangle b = hsb.getBounds();
                if (b.y >= vp.y + vp.height) vp.height = b.y + b.height - vp.y - vpbi.bottom;
                if (rowHead != null) {
                    Rectangle h = rowHead.getBounds();
                    rowHead.setBounds(h.x, vp.y, h.width, vp.height);
                }
                toTop(sp, hsb);
            }
            viewport.setBounds(vp);
            if (lowerRight != null) lowerRight.setBounds(0, 0, 0, 0);
        }

        private static void toTop(JScrollPane sp, Component bar) {
            if (sp.getComponentZOrder(bar) > 0) sp.setComponentZOrder(bar, 0);
        }
    }
}
