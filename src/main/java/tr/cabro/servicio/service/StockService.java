package tr.cabro.servicio.service;

import org.jdbi.v3.core.Handle;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.StockLedgerRepository;
import tr.cabro.servicio.database.repository.WarehouseRepository;
import tr.cabro.servicio.model.StockLevel;
import tr.cabro.servicio.model.StockMovement;
import tr.cabro.servicio.model.Warehouse;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.model.enums.StockType;
import tr.cabro.servicio.service.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Stoğun tek giriş kapısı. Stok yalnızca defter hareketiyle değişir; depo bakiyesi ve toplam
 * {@code stock_quantity} tetikleyiciyle güncellenir (V25). Doğrudan {@code stock_quantity} yazan kod yoktur.
 * <p>
 * İki katman:
 * <ul>
 *   <li>{@code Handle} alan metotlar çağıranın transaction'ında çalışır — servis kalemi, satış gibi
 *       stokla birlikte başka tabloya da yazan akışlar bunları kullanır ki yarım kayıt kalmasın.</li>
 *   <li>Kullanıcı işlemleri (giriş, çıkış, sayım, transfer) kendi transaction'ını açar.</li>
 * </ul>
 */
public class StockService {

    private final StockLedgerRepository ledgerRepository;

    public StockService(StockLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    // =========================================================================
    // TRANSACTION İÇİ YARDIMCILAR
    // =========================================================================

    /**
     * Depo kimliğini doğrular; {@code null} ise varsayılan depo döner.
     *
     * @param requireActive true → pasif depo reddedilir (yeni giriş/çıkış). Sisteme geri dönen stok
     *                      (servisten dönüş, satış iadesi) çıktığı depoya döner, pasif olsa bile.
     */
    public Long resolveWarehouse(Handle handle, Long warehouseId, boolean requireActive) {
        WarehouseRepository repo = handle.attach(WarehouseRepository.class);
        if (warehouseId == null) {
            return repo.findDefaultId().orElseThrow(() -> new ValidationException("Varsayılan depo tanımlı değil."));
        }
        Warehouse warehouse = repo.findById(warehouseId)
                .orElseThrow(() -> new ValidationException("Depo bulunamadı: #" + warehouseId));
        if (requireActive && !warehouse.isActive()) {
            throw new ValidationException("'" + warehouse.getName() + "' deposu pasif; bu depoya işlem yapılamaz.");
        }
        return warehouse.getId();
    }

    /** Depoda en az {@code quantity} adet yoksa {@link ValidationException} fırlatır. */
    public void requireAvailable(Handle handle, StockItemKind kind, Long itemId, Long warehouseId, int quantity) {
        StockLedgerRepository ledger = handle.attach(StockLedgerRepository.class);
        int available = ledger.level(kind, itemId, warehouseId);
        if (available < quantity) {
            String warehouseName = handle.attach(WarehouseRepository.class).findById(warehouseId)
                    .map(Warehouse::getName).orElse("#" + warehouseId);
            throw new ValidationException("Yetersiz stok! '" + ledger.itemName(kind, itemId) + "' — "
                    + warehouseName + " deposunda " + available + " adet var, " + quantity + " adet gerekli.");
        }
    }

    /**
     * Deftere tek hareket yazar. {@code signedQuantity} giriş için pozitif, çıkış için negatiftir;
     * sıfırsa hiçbir şey yazılmaz. Stok kontrolü yapmaz — gerekiyorsa önce {@link #requireAvailable}.
     */
    public void record(Handle handle, StockItemKind kind, Long itemId, Long warehouseId, int signedQuantity,
                       ReferenceType referenceType, Long referenceId, BigDecimal unitCost, String note) {
        if (signedQuantity == 0) return;
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(referenceType, "referenceType");

        StockMovement movement = new StockMovement();
        movement.setItemId(itemId);
        movement.setWarehouseId(warehouseId);
        movement.setQuantity(signedQuantity);
        movement.setType(signedQuantity > 0 ? StockType.IN : StockType.OUT);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setUnitCost(unitCost);
        movement.setNote(blankToNull(note));
        movement.setCreatedAt(LocalDateTime.now());
        handle.attach(StockLedgerRepository.class).insert(kind, movement);
    }

    // =========================================================================
    // KULLANICI İŞLEMLERİ
    // =========================================================================

    /** Stok girişi (alış). {@code unitCost} isteğe bağlı birim alış maliyetidir (TL). */
    public CompletableFuture<Void> receive(StockItemKind kind, Long itemId, Long warehouseId, int quantity,
                                           BigDecimal unitCost, String note) {
        requireItem(itemId);
        requirePositive(quantity);
        if (unitCost != null && unitCost.signum() < 0) throw new ValidationException("Birim maliyet negatif olamaz.");
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            Long wh = resolveWarehouse(handle, warehouseId, true);
            record(handle, kind, itemId, wh, quantity, ReferenceType.PURCHASE, null, unitCost, note);
        }));
    }

    /**
     * Stok çıkışı (fire/kayıp ya da düzeltme). Depodaki miktardan fazlası çıkarılamaz.
     *
     * @param reason {@link ReferenceType#LOSS} veya {@link ReferenceType#ADJUSTMENT}
     */
    public CompletableFuture<Void> issue(StockItemKind kind, Long itemId, Long warehouseId, int quantity,
                                         ReferenceType reason, String note) {
        requireItem(itemId);
        requirePositive(quantity);
        if (reason != ReferenceType.LOSS && reason != ReferenceType.ADJUSTMENT) {
            throw new ValidationException("Çıkış sebebi fire/kayıp veya düzeltme olmalıdır.");
        }
        if (reason == ReferenceType.ADJUSTMENT && isBlank(note)) {
            throw new ValidationException("Düzeltme çıkışında açıklama zorunludur.");
        }
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            Long wh = resolveWarehouse(handle, warehouseId, true);
            requireAvailable(handle, kind, itemId, wh, quantity);
            record(handle, kind, itemId, wh, -quantity, reason, null, null, note);
        }));
    }

    /**
     * Sayım: depodaki gerçek adet girilir, sistemle fark sayım hareketi olarak yazılır.
     *
     * @return yazılan fark (0 → sayım sistemle aynı, hareket yazılmadı)
     */
    public CompletableFuture<Integer> count(StockItemKind kind, Long itemId, Long warehouseId, int countedQuantity,
                                            String note) {
        requireItem(itemId);
        if (countedQuantity < 0) throw new ValidationException("Sayılan adet negatif olamaz.");
        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            Long wh = resolveWarehouse(handle, warehouseId, true);
            int current = handle.attach(StockLedgerRepository.class).level(kind, itemId, wh);
            int delta = countedQuantity - current;
            record(handle, kind, itemId, wh, delta, ReferenceType.COUNT, null, null,
                    isBlank(note) ? "Sistem " + current + ", sayılan " + countedQuantity : note);
            return delta;
        }));
    }

    /** Depolar arası transfer: kaynaktan çıkış + hedefe giriş, tek transaction. */
    public CompletableFuture<Void> transfer(StockItemKind kind, Long itemId, Long fromWarehouseId, Long toWarehouseId,
                                            int quantity, String note) {
        requireItem(itemId);
        requirePositive(quantity);
        if (fromWarehouseId == null || toWarehouseId == null) throw new ValidationException("Kaynak ve hedef depo seçilmelidir.");
        if (fromWarehouseId.equals(toWarehouseId)) throw new ValidationException("Kaynak ve hedef depo aynı olamaz.");
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            Long from = resolveWarehouse(handle, fromWarehouseId, false);
            Long to = resolveWarehouse(handle, toWarehouseId, true);
            requireAvailable(handle, kind, itemId, from, quantity);
            WarehouseRepository warehouses = handle.attach(WarehouseRepository.class);
            String fromName = warehouses.findById(from).map(Warehouse::getName).orElse("#" + from);
            String toName = warehouses.findById(to).map(Warehouse::getName).orElse("#" + to);
            String suffix = isBlank(note) ? "" : " — " + note.trim();
            record(handle, kind, itemId, from, -quantity, ReferenceType.TRANSFER, to, null, "→ " + toName + suffix);
            record(handle, kind, itemId, to, quantity, ReferenceType.TRANSFER, from, null, "← " + fromName + suffix);
        }));
    }

    // =========================================================================
    // OKUMA
    // =========================================================================

    /** Kalemin depo bazında bakiyeleri (varsayılan depo önce). */
    public CompletableFuture<List<StockLevel>> getLevels(StockItemKind kind, Long itemId) {
        return DbExecutor.supply(() -> ledgerRepository.levels(kind, itemId));
    }

    /** Kalemin hareket geçmişi, en yeni önce; her satırda hareket sonrası toplam ve depo bakiyesi. */
    public CompletableFuture<PageResult<StockMovement>> getHistory(StockItemKind kind, Long itemId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return DbExecutor.supply(() -> new PageResult<>(
                ledgerRepository.history(kind, itemId, pageSize, offset), page, pageSize,
                ledgerRepository.countHistory(kind, itemId)));
    }

    /** Depodaki stoğu sıfır olmayan parça ve ürünler. */
    public CompletableFuture<List<StockLevel>> getWarehouseContents(Long warehouseId) {
        return DbExecutor.supply(() -> ledgerRepository.findWarehouseContents(warehouseId));
    }

    // =========================================================================

    private static void requireItem(Long itemId) {
        if (itemId == null) throw new ValidationException("Stok kalemi seçilmelidir.");
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) throw new ValidationException("Miktar 0'dan büyük olmalıdır.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
