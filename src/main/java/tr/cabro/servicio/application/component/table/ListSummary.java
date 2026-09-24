package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Liste sayfası başlığının altındaki tek satırlık özet: "43 müşteri · 6 kurumsal · 31 borçlu".
 * Dört istatistik kutusunun yerini alır; parçalar " · " ile ayrılır, anlam taşıyan parça kendi
 * anlamsal rengini alır (borç danger, açık alacak warning, tahsil edilmiş success).
 */
public class ListSummary extends JPanel {

    /** Özetin bir parçası: metin + isteğe bağlı FlatLaf stil eki. */
    public static final class Part {
        final String text;
        final String style;

        private Part(String text, String style) {
            this.text = text;
            this.style = style;
        }

        /** Sade (soluk) parça. */
        public static Part of(String text) {
            return new Part(text, null);
        }

        /** Kalın, normal yazı renginde — asıl sayı. */
        public static Part strong(String text) {
            return new Part(text, "font: bold; foreground: $Label.foreground");
        }

        /** Anlamsal renkli ve kalın, ör. {@code meaning("31 borçlu", "Servicio.dangerColor")}. */
        public static Part meaning(String text, String colorKey) {
            return new Part(text, "font: bold; foreground: $" + colorKey);
        }
    }

    public ListSummary() {
        super(new FlowLayout(FlowLayout.LEADING, 0, 0));
        setOpaque(false);
        showLoading();
    }

    public void showLoading() {
        set(Part.of("Yükleniyor…"));
    }

    public void set(Part... parts) {
        set(Arrays.asList(parts));
    }

    public void set(List<Part> parts) {
        removeAll();
        List<Part> visible = new ArrayList<>();
        for (Part p : parts) if (p != null && p.text != null && !p.text.isEmpty()) visible.add(p);
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0) add(label("  ·  ", null));
            add(label(visible.get(i).text, visible.get(i).style));
        }
        revalidate();
        repaint();
    }

    private static JLabel label(String text, String style) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE,
                style != null ? style : "foreground: $Label.disabledForeground");
        return label;
    }
}
