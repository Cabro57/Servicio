package tr.cabro.servicio.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.ColorFunctions;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;

@Setter
@Getter
public class SVGIconUIColor extends FlatSVGIcon {

    private String colorKey;
    private float alpha;

    public SVGIconUIColor(String name, float scale, String colorKey) {
        this(name, scale, colorKey, 1f);
    }

    public SVGIconUIColor(String name, float scale, String colorKey, float alpha) {
        super(name, scale);
        this.colorKey = colorKey;
        this.alpha = alpha;
        setColorFilter(new ColorFilter(color -> {
            Color uiColor = UIManager.getColor(getColorKey());
            if (uiColor != null) {
                return getAlpha() == 1 ? uiColor : ColorFunctions.fade(uiColor, getAlpha());
            }
            return color;
        }));
    }

}
