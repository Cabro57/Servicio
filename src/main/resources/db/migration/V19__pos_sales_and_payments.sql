-- =========================================================================
-- V19__pos_sales_and_payments.sql
-- POS satış modülü + birleşik ödeme/tahsis katmanı + ürün/parça ayrımı.
--
-- Tasarım özeti (bkz. proje planı):
--  - parts tek stok kaynağı kalır; is_service_part/is_sale_product bayraklarıyla
--    UI'da ayrı formlar (Parçalar/Ürünler) beslenir, arka planda tek tablo kalır.
--  - payments = "para alındı/verildi" olayı (kasa raporunun kaynağı).
--    payment_allocations = paranın hangi belgeye (iş emri/satış) dağıtıldığı
--    (belge bazlı Ödenmedi/Kısmi/Ödendi durumunun kaynağı). Tahsis edilmemiş
--    fark müşterinin avans bakiyesi olarak kalır.
--  - work_order_payments buraya taşınır ve düşürülür; eski id'ler KORUNUR
--    çünkü payment_allocations bu id'ler üzerinden work_orders'a bağlanıyor.
--  - İade ayrı tablo değil: sales.type='RETURN' + parent_sale_id ile orijinale
--    bağlı, negatif miktar/tutarlı yeni bir sales kaydı.
-- =========================================================================

-- ---------------------------------------------------------
-- 1) Ürün / parça ayrımı (tek stok kaynağı korunur)
-- ---------------------------------------------------------
ALTER TABLE parts ADD COLUMN is_service_part BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE parts ADD COLUMN is_sale_product BOOLEAN NOT NULL DEFAULT 0;

CREATE INDEX idx_parts_is_sale_product ON parts(is_sale_product);

-- ---------------------------------------------------------
-- 2) Satışlar
-- ---------------------------------------------------------
CREATE TABLE sales (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id      INTEGER NULL,                    -- NULL = perakende/hızlı satış
    type             VARCHAR(10) NOT NULL DEFAULT 'SALE', -- 'SALE' | 'RETURN'
    parent_sale_id   INTEGER NULL,                    -- RETURN ise orijinal satışa referans
    sale_date        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subtotal         DECIMAL(12,2) NOT NULL DEFAULT 0.0, -- kalem line_total toplamı
    discount_type    VARCHAR(10) NULL,                -- 'AMOUNT' | 'PERCENT' | NULL
    discount_value   DECIMAL(12,2) NOT NULL DEFAULT 0.0,
    tax_rate         DECIMAL(5,2) NOT NULL DEFAULT 0.0,  -- KDV altyapısı hazır, hesap yok
    total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.0, -- fiş indirimi düşülmüş nihai tutar
    note             TEXT,
    is_deleted       BOOLEAN DEFAULT 0,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id)    REFERENCES customers(id) ON DELETE RESTRICT,
    FOREIGN KEY (parent_sale_id) REFERENCES sales(id)     ON DELETE RESTRICT
);

CREATE INDEX idx_sales_customer   ON sales(customer_id);
CREATE INDEX idx_sales_date       ON sales(sale_date);
CREATE INDEX idx_sales_parent     ON sales(parent_sale_id);
CREATE INDEX idx_sales_type       ON sales(type);

CREATE TABLE sale_items (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id          INTEGER NOT NULL,
    part_id          INTEGER NULL,                    -- NULL = katalogda olmayan manuel kalem
    item_name        VARCHAR(255) NOT NULL,            -- snapshot (Part.name kopyası veya elle yazılan)
    quantity         INTEGER NOT NULL DEFAULT 1,
    purchase_price   DECIMAL(12,2) DEFAULT 0.0,        -- satış anındaki maliyet snapshot'ı (kâr raporu için)
    unit_price       DECIMAL(12,2) NOT NULL,           -- TL kanonik birim satış fiyatı
    sale_currency    TEXT NOT NULL DEFAULT 'TRY',
    unit_price_original DECIMAL(12,2) NULL,            -- girişte seçilen dövizden ham tutar
    line_discount_type  VARCHAR(10) NULL,              -- 'AMOUNT' | 'PERCENT' | NULL
    line_discount_value DECIMAL(12,2) NOT NULL DEFAULT 0.0,
    line_total       DECIMAL(12,2) NOT NULL,           -- satır indirimi düşülmüş (quantity dahil)
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (part_id) REFERENCES parts(id) ON DELETE RESTRICT
);

CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_part ON sale_items(part_id);

-- ---------------------------------------------------------
-- 3) Birleşik ödeme / tahsis katmanı
-- ---------------------------------------------------------
CREATE TABLE payments (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id  INTEGER NULL,                        -- NULL = perakende/hızlı satış ödemesi
    amount       DECIMAL(12,2) NOT NULL,               -- iade tahsilatlarında negatif olabilir
    payment_type VARCHAR(20)   NOT NULL,
    note         TEXT,
    payment_date TIMESTAMP,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT
);

CREATE INDEX idx_payments_customer ON payments(customer_id);
CREATE INDEX idx_payments_date     ON payments(payment_date);

-- target_type/target_id polimorfik olduğu için gerçek bir FK kurulamıyor
-- (WORK_ORDER -> work_orders, SALE -> sales); uygulama katmanında yönetilir.
CREATE TABLE payment_allocations (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    payment_id   INTEGER NOT NULL,
    target_type  VARCHAR(20) NOT NULL,                 -- 'WORK_ORDER' | 'SALE'
    target_id    INTEGER NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

CREATE INDEX idx_payment_allocations_payment ON payment_allocations(payment_id);
CREATE INDEX idx_payment_allocations_target  ON payment_allocations(target_type, target_id);

-- ---------------------------------------------------------
-- 4) work_order_payments -> payments taşıması
--    ESKİ id KORUNUR: payment_allocations bu id'ler üzerinden work_orders'a bağlanacak.
-- ---------------------------------------------------------
INSERT INTO payments (id, customer_id, amount, payment_type, note, payment_date, created_at)
SELECT wop.id, wo.customer_id, wop.amount, wop.payment_type, wop.note, wop.payment_date, wop.created_at
FROM work_order_payments wop
JOIN work_orders wo ON wo.id = wop.service_id;

INSERT INTO payment_allocations (payment_id, target_type, target_id, amount)
SELECT id, 'WORK_ORDER', service_id, amount FROM work_order_payments;

DROP TABLE work_order_payments;

-- payments tablosundaki AUTOINCREMENT sayacını taşınan en yüksek id'ye göre ilerlet;
-- aksi halde yeni bir payment eski (taşınmış) bir id ile çakışabilir.
UPDATE sqlite_sequence SET seq = (SELECT COALESCE(MAX(id), 0) FROM payments) WHERE name = 'payments';

-- ---------------------------------------------------------
-- 5) Raporlama view'ları
-- ---------------------------------------------------------

-- Her belgenin (iş emri / satış) toplam tutarı, tek şemada.
CREATE VIEW v_document_totals AS
SELECT
    'WORK_ORDER' AS document_type,
    wo.id        AS document_id,
    wo.customer_id,
    wo.created_at AS document_date,
    COALESCE((SELECT SUM(unit_price * quantity) FROM work_order_items WHERE service_id = wo.id), 0.0) AS total_amount
FROM work_orders wo
WHERE wo.is_deleted = 0
UNION ALL
SELECT
    'SALE' AS document_type,
    s.id   AS document_id,
    s.customer_id,
    s.sale_date AS document_date,
    s.total_amount
FROM sales s
WHERE s.is_deleted = 0;

-- Her belgenin tahsis edilmiş tutarı ve kalanı.
CREATE VIEW v_document_balances AS
SELECT
    dt.document_type,
    dt.document_id,
    dt.customer_id,
    dt.document_date,
    dt.total_amount,
    COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
              WHERE pa.target_type = dt.document_type AND pa.target_id = dt.document_id), 0.0) AS allocated_amount,
    dt.total_amount - COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
              WHERE pa.target_type = dt.document_type AND pa.target_id = dt.document_id), 0.0) AS remaining_amount
FROM v_document_totals dt;

-- Müşteri bazında toplam borç / tahsilat / bakiye.
CREATE VIEW v_customer_balances AS
SELECT
    c.id AS customer_id,
    COALESCE((SELECT SUM(total_amount) FROM v_document_totals WHERE customer_id = c.id), 0.0) AS total_debt,
    COALESCE((SELECT SUM(amount) FROM payments WHERE customer_id = c.id), 0.0) AS total_paid,
    COALESCE((SELECT SUM(total_amount) FROM v_document_totals WHERE customer_id = c.id), 0.0)
        - COALESCE((SELECT SUM(amount) FROM payments WHERE customer_id = c.id), 0.0) AS balance
FROM customers c
WHERE c.is_deleted = 0;
