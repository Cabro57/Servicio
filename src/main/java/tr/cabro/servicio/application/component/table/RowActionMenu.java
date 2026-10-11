package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Liste satırının sağ tık menüsü. Satır üstündeki işlem şeridinin aynısını, kayıt kimliğiyle
 * birlikte sunar: tezgâhta sürekli bölünen kullanıcı, menüyü açınca hangi kayıt üzerinde işlem
 * yapacağını (ör. "SRV-681 · Canan Köse") başlıkta görür.
 * <p>
 * Kabuk (zemin, çizgi, köşe, nötr seçim tonu) tüm menülerle ortaktır ve temada tanımlıdır
 * ({@code themes/FlatLaf.properties}, "Acilir menuler"). Burada yalnızca bu menüye özgü olanlar
 * var: kayıt başlığı, şeritle aynı ikon davranışı ve ayraçla ayrılan tehlike rengindeki işlemler.
 */
final class RowActionMenu {

    private static final float ICON_SCALE = 0.8f;
    private static final float ICON_REST_ALPHA = 0.6f;

    private RowActionMenu() {
    }

    /**
     * Menüyü kurar. {@code title}/{@code subtitle} boşsa başlık bölümü çizilmez.
     *
     * @param row işlemlere verilecek görünüm (view) satır indeksi
     */
    static JPopupMenu build(List<ListTable.RowAction> actions, int row, String title, String subtitle) {
        JPopupMenu menu = new JPopupMenu();

        if (!isBlank(title)) {
            menu.add(header(title, subtitle));
            menu.add(separator());
        }

        boolean any = false;
        for (ListTable.RowAction a : actions) {
            boolean danger = "Servicio.dangerColor".equals(a.colorKey);
            if (danger && any) menu.add(separator());
            menu.add(item(a, row, danger));
            any = true;
        }
        return menu;
    }

    /** Kayıt kimliği: kalın birincil metin, soluk ikincil metin; seçilemez, yalnızca bilgi. */
    private static JComponent header(String title, String subtitle) {
        JPanel p = new JPanel(new MigLayout("insets 6 10 7 10, gap 0 1, fillx, wrap", "[grow, fill]"));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        p.add(t);
        if (!isBlank(subtitle)) {
            JLabel s = new JLabel(subtitle);
            s.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            p.add(s);
        }
        return p;
    }

    private static JComponent separator() {
        return new JPopupMenu.Separator();
    }

    /**
     * Şeritteki düğmeyle aynı ikon dili: dinlenmede soluk nötr ikon, üstüne gelince tam renk
     * (sil için tehlike rengi). Tehlikeli satırın metni de baştan tehlike rengindedir.
     */
    private static JMenuItem item(ListTable.RowAction a, int row, boolean danger) {
        String restKey = danger ? "Servicio.dangerColor" : "Label.foreground";
        float restAlpha = danger ? 0.85f : ICON_REST_ALPHA;
        Icon rest = new Ikon(a.iconPath, ICON_SCALE, restKey, restAlpha);
        Icon armed = new Ikon(a.iconPath, ICON_SCALE, a.hoverKey(), 1f);

        JMenuItem item = new JMenuItem(a.tooltip, rest);
        item.putClientProperty(FlatClientProperties.STYLE, danger
                ? "minimumWidth: 220; foreground: $Servicio.dangerColor;"
                        + " selectionBackground: fade($Servicio.dangerColor,12%); selectionForeground: $Servicio.dangerColor"
                : "minimumWidth: 220");
        item.getModel().addChangeListener(e -> item.setIcon(item.isArmed() ? armed : rest));
        item.addActionListener(e -> a.onRow.accept(row));
        return item;
    }

    /** Klavyeyle açılan menüde ilk işlemi hazır seçer; ok tuşları ve Enter hemen çalışır. */
    static void armFirst(JPopupMenu menu) {
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item && item.isEnabled()) {
                MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menu, item});
                return;
            }
        }
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
