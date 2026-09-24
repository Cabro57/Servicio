package tr.cabro.servicio.application.panels.edit;

import tr.cabro.servicio.application.utils.Toasts;
import lombok.NonNull;
import raven.modal.Toast;
import raven.modal.component.ModalBorderAction;
import raven.modal.component.SimpleModalBorder;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public abstract class AbstractEditPanel<T> extends JPanel {

    private T data;

    /**
     * Ctrl+Enter'ın tetikleyeceği modal eylemi. Varsayılan {@code YES_OPTION}; modalini
     * farklı bir birincil seçenekle açan çağıran ({@code OK_OPTION} gibi) bunu
     * {@link #setPrimaryModalAction(int)} ile bildirmelidir — aksi halde kısayol,
     * seçenek listesinde bulunmayan bir eylem gönderir ve sessizce hiçbir şey yapmaz.
     */
    private int primaryModalAction = SimpleModalBorder.YES_OPTION;

    public AbstractEditPanel(T data) {
        init();
        setData(data);
    }

    private void init() {
        initComponent();
        installSubmitShortcut();
    }

    /**
     * Ctrl+Enter ile formu kaydeder. Kapsam {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}:
     * modaldeki hangi alanda olursanız olun çalışır, modal dışına taşmaz.
     */
    private void installSubmitShortcut() {
        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
        String actionKey = "servicio.submitModal";
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlEnter, actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ModalBorderAction action = ModalBorderAction.getModalBorderAction(AbstractEditPanel.this);
                if (action != null) action.doAction(primaryModalAction);
            }
        });
    }

    /** Modalin birincil ("kaydet") seçeneğinin {@link SimpleModalBorder} eylem kodunu bildirir. */
    public void setPrimaryModalAction(int primaryModalAction) {
        this.primaryModalAction = primaryModalAction;
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
        Toasts.show(this, type, message);
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
