package tr.cabro.servicio.service;

import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.database.repository.UserRepository;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PasswordUtil;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<User> save(User user, boolean update) {
        return DbExecutor.supply(() -> {
            // Şifre henüz hash'lenmemişse (düz PIN) doğrula ve hash'le
            if (!PasswordUtil.isHashed(user.getPassword())) {
                if (user.getPassword().length() != 6 || !user.getPassword().matches("\\d+")) {
                    throw new ValidationException("Şifre sadece 6 haneli rakamlardan oluşmalıdır!");
                }
                user.setPassword(PasswordUtil.hash(user.getPassword()));
            }

            if (!update) {
                user.setId(1L);
                repository.insert(user);
            } else {
                user.setId(1L);
                repository.update(user);
            }
            return user;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return DbExecutor.run(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<User>> get(Long id) {
        return DbExecutor.supply(() -> repository.findById(id));
    }

    public CompletableFuture<List<User>> getAll() {
        return DbExecutor.supply(repository::findAll);
    }

    /**
     * PIN (Şifre) Doğrulama
     * Sistemde tek kullanıcı olduğu için doğrudan ID=1 üzerinden PIN kontrolü yapar.
     */
    public CompletableFuture<Boolean> authenticate(String pin) {
        return DbExecutor.supply(() -> {

            // Eğer pin boş gönderilmişse direkt false dön
            if (pin == null || pin.trim().isEmpty()) {
                return false;
            }

            Optional<User> userOpt = repository.findById(1L);
            if (!userOpt.isPresent()) return false;

            User user = userOpt.get();
            if (!PasswordUtil.verify(pin, user.getPassword())) return false;

            // Lazy migration: eski düz metin şifreyi hash'le
            if (!PasswordUtil.isHashed(user.getPassword())) {
                user.setPassword(PasswordUtil.hash(pin));
                repository.update(user);
            }

            MyDrawerBuilder.getInstance().setUser(user);
            return true;
        });
    }

    /**
     * Kilit PIN'ini değiştirir. Mevcut PIN veritabanındaki güncel kayda karşı doğrulanır;
     * yanlışsa {@code false} döner ve hiçbir şey yazılmaz. Yeni PIN 6 haneli rakam olmalıdır.
     * Yalnızca şifre alanı yazılır, işletme ve profil bilgilerine dokunulmaz.
     */
    public CompletableFuture<Boolean> changePin(Long userId, String currentPin, String newPin) {
        return DbExecutor.supply(() -> {
            if (newPin == null || !newPin.matches("\\d{6}")) {
                throw new ValidationException("Yeni PIN 6 haneli rakamlardan oluşmalıdır.");
            }
            User user = repository.findById(userId).orElseThrow(() -> new ValidationException("Kullanıcı bulunamadı."));
            if (!PasswordUtil.verify(currentPin, user.getPassword())) return false;
            user.setPassword(PasswordUtil.hash(newPin));
            repository.update(user);
            return true;
        });
    }

    /**
     * Profil bilgilerini (ad, soyad, e-posta, profil resmi) günceller. Kayıt veritabanından taze
     * okunur ve yalnızca bu alanlar değişir; aynı satırda duran işletme bilgileri ve PIN,
     * başka bir pencerede değişmiş olsa bile eski kopyayla ezilmez.
     */
    public CompletableFuture<User> updateProfile(Long userId, String name, String surname, String email, String profilePicture) {
        return DbExecutor.supply(() -> {
            User user = repository.findById(userId).orElseThrow(() -> new ValidationException("Kullanıcı bulunamadı."));
            user.setName(name);
            user.setSurname(surname);
            user.setEmail(email);
            user.setProfilePicture(profilePicture);
            repository.update(user);
            return user;
        });
    }

    // Sistemde kayıtlı bir işletme sahibi var mı?
    public CompletableFuture<Boolean> hasSetupCompleted() {
        return DbExecutor.supply(() -> {
            List<User> users = repository.findAll();
            return !users.isEmpty();
        });
    }
}