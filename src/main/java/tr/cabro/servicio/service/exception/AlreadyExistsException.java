package tr.cabro.servicio.service.exception;

/**
 * Aynı adla kayıt zaten var. {@link ValidationException} alt sınıfıdır: mesajı kullanıcıya
 * doğrudan gösterilir (ErrorHandler teknik hatalar gibi genel mesaja çevirmez).
 */
public class AlreadyExistsException extends ValidationException {
    public AlreadyExistsException(String message) {
        super(message);
    }
}
