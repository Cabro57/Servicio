package tr.cabro.servicio.application.panels.edit;

import raven.modal.Toast;

import javax.swing.*;

public abstract class AbstractEditPanel<T> extends JPanel {

    public AbstractEditPanel() {
        init();
    }

    private void init() {
        initComponent();
    }

    /**
     * Alt sınıflar burada bileşenleri oluşturup mainPanel'e ekler.
     */
    protected abstract void initComponent();

    /**
     * Form verilerini doğrular.
     * @return geçerliyse true, değilse false
     */
    protected abstract boolean validateForm();

    /**
     * Form alanlarından nesne oluşturur.
     * @return model nesnesi
     */
    protected abstract T collectFormData();

    /**
     * Verilen nesnedeki verileri forma doldurur.
     * @param data doldurulacak model
     */
    protected abstract void populateFormWith(T data);

    /**
     * Formu temizler.
     */
    protected abstract void clearForm();

    /**
     * Ortak doğrulama hatası gösterimi.
     */
    protected void showValidationError(String message) {
        Toast.show(this, Toast.Type.INFO, message);
    }

    /**
     * Form geçerliyse nesneyi döndürür, değilse null.
     */
    public T getDataIfValid() {
        if (validateForm()) {
            return collectFormData();
        }
        return null;
    }
}
