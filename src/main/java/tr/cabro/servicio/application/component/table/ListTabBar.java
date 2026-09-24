package tr.cabro.servicio.application.component.table;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Liste kartının üstündeki sekme çubuğu: sekmeler solda, ek kontroller (filtre temizle, açılır kutu…)
 * sağda, altta tek bir hairline tabloyu ayırır. Liste sayfaları, kasa raporu ve detay
 * sayfalarındaki liste bölümleri aynı çubuğu kullanır.
 */
public class ListTabBar extends JPanel {

    public ListTabBar() {
        super(new MigLayout("insets 0 6 0 6, gap 8, hidemode 3", "[]push[][][]", "[center]"));
        setOpaque(false);
    }

    public ListTabBar(ViewTabs tabs) {
        this();
        add(tabs, "wmin 0");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Color line = UIManager.getColor("Component.borderColor");
        if (line == null) return;
        g.setColor(line);
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
    }
}
