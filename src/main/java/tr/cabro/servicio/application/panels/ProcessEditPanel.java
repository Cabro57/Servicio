package tr.cabro.servicio.application.panels;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.Modal;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.model.Labor;

import javax.swing.*;
import java.math.BigDecimal;

public class ProcessEditPanel extends Modal {

    private DefaultComboBoxModel<String> model;

    public ProcessEditPanel() {
        model = new DefaultComboBoxModel<>();

        init();
    }



    private void init() {
        initComponent();
    }

    public void  formFill(@NonNull Labor labor) {
        name.setText(labor.getName());
        price.setValue(labor.getDefaultPrice());
        comment.setText(labor.getDescription());
    }

    public void formOpen() {
        name.grabFocus();
    }

    public Labor getLabor() {
        Labor labor = new Labor();
        labor.setName(name.getText().trim());
        labor.setDescription(comment.getText().trim());

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
    private JComboBox<String> type;
    private CurrencyField price;
    private JTextArea comment;
}
