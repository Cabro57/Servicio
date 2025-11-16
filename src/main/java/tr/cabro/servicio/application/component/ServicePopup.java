package tr.cabro.servicio.application.component;

import raven.modal.Drawer;
import raven.modal.Toast;
import raven.modal.system.FormManager;
import tr.cabro.servicio.forms.FormService;
import tr.cabro.servicio.model.Service;
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

        add(item("Kaydı Aç", () -> {
            FormService form = new FormService(service);
            FormManager.showForm(form);
        }));

        add(item("Teslim Et", () -> {
            if (ServiceManager.getRepairService().setDelivered(service.getId())) {
                Toast.show(FormManager.getFrame(), Toast.Type.SUCCESS, "Başarılı şekilde teslim edildi.");
            }
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
