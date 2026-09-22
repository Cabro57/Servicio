package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.model.contract.Visualizable;

import javax.swing.*;
import java.awt.*;

public class Badge extends JLabel {

    private boolean showIcon = false;
    private boolean showBorder = false;

    private Visualizable visualizable;
    private Color customBgColor;
    private Color customFgColor;
    private String customIconPath;

    // --- CONSTRUCTORS ---
    public Badge(Visualizable visualizable) {
        // Metin yalnızca setVisualizable'da atanıyordu; bu yapıcıyla kurulan ve sonra
        // setVisualizable çağrılmayan rozet yazısız, sadece ikonla çiziliyordu.
        super(visualizable != null ? visualizable.getDisplayName() : "");
        this.visualizable = visualizable;
        updateStyle();
    }

    public Badge(String text, Color bgColor, Color fgColor) {
        super(text);
        this.customBgColor = bgColor;
        this.customFgColor = fgColor;
        updateStyle();
    }

    // --- ZİNCİRLEME (BUILDER) AYAR METOTLARI ---

    public Badge setShowIcon(boolean showIcon) {
        this.showIcon = showIcon;
        updateStyle();
        return this;
    }

    public Badge setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
        updateStyle();
        return this;
    }

    // --- VERİ GÜNCELLEME METOTLARI ---

    public void setVisualizable(Visualizable visualizable) {
        this.visualizable = visualizable;
        this.setText(visualizable.getDisplayName());
        updateStyle();
    }

    public void setCustom(String text, Color bgColor, Color fgColor, String iconPath) {
        this.visualizable = null;
        this.setText(text);
        this.customBgColor = bgColor;
        this.customFgColor = fgColor;
        this.customIconPath = iconPath;
        updateStyle();
    }

    /**
     * Tema değişiminde Swing tüm bileşenlerde {@code updateUI()} çağırır; rozet renkleri
     * temaya bağlı olduğu için stili burada yeniden uygularız, aksi halde rozet eski
     * temanın renkleriyle kalır.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        updateStyle();
    }

    /**
     * Görünümü uygular.
     * <p>
     * {@code bg}/{@code fg} burada {@code null} olabilir ve bu normaldir: {@link #updateUI()}
     * Swing tarafından {@code JLabel} yapıcısının içinden, yani bu sınıfın alanları
     * ({@code visualizable}, {@code customBgColor}...) atanmadan ÖNCE çağrılır. O ilk
     * çağrıda renk bilgisi henüz yoktur; yapıcı alanları doldurduktan sonra
     * {@code updateStyle()} yeniden çalışır. {@code setForeground(null)}/
     * {@code setBackground(null)} geçerlidir (renk ebeveynden/LaF'tan gelir), bu yüzden
     * erken çağrı zararsızdır — yeter ki renk üzerinde metot çağrılmasın.
     */
    private void updateStyle() {
        Color bg = visualizable != null ? BadgePalette.background(visualizable.getBadgeColor()) : customBgColor;
        Color fg = visualizable != null ? BadgePalette.foreground(visualizable.getBadgeColor()) : customFgColor;
        String iconPath = visualizable != null ? visualizable.getIconPath() : customIconPath;

        // 1. İkon Ayarı (Renk filtresi ile birlikte)
        if (showIcon && iconPath != null && !iconPath.isEmpty()) {
            FlatSVGIcon icon = new FlatSVGIcon(iconPath, 0.75f);
            if (fg != null) {
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> fg)); // İkonu yazı rengine boya
            }
            setIcon(icon);
            setIconTextGap(6);
        } else {
            setIcon(null);
        }

        setForeground(fg);
        setBackground(bg);
        putClientProperty(FlatClientProperties.STYLE, ""
                + "arc: 999;"
                + "border: 2,10,2,10;"
                + "font: bold +0");
    }
}