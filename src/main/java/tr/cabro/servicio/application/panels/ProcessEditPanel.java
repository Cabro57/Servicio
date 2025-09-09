package tr.cabro.servicio.application.panels;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.Modal;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.model.Process;

import javax.swing.*;
import java.util.List;

public class ProcessEditPanel extends Modal {

    private DefaultComboBoxModel<String> model;

    public ProcessEditPanel() {
        model = new DefaultComboBoxModel<>();

        init();
    }



    private void init() {
        initComponent();
    }

    public void  formFill(@NonNull String type, @NonNull Process process) {
        setSelectedType(type);

        name.setText(process.getName());
        price.setValue(process.getPrice());
        comment.setText(process.getComment());
    }

    public void formOpen() {
        name.grabFocus();
    }

    public void setType(List<String> types) {
        model.removeAllElements();

        model.addElement("Seçiniz...");
        types.forEach(model::addElement);
    }

    public void setSelectedType(String type) {

        model.setSelectedItem(type);
    }

    public String getSelectedType() {
        return (String) model.getSelectedItem();
    }

    public Process getProcess() {
        return new Process(
                name.getText(),
                comment.getText(),
                (Double) price.getValue()
        );
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
    private JComboBox<String> type;
    private CurrencyField price;
    private JTextArea comment;
}
