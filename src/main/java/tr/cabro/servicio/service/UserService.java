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

    // ------------------------------------------------------------------ PIN kurtarma

    /** Karışabilecek karakterler (0/O, 1/I/L) yok; kod elle yazılıp okunacak. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    /** Veri klasöründe bu dosya varken kilit ekranı kodsuz PIN sıfırlamaya izin verir (destek yöntemi). */
    public static final String RESET_FILE_NAME = "pin-sifirla.txt";
    private static final int MAX_RECOVERY_ATTEMPTS = 5;
    private static final java.time.Duration RECOVERY_COOLDOWN = java.time.Duration.ofMinutes(5);
    private int recoveryFailures;
    private java.time.Instant recoveryLockedUntil;

    /**
     * Yeni kurtarma kodu üretir ({@code XXXX-XXXX-XXXX}); özeti yazılır, eski kod geçersiz olur.
     * Düz kod yalnızca dönüş değerinde bir kez görünür.
     */
    public CompletableFuture<String> generateRecoveryCode(Long userId) {
        return DbExecutor.supply(() -> {
            repository.findById(userId).orElseThrow(() -> new ValidationException("Kullanıcı bulunamadı."));
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < 12; i++) raw.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            repository.setRecoveryCode(userId, PasswordUtil.hash(raw.toString()));
            return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8);
        });
    }

    public CompletableFuture<Boolean> hasRecoveryCode() {
        return DbExecutor.supply(() -> repository.findById(1L)
                .map(u -> u.getRecoveryCode() != null && !u.getRecoveryCode().isBlank()).orElse(false));
    }

    /**
     * Kurtarma koduyla yeni PIN belirler. 5 yanlış denemeden sonra 5 dakika bekletir.
     * Kod doğruysa {@code true}; kod yanlışsa {@code false}.
     */
    public CompletableFuture<Boolean> resetPinWithRecoveryCode(String code, String newPin) {
        return DbExecutor.supply(() -> {
            validateNewPin(newPin);
            synchronized (this) {
                if (recoveryLockedUntil != null && java.time.Instant.now().isBefore(recoveryLockedUntil)) {
                    long secs = java.time.Duration.between(java.time.Instant.now(), recoveryLockedUntil).toSeconds() + 1;
                    throw new ValidationException("Çok fazla yanlış deneme. " + secs + " saniye sonra tekrar deneyin.");
                }
            }
            User user = repository.findById(1L).orElseThrow(() -> new ValidationException("Kullanıcı bulunamadı."));
            if (user.getRecoveryCode() == null || user.getRecoveryCode().isBlank()) {
                throw new ValidationException("Kurtarma kodu oluşturulmamış.");
            }
            String normalized = code == null ? "" : code.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (normalized.length() != 12 || !PasswordUtil.verify(normalized, user.getRecoveryCode())) {
                synchronized (this) {
                    if (++recoveryFailures >= MAX_RECOVERY_ATTEMPTS) {
                        recoveryFailures = 0;
                        recoveryLockedUntil = java.time.Instant.now().plus(RECOVERY_COOLDOWN);
                    }
                }
                return false;
            }
            synchronized (this) {
                recoveryFailures = 0;
                recoveryLockedUntil = null;
            }
            repository.setPassword(user.getId(), PasswordUtil.hash(newPin));
            return true;
        });
    }

    /** Destek yöntemi: veri klasöründe sıfırlama dosyası var mı? */
    public static java.io.File resetFile() {
        String base = System.getProperty("servicio.baseDir");
        return base != null ? new java.io.File(base, RESET_FILE_NAME) : null;
    }

    public static boolean isResetFilePresent() {
        java.io.File f = resetFile();
        return f != null && f.isFile();
    }

    /**
     * Sıfırlama dosyası varken kodsuz yeni PIN belirler ve dosyayı siler (tek kullanımlık).
     * Bu yolu ancak bilgisayara ve veri klasörüne erişimi olan biri açabilir; o kişi veritabanını
     * zaten elle değiştirebileceği için yeni bir açık oluşturmaz.
     */
    public CompletableFuture<Boolean> resetPinWithFile(String newPin) {
        return DbExecutor.supply(() -> {
            validateNewPin(newPin);
            java.io.File f = resetFile();
            if (f == null || !f.isFile()) return false;
            repository.setPassword(1L, PasswordUtil.hash(newPin));
            if (!f.delete()) {
                tr.cabro.servicio.Servicio.getLogger().warn("PIN sıfırlama dosyası silinemedi: {}", f.getAbsolutePath());
            }
            tr.cabro.servicio.Servicio.getLogger().warn("PIN, veri klasöründeki sıfırlama dosyasıyla sıfırlandı");
            return true;
        });
    }

    private static void validateNewPin(String newPin) {
        if (newPin == null || !newPin.matches("\\d{6}")) {
            throw new ValidationException("Yeni PIN 6 haneli rakamlardan oluşmalıdır.");
        }
    }

    // Sistemde kayıtlı bir işletme sahibi var mı?
    public CompletableFuture<Boolean> hasSetupCompleted() {
        return DbExecutor.supply(() -> {
            List<User> users = repository.findAll();
            return !users.isEmpty();
        });
    }
}