package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.database.repository.PaymentAllocationRepository;
import tr.cabro.servicio.database.repository.PaymentRepository;
import tr.cabro.servicio.database.repository.ProductStockMovementRepository;
import tr.cabro.servicio.database.repository.SaleItemRepository;
import tr.cabro.servicio.database.repository.SaleRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.ProductStockMovement;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.DiscountType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.SaleType;
import tr.cabro.servicio.model.enums.StockType;
import tr.cabro.servicio.service.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * POS satışı — {@code sales} + {@code sale_items} + stok çıkışı + ödeme(ler) tek transaction'da
 * yazılır (bkz. {@code DatabaseManager.inTransaction}). Stok yetersizse satış ENGELLENMEZ —
 * market akışında kasadan geri döndürmek yanlış olur; UI (FormPos) satır bazında uyarı gösterir.
 */
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;

    public SaleService(SaleRepository saleRepository, SaleItemRepository saleItemRepository,
                        CustomerRepository customerRepository, PaymentService paymentService) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.customerRepository = customerRepository;
        this.paymentService = paymentService;
    }

    /** {@link #recordReturn} girişi — hangi orijinal satış kaleminden ne kadar iade edileceği. */
    public static class ReturnLine {
        public final Long originalItemId;
        public final int quantity;

        public ReturnLine(Long originalItemId, int quantity) {
            this.originalItemId = originalItemId;
            this.quantity = quantity;
        }
    }

    /**
     * Bir satış kaleminin iade edilebilir kalanı: adet ve müşteriye geri ödenebilecek tutar.
     * Tutar, kalem iskontosu ve fiş iskontosu düşülmüş NET tutardır (fiş iskontosu kalemlere
     * net tutarları oranında dağıtılır); daha önce yapılmış iadeler bu tutardan düşülür.
     * Kısmi iadede kalan tutar kalan adede orantılanır, son adet kalan kuruşu da alır —
     * böylece bir kalemin toplam iadesi hiçbir zaman ödenen net tutarı aşmaz.
     */
    public static class ReturnQuote {
        public final SaleItem item;
        public final int returnable;
        public final BigDecimal refundable;

        ReturnQuote(SaleItem item, int returnable, BigDecimal refundable) {
            this.item = item;
            this.returnable = returnable;
            this.refundable = refundable;
        }

        /** {@code quantity} adet iade edilince geri ödenecek tutar (pozitif). */
        public BigDecimal amountFor(int quantity) {
            if (quantity <= 0 || returnable <= 0) return BigDecimal.ZERO;
            if (quantity >= returnable) return refundable;
            return refundable.multiply(BigDecimal.valueOf(quantity))
                    .divide(BigDecimal.valueOf(returnable), 2, RoundingMode.HALF_UP);
        }
    }

    /** Kalemin iskontolar sonrası net tutarı; fiş iskontosu subtotal/total oranıyla dağıtılır. */
    private static BigDecimal netLineTotal(Sale original, SaleItem item) {
        BigDecimal line = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
        BigDecimal subtotal = original.getSubtotal();
        BigDecimal total = original.getTotalAmount();
        if (subtotal == null || total == null || subtotal.signum() <= 0 || total.compareTo(subtotal) == 0) {
            return line.setScale(2, RoundingMode.HALF_UP);
        }
        return line.multiply(total).divide(subtotal, 2, RoundingMode.HALF_UP);
    }

    private static ReturnQuote quote(Sale original, SaleItem item, SaleItemRepository itemRepo) {
        int alreadyQty = -itemRepo.sumReturnedQuantity(item.getId());
        BigDecimal alreadyAmount = itemRepo.sumReturnedAmount(item.getId()).abs();
        int returnable = item.getQuantity() - alreadyQty;
        BigDecimal refundable = netLineTotal(original, item).subtract(alreadyAmount).max(BigDecimal.ZERO);
        return new ReturnQuote(item, returnable, refundable);
    }

    /** İade paneli için satışın kalemleri ve her birinin iade edilebilir kalanı. */
    public CompletableFuture<List<ReturnQuote>> getReturnQuotes(Long saleId) {
        return CompletableFuture.supplyAsync(() -> DatabaseManager.inTransaction(handle -> {
            SaleRepository saleRepo = handle.attach(SaleRepository.class);
            SaleItemRepository itemRepo = handle.attach(SaleItemRepository.class);
            Sale original = saleRepo.findById(saleId)
                    .orElseThrow(() -> new ValidationException("Satış bulunamadı."));
            List<ReturnQuote> result = new ArrayList<>();
            for (SaleItem item : itemRepo.findBySaleId(saleId)) result.add(quote(original, item, itemRepo));
            return result;
        }));
    }

    /**
     * İade — orijinal satışa {@code parent_sale_id} ile bağlı, {@code type='RETURN'}, negatif
     * miktar/tutarlı YENİ bir satış kaydı (ayrı bir sale_returns tablosu yok). Stok girişi
     * {@link ReferenceType#RETURN} ile geri eklenir. Aynı kalemin iki kez iadesi
     * {@code source_sale_item_id} üzerinden (V20 migration) engellenir.
     * <p>
     * Tüm doğrulama transaction İÇİNDE yapılır (checkout()'un aksine) — böylece bir
     * ValidationException her zaman asenkron olarak {@code .exceptionally()}'e düşer,
     * çağıran taraf senkron throw için try/catch yazmak zorunda kalmaz.
     */
    public CompletableFuture<Sale> recordReturn(Long originalSaleId, List<ReturnLine> lines,
                                                 PaymentType refundType, BigDecimal refundAmount) {
        return CompletableFuture.supplyAsync(() -> DatabaseManager.inTransaction(handle -> {
            if (originalSaleId == null) {
                throw new ValidationException("İade edilecek satış belirtilmelidir.");
            }
            if (lines == null || lines.isEmpty()) {
                throw new ValidationException("İade edilecek en az bir kalem seçilmelidir.");
            }

            SaleRepository saleRepo = handle.attach(SaleRepository.class);
            SaleItemRepository itemRepo = handle.attach(SaleItemRepository.class);
            ProductStockMovementRepository stockRepo = handle.attach(ProductStockMovementRepository.class);
            PaymentRepository paymentRepo = handle.attach(PaymentRepository.class);
            PaymentAllocationRepository allocationRepo = handle.attach(PaymentAllocationRepository.class);

            Sale original = saleRepo.findById(originalSaleId)
                    .orElseThrow(() -> new ValidationException("Orijinal satış bulunamadı."));
            if (original.getType() != SaleType.SALE) {
                throw new ValidationException("Bir iade işlemi tekrar iade edilemez.");
            }

            LocalDateTime now = LocalDateTime.now();
            List<SaleItem> returnItems = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;

            for (ReturnLine line : lines) {
                if (line.quantity <= 0) continue;

                SaleItem originalItem = itemRepo.findById(line.originalItemId)
                        .orElseThrow(() -> new ValidationException("İade edilecek kalem bulunamadı."));
                if (!originalItem.getSaleId().equals(originalSaleId)) {
                    throw new ValidationException("Kalem bu satışa ait değil.");
                }

                ReturnQuote q = quote(original, originalItem, itemRepo);
                if (line.quantity > q.returnable) {
                    throw new ValidationException(
                            "'" + originalItem.getItemName() + "' için en fazla " + q.returnable + " adet iade edilebilir.");
                }
                // İskontolar düşülmüş net tutar; brüt (birim fiyat × adet) ile aradaki fark iskonto olarak
                // kalemde görünür, böylece iade fişi satış fişiyle aynı indirimi taşır.
                BigDecimal refund = q.amountFor(line.quantity);
                BigDecimal gross = originalItem.getUnitPrice().multiply(BigDecimal.valueOf(line.quantity));
                BigDecimal discount = gross.subtract(refund).max(BigDecimal.ZERO);

                SaleItem returnItem = new SaleItem();
                returnItem.setProductId(originalItem.getProductId());
                returnItem.setItemName(originalItem.getItemName());
                returnItem.setQuantity(-line.quantity);
                returnItem.setPurchasePrice(originalItem.getPurchasePrice());
                returnItem.setUnitPrice(originalItem.getUnitPrice());
                returnItem.setSaleCurrency(originalItem.getSaleCurrency());
                returnItem.setUnitPriceOriginal(originalItem.getUnitPriceOriginal());
                returnItem.setSourceSaleItemId(originalItem.getId());
                if (discount.signum() > 0) {
                    returnItem.setLineDiscountType(DiscountType.AMOUNT);
                    returnItem.setLineDiscountValue(discount);
                }
                returnItem.setLineTotal(refund.negate());

                returnItems.add(returnItem);
                subtotal = subtotal.add(returnItem.getLineTotal());
            }
            if (returnItems.isEmpty()) {
                throw new ValidationException("İade edilecek en az bir kalem seçilmelidir.");
            }
            if (refundAmount != null && refundAmount.signum() > 0 && refundAmount.compareTo(subtotal.abs()) > 0) {
                throw new ValidationException("İade tutarı, iade edilen kalemlerin toplamından büyük olamaz.");
            }

            Sale returnSale = new Sale();
            returnSale.setCustomerId(original.getCustomerId());
            returnSale.setType(SaleType.RETURN);
            returnSale.setParentSaleId(originalSaleId);
            returnSale.setSaleDate(now);
            returnSale.setSubtotal(subtotal);
            returnSale.setTotalAmount(subtotal);
            returnSale.setCreatedAt(now);
            returnSale.setUpdatedAt(now);

            Long saleId = saleRepo.insert(returnSale);
            returnSale.setId(saleId);

            for (SaleItem item : returnItems) {
                item.setSaleId(saleId);
                item.setCreatedAt(now);
                Long itemId = itemRepo.insert(item);
                item.setId(itemId);

                if (item.getProductId() != null) {
                    ProductStockMovement movement = new ProductStockMovement();
                    movement.setProductId(item.getProductId());
                    movement.setQuantity(Math.abs(item.getQuantity()));
                    movement.setType(StockType.IN);
                    movement.setReferenceType(ReferenceType.RETURN);
                    movement.setReferenceId(saleId);
                    stockRepo.insert(movement);
                }
            }
            returnSale.setItems(returnItems);

            if (refundAmount != null && refundAmount.signum() > 0) {
                Payment refund = new Payment();
                refund.setCustomerId(original.getCustomerId());
                refund.setAmount(refundAmount.negate());
                refund.setPaymentType(refundType);
                refund.setPaymentDate(now);
                refund.setCreatedAt(now);
                Long paymentId = paymentRepo.insert(refund);

                PaymentAllocation allocation = new PaymentAllocation();
                allocation.setPaymentId(paymentId);
                allocation.setTargetType(AllocationTargetType.SALE);
                allocation.setTargetId(saleId);
                allocation.setAmount(refundAmount.negate());
                allocation.setCreatedAt(now);
                allocationRepo.insert(allocation);
            }

            return returnSale;
        }));
    }

    public CompletableFuture<Sale> checkout(Sale sale, List<Payment> payments) {
        BigDecimal total = computeTotals(sale);
        validate(sale, payments, total);

        return CompletableFuture.supplyAsync(() -> DatabaseManager.inTransaction(handle -> {
            SaleRepository saleRepo = handle.attach(SaleRepository.class);
            SaleItemRepository itemRepo = handle.attach(SaleItemRepository.class);
            ProductStockMovementRepository stockRepo = handle.attach(ProductStockMovementRepository.class);
            PaymentRepository paymentRepo = handle.attach(PaymentRepository.class);
            PaymentAllocationRepository allocationRepo = handle.attach(PaymentAllocationRepository.class);

            sale.setType(SaleType.SALE);
            LocalDateTime now = LocalDateTime.now();
            sale.setSaleDate(sale.getSaleDate() != null ? sale.getSaleDate() : now);
            sale.setCreatedAt(now);
            sale.setUpdatedAt(now);
            Long saleId = saleRepo.insert(sale);
            sale.setId(saleId);

            for (SaleItem item : sale.getItems()) {
                item.setSaleId(saleId);
                item.setCreatedAt(now);
                Long itemId = itemRepo.insert(item);
                item.setId(itemId);

                if (item.getProductId() != null) {
                    ProductStockMovement movement = new ProductStockMovement();
                    movement.setProductId(item.getProductId());
                    movement.setQuantity(-Math.abs(item.getQuantity()));
                    movement.setType(StockType.OUT);
                    movement.setReferenceType(ReferenceType.SALE);
                    movement.setReferenceId(saleId);
                    stockRepo.insert(movement);
                }
            }

            for (Payment payment : payments) {
                payment.setCustomerId(sale.getCustomerId());
                payment.setPaymentDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : now);
                payment.setCreatedAt(now);
                Long paymentId = paymentRepo.insert(payment);
                payment.setId(paymentId);

                PaymentAllocation allocation = new PaymentAllocation();
                allocation.setPaymentId(paymentId);
                allocation.setTargetType(AllocationTargetType.SALE);
                allocation.setTargetId(saleId);
                allocation.setAmount(payment.getAmount());
                allocation.setCreatedAt(now);
                allocationRepo.insert(allocation);
            }

            return sale;
        }));
    }

    private void validate(Sale sale, List<Payment> payments, BigDecimal total) {
        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            throw new ValidationException("Sepette en az bir kalem olmalı.");
        }
        BigDecimal paidTotal = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paidTotal.compareTo(total) < 0 && sale.getCustomerId() == null) {
            throw new ValidationException("Kalan tutar için (veresiye) müşteri seçimi zorunludur.");
        }
        if (paidTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Ödeme tutarı negatif olamaz.");
        }
    }

    /** Kalem/fiş indirimlerini uygulayıp {@code sale.subtotal}/{@code sale.totalAmount}'ı doldurur, nihai tutarı döner. */
    private BigDecimal computeTotals(Sale sale) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SaleItem item : sale.getItems()) {
            BigDecimal lineGross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal lineDiscount = computeDiscount(lineGross, item.getLineDiscountType(), item.getLineDiscountValue());
            BigDecimal lineTotal = lineGross.subtract(lineDiscount);
            item.setLineTotal(lineTotal);
            subtotal = subtotal.add(lineTotal);
        }
        sale.setSubtotal(subtotal);

        BigDecimal saleDiscount = computeDiscount(subtotal, sale.getDiscountType(), sale.getDiscountValue());
        BigDecimal total = subtotal.subtract(saleDiscount);
        sale.setTotalAmount(total);
        return total;
    }

    private BigDecimal computeDiscount(BigDecimal base, DiscountType type, BigDecimal value) {
        if (type == null || value == null || value.signum() <= 0) return BigDecimal.ZERO;
        if (type == DiscountType.PERCENT) {
            // Yüzde %100 ile sınırlanır — aşarsa indirim tabanı geçer ve toplam negatife düşer.
            BigDecimal percent = value.min(BigDecimal.valueOf(100));
            return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        // Tutar indirimi kalem/fiş toplamını geçemez (negatif toplam oluşmasın).
        return value.min(base);
    }

    // =========================================================================
    // LİSTELEME (FormSales)
    // =========================================================================

    public CompletableFuture<PageResult<Sale>> getAllPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return CompletableFuture.supplyAsync(() -> {
            List<Sale> items = hydrateSales(saleRepository.findAllPaged(pageSize, offset));
            long total = saleRepository.countAll();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    /** Liste sayfası: arama + görünüm sekmesi/başlık filtreleri + sayfalama. */
    public CompletableFuture<PageResult<Sale>> searchFilteredPaged(String searchTerm,
            Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            PageResult<Sale> r = saleRepository.searchFilteredPaged(searchTerm, filters, page, pageSize);
            return new PageResult<>(hydrateSales(r.getItems()), r.getPage(), r.getPageSize(), r.getTotalItems());
        });
    }

    public CompletableFuture<PageResult<Sale>> searchFilteredPaged(String searchTerm,
            Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters, int page, int pageSize, String sortKey) {
        return CompletableFuture.supplyAsync(() -> {
            PageResult<Sale> r = saleRepository.searchFilteredPaged(searchTerm, filters, page, pageSize, sortKey);
            return new PageResult<>(hydrateSales(r.getItems()), r.getPage(), r.getPageSize(), r.getTotalItems());
        });
    }

    /** Süzgeçle eşleşen fişlerin net toplamı (liste özeti için). */
    public CompletableFuture<BigDecimal> sumFiltered(Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters) {
        return CompletableFuture.supplyAsync(() -> saleRepository.sumFiltered(filters));
    }

    public CompletableFuture<PageResult<Sale>> searchPaged(String searchTerm, int page, int pageSize) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAllPaged(page, pageSize);
        int offset = (page - 1) * pageSize;
        String likeTerm = "%" + searchTerm.trim() + "%";
        return CompletableFuture.supplyAsync(() -> {
            List<Sale> items = hydrateSales(saleRepository.searchPaged(likeTerm, pageSize, offset));
            long total = saleRepository.countSearch(likeTerm);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    /**
     * Günlük kasa raporu — ödeme yöntemi bazında net toplam (servis tahsilatı + POS satışı + iade
     * hepsi {@code payments} tablosundan geliyor, bkz. PaymentService.getDailyBreakdown) + gün
     * içindeki satış/iade adedi.
     */
    public CompletableFuture<tr.cabro.servicio.model.dto.DailyCashReportDto> getDailyCashReport(java.time.LocalDate date) {
        return getCashReport(date, date);
    }

    /** Kasa raporunun tarih aralığı sürümü; {@code from} ve {@code to} günleri dahil ({@code date} = {@code from}). */
    public CompletableFuture<tr.cabro.servicio.model.dto.DailyCashReportDto> getCashReport(java.time.LocalDate from, java.time.LocalDate to) {
        java.time.LocalDate date = from;
        java.time.LocalDateTime start = from.atStartOfDay();
        java.time.LocalDateTime end = to.plusDays(1).atStartOfDay();

        return paymentService.getBreakdown(from, to).thenApply(breakdown -> {
            long saleCount = saleRepository.countByTypeForDateRange(SaleType.SALE, start, end);
            long returnCount = saleRepository.countByTypeForDateRange(SaleType.RETURN, start, end);
            BigDecimal total = breakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            tr.cabro.servicio.model.dto.DailyCashReportDto dto = new tr.cabro.servicio.model.dto.DailyCashReportDto();
            dto.setDate(date);
            dto.setBreakdown(breakdown);
            dto.setTotal(total);
            dto.setSaleCount(saleCount);
            dto.setReturnCount(returnCount);
            return dto;
        });
    }

    /** İade panelinde kalem başına gösterilecek "kalan iade edilebilir miktar" haritası. */
    public CompletableFuture<Map<Long, Integer>> getReturnableQuantities(Long saleId) {
        return CompletableFuture.supplyAsync(() -> {
            List<SaleItem> items = saleItemRepository.findBySaleId(saleId);
            Map<Long, Integer> result = new java.util.HashMap<>();
            for (SaleItem item : items) {
                int alreadyReturned = -saleItemRepository.sumReturnedQuantity(item.getId());
                result.put(item.getId(), item.getQuantity() - alreadyReturned);
            }
            return result;
        });
    }

    /** Satış detayı: bu satışa bağlı iade fişleri (en yeni önce). */
    public CompletableFuture<List<Sale>> getReturnsOf(Long saleId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Sale> returns = new ArrayList<>(saleRepository.findByParentSaleId(saleId));
            returns.sort(java.util.Comparator.comparing(Sale::getSaleDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed());
            return hydrateSales(returns);
        });
    }

    /** Ürün detayı: ürünün geçtiği kalemler ({@link SaleItem#getSaleId()} ile) ve o fişler. */
    public CompletableFuture<ProductSales> getProductSales(Long productId) {
        return CompletableFuture.supplyAsync(() -> {
            List<SaleItem> items = saleItemRepository.findByProductId(productId);
            List<Long> saleIds = items.stream().map(SaleItem::getSaleId).distinct().collect(Collectors.toList());
            List<Sale> sales = new ArrayList<>();
            for (Long id : saleIds) saleRepository.findById(id).ifPresent(sales::add);
            return new ProductSales(items, hydrateSales(sales));
        });
    }

    /** {@link #getProductSales} sonucu: kalemler ve fişler (fişler en yeni önce). */
    public static class ProductSales {
        public final List<SaleItem> items;
        public final List<Sale> sales;

        ProductSales(List<SaleItem> items, List<Sale> sales) {
            this.items = items;
            this.sales = sales;
        }
    }

    /** Müşteri detayındaki Satışlar bölümü için — müşterinin satış ve iadeleri, en yeni önce. */
    public CompletableFuture<List<Sale>> getByCustomer(Long customerId) {
        return CompletableFuture.supplyAsync(() -> hydrateSales(saleRepository.findByCustomerId(customerId)));
    }

    public CompletableFuture<Optional<Sale>> getById(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Sale> opt = saleRepository.findById(id);
            opt.ifPresent(sale -> {
                sale.setItems(saleItemRepository.findBySaleId(sale.getId()));
                hydrateSales(List.of(sale));
            });
            return opt;
        });
    }

    /** Müşteri nesnesini ve toplam tahsis edilmiş tutarı (PaymentStatus türetmek için) doldurur. */
    private List<Sale> hydrateSales(List<Sale> sales) {
        if (sales == null || sales.isEmpty()) return sales;

        List<Long> customerIds = sales.stream()
                .map(Sale::getCustomerId).filter(id -> id != null).distinct().collect(Collectors.toList());
        Map<Long, Customer> customerMap = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerRepository.findByIds(customerIds).stream().collect(Collectors.toMap(Customer::getId, c -> c));

        List<Long> saleIds = sales.stream().map(Sale::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> allocatedMap = paymentService.getAllocatedAmounts(AllocationTargetType.SALE, saleIds).join();

        for (Sale sale : sales) {
            sale.setCustomer(customerMap.get(sale.getCustomerId()));
            sale.setTotalPaid(allocatedMap.getOrDefault(sale.getId(), BigDecimal.ZERO));
        }
        return sales;
    }
}
