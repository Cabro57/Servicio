package tr.cabro.servicio.application.component.detail;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detay sayfalarının kimlik şeridi (müşteri detayındaki şeridin genelleştirilmiş hâli):
 * <pre>
 *  [←]  SRV-13  [Hazır] [Acil]                 Toplam    Ödenen   Kalan    [ikincil…] [Birincil]
 *       Geliş 08 Eyl · 16 gündür serviste     4.200 ₺   2.000 ₺  2.200 ₺
 *       ⚠ uyarı satırı (isteğe bağlı)
 * </pre>
 * Kayıt kim/ne, parası ne durumda ve sık işlemler her detay sayfasında aynı yerde durur.
 * Kalın büyük sayılar ({@link #addStat}) yalnızca anlamlı olduğunda renk alır.
 */
public class DetailHeader extends JPanel {

    private final JLabel title = new JLabel();
    private final JPanel badges = new JPanel(new MigLayout("insets 0, gap 8, hidemode 3", "", "[center]"));
    private final JLabel meta = new JLabel(" ");
    private final JPanel stats = new JPanel(new MigLayout("insets 0, gap 22, hidemode 3", "", "[]"));
    private final JPanel actions = new JPanel(new MigLayout("insets 0, gap 8, hidemode 3", "", "[center]"));
    private final JLabel warning = new JLabel();
    private final Map<String, JLabel> statValues = new LinkedHashMap<>();
    private final Map<String, JPanel> statBoxes = new LinkedHashMap<>();

    public DetailHeader() {
        this(FormManager::undo);
    }

    public DetailHeader(Runnable onBack) {
        setLayout(new MigLayout("insets 14 16 14 20, fillx, gapx 14, hidemode 3", "[][grow, fill][][]", "[center][]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        JButton back = new JButton(new Ikon("icons/arrow-left.svg", 18, "Label.foreground"));
        back.setToolTipText("Geri");
        back.getAccessibleContext().setAccessibleName("Geri");
        back.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        back.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 8,8,8,8");
        back.addActionListener(e -> onBack.run());
        add(back, "aligny center");

        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +6");
        meta.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        badges.setOpaque(false);

        JPanel identity = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]4[]"));
        identity.setOpaque(false);
        JPanel nameRow = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[center]"));
        nameRow.setOpaque(false);
        // Başlık (kayıt no, ad) hiç kesilmez; dar ekranda işlemler alta iner (bkz. doLayout).
        nameRow.add(title, "wmin pref");
        nameRow.add(badges);
        identity.add(nameRow, "wmin 0");
        identity.add(meta, "wmin 0");
        add(identity, "wmin 0");

        stats.setOpaque(false);
        add(stats, "gapright 10, wmin pref");
        actions.setOpaque(false);
        add(actions, "wmin pref, wrap");

        warning.setIcon(new Ikon("icons/triangle-alert.svg", 16, "Servicio.dangerColor"));
        warning.setIconTextGap(8);
        warning.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.dangerColor");
        warning.setVisible(false);
        add(warning, "skip 1, span 3, wmin 0, gaptop 6");
    }

    private boolean compact;

    /**
     * Dar pencerede (1366 + açık menü) başlık, sayılar ve işlemler tek satıra sığmazsa işlemler
     * sağa yaslı ikinci satıra iner; kimlik kolonu ezilip başlık "SR…" olmaz.
     */
    @Override
    public void doLayout() {
        Insets in = getInsets();
        int identityNeed = Math.max(title.getPreferredSize().width + badges.getPreferredSize().width + 10,
                meta.getPreferredSize().width);
        int need = 60 + identityNeed + stats.getPreferredSize().width + 24 + actions.getPreferredSize().width + 42
                + in.left + in.right;
        boolean shouldCompact = getWidth() > 0 && getWidth() < need;
        if (shouldCompact != compact) {
            compact = shouldCompact;
            MigLayout layout = (MigLayout) getLayout();
            layout.setComponentConstraints(actions, compact ? "newline, skip 1, span 3, al right, gaptop 8" : "wmin pref, wrap");
            layout.setComponentConstraints(warning, compact ? "newline, skip 1, span 3, wmin 0, gaptop 6" : "skip 1, span 3, wmin 0, gaptop 6");
            invalidate();
            SwingUtilities.invokeLater(() -> { revalidate(); repaint(); });
        }
        super.doLayout();
    }

    public void setTitle(String text) {
        title.setText(text);
        title.setToolTipText(text);
    }

    public void addBadge(JComponent badge) {
        badges.add(badge);
    }

    /** Başlık altındaki soluk satır; boş parçalar yazılmaz, satır " · " ile birleşir. */
    public void setMeta(String... parts) {
        List<String> visible = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isBlank()) visible.add(p.trim());
        String text = visible.isEmpty() ? " " : String.join("   ·   ", visible);
        meta.setText(text);
        meta.setToolTipText(visible.isEmpty() ? null : text);
    }

    /** Sağdaki büyük sayı kutusu (başlık + değer). Anahtar {@link #setStat} ile güncellenir. */
    public void addStat(String key, String caption) {
        JLabel cap = new JLabel(caption);
        cap.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        JLabel value = new JLabel("—");
        value.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        JPanel box = new JPanel(new MigLayout("insets 0, gap 0, wrap", "[right]", "[]2[]"));
        box.setOpaque(false);
        box.add(cap);
        box.add(value);
        stats.add(box);
        statValues.put(key, value);
        statBoxes.put(key, box);
    }

    /**
     * @param colorKey tema renk anahtarı (ör. {@code Servicio.warningColor}); {@code null} normal yazı rengi.
     */
    public void setStat(String key, String text, String colorKey) {
        JLabel value = statValues.get(key);
        if (value == null) return;
        value.setText(text);
        value.putClientProperty(FlatClientProperties.STYLE, "font: bold +5; foreground: $"
                + (colorKey != null ? colorKey : "Label.foreground"));
        // Metin/yazı tipi değişince kutu yeniden ölçülsün (yoksa uzayan değer kırpılıyor).
        value.revalidate();
    }

    public void setStatVisible(String key, boolean visible) {
        JPanel box = statBoxes.get(key);
        if (box != null) box.setVisible(visible);
    }

    public void setStatTooltip(String key, String tip) {
        JPanel box = statBoxes.get(key);
        if (box != null) box.setToolTipText(tip);
    }

    /** İkincil işlem: çerçeveli, ikonlu. */
    public JButton addAction(String text, String iconPath, Runnable action) {
        JButton b = DetailKit.secondaryButton(text, iconPath, action);
        actions.add(b);
        return b;
    }

    /** İkincil işlem olarak hazır bir bileşen (ör. durum seçici). */
    public void addActionComponent(JComponent component) {
        actions.add(component);
    }

    /** Sayfanın tek vurgu dolgulu düğmesi; en sağa konur. */
    public JButton setPrimary(String text, String iconPath, Runnable action) {
        JButton b = DetailKit.primaryButton(text, iconPath, action);
        actions.add(b);
        return b;
    }

    /** Birincil düğme + genel kısayolu (ör. Alt+T); kısayol liste sayfalarındaki gibi tuş ipucuyla gösterilir. */
    public JButton setPrimary(String text, String iconPath, Runnable action, tr.cabro.servicio.application.system.QuickAction shortcut) {
        JButton b = DetailKit.primaryButton(text, iconPath, action, shortcut);
        actions.add(b);
        return b;
    }

    /** Tam genişlik kırmızı uyarı satırı; {@code null} gizler. */
    public void setWarning(String text, String tooltip) {
        warning.setText(text != null ? text : "");
        warning.setToolTipText(tooltip);
        warning.setVisible(text != null && !text.isBlank());
    }
}
