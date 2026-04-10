package tr.cabro.servicio.application.component;

import raven.modal.Toast;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.FormService;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

public class ServicePopup extends JPopupMenu {

    private final Service service;

    public ServicePopup(Service service) {
        this.service = service;
        init();
    }

    private void init() {
        initComponent();

        add(item("Servis Kaydını Aç", () -> {
            FormService form = new FormService(service);
            FormManager.showForm(form);
        }));

        add(new Separator());

        add(item("Teslim Et", () -> {

            RepairService repairService = ServiceManager.getRepairService();
            repairService.setDelivered(service.getId()).thenAccept(repair -> {
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
