package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.DeviceLoanRepository;
import tr.cabro.servicio.database.repository.PaymentAllocationRepository;
import tr.cabro.servicio.database.repository.PaymentRepository;
import tr.cabro.servicio.model.DeviceLoan;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.LoanDirection;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Emanet cihaz işlemleri. İki yön ({@link LoanDirection}) aynı kayıtla tutulur.
 * <p>
 * Depozito kasaya işlenir: alınınca {@code +depozito}, iadede {@code -iade} tutarında birer
 * {@link Payment} yazılır. Ödemeler müşteriye bağlanmaz ({@code customer_id NULL}), emanete
 * tahsis edilir ({@link AllocationTargetType#DEVICE_LOAN}); böylece kasa raporunda görünür ama
 * müşterinin cari bakiyesini (borç/alacak) değiştirmez. Hasar bedeli depozitodan kesilir: kesilen
 * kısım iade edilmediği için kasada gelir olarak kalır.
 */
public class DeviceLoanService {

    private final DeviceLoanRepository repository;

    public DeviceLoanService(DeviceLoanRepository repository) {
        this.repository = repository;
    }

    /**
     * Emaneti başlatır (ver ya da al). Cihazın açık emaneti varsa reddedilir. Yedek cihaz verirken
     * cihaz "dükkân cihazı" işaretli olmalıdır.
     *
     * @param loan         doldurulmuş kayıt (id ve kapanış alanları boş)
     * @param deviceLoaner cihaz dükkân cihazı mı (OUT için zorunlu)
     * @param partyName    kasa notunda görünecek müşteri adı
     */
    public CompletableFuture<DeviceLoan> start(DeviceLoan loan, boolean deviceLoaner, String partyName) {
        if (loan.getDeviceId() == null) throw new ValidationException("Emanet için cihaz belirtilmelidir.");
        if (loan.getCustomerId() == null) throw new ValidationException("Emanet için müşteri seçilmelidir.");
        if (loan.getDirection() == null) throw new ValidationException("Emanet yönü belirtilmelidir.");
        if (loan.getDirection() == LoanDirection.OUT && !deviceLoaner) {
            throw new ValidationException("Yalnızca \"dükkân cihazı\" işaretli cihazlar emanet verilebilir. Cihazı düzenleyip işaretleyin.");
        }
        BigDecimal deposit = loan.getDeposit() != null ? loan.getDeposit() : BigDecimal.ZERO;
        if (deposit.signum() < 0) throw new ValidationException("Depozito eksi olamaz.");
        if (loan.getDirection() == LoanDirection.IN) deposit = BigDecimal.ZERO;
        if (deposit.signum() > 0 && loan.getDepositPaymentType() == null) loan.setDepositPaymentType(PaymentType.CASH);
        if (deposit.signum() == 0) loan.setDepositPaymentType(null);
        loan.setDeposit(deposit);
        if (loan.getGivenAt() == null) loan.setGivenAt(LocalDateTime.now());
        if (loan.getDueAt() != null && loan.getDueAt().isBefore(loan.getGivenAt().toLocalDate().atStartOfDay())) {
            throw new ValidationException("İade tarihi, emanet tarihinden önce olamaz.");
        }

        BigDecimal finalDeposit = deposit;
        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            DeviceLoanRepository loans = handle.attach(DeviceLoanRepository.class);
            if (loans.findOpenByDevice(loan.getDeviceId()).isPresent()) {
                throw new ValidationException("Bu cihazın zaten açık bir emaneti var. Önce onu kapatın.");
            }
            LocalDateTime now = LocalDateTime.now();
            loan.setCreatedAt(now);
            loan.setUpdatedAt(now);
            Long id = loans.insert(loan);
            loan.setId(id);

            if (finalDeposit.signum() > 0) {
                recordCash(handle, id, finalDeposit, loan.getDepositPaymentType(),
                        "Emanet depozitosu · " + safe(partyName), loan.getGivenAt());
            }
            return loan;
        }));
    }

    /**
     * Emaneti kapatır. Yedek cihazda hasar bedeli depozitodan kesilir, kalan iade edilir
     * (kasadan çıkış). Hasar bedeli depozitoyu aşamaz.
     *
     * @param refundType iade ödemesinin yöntemi; null ise depozitonun alındığı yöntem
     */
    public CompletableFuture<DeviceLoan> close(Long loanId, LocalDateTime returnedAt, String conditionIn,
                                               BigDecimal damageCharge, PaymentType refundType, String partyName) {
        if (loanId == null) throw new ValidationException("Emanet kaydı bulunamadı.");
        BigDecimal damage = damageCharge != null ? damageCharge : BigDecimal.ZERO;
        if (damage.signum() < 0) throw new ValidationException("Hasar bedeli eksi olamaz.");

        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            DeviceLoanRepository loans = handle.attach(DeviceLoanRepository.class);
            DeviceLoan loan = loans.findById(loanId)
                    .orElseThrow(() -> new ValidationException("Emanet kaydı bulunamadı."));
            if (!loan.isOpen()) throw new ValidationException("Bu emanet zaten kapatılmış.");

            BigDecimal deposit = loan.getDeposit() != null ? loan.getDeposit() : BigDecimal.ZERO;
            BigDecimal kept = loan.getDirection() == LoanDirection.OUT ? damage : BigDecimal.ZERO;
            if (kept.compareTo(deposit) > 0) {
                throw new ValidationException("Hasar bedeli depozitodan (" + deposit.toPlainString()
                        + ") fazla olamaz. Aşan kısmı ayrıca tahsil edin.");
            }
            BigDecimal refund = deposit.subtract(kept);
            LocalDateTime when = returnedAt != null ? returnedAt : LocalDateTime.now();

            loan.setReturnedAt(when);
            loan.setConditionIn(conditionIn);
            loan.setDamageCharge(kept);
            loan.setDepositRefund(refund);
            loan.setUpdatedAt(LocalDateTime.now());
            if (loans.close(loan) != 1) throw new ValidationException("Bu emanet zaten kapatılmış.");

            if (refund.signum() > 0) {
                PaymentType type = refundType != null ? refundType
                        : (loan.getDepositPaymentType() != null ? loan.getDepositPaymentType() : PaymentType.CASH);
                String note = "Emanet depozito iadesi · " + safe(partyName)
                        + (kept.signum() > 0 ? " (hasar kesintisi " + kept.toPlainString() + ")" : "");
                recordCash(handle, loan.getId(), refund.negate(), type, note, when);
            }
            return loan;
        }));
    }

    /**
     * Yanlış açılmış emaneti iptal eder: kayıt gizlenir, depozito kasa hareketi silinir (para hiç
     * el değiştirmemiş sayılır). Yalnızca açık emanet iptal edilir; gerçekten verilip geri gelen
     * emanet iade akışıyla kapatılmalıdır, geçmiş ve kasa izi korunur.
     */
    public CompletableFuture<Void> cancel(Long loanId) {
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            DeviceLoanRepository loans = handle.attach(DeviceLoanRepository.class);
            DeviceLoan loan = loans.findById(loanId)
                    .orElseThrow(() -> new ValidationException("Emanet kaydı bulunamadı."));
            if (!loan.isOpen()) {
                throw new ValidationException("Kapatılmış emanet iptal edilemez; geçmiş ve kasa kaydı korunur.");
            }
            PaymentRepository payments = handle.attach(PaymentRepository.class);
            PaymentAllocationRepository allocations = handle.attach(PaymentAllocationRepository.class);
            for (PaymentAllocation a : allocations.findByTarget(AllocationTargetType.DEVICE_LOAN, loanId)) {
                allocations.delete(a.getId()); // FK cascade'e güvenmeden önce tahsis
                payments.delete(a.getPaymentId());
            }
            loans.softDelete(loanId);
        }));
    }

    /** Müşteri detayındaki Emanetler bölümü: açıklar önce. */
    public CompletableFuture<List<DeviceLoan>> getByCustomer(Long customerId) {
        return DbExecutor.supply(() -> repository.findByCustomer(customerId));
    }

    public CompletableFuture<Optional<DeviceLoan>> getOpenByDevice(Long deviceId) {
        return DbExecutor.supply(() -> repository.findOpenByDevice(deviceId));
    }

    public CompletableFuture<List<DeviceLoan>> getByDevice(Long deviceId) {
        return DbExecutor.supply(() -> repository.findByDevice(deviceId));
    }

    public CompletableFuture<Optional<DeviceLoan>> get(Long id) {
        return DbExecutor.supply(() -> repository.findById(id));
    }

    public CompletableFuture<List<DeviceLoan>> getOpen() {
        return DbExecutor.supply(repository::findOpen);
    }

    /** Emanete bağlı, müşteriye bağlanmamış kasa hareketi. */
    private static void recordCash(org.jdbi.v3.core.Handle handle, Long loanId, BigDecimal amount,
                                   PaymentType type, String note, LocalDateTime date) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setCustomerId(null);
        payment.setAmount(amount);
        payment.setPaymentType(type);
        payment.setNote(note);
        payment.setPaymentDate(date != null ? date : now);
        payment.setCreatedAt(now);
        Long paymentId = handle.attach(PaymentRepository.class).insert(payment);

        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPaymentId(paymentId);
        allocation.setTargetType(AllocationTargetType.DEVICE_LOAN);
        allocation.setTargetId(loanId);
        allocation.setAmount(amount);
        allocation.setCreatedAt(now);
        handle.attach(PaymentAllocationRepository.class).insert(allocation);
    }

    private static String safe(String s) {
        return s != null && !s.isBlank() ? s : "müşteri";
    }
}
