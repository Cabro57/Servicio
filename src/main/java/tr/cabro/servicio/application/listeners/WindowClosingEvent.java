package tr.cabro.servicio.application.listeners;

import tr.cabro.servicio.Servicio;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class WindowClosingEvent implements WindowListener {
    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
        Servicio.getInstance().disable();
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }
}
