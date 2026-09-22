package tr.cabro.servicio.application.panels.edit;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.PhoneField;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.util.Validator;

import javax.swing.*;
import java.awt.*;

/**
 * Tedarikçi ekleme/düzenleme formu: Firma, İletişim ve Not bölümleri; müşteri formuyla aynı
 * raylı düzen ({@link FormKit}). Doğrulama hataları ilgili alanın altında gösterilir.
 * <p>
 * Eski formdaki "TC Kimlik No" alanı kaldırıldı: {@link Supplier} modelinde karşılığı yoktu,
 * yazılan değer hiçbir yere kaydedilmiyordu.
 * <p>
 * DİKKAT: {@link #initComponent()} üst sınıfın kurucusundan çağrılır — alanlara başlangıç değeri
 * ({@code = ...}) VERİLMEMELİ.
 */
public class SupplierEditPanel extends AbstractEditPanel<Supplier> {

    public SupplierEditPanel(Supplier data) {
        super(data);
    }

    @Override
    protected boolean validateForm() {
        JComponent first = null;

        if (Validator.isEmpty(businessNameField.getText())) {
            first = FormKit.fail(businessNameField, businessNameError, "Firma ismini yazın.");
        } else if (Validator.exceedsMaxLength(businessNameField.getText(), 100)) {
            first = FormKit.fail(businessNameField, businessNameError, "En fazla 100 karakter olabilir.");
        }

        if (Validator.isEmpty(nameField.getText())) {
            first = first(first, FormKit.fail(nameField, nameError, "Yetkili kişinin adını yazın."));
        } else if (Validator.exceedsMaxLength(nameField.getText(), 100)) {
            first = first(first, FormKit.fail(nameField, nameError, "En fazla 100 karakter olabilir."));
        }

        // Telefon isteğe bağlı; yazıldıysa geçerli olmalı (PhoneField geçersizde null döner).
        if (!Validator.isEmpty(phoneField.getText()) && phoneField.getNormalizedNumber() == null) {
            first = first(first, FormKit.fail(phoneField, phoneError, "Numara eksik veya hatalı."));
        }

        String email = emailField.getText().trim();
        if (!Validator.isEmpty(email) && !Validator.isValidEmail(email)) {
            first = first(first, FormKit.fail(emailField, emailError, "Geçerli bir e-posta adresi girin."));
        }

        if (Validator.exceedsMaxLength(taxNoField.getText().trim(), 50)) {
            first = first(first, FormKit.fail(taxNoField, taxNoError, "En fazla 50 karakter olabilir."));
        }

        if (first != null) {
            first.requestFocusInWindow();
            return false;
        }
        return true;
    }

    private static JComponent first(JComponent current, JComponent candidate) {
        return current != null ? current : candidate;
    }

    @Override
    protected Supplier collectFormData(@NonNull Supplier data) {
        data.setName(nameField.getText().trim());
        data.setBusinessName(businessNameField.getText().trim());
        data.setPhone(phoneField.getNormalizedNumber());
        data.setAddress(addressField.getText().trim());
        data.setNote(notesField.getText().trim());
        data.setEmail(emailField.getText().trim());
        data.setTaxNumber(taxNoField.getText().trim());
        data.setTaxOffice(taxOfficeField.getText().trim());
        return data;
    }

    @Override
    public void populateFormWith(Supplier data) {
        nameField.setText(data.getName());
        businessNameField.setText(data.getBusinessName());
        phoneField.setNumber(data.getPhone());
        addressField.setText(nvl(data.getAddress()));
        notesField.setText(nvl(data.getNote()));
        emailField.setText(data.getEmail());
        taxNoField.setText(data.getTaxNumber());
        taxOfficeField.setText(data.getTaxOffice());
        clearErrors();
    }

    @Override
    public void clearForm() {
        nameField.setText("");
        businessNameField.setText("");
        phoneField.setNumber("");
        addressField.setText("");
        notesField.setText("");
        emailField.setText("");
        taxNoField.setText("");
        taxOfficeField.setText("");
        clearErrors();
    }

    private void clearErrors() {
        FormKit.clear(businessNameField, businessNameError);
        FormKit.clear(nameField, nameError);
        FormKit.clear(emailField, emailError);
        FormKit.clear(taxNoField, taxNoError);
        FormKit.clear(null, phoneError); // PhoneField çerçevesini kendi anlık kontrolü yönetir.
    }

    @Override
    protected Supplier createEmptyObject() {
        return new Supplier();
    }

    @Override
    protected void initComponent() {
        setLayout(new BorderLayout());
        JPanel form = FormKit.railForm(720);
        add(FormKit.scroll(form), BorderLayout.CENTER);

        // --- Firma ---
        form.add(FormKit.rail("Firma", "Parça kayıtlarında tedarikçi olarak görünür."), "top");
        JPanel company = FormKit.grid(2);
        businessNameField = new JTextField();
        businessNameError = FormKit.errorLabel();
        company.add(FormKit.cell("Firma İsmi *", businessNameField, businessNameError), "span 2");
        nameField = new JTextField();
        nameError = FormKit.errorLabel();
        company.add(FormKit.cell("Yetkili Ad Soyad *", nameField, nameError), "wrap");
        taxOfficeField = new JTextField();
        taxNoField = new JTextField();
        taxNoError = FormKit.errorLabel();
        company.add(FormKit.cell("Vergi Dairesi", taxOfficeField, null));
        company.add(FormKit.cell("Vergi No", taxNoField, taxNoError));
        form.add(company);

        form.add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- İletişim ---
        form.add(FormKit.rail("İletişim", "Sipariş ve iade için ulaşılacak bilgiler."), "top");
        JPanel contact = FormKit.grid(2);
        phoneField = new PhoneField();
        phoneError = FormKit.errorLabel();
        emailField = new JTextField();
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İsteğe bağlı");
        emailError = FormKit.errorLabel();
        contact.add(FormKit.cell("Telefon", phoneField, phoneError));
        contact.add(FormKit.cell("E-posta", emailField, emailError));
        addressField = FormKit.textArea(2);
        contact.add(FormKit.cell("Adres", FormKit.areaScroll(addressField), null), "span 2");
        form.add(contact);

        form.add(FormKit.separator(), "span 2, growx, gaptop 12, gapbottom 12");

        // --- Not ---
        form.add(FormKit.rail("Not", "Çalışma koşulları, vade, iskonto gibi hatırlatmalar."), "top");
        notesField = FormKit.textArea(3);
        form.add(FormKit.areaScroll(notesField));

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(() -> businessNameField.requestFocusInWindow());
            }
        });
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private JTextField businessNameField;
    private JTextField nameField;
    private JTextField taxOfficeField;
    private JTextField taxNoField;
    private PhoneField phoneField;
    private JTextField emailField;
    private JTextArea addressField;
    private JTextArea notesField;

    private JLabel businessNameError;
    private JLabel nameError;
    private JLabel taxNoError;
    private JLabel phoneError;
    private JLabel emailError;
}
