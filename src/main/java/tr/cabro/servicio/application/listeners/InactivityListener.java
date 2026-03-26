package tr.cabro.servicio.application.listeners;

import lombok.Getter;
import tr.cabro.servicio.Servicio;

import javax.swing.Timer;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class InactivityListener implements AWTEventListener, ActionListener {

    private final Timer timer;
    @Getter private final int timeoutMilliseconds;

    public InactivityListener() {
        int timeoutMin = Servicio.getSettings().getPin().getTimeout();
        timeoutMilliseconds = timeoutMin * 60 * 1000;
        timer = new Timer(timeoutMilliseconds, this);
        timer.setRepeats(false);
    }

    public void start() {
        timer.start();
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    public void stop() {
        timer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        timer.restart();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        stop();
    }
}