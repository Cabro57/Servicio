package tr.cabro.servicio.application.component;

import raven.modal.Toast;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.FormWorkOrder;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

@Deprecated
public class WorkOrderPopup extends JPopupMenu {

    private final WorkOrder workOrder;

    public WorkOrderPopup(WorkOrder workOrder) {
        this.workOrder = workOrder;
        init();
    }

    private void init() {
        initComponent();

        add(item("Servis Kaydını Aç", () -> {
            FormWorkOrder form = new FormWorkOrder(workOrder);
            FormManager.showForm(form);
        }));

        add(new Separator());

        add(item("Teslim Et", () -> {

            WorkOrderService workOrderService = ServiceManager.getWorkOrderService();
            workOrderService.setDelivered(workOrder.getId()).thenAccept(repair -> {
                Toast.show(FormManager.getFrame(), Toast.Type.SUCCESS, "Başarılı şekilde teslim edildi.");
            }).exceptionally(ex -> {
                Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + ex.getMessage());
                Servicio.getLogger().error("Servis güncelleme hatası", ex);
                return null;
            });

        }));
    }

    private JMenuItem item(String text, Runnable action) {
        JMenuItem mi = new JMenuItem(text);

        if (action != null)
            mi.addActionListener(e -> action.run());

        return mi;
    }

    private void initComponent() {

    }
}
