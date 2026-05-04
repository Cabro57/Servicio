package tr.cabro.servicio.application.panels.edit;

import lombok.NonNull;
import raven.modal.Toast;

import javax.swing.*;

public abstract class AbstractEditPanel<T> extends JPanel {

    private T data;

    public AbstractEditPanel(T data) {
        init();
        setData(data);
    }

    private void init() {
        initComponent();
    }

    /**
     * Alt sınıflar burada bileşenleri oluşturup mainPanel'e ekler.
     */
    protected abstract void initComponent();

    /**
     * Form alanlarından nesne oluşturur.
     *
     * @return model nesnesi
     */
    protected abstract T collectFormData(T data);

    /**
     * Verilen nesnedeki verileri forma doldurur.
     *
     * @param data doldurulacak model
     */
    protected abstract void populateFormWith(T data);

    /**
     * Formu temizler.
     */
    protected abstract void clearForm();

    /**
     * Yeni kayıt modu için boş nesne üretir.
     */
    protected abstract T createEmptyObject();

    /**
     * Ortak doğrulama hatası gösterimi.
     */
    protected void showValidationError(String message) {
        showValidationError(Toast.Type.WARNING, message);
    }

    protected void showValidationError(raven.modal.Toast.Type type, String message) {
        Toast.show(this, type, message);
    }

    /**
     * Form alanlarını doğrular.
     * Alt sınıflar override ederek zorunlu alan ve format kontrollerini uygular.
     * Hata durumunda {@link #showValidationError} ile mesaj gösterilmeli ve false dönülmelidir.
     *
     * @return true: form geçerli; false: hata var, kayıt durduruldu
     */
    protected boolean validateForm() {
        return true;
    }

    /**
     * Form geçerliyse nesneyi döndürür, değilse null.
     * Önce {@link #validateForm()} çağrılır; validasyon başarısızsa null döner.
     */
    public T getData() {
        if (!validateForm()) return null;
        T objectToProcess = (this.data != null) ? this.data : createEmptyObject();
        this.data = collectFormData(objectToProcess);

        return this.data;
    }

    public void setData(@NonNull T data) {
        this.data = data;
        populateFormWith(data);
    }
}
