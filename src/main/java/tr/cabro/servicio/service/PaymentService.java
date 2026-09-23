package tr.cabro.servicio.service;

import org.jdbi.v3.core.Handle;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.AccountRepository;
import tr.cabro.servicio.database.repository.PaymentAllocationRepository;
import tr.cabro.servicio.database.repository.PaymentRepository;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.dto.AllocationSumDto;
import tr.cabro.servicio.model.dto.CustomerBalanceDto;
import tr.cabro.servicio.model.dto.OpenDocumentDto;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * "Para alındı/verildi" olayı ({@link Payment}) ile bunun hangi belgeye
 * (iş emri/satış) ne kadar tahsis edildiğini ({@link PaymentAllocation}) yönetir.
 * Belge bazlı ödeme durumu (Ödenmedi/Kısmi/Ödendi) burada saklanmaz — tahsis
 * toplamından türetilir (bkz. {@code PaymentStatus}).
 */
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final AccountRepository accountRepository;

    public PaymentService(PaymentRepository paymentRepository, PaymentAllocationRepository allocationRepository,
                           AccountRepository accountRepository) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Tek bir belgeye tam tutar tahsis edilen basit ödeme — iş emri/satış ekranlarındaki
     * "bu ödeme sadece bu belgeye ait" akışı. Cari ekranından birden fazla belgeye
     * dağıtılan tahsilat için {@link #recordCollection} kullanılır.
     */
    public CompletableFuture<Payment> recordPaymentForTarget(AllocationTargetType targetType, Long targetId,
                                                               Long customerId, BigDecimal amount,
                                                               PaymentType paymentType, String note,
                                                               LocalDateTime paymentDate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new ValidationException("Tutar 0 olamaz.");
        }
        if (targetId == null) {
            throw new ValidationException("Ödemenin bağlanacağı belge belirtilmelidir.");
        }

        return CompletableFuture.supplyAsync(() -> DatabaseManager.inTransaction(handle -> {
            PaymentRepository paymentRepo = handle.attach(PaymentRepository.class);
            PaymentAllocationRepository allocationRepo = handle.attach(PaymentAllocationRepository.class);

            Payment payment = new Payment();
            payment.setCustomerId(customerId);
            payment.setAmount(amount);
            payment.setPaymentType(paymentType);
            payment.setNote(note);
            payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDateTime.now());
            payment.setCreatedAt(LocalDateTime.now());
            Long paymentId = paymentRepo.insert(payment);
            payment.setId(paymentId);

            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPaymentId(paymentId);
            allocation.setTargetType(targetType);
            allocation.setTargetId(targetId);
            allocation.setAmount(amount);
            allocation.setCreatedAt(LocalDateTime.now());
            allocationRepo.insert(allocation);

            return payment;
        }));
    }

    /**
     * Cari ekranından/iş emri ekranından yapılan tahsilat — TEK bir {@link Payment} birden fazla
     * belgeye ({@code allocations}) dağıtılabilir. Dağıtım tutarları toplamı {@code amount}'ı
     * geçemez; eksik kalan (dağıtılmamış) fark otomatik olarak müşterinin avans bakiyesi olur —
     * çünkü {@code v_customer_balances.total_paid} tüm {@code payments} tablosunu toplar,
     * tahsis edilip edilmediğine bakmaz.
     */
    public CompletableFuture<Payment> recordCollection(Long customerId, BigDecimal amount, PaymentType paymentType,
                                                        String note, LocalDateTime paymentDate,
                                                        List<PaymentAllocation> allocations) {
        if (customerId == null) {
            throw new ValidationException("Tahsilat için müşteri seçilmelidir.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Tutar 0'dan büyük olmalıdır.");
        }
        BigDecimal allocatedTotal = allocations == null ? BigDecimal.ZERO
                : allocations.stream().map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedTotal.compareTo(amount) > 0) {
            throw new ValidationException("Dağıtılan tutar, tahsilat tutarından büyük olamaz.");
        }

        return CompletableFuture.supplyAsync(() -> DatabaseManager.inTransaction(handle -> {
            PaymentRepository paymentRepo = handle.attach(PaymentRepository.class);
            PaymentAllocationRepository allocationRepo = handle.attach(PaymentAllocationRepository.class);

            LocalDateTime now = LocalDateTime.now();
            Payment payment = new Payment();
            payment.setCustomerId(customerId);
            payment.setAmount(amount);
            payment.setPaymentType(paymentType);
            payment.setNote(note);
            payment.setPaymentDate(paymentDate != null ? paymentDate : now);
            payment.setCreatedAt(now);
            Long paymentId = paymentRepo.insert(payment);
            payment.setId(paymentId);

            if (allocations != null) {
                for (PaymentAllocation allocation : allocations) {
                    if (allocation.getAmount() == null || allocation.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
                    allocation.setPaymentId(paymentId);
                    allocation.setCreatedAt(now);
                    allocationRepo.insert(allocation);
                }
            }

            return payment;
        }));
    }

    /** Günlük kasa raporu — ödeme yöntemi bazında gün içi net toplam (iade tutarları negatif olduğu için doğal olarak netleşir). */
    public CompletableFuture<Map<PaymentType, BigDecimal>> getDailyBreakdown(java.time.LocalDate date) {
        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = start.plusDays(1);
        return CompletableFuture.supplyAsync(() -> paymentRepository.sumByTypeForDateRange(start, end).stream()
                .collect(Collectors.toMap(tr.cabro.servicio.model.dto.PaymentTypeSumDto::getPaymentType,
                        tr.cabro.servicio.model.dto.PaymentTypeSumDto::getTotal)));
    }

    /** Ana sayfadaki "Bugünkü hareketler" — verilen günün ödemeleri (en yeni önce, en fazla {@code limit}). */
    public CompletableFuture<List<Payment>> getPaymentsOn(java.time.LocalDate date, int limit) {
        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = start.plusDays(1);
        return CompletableFuture.supplyAsync(() -> paymentRepository.findByDateRange(start, end, limit));
    }

    /** Borçlu müşterilerin toplam bakiyesi (açık alacak). */
    public CompletableFuture<BigDecimal> getTotalReceivables() {
        return CompletableFuture.supplyAsync(() -> BigDecimal.valueOf(accountRepository.sumPositiveBalances())
                .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    public CompletableFuture<List<OpenDocumentDto>> getOpenDocuments(Long customerId) {
        return CompletableFuture.supplyAsync(() -> accountRepository.findOpenDocumentsByCustomer(customerId));
    }

    /** Müşteri detayındaki Ödemeler bölümü için — müşterinin tüm tahsilatları, en yeni önce. */
    public CompletableFuture<List<Payment>> getByCustomer(Long customerId) {
        return CompletableFuture.supplyAsync(() -> paymentRepository.findByCustomerId(customerId));
    }

    public CompletableFuture<Optional<CustomerBalanceDto>> getCustomerBalance(Long customerId) {
        return CompletableFuture.supplyAsync(() -> accountRepository.findBalanceByCustomer(customerId));
    }

    public CompletableFuture<PageResult<CustomerBalanceDto>> getCustomersWithBalancePaged(String searchTerm, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        boolean searching = searchTerm != null && !searchTerm.trim().isEmpty();
        String likeTerm = searching ? "%" + searchTerm.trim() + "%" : null;
        return CompletableFuture.supplyAsync(() -> {
            List<CustomerBalanceDto> items = searching
                    ? accountRepository.searchCustomersWithBalancePaged(likeTerm, pageSize, offset)
                    : accountRepository.findCustomersWithBalancePaged(pageSize, offset);
            long total = searching
                    ? accountRepository.countSearchCustomersWithBalance(likeTerm)
                    : accountRepository.countCustomersWithBalance();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<Void> deletePayment(Long paymentId) {
        return CompletableFuture.runAsync(() -> paymentRepository.delete(paymentId));
    }

    public CompletableFuture<List<Payment>> getPaymentsForTarget(AllocationTargetType targetType, Long targetId) {
        return CompletableFuture.supplyAsync(() -> paymentRepository.findByTarget(targetType, targetId));
    }

    public CompletableFuture<BigDecimal> getAllocatedAmount(AllocationTargetType targetType, Long targetId) {
        return CompletableFuture.supplyAsync(() -> allocationRepository.sumByTarget(targetType, targetId));
    }

    /** Liste ekranları için — N+1 yerine tek sorguda hedef id -> tahsis toplamı. */
    public CompletableFuture<Map<Long, BigDecimal>> getAllocatedAmounts(AllocationTargetType targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) return CompletableFuture.completedFuture(Map.of());
        return CompletableFuture.supplyAsync(() -> allocationRepository.sumByTargets(targetType, targetIds).stream()
                .collect(Collectors.toMap(AllocationSumDto::getTargetId, AllocationSumDto::getTotal)));
    }

    /** Tahsis toplamı ile belge toplamından {@link PaymentStatus}'u türetir; saklanan bir durum kolonu YOK. */
    public static PaymentStatus resolveStatus(BigDecimal totalAmount, BigDecimal allocatedAmount) {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal allocated = allocatedAmount == null ? BigDecimal.ZERO : allocatedAmount;

        // İade: toplam eksi, müşteriye verilen para da eksi tahsis olarak bağlı (bkz. SaleService.recordReturn).
        // Pozitif belgelerin kuralı burada her iadeyi "Ödenmedi" yapıyordu; işaretler çevrilip karşılaştırılır.
        if (total.signum() < 0) {
            BigDecimal refundDue = total.negate();
            BigDecimal refunded = allocated.negate();
            if (refunded.signum() <= 0) return PaymentStatus.CREDITED;
            if (refunded.compareTo(refundDue) >= 0) return PaymentStatus.REFUNDED;
            return PaymentStatus.PARTIAL_REFUND;
        }

        if (allocated.compareTo(BigDecimal.ZERO) <= 0) return PaymentStatus.UNPAID;
        if (allocated.compareTo(total) >= 0) return PaymentStatus.PAID;
        return PaymentStatus.PARTIAL;
    }

    /**
     * Bir belge (iş emri/satış) silinirken çağrılır — allocation'ları söker.
     * Payment'ın tek bağı bu belgeyse payment de silinir (yetim kalmaz); birden
     * fazla belgeye dağıtılmışsa payment kalır ve serbest kalan tutar avansa döner.
     * Çağıran taraf bu metodu {@code handle} üzerinden AYNI transaction'ın içinde çağırmalı.
     */
    public void releaseAllocationsForTarget(Handle handle, AllocationTargetType targetType, Long targetId) {
        PaymentAllocationRepository allocationRepo = handle.attach(PaymentAllocationRepository.class);
        PaymentRepository paymentRepo = handle.attach(PaymentRepository.class);

        List<PaymentAllocation> allocations = allocationRepo.findByTarget(targetType, targetId);
        for (PaymentAllocation allocation : allocations) {
            allocationRepo.delete(allocation.getId());
            if (allocationRepo.countByPaymentId(allocation.getPaymentId()) == 0) {
                paymentRepo.delete(allocation.getPaymentId());
            }
        }
    }
}
