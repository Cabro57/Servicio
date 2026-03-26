package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.application.context.ServiceContext;

import javax.swing.*;

public abstract class ServicePanel extends JPanel {
    protected ServiceContext context;
    protected ServiceEditListener listener;

    public ServicePanel(ServiceContext context) {
        this.context = context;
    }

    public void setServicePanelListener(ServiceEditListener listener) {
        this.listener = listener;
    }

    protected ServiceEditListener getListener() {
        return listener;
    }
}

