package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.model.WorkOrder;
import javax.swing.JPanel;

public abstract class ServicePanel extends JPanel {
    protected WorkOrder workOrder;
    protected ServiceEditListener listener;

    public ServicePanel() {
        // Layout ve temel ayarlar
    }

    // Ana form (FormService) veri yüklendiğinde bu metodu çağıracak
    public final void bindService(WorkOrder workOrder) {
        this.workOrder = workOrder;
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