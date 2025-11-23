package tr.cabro.servicio.component.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

public class UIHelper {

    public static void fixNestedScroll(JScrollPane innerScrollPane) {
        // 1. Varsayılan kaydırmayı KAPATIYORUZ.
        // Böylece hem iç hem dış aynı anda kaymayacak.
        innerScrollPane.setWheelScrollingEnabled(false);

        innerScrollPane.addMouseWheelListener(e -> {
            JScrollPane parent = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, innerScrollPane);
            JScrollBar bar = innerScrollPane.getVerticalScrollBar();

            // A) İçerik tamamen sığıyor mu? (Scrollbar gizliyse sığıyordur)
            boolean contentFits = !bar.isVisible();

            // B) Focus (Odak) kontrolü:
            // Kullanıcı ne ScrollPane'e ne de içindeki (örn: JTextArea) bileşene tıklamış mı?
            Component view = innerScrollPane.getViewport().getView();
            boolean hasFocus = innerScrollPane.hasFocus() || (view != null && view.hasFocus());

            // KARAR ANI:
            // Eğer (Odaklanılmamışsa) VEYA (İçerik zaten sığıyorsa) -> DIŞARIYI KAYDIR
            if (!hasFocus || contentFits) {
                if (parent != null) {
                    // Olayı klonlayıp üst panele gönderiyoruz
                    MouseWheelEvent newEvent = (MouseWheelEvent) SwingUtilities.convertMouseEvent(innerScrollPane, e, parent);
                    parent.dispatchEvent(newEvent);
                }
            }
            // AKSİ HALDE (Odaklanılmış VE içerik taşıyor) -> İÇERİYİ KAYDIR
            else {
                // Varsayılanı kapattığımız için manuel hesaplayıp kaydırıyoruz
                int rotation = e.getWheelRotation();
                if (rotation == 0) return;

                // Kaydırma hızı (scroll amount * birim artış)
                int scrollAmount = e.getScrollAmount() * bar.getUnitIncrement();
                int newValue = bar.getValue() + (rotation * scrollAmount);

                // Sınırların dışına çıkmasını engelle
                if (newValue < 0) newValue = 0;
                int max = bar.getMaximum() - bar.getVisibleAmount();
                if (newValue > max) newValue = max;

                bar.setValue(newValue);
            }
        });
    }
}