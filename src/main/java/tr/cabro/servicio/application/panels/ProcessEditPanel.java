package tr.cabro.servicio.application.panels;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.Modal;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.math.BigDecimal;

public class ProcessEditPanel extends Modal {

    private static final DeviceType GENERAL_TYPE = new DeviceType(null, "Genel (Tüm Türler)", 0);

    private DefaultComboBoxModel<DeviceType> model;
    private Long pendingDeviceTypeId;

    public ProcessEditPanel() {
        model = new DefaultComboBoxModel<>();

        init();
    }



    private void init() {
        initComponent();

        model.addElement(GENERAL_TYPE);
        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(types -> {
            SwingUtilities.invokeLater(() -> {
                types.forEach(model::addElement);
                selectPendingDeviceType();
            });
        });
    }

    public void  formFill(@NonNull Labor labor) {
        name.setText(labor.getName());
        price.setValue(labor.getDefaultPrice());
        comment.setText(labor.getDescription());
        pendingDeviceTypeId = labor.getDeviceTypeId();
        selectPendingDeviceType();
    }

    private void selectPendingDeviceType() {
        for (int i = 0; i < model.getSize(); i++) {
            DeviceType candidate = model.getElementAt(i);
            if (java.util.Objects.equals(candidate.getId(), pendingDeviceTypeId)) {
                model.setSelectedItem(candidate);
                return;
            }
        }
    }

    public void formOpen() {
        name.grabFocus();
    }

    public Labor getLabor() {
        Labor labor = new Labor();
        labor.setName(name.getText().trim());
        labor.setDescription(comment.getText().trim());

        DeviceType selected = (DeviceType) type.getSelectedItem();
        labor.setDeviceTypeId(selected != null ? selected.getId() : null);

        return labor;
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,wrap,insets 5 30 5 30, width 400", "[][fill, grow]", "[][][]"));

        name = new JTextField();

        add(new JLabel("Ad: "), "alignx right");
        add(name, "wrap");

        type = new JComboBox<>(model);

        add(new JLabel("Cihaz Türü:"), "align right");
        add(type, "wrap");

        price = new CurrencyField();

        add(new JLabel("Ücret: "), "alignx right");
        add(price, "wrap");

        comment = new JTextArea(3, 0);

        add(new JLabel("Açıklama: "), "alignx right");
        add(new JScrollPane(comment));



    }

    private JTextField name;
    private JComboBox<DeviceType> type;
    private CurrencyField price;
    private JTextArea comment;
}
