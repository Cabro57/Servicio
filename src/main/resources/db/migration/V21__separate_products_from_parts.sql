-- =========================================================================
-- V21__separate_products_from_parts.sql
-- Ürün (satış/POS) ve Parça (servis) alanları gerçekte farklı: Ürün'de marka var,
-- uyumlu model/tedarikçi yok; Parça'da tedarikçi/uyumlu model var, marka yok.
-- V19/V20'deki "tek parts tablosu + is_service_part/is_sale_product bayrağı"
-- tasarımı bu yüzden terk edildi — POS artık kendi bağımsız kataloğunu
-- (products + product_stock_movements) kullanıyor. Stok artık iki AYRI kaynakta
-- takip ediliyor; aynı fiziksel ürün hem parça hem ürün olarak işaretliyse
-- (nadir), iki taraf ayrı stok sayar — bu bilinçli bir tercih.
-- =========================================================================

-- ---------------------------------------------------------
-- 1) products — bağımsız satış kataloğu (parts'tan TAMAMEN ayrı)
-- ---------------------------------------------------------
CREATE TABLE products (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    barcode                  VARCHAR(50) UNIQUE NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    brand                    VARCHAR(100),
    category_id              INTEGER,
    purchase_price           DECIMAL(12,2) DEFAULT 0.0,
    sale_price                DECIMAL(12,2) DEFAULT 0.0,
    purchase_currency        TEXT NOT NULL DEFAULT 'TRY',
    purchase_price_original  NUMERIC NULL,
    sale_currency             TEXT NOT NULL DEFAULT 'TRY',
    sale_price_original       NUMERIC NULL,
    stock_quantity            INTEGER DEFAULT 0,
    min_stock_level           INTEGER DEFAULT 0,
    description               TEXT,
    is_deleted                BOOLEAN DEFAULT 0,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES part_categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_products_category ON products(category_id);

-- parts'ta is_sale_product=1 işaretli kalemler ürün kataloğuna kopyalanır (marka boş başlar,
-- kullanıcı sonradan girer). Orijinal parça kaydı DEĞİŞMEDEN kalır — dual-purpose kalemlerde
-- (nadir) artık iki ayrı stok kaynağı olur, bilinçli bir tercih (bkz. yukarıdaki not).
INSERT INTO products (barcode, name, purchase_price, sale_price, purchase_currency, purchase_price_original,
    sale_currency, sale_price_original, stock_quantity, min_stock_level, description, is_deleted, created_at, updated_at)
SELECT barcode, name, purchase_price, sale_price, purchase_currency, purchase_price_original,
    sale_currency, sale_price_original, stock_quantity, min_stock_level, description, is_deleted, created_at, updated_at
FROM parts WHERE is_sale_product = 1;

-- ---------------------------------------------------------
-- 2) product_stock_movements — parts/stock_movements'ın ayrı kopyası
-- ---------------------------------------------------------
CREATE TABLE product_stock_movements (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id     INTEGER NOT NULL,
    quantity       INTEGER NOT NULL,
    type           VARCHAR(10) NOT NULL,   -- 'IN' | 'OUT'
    reference_type VARCHAR(50),
    reference_id   INTEGER,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

CREATE INDEX idx_product_stock_movements_product ON product_stock_movements(product_id);

-- Taşınan ürünlerin mevcut stoğu için başlangıç hareketi — triggerlar henüz AKTİF DEĞİL,
-- bu INSERT güvenle çalışır (V7'deki aynı desen: migration insert'i trigger'lardan önce).
INSERT INTO product_stock_movements (product_id, quantity, type, reference_type, created_at)
SELECT id, stock_quantity, 'IN', 'MIGRATION_INITIAL', CURRENT_TIMESTAMP
FROM products WHERE stock_quantity > 0;

CREATE TRIGGER trg_product_stock_movement_in
    AFTER INSERT ON product_stock_movements
    WHEN NEW.type = 'IN'
BEGIN
    UPDATE products SET stock_quantity = stock_quantity + NEW.quantity WHERE id = NEW.product_id;
END;

CREATE TRIGGER trg_product_stock_movement_out
    AFTER INSERT ON product_stock_movements
    WHEN NEW.type = 'OUT'
BEGIN
    UPDATE products SET stock_quantity = stock_quantity + NEW.quantity WHERE id = NEW.product_id;
END;

-- ---------------------------------------------------------
-- 3) sale_items.part_id -> product_id (FK hedefi parts'tan products'a değişiyor)
--    SQLite ALTER TABLE ile FK hedefi değiştirilemez; V2/V5/V7'deki desen izlenir:
--    yeni tablo, veri taşı, eskiyi sil, yeniden adlandır.
-- ---------------------------------------------------------
CREATE TABLE sale_items_new (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id              INTEGER NOT NULL,
    product_id           INTEGER NULL,
    item_name            VARCHAR(255) NOT NULL,
    quantity             INTEGER NOT NULL DEFAULT 1,
    purchase_price       DECIMAL(12,2) DEFAULT 0.0,
    unit_price           DECIMAL(12,2) NOT NULL,
    sale_currency        TEXT NOT NULL DEFAULT 'TRY',
    unit_price_original  DECIMAL(12,2) NULL,
    line_discount_type   VARCHAR(10) NULL,
    line_discount_value  DECIMAL(12,2) NOT NULL DEFAULT 0.0,
    line_total           DECIMAL(12,2) NOT NULL,
    source_sale_item_id  INTEGER NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (source_sale_item_id) REFERENCES sale_items_new(id) ON DELETE RESTRICT
);

-- Eski part_id -> yeni product_id eşlemesi barkod üzerinden (products.barcode = parts.barcode,
-- INSERT...SELECT'te birebir kopyalandığı için güvenli bir eşleme anahtarı).
INSERT INTO sale_items_new (id, sale_id, product_id, item_name, quantity, purchase_price, unit_price,
    sale_currency, unit_price_original, line_discount_type, line_discount_value, line_total,
    source_sale_item_id, created_at)
SELECT si.id, si.sale_id,
    (SELECT pr.id FROM products pr JOIN parts pa ON pa.barcode = pr.barcode WHERE pa.id = si.part_id),
    si.item_name, si.quantity, si.purchase_price, si.unit_price, si.sale_currency,
    si.unit_price_original, si.line_discount_type, si.line_discount_value, si.line_total,
    si.source_sale_item_id, si.created_at
FROM sale_items si;

-- Eski tablonun kendine-referanslı source_sale_item_id FK'ı (V20'de ON DELETE RESTRICT ile
-- eklenmişti) DROP TABLE'ın örtük DELETE'ini engelliyor: bir satır aynı tabloda BAŞKA bir
-- satır tarafından referans alınıyorsa RESTRICT bunu reddediyor (iade verisi olan her
-- kurulumda oluşur). Veri zaten sale_items_new'e taşındığı için burada güvenle NULL'lanabilir.
UPDATE sale_items SET source_sale_item_id = NULL;

DROP TABLE sale_items;
ALTER TABLE sale_items_new RENAME TO sale_items;

CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_items(product_id);
CREATE INDEX idx_sale_items_source ON sale_items(source_sale_item_id);

-- AUTOINCREMENT sayacı — id'ler açıkça kopyalandığı için sqlite_sequence otomatik ilerlemedi.
UPDATE sqlite_sequence SET seq = (SELECT COALESCE(MAX(id), 0) FROM sale_items) WHERE name = 'sale_items';

-- ---------------------------------------------------------
-- 4) parts — artık her zaman "servis parçası", bayraklar anlamsız kaldı
-- ---------------------------------------------------------
DROP INDEX IF EXISTS idx_parts_is_sale_product;
ALTER TABLE parts DROP COLUMN is_sale_product;
ALTER TABLE parts DROP COLUMN is_service_part;
