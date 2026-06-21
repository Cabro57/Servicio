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
        return CompletableFuture.supplyAsync(() -> {
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
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<User>> get(Long id) {
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

    // Sistemde kayıtlı bir işletme sahibi var mı?
    public CompletableFuture<Boolean> hasSetupCompleted() {
        return CompletableFuture.supplyAsync(() -> {
            List<User> users = repository.findAll();
            return !users.isEmpty();
        });
    }
}