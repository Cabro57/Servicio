-- =====================================================================
-- V25 — Depo sistemi + kontrollü stok defteri
-- =====================================================================
-- Amaç: stok yalnızca hareket defterinden değişir, her hareketin deposu, kaynağı ve notu vardır.
--   * warehouses genişletildi (varsayılan depo, pasif depo, açıklama, sıra).
--   * Parça ve ürün defterlerine not + birim maliyet eklendi; ürün defterine depo eklendi.
--   * stock_movements.product_id → part_id (parçaya bakıyordu, isim yanıltıcıydı).
--   * Depo bazında bakiye tabloları (part_stock_levels / product_stock_levels) tetikleyiciyle tutulur;
--     parts/products.stock_quantity tüm depoların toplamı olarak kalır (liste/rapor sorguları bozulmaz).
--   * Servis ve satış kalemleri stoğun hangi depodan düştüğünü saklar (iade/iptal aynı depoya döner).
--   * Hareketler değiştirilemez/silinemez; düzeltme yeni hareketle yapılır.
--
-- Sıra önemli: eski tetikleyiciler önce kaldırılır, geçiş hareketleri yazılır, bakiyeler defterden
-- hesaplanır ve yeni tetikleyiciler EN SONDA kurulur (V7/V21'deki çift sayımı önleme deseni).

-- ---------------------------------------------------------------------
-- 1) Depolar
-- ---------------------------------------------------------------------
ALTER TABLE warehouses ADD COLUMN description TEXT;
ALTER TABLE warehouses ADD COLUMN is_default  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE warehouses ADD COLUMN is_active   INTEGER NOT NULL DEFAULT 1;
ALTER TABLE warehouses ADD COLUMN sort_order  INTEGER NOT NULL DEFAULT 0;

INSERT INTO warehouses (name) SELECT 'Ana Depo' WHERE NOT EXISTS (SELECT 1 FROM warehouses);
UPDATE warehouses SET is_default = CASE WHEN id = (SELECT MIN(id) FROM warehouses) THEN 1 ELSE 0 END;

-- Aynı anda yalnızca bir varsayılan depo olabilir.
CREATE UNIQUE INDEX ux_warehouses_default ON warehouses(is_default) WHERE is_default = 1;

-- ---------------------------------------------------------------------
-- 2) Eski tetikleyiciler (IN/OUT ayrı, yalnızca toplamı güncelliyordu)
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_stock_movement_in;
DROP TRIGGER IF EXISTS trg_stock_movement_out;
DROP TRIGGER IF EXISTS trg_product_stock_movement_in;
DROP TRIGGER IF EXISTS trg_product_stock_movement_out;

-- ---------------------------------------------------------------------
-- 3) Defter sütunları
-- ---------------------------------------------------------------------
ALTER TABLE stock_movements RENAME COLUMN product_id TO part_id;
ALTER TABLE stock_movements ADD COLUMN unit_cost DECIMAL(12,2);
ALTER TABLE stock_movements ADD COLUMN note TEXT;

-- FK'lı sütun ALTER ile yalnızca NULL varsayılanla eklenebilir; değer hemen aşağıda doldurulur.
ALTER TABLE product_stock_movements ADD COLUMN warehouse_id INTEGER REFERENCES warehouses(id) ON DELETE RESTRICT;
ALTER TABLE product_stock_movements ADD COLUMN unit_cost DECIMAL(12,2);
ALTER TABLE product_stock_movements ADD COLUMN note TEXT;
UPDATE product_stock_movements SET warehouse_id = (SELECT id FROM warehouses WHERE is_default = 1);

-- Enum dışında kalmış eski kaynak değerleri (V7/V21 geçişleri) — okurken enum eşlemesi patlamasın.
UPDATE stock_movements SET reference_type = 'OPENING'
    WHERE reference_type IN ('MIGRATION_INITIAL', 'MIGRATION');
UPDATE stock_movements SET reference_type = 'ADJUSTMENT'
    WHERE reference_type IS NULL OR reference_type NOT IN
        ('WORK_ORDER', 'WORK_ORDER_CANCEL', 'PURCHASE', 'SALE', 'ADJUSTMENT', 'RETURN', 'OPENING');
UPDATE product_stock_movements SET reference_type = 'OPENING'
    WHERE reference_type IN ('MIGRATION_INITIAL', 'MIGRATION');
UPDATE product_stock_movements SET reference_type = 'ADJUSTMENT'
    WHERE reference_type IS NULL OR reference_type NOT IN
        ('WORK_ORDER', 'WORK_ORDER_CANCEL', 'PURCHASE', 'SALE', 'ADJUSTMENT', 'RETURN', 'OPENING');

CREATE INDEX idx_stock_movements_ref         ON stock_movements(reference_type, reference_id);
CREATE INDEX idx_stock_movements_wh          ON stock_movements(warehouse_id);
CREATE INDEX idx_product_stock_movements_ref ON product_stock_movements(reference_type, reference_id);
CREATE INDEX idx_product_stock_movements_wh  ON product_stock_movements(warehouse_id);

-- ---------------------------------------------------------------------
-- 4) Kalemlerde depo bilgisi
-- ---------------------------------------------------------------------
ALTER TABLE work_order_items ADD COLUMN warehouse_id INTEGER REFERENCES warehouses(id) ON DELETE SET NULL;
ALTER TABLE sale_items       ADD COLUMN warehouse_id INTEGER REFERENCES warehouses(id) ON DELETE SET NULL;

UPDATE work_order_items SET warehouse_id = (SELECT id FROM warehouses WHERE is_default = 1)
    WHERE item_type = 'PART' AND part_id IS NOT NULL;
UPDATE sale_items SET warehouse_id = (SELECT id FROM warehouses WHERE is_default = 1)
    WHERE product_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5) Servis kayıtlarını defterle eşitle (stok DEĞİŞMEZ)
-- ---------------------------------------------------------------------
-- Yeni düzende bir servisin defterdeki net tüketimi = kalemlerindeki parça adetleri ("İade"
-- durumundaki serviste sıfır). Eski sürümde kalem kaydedilip stok düşümü başarısız olabiliyordu
-- ve "İade" durumu stoğa dokunmuyordu; bu farklar kapatılmazsa ilk düzenlemede beklenmedik stok
-- hareketi yazılırdı. Her fark için servis hareketi + zıt yönde düzeltme yazılır: net etki sıfır,
-- kullanıcının gördüğü stok aynı kalır, defter servisle tutarlı olur.
CREATE TEMP TABLE v25_wo_diff AS
WITH desired AS (
    SELECT i.service_id AS wo, i.part_id, SUM(i.quantity) AS qty
    FROM work_order_items i
    JOIN work_orders w ON w.id = i.service_id
    WHERE i.item_type = 'PART' AND i.part_id IS NOT NULL AND w.service_status != 'RETURN'
    GROUP BY i.service_id, i.part_id
), actual AS (
    SELECT reference_id AS wo, part_id, -SUM(quantity) AS qty
    FROM stock_movements
    WHERE reference_type IN ('WORK_ORDER', 'WORK_ORDER_CANCEL')
      AND reference_id IN (SELECT id FROM work_orders)
    GROUP BY reference_id, part_id
), keys AS (
    SELECT wo, part_id FROM desired UNION SELECT wo, part_id FROM actual
)
SELECT k.wo, k.part_id, COALESCE(d.qty, 0) - COALESCE(a.qty, 0) AS diff
FROM keys k
LEFT JOIN desired d ON d.wo = k.wo AND d.part_id = k.part_id
LEFT JOIN actual  a ON a.wo = k.wo AND a.part_id = k.part_id
WHERE k.part_id IN (SELECT id FROM parts);

INSERT INTO stock_movements (part_id, warehouse_id, quantity, type, reference_type, reference_id, note)
SELECT part_id, (SELECT id FROM warehouses WHERE is_default = 1), -diff,
       CASE WHEN diff > 0 THEN 'OUT' ELSE 'IN' END,
       CASE WHEN diff > 0 THEN 'WORK_ORDER' ELSE 'WORK_ORDER_CANCEL' END,
       wo, 'Sürüm geçişi: servis kalemleriyle eşitlendi'
FROM v25_wo_diff WHERE diff != 0;

INSERT INTO stock_movements (part_id, warehouse_id, quantity, type, reference_type, reference_id, note)
SELECT part_id, (SELECT id FROM warehouses WHERE is_default = 1), diff,
       CASE WHEN diff > 0 THEN 'IN' ELSE 'OUT' END,
       'ADJUSTMENT', NULL,
       'Sürüm geçişi: servis #' || wo || ' eşitlemesinin karşılığı, stok değişmedi'
FROM v25_wo_diff WHERE diff != 0;

DROP TABLE v25_wo_diff;

-- ---------------------------------------------------------------------
-- 6) Kayıtlı stok ile defter toplamını eşitle
-- ---------------------------------------------------------------------
-- Eski sürümlerde stok_quantity doğrudan yazılabiliyordu (ör. düzenlemede stok ikiye katlanma
-- bug'ı); kullanıcının ekranda gördüğü değer esas alınır, fark düzeltme hareketi olarak yazılır.
INSERT INTO stock_movements (part_id, warehouse_id, quantity, type, reference_type, note)
SELECT p.id,
       COALESCE(p.warehouse_id, (SELECT id FROM warehouses WHERE is_default = 1)),
       COALESCE(p.stock_quantity, 0) - COALESCE(s.total, 0),
       CASE WHEN COALESCE(p.stock_quantity, 0) - COALESCE(s.total, 0) > 0 THEN 'IN' ELSE 'OUT' END,
       'ADJUSTMENT', 'Sürüm geçişi: kayıtlı stok ile hareket toplamı eşitlendi'
FROM parts p
LEFT JOIN (SELECT part_id, SUM(quantity) AS total FROM stock_movements GROUP BY part_id) s ON s.part_id = p.id
WHERE COALESCE(p.stock_quantity, 0) != COALESCE(s.total, 0);

INSERT INTO product_stock_movements (product_id, warehouse_id, quantity, type, reference_type, note)
SELECT p.id,
       (SELECT id FROM warehouses WHERE is_default = 1),
       COALESCE(p.stock_quantity, 0) - COALESCE(s.total, 0),
       CASE WHEN COALESCE(p.stock_quantity, 0) - COALESCE(s.total, 0) > 0 THEN 'IN' ELSE 'OUT' END,
       'ADJUSTMENT', 'Sürüm geçişi: kayıtlı stok ile hareket toplamı eşitlendi'
FROM products p
LEFT JOIN (SELECT product_id, SUM(quantity) AS total FROM product_stock_movements GROUP BY product_id) s ON s.product_id = p.id
WHERE COALESCE(p.stock_quantity, 0) != COALESCE(s.total, 0);

-- ---------------------------------------------------------------------
-- 7) Depo bazında bakiyeler
-- ---------------------------------------------------------------------
CREATE TABLE part_stock_levels (
    part_id      INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    quantity     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (part_id, warehouse_id),
    FOREIGN KEY (part_id)      REFERENCES parts(id)      ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE product_stock_levels (
    product_id   INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    quantity     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, warehouse_id),
    FOREIGN KEY (product_id)   REFERENCES products(id)   ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE INDEX idx_part_stock_levels_wh    ON part_stock_levels(warehouse_id);
CREATE INDEX idx_product_stock_levels_wh ON product_stock_levels(warehouse_id);

INSERT INTO part_stock_levels (part_id, warehouse_id, quantity)
SELECT part_id, warehouse_id, SUM(quantity) FROM stock_movements GROUP BY part_id, warehouse_id;

INSERT INTO product_stock_levels (product_id, warehouse_id, quantity)
SELECT product_id, warehouse_id, SUM(quantity) FROM product_stock_movements GROUP BY product_id, warehouse_id;

UPDATE parts    SET stock_quantity = COALESCE((SELECT SUM(quantity) FROM part_stock_levels    WHERE part_id    = parts.id), 0);
UPDATE products SET stock_quantity = COALESCE((SELECT SUM(quantity) FROM product_stock_levels WHERE product_id = products.id), 0);

-- ---------------------------------------------------------------------
-- 8) Tetikleyiciler — EN SONDA (yukarıdaki geçiş hareketleri ikinci kez sayılmasın)
-- ---------------------------------------------------------------------
-- Hareket miktarı işaretlidir (giriş +, çıkış −); tetikleyici depo bakiyesine ve toplama ekler.
CREATE TRIGGER trg_stock_movement_apply
    AFTER INSERT ON stock_movements
BEGIN
    INSERT INTO part_stock_levels (part_id, warehouse_id, quantity)
    VALUES (NEW.part_id, NEW.warehouse_id, NEW.quantity)
    ON CONFLICT (part_id, warehouse_id) DO UPDATE SET quantity = quantity + excluded.quantity;
    UPDATE parts SET stock_quantity = COALESCE(stock_quantity, 0) + NEW.quantity WHERE id = NEW.part_id;
END;

CREATE TRIGGER trg_product_stock_movement_apply
    AFTER INSERT ON product_stock_movements
BEGIN
    INSERT INTO product_stock_levels (product_id, warehouse_id, quantity)
    VALUES (NEW.product_id, NEW.warehouse_id, NEW.quantity)
    ON CONFLICT (product_id, warehouse_id) DO UPDATE SET quantity = quantity + excluded.quantity;
    UPDATE products SET stock_quantity = COALESCE(stock_quantity, 0) + NEW.quantity WHERE id = NEW.product_id;
END;

-- Defter değiştirilemez: yanlış hareket, ters hareketle düzeltilir.
CREATE TRIGGER trg_stock_movement_no_update BEFORE UPDATE ON stock_movements
BEGIN
    SELECT RAISE(ABORT, 'Stok hareketleri değiştirilemez; düzeltme için yeni hareket yazın.');
END;

CREATE TRIGGER trg_stock_movement_no_delete BEFORE DELETE ON stock_movements
BEGIN
    SELECT RAISE(ABORT, 'Stok hareketleri silinemez; düzeltme için yeni hareket yazın.');
END;

CREATE TRIGGER trg_product_stock_movement_no_update BEFORE UPDATE ON product_stock_movements
BEGIN
    SELECT RAISE(ABORT, 'Stok hareketleri değiştirilemez; düzeltme için yeni hareket yazın.');
END;

CREATE TRIGGER trg_product_stock_movement_no_delete BEFORE DELETE ON product_stock_movements
BEGIN
    SELECT RAISE(ABORT, 'Stok hareketleri silinemez; düzeltme için yeni hareket yazın.');
END;
