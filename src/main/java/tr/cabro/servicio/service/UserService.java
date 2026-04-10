package tr.cabro.servicio.service;

import raven.modal.menu.MyDrawerBuilder;
import raven.modal.system.FormManager;
import tr.cabro.servicio.database.repository.UserRepository;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Validator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<User> save(User user, boolean update) {
        return CompletableFuture.supplyAsync(() -> {
            // ... validasyonlar (PIN 6 haneli mi kontrolü vb.)

            if (user.getPassword().length() != 6 || !user.getPassword().matches("\\d+")) {
                throw new ValidationException("Şifre sadece 6 haneli rakamlardan oluşmalıdır!");
            }

            if (!update) {
                // KRİTİK: Sisteme ilk kez kayıt olunuyorsa, id'yi 1'e zorla.
                // Eğer id=1 zaten varsa SQLite hata fırlatır ve 2. kişinin kaydını reddeder.
                user.setId(1);
                repository.insert(user); // SQL'ini "INSERT INTO users (id, name... )" şeklinde güncellemelisin
            } else {
                user.setId(1); // Güncellemede de her zaman 1. kullanıcıyı güncelle
                repository.update(user);
            }
            return user;
        });
    }

    public CompletableFuture<Void> delete(int id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<User>> get(int id) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }

    public CompletableFuture<List<User>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    /**
     * PIN (Şifre) Doğrulama
     * Sistemde tek kullanıcı olduğu için doğrudan ID=1 üzerinden PIN kontrolü yapar.
     */
    public CompletableFuture<Boolean> authenticate(String pin) {
        return CompletableFuture.supplyAsync(() -> {

            // Eğer pin boş gönderilmişse direkt false dön
            if (pin == null || pin.trim().isEmpty()) {
                return false;
            }

            // Kurulum aşamasında (SetupPanel) kullanıcının ID'sini hep 1'e zorlamıştık.
            // Bu yüzden direkt 1 numaralı kullanıcıyı veritabanından çekiyoruz.
            Optional<User> userOpt = repository.findById(1);

            // Kullanıcı veritabanında varsa ve şifresi girilen PIN ile birebir aynıysa
            if (userOpt.isPresent() && userOpt.get().getPassword().equals(pin)) {
                MyDrawerBuilder.getInstance().setUser(userOpt.get());
                return true; // Giriş Başarılı
            }

            return false; // Yanlış PIN
        });
    }

    // Sistemde kayıtlı bir işletme sahibi var mı?
    public CompletableFuture<Boolean> hasSetupCompleted() {
        return CompletableFuture.supplyAsync(() -> {
            List<User> users = repository.findAll();
            return !users.isEmpty();
        });
    }
}