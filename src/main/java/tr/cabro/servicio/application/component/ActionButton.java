package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class ActionButton extends JButton {

    private boolean mousePress;
    private boolean mouseHover; // Hover durumunu garantilemek için yeni değişken eklendi
    private Color hoverColor;

    public ActionButton(FlatSVGIcon icon, Color hoverColor) {
        super(icon);
        this.hoverColor = hoverColor;
        setContentAreaFilled(false);
        setBorder(new EmptyBorder(3, 3, 3, 3));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent me) {
                mousePress = true;
                updateIconColor();
            }

            @Override
            public void mouseReleased(MouseEvent me) {
                mousePress = false;
                updateIconColor();
            }

            @Override
            public void mouseEntered(MouseEvent me) {
                mouseHover = true; // Fare ikonun üzerine geldi
                updateIconColor();
            }

            @Override
            public void mouseExited(MouseEvent me) {
                mouseHover = false; // Fare ikonun üzerinden çıktı
                updateIconColor();
            }
        });
    }

    private void updateIconColor() {
        if (getIcon() instanceof FlatSVGIcon) {
            FlatSVGIcon svgIcon = (FlatSVGIcon) getIcon();

            // Swing'in modeline güvenmek yerine kendi mouseHover değişkenimizi kullanıyoruz
            if (mousePress || mouseHover) {
                svgIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> hoverColor));
            } else {
                svgIcon.setColorFilter(null);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics grphcs) {
        Graphics2D g2 = (Graphics2D) grphcs.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;

        if (mousePress) {
            g2.setColor(UIManager.getColor("Button.pressedBackground"));
            g2.fillRect(x, y, size, size);
        } else if (mouseHover) { // Arka plan çizimini de kendi değişkenimize bağladık
            g2.setColor(UIManager.getColor("Button.hoverBackground"));
            g2.fillRect(x, y, size, size);
        }

        g2.dispose();
        super.paintComponent(grphcs);
    }
}