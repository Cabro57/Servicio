package tr.cabro.servicio.application.panels;

import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.Modal;
import tr.cabro.servicio.model.DocumentTemplate;
import tr.cabro.servicio.model.enums.TemplateType;

import javax.swing.*;

public class TemplateEditPanel extends Modal {

    public TemplateEditPanel() {
        init();
    }

    private void init() {
        initComponent();
    }

    public void formFill(@NonNull DocumentTemplate template) {
        name.setText(template.getName());
        body.setText(template.getBody());
    }

    public void formOpen() {
        name.grabFocus();
    }

    public DocumentTemplate getTemplate() {
        DocumentTemplate template = new DocumentTemplate();
        template.setName(name.getText().trim());
        template.setType(TemplateType.WHATSAPP_MESSAGE);
        template.setBody(body.getText().trim());
        return template;
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,wrap,insets 5 30 5 30, width 500", "[][fill,grow]", "[][][]"));

        name = new JTextField();
        add(new JLabel("Ad: "), "alignx right");
        add(name, "wrap");

        body = new JTextArea(10, 0);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        add(new JLabel("Metin: "), "alignx right");
        add(new JScrollPane(body), "wrap");

        JLabel hint = new JLabel("<html><i>Kullanılabilir alanlar: {musteri_adi}, {musteri_telefon}, {cihaz_bilgisi}, " +
                "{cihaz_marka}, {cihaz_model}, {cihaz_seri_no}, {servis_no}, {ariza_aciklamasi}, {servis_durumu}, " +
                "{toplam_tutar}, {odenen_tutar}, {kalan_tutar}, {teslim_tarihi}, {bugunun_tarihi}, {isletme_adi}, " +
                "{isletme_telefon}, {isletme_adres}</i></html>");
        add(hint, "span 2");
    }

    private JTextField name;
    private JTextArea body;
}
