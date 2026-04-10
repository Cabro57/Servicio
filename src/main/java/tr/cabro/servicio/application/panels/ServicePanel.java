package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.model.Service;
import javax.swing.JPanel;

public abstract class ServicePanel extends JPanel {
    protected Service service;
    protected ServiceEditListener listener;

    public ServicePanel() {
        // Layout ve temel ayarlar
    }

    // Ana form (FormService) veri yüklendiğinde bu metodu çağıracak
    public final void bindService(Service service) {
        this.service = service;
        onServiceSet(); // Alt panel kendi içini doldursun
    }

    public void setServicePanelListener(ServiceEditListener listener) {
        this.listener = listener;
    }

    protected ServiceEditListener getListener() {
        return listener;
    }

    // Alt panellerin veriyi UI'a yansıtacağı kanca (hook) metot
    protected abstract void onServiceSet();
}