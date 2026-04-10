package tr.cabro.servicio.application.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.ColorFunctions;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;

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