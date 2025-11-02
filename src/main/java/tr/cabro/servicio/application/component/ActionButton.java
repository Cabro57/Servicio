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

    public ActionButton(FlatSVGIcon icon) {
        super(icon);
        setContentAreaFilled(false);
        setBorder(new EmptyBorder(3, 3, 3, 3));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent me) {
                mousePress = true;
            }

            @Override
            public void mouseReleased(MouseEvent me) {
                mousePress = false;
            }
        });
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
        Color fillColor;

        if (mousePress) {
            fillColor = UIManager.getColor("Button.pressedBackground");
        } else if (getModel().isRollover()) {
            fillColor = UIManager.getColor("Button.hoverBackground");
        } else {
            fillColor = UIManager.getColor("Button.background");
        }

        g2.setColor(fillColor);
        g2.fillRect(x, y, size, size);
        g2.dispose();
        super.paintComponent(grphcs);
    }
}
