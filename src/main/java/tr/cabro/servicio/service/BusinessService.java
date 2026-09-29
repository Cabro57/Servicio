package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.BusinessRepository;
import tr.cabro.servicio.model.Business;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * İşletme bilgileri. Okunan ya da kaydedilen son hâl bellekte tutulur ({@link #cached()}):
 * menü başlığı ve kilit ekranı işletme adını beklemeden çizebilsin.
 */
public class BusinessService {

    private final BusinessRepository repository;
    private volatile Business cached;

    public BusinessService(BusinessRepository repository) {
        this.repository = repository;
    }

    /**
     * Güncel işletme bilgisi. Satır henüz yoksa (kurulum öncesi) boş bir {@link Business} döner;
     * Optional çağıranların eski {@code get(1L)} kalıbıyla aynı kalması için.
     */
    public CompletableFuture<Optional<Business>> get() {
        return DbExecutor.supply(() -> {
            Business b = repository.find().orElseGet(Business::new);
            cached = b;
            return Optional.of(b);
        });
    }

    public CompletableFuture<Business> save(Business business) {
        return DbExecutor.supply(() -> {
            business.setId(1L);
            repository.save(business);
            Business saved = repository.find().orElse(business);
            cached = saved;
            return saved;
        });
    }

    /** Son okunan/kaydedilen hâl; henüz okunmadıysa null. */
    public Business cached() {
        return cached;
    }

    /** Bellekteki işletme adı; yoksa boş. */
    public String cachedName() {
        Business b = cached;
        return b != null && b.getBusinessName() != null ? b.getBusinessName() : "";
    }
}
