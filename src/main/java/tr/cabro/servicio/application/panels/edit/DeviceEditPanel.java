package tr.cabro.servicio.application.panels.edit;

import lombok.NonNull;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.panels.DeviceFormPanel;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;

/**
 * Cihaz ekleme/düzenleme formu. Alanlar ve IMEI araması servis kaydıyla aynı bileşenden
 * ({@link DeviceFormPanel}) gelir; böylece iki yerde farklı cihaz formu oluşmaz.
 * <ul>
 *   <li>Yeni kayıtta IMEI sistemde bulunursa form o cihazla dolar ve kayıt o cihazı günceller
 *       (aynı cihaz iki kez eklenmez).</li>
 *   <li>Düzenlemede bulunan başka bir kayıt formu ezmez, yalnızca uyarı verilir.</li>
 * </ul>
 * Modalı açmak için {@link #open(Component, Device, Runnable)} kullanılır.
 * <p>
 * DİKKAT: {@link #initComponent()} üst sınıfın kurucusundan çağrılır — alanlara başlangıç değeri verilmemeli.
 */
public class DeviceEditPanel extends AbstractEditPanel<Device> {

    private DeviceFormPanel deviceForm;

    public DeviceEditPanel(Device data) {
        super(data);
    }

    /**
     * Ekleme ya da düzenleme modalını açar ve kaydeder.
     *
     * @param device  düzenlenecek cihaz; yeni kayıt için {@code new Device()}
     * @param onSaved kayıttan sonra (EDT'de) çalışır
     */
    public static void open(Component owner, Device device, Runnable onSaved) {
        boolean isEdit = device.getId() != null;
        DeviceEditPanel panel = new DeviceEditPanel(device);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option(isEdit ? "Güncelle" : "Kaydet", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        panel.setPrimaryModalAction(SimpleModalBorder.OK_OPTION);

        AppModal.showModal(owner, new SimpleModalBorder(panel, isEdit ? "Cihaz Düzenle" : "Yeni Cihaz", options, (controller, action) -> {
            if (action != SimpleModalBorder.OK_OPTION) return;
            Device data = panel.getData();
            if (data == null) {
                controller.consume();
                return;
            }
            // Yeni kayıtta IMEI ile mevcut bir cihaz bulunduysa o cihaz güncellenir.
            boolean update = data.getId() != null;
            if (!update) data.setCreatedAt(LocalDateTime.now());
            ServiceManager.getDeviceService().save(data, update).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(owner, Toast.Type.SUCCESS,
                        Messages.get(update ? "toast.entity.updated" : "toast.entity.added", saved.getDisplayName()));
                if (onSaved != null) onSaved.run();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(owner, update ? "Cihaz güncellenemedi" : "Cihaz eklenemedi", ex);
            });
        }), isEdit ? "DeviceEdit" : "DeviceNew");
    }

    @Override
    protected void initComponent() {
        setLayout(new BorderLayout());
        JPanel form = FormKit.railForm(680);
        add(FormKit.scroll(form), BorderLayout.CENTER);

        form.add(FormKit.rail("Cihaz", "IMEI okutulunca sistemde aranır. Servis kaydı açılınca cihaz müşterinin geçmişine bağlanır."), "top");
        deviceForm = new DeviceFormPanel();
        form.add(deviceForm);

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(deviceForm::focusFirstField);
            }
        });
    }

    @Override
    protected boolean validateForm() {
        JComponent first = deviceForm.validateRequired();
        if (first != null) {
            first.requestFocusInWindow();
            return false;
        }
        return true;
    }

    @Override
    protected Device collectFormData(@NonNull Device data) {
        Device form = deviceForm.getDevice();
        data.setId(form.getId());
        data.setDeviceType(form.getDeviceType());
        data.setBrand(form.getBrand());
        data.setModel(form.getModel());
        data.setSerialNo(form.getSerialNo());
        data.setAccessory(form.getAccessory());
        data.setUpdatedAt(LocalDateTime.now());
        return data;
    }

    @Override
    protected void populateFormWith(Device data) {
        boolean isEdit = data.getId() != null;
        deviceForm.setLookupFillsForm(!isEdit);
        if (isEdit) deviceForm.setDevice(data); else deviceForm.clear();
    }

    @Override
    protected void clearForm() {
        deviceForm.clear();
    }

    @Override
    protected Device createEmptyObject() {
        return new Device();
    }
}
