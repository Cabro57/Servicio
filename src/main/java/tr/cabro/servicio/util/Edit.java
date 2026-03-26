package tr.cabro.servicio.util;

/**
 * Form sınıflarında model-UI dönüşümünü sağlayan yapı
 */
public interface Edit<T> {

    /**
     * Formdaki alanlardan model oluşturur (kayıt veya güncelleme için)
     */
    T collectFormData();

    /**
     * Var olan bir modeli forma yükler (güncelleme için)
     */
    void populateFormWith(T data);

    /**
     * Formun doğruluğunu kontrol eder. Hatalıysa false döner.
     */
    boolean validateForm();

    void clearForm();
}
