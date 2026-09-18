package tr.cabro.servicio.application.utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.ColorFunctions;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;

/**
 * Tema-duyarlı SVG ikon. {@code colorKey} ile verilen UIManager anahtarını okuyup ikonu
 * o renge boyar, böylece tema değiştiğinde ikon da döner.
 * <p>
 * <b>Örnek paylaşmayın.</b> Bu sınıf değiştirilebilir: {@code setColorKey}/{@code setAlpha}
 * ve miras alınan {@code setColorFilter} nesneyi yerinde değiştirir (FlatLaf'ta
 * {@code setColorFilter} kopya üretmez, {@code this} döndürür). Aynı örneği iki bileşene
 * vermek, birinin rengini diğerinin ezmesi demektir. Ayrıştırma maliyetini düşünmeyin:
 * {@code FlatSVGIcon} ayrıştırılmış SVG belgelerini kendi statik cache'inde tutar, her
 * çağrıda yeni {@code Ikon} kurmak ucuzdur (bkz. {@code IconManager}).
 */
@Getter
@Setter
public class Ikon extends FlatSVGIcon {

    private String colorKey;
    private float alpha;

    public Ikon(String name) {
        this(name, 1f, "Label.foreground");
    }

    public Ikon(String name, int size) {
        this(name, size, "Label.foreground");
    }

    public Ikon(String name, int size, String colorKey) {
        super(name, size, size);
        init(colorKey, 1f);
    }

    public Ikon(String name, float scale) {
        this(name, scale, "Label.foreground", 1f);
    }

    public Ikon(String name, float scale, String colorKey) {
        this(name, scale, colorKey, 1f);
    }

    public Ikon(String name, float scale, String colorKey, float alpha) {
        super(name, scale);
        init(colorKey, alpha);
    }

    private void init(String colorKey, float alpha) {
        this.colorKey = colorKey;
        this.alpha = alpha;

        setColorFilter(new ColorFilter(color -> {
            Color uiColor = UIManager.getColor(getColorKey());
            if (uiColor != null) {
                return getAlpha() == 1f ? uiColor : ColorFunctions.fade(uiColor, getAlpha());
            }
            return color;
        }));
    }
}