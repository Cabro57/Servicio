-- =========================================================================
-- V7__extract_devices.sql (REVİZYON v2)
-- Kurumsal Dönüşüm, Seed Data, Parça Refactoring ve Veri Temizliği
-- =========================================================================

-- ---------------------------------------------------------
-- 1. DİNAMİK CİHAZ AYARLARI
-- ---------------------------------------------------------
CREATE TABLE device_types (
                              id   INTEGER PRIMARY KEY AUTOINCREMENT,
                              name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE device_brands (
                               id   INTEGER PRIMARY KEY AUTOINCREMENT,
                               name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE device_type_brand (
                                   type_id  INTEGER,
                                   brand_id INTEGER,
                                   PRIMARY KEY (type_id, brand_id),
                                   FOREIGN KEY (type_id)  REFERENCES device_types(id)  ON DELETE CASCADE,
                                   FOREIGN KEY (brand_id) REFERENCES device_brands(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------
-- 2. HAZIR İŞÇİLİKLER TABLOSU
-- ---------------------------------------------------------
CREATE TABLE labors (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        name          VARCHAR(255) NOT NULL,
                        description   TEXT,
                        category      VARCHAR(100),
                        default_price DECIMAL(12,2) DEFAULT 0.0,
                        is_deleted    BOOLEAN DEFAULT 0,
                        created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE warehouses (
                            id   INTEGER PRIMARY KEY AUTOINCREMENT,
                            name VARCHAR(100) UNIQUE NOT NULL
);

-- ---------------------------------------------------------
-- 3. TEMEL TABLOLAR
-- ---------------------------------------------------------
CREATE TABLE customers_new (
                               id             INTEGER PRIMARY KEY AUTOINCREMENT,
                               customer_type  VARCHAR(20)  DEFAULT 'NORMAL',
                               business_name  VARCHAR(100),
                               first_name     VARCHAR(50)  NOT NULL,
                               last_name      VARCHAR(50)  NOT NULL,
                               identity_no    VARCHAR(11)  UNIQUE,
                               tax_number     VARCHAR(50)  UNIQUE,
                               tax_office     VARCHAR(50),
                               phone_number_1 VARCHAR(20)  NOT NULL,
                               phone_number_2 VARCHAR(20),
                               email          VARCHAR(100),
                               address        VARCHAR(255),
                               note           TEXT,
                               is_deleted     BOOLEAN DEFAULT 0,
                               created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suppliers_new (
                               id            INTEGER PRIMARY KEY AUTOINCREMENT,
                               name          VARCHAR(100) NOT NULL,
                               business_name VARCHAR(100),
                               tax_number    VARCHAR(50),
                               tax_office    VARCHAR(50),
                               email         VARCHAR(100),
                               phone         VARCHAR(20),
                               address       VARCHAR(255),
                               note          TEXT,
                               is_deleted    BOOLEAN DEFAULT 0,
                               created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parts (
                       id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                       barcode             VARCHAR(50)  UNIQUE NOT NULL,
                       name                VARCHAR(255) NOT NULL,
                       category            VARCHAR(100) DEFAULT 'Genel',
                       model_compatibility TEXT,
                       supplier_id         INTEGER,
                       warehouse_id        INTEGER,
                       purchase_price      DECIMAL(12,2) DEFAULT 0.0,
                       sale_price          DECIMAL(12,2) DEFAULT 0.0,
                       stock_quantity      INTEGER DEFAULT 0,
                       min_stock_level     INTEGER DEFAULT 0,
                       description         TEXT,
                       is_deleted          BOOLEAN DEFAULT 0,
                       created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       FOREIGN KEY (supplier_id) REFERENCES suppliers_new(id) ON DELETE SET NULL,
                       FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE SET NULL
);

-- [DÜZELTİLDİ] customer_id KALDIRILDI:
-- Cihaz birden fazla farklı müşteriye ait servis kaydına konu olabilir;
-- müşteri ilişkisi work_orders.customer_id üzerinden yönetilmelidir.
CREATE TABLE devices (
                         id             INTEGER PRIMARY KEY AUTOINCREMENT,
                         device_type_id INTEGER NOT NULL,
                         brand_id       INTEGER NOT NULL,
                         model          VARCHAR(100) NOT NULL,
                         serial_no      VARCHAR(100) UNIQUE,
                         password       VARCHAR(50),
                         accessory      TEXT,
                         is_deleted     BOOLEAN DEFAULT 0,
                         created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (device_type_id) REFERENCES device_types(id) ON DELETE RESTRICT,
                         FOREIGN KEY (brand_id)       REFERENCES device_brands(id) ON DELETE RESTRICT
);

CREATE TABLE work_orders (
                             id                INTEGER PRIMARY KEY AUTOINCREMENT,
                             customer_id       INTEGER,          -- NULL olabilir (V5 ile uyumlu)
                             device_id         INTEGER NOT NULL,
                             technician_id     INTEGER,
                             reported_fault    TEXT,
                             detected_fault    TEXT,
                             urgency_status    VARCHAR(20) DEFAULT 'NORMAL',
                             service_status    VARCHAR(20) DEFAULT 'PENDING',
                             warranty_end_date TIMESTAMP,
                             delivery_date     TIMESTAMP,
                             is_deleted          BOOLEAN DEFAULT 0,
                             created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (customer_id) REFERENCES customers_new(id) ON DELETE RESTRICT,
                             FOREIGN KEY (device_id)   REFERENCES devices(id)   ON DELETE RESTRICT
);

CREATE TABLE work_order_items (
                                  id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                  service_id     INTEGER       NOT NULL,
                                  item_type      VARCHAR(20)   NOT NULL, -- 'PART' | 'LABOR'
                                  source_type    VARCHAR(20)   NOT NULL, -- 'PRESET' | 'MANUAL'
                                  part_id        INTEGER,
                                  labor_id       INTEGER,
                                  item_name      VARCHAR(255)  NOT NULL,
                                  used_serial_no VARCHAR(100),
                                  quantity       INTEGER DEFAULT 1,
                                  purchase_price DECIMAL(12,2) DEFAULT 0.0,
                                  unit_price     DECIMAL(12,2) NOT NULL,
                                  tax_rate       DECIMAL(5,2)  DEFAULT 0.0,
                                  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  FOREIGN KEY (service_id) REFERENCES work_orders(id) ON DELETE CASCADE,
                                  FOREIGN KEY (part_id)    REFERENCES parts(id)       ON DELETE SET NULL,
                                  FOREIGN KEY (labor_id)   REFERENCES labors(id)      ON DELETE SET NULL
);

CREATE TABLE work_order_payments (
                                     id           INTEGER PRIMARY KEY AUTOINCREMENT,
                                     service_id   INTEGER       NOT NULL,
                                     amount       DECIMAL(12,2) NOT NULL,
                                     payment_type VARCHAR(20)   NOT NULL,
                                     note         TEXT,
                                     payment_date TIMESTAMP,
                                     created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     FOREIGN KEY (service_id) REFERENCES work_orders(id) ON DELETE CASCADE
);

CREATE TABLE work_order_notes (
                                  id            INTEGER PRIMARY KEY AUTOINCREMENT,
                                  service_id    INTEGER NOT NULL,
                                  technician_id INTEGER,
                                  note          TEXT NOT NULL,
                                  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  FOREIGN KEY (service_id)    REFERENCES work_orders(id) ON DELETE CASCADE,
                                  FOREIGN KEY (technician_id) REFERENCES users(id)       ON DELETE SET NULL
);

CREATE TABLE stock_movements (
                                 id             INTEGER PRIMARY KEY AUTOINCREMENT,
                                 product_id     INTEGER NOT NULL,
                                 warehouse_id   INTEGER NOT NULL,
                                 quantity       INTEGER NOT NULL,
                                 type           VARCHAR(10) NOT NULL,   -- 'IN' | 'OUT'
                                 reference_type VARCHAR(50),            -- 'SERVICE' | 'PURCHASE' | 'MANUAL' | 'MIGRATION'
                                 reference_id   INTEGER,
                                 created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 FOREIGN KEY (product_id)   REFERENCES parts(id)      ON DELETE RESTRICT,
                                 FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------
-- 4. VERİ TAŞIMA (MIGRATION)
-- ---------------------------------------------------------

INSERT INTO warehouses (id, name) VALUES (1, 'Ana Depo');

-- A) MÜŞTERİLER
-- [DÜZELTİLDİ] phone_number_1 NOT NULL kısıtı: kaynak tabloda NULL olabilir.
-- COALESCE ile 'Bilinmiyor' fallback eklendi; aksi hâlde INSERT başarısız olur.
INSERT INTO customers_new (
    id, customer_type, business_name, first_name, last_name,
    identity_no, phone_number_1, phone_number_2,
    email, address, note, created_at
)
SELECT
    id,
    status,
    business_name,
    name,
    surname,
    CASE
        WHEN id_no IS NULL OR TRIM(id_no) = ''   THEN NULL
        WHEN TRIM(id_no) GLOB '*[^0-9]*'         THEN NULL
        WHEN LENGTH(TRIM(id_no)) NOT IN (10, 11) THEN NULL
        ELSE TRIM(id_no)
        END,
    CASE
        WHEN NULLIF(TRIM(phone_number_1), '') IS NULL    THEN '+90000' || SUBSTR('00000' || id, -5, 5)
        ELSE TRIM(phone_number_1)
        END, -- [DÜZELTİLDİ] Boş kayıtlara id bazlı placeholder
    phone_number_2,
    email, address, note, created_at
FROM customers;

-- B) TEDARİKÇİLER
INSERT INTO suppliers_new (
    id, name, business_name, tax_number, tax_office,
    email, phone, address, note, created_at
)
SELECT id, name, business_name, tax_no, tax_office,
       email, phone, address, note, created_at
FROM suppliers;

-- C) PARÇALAR (Marka ismi parça adına entegre ediliyor)
INSERT INTO parts (
    barcode, name, category, model_compatibility, supplier_id, warehouse_id,
    purchase_price, sale_price, stock_quantity, min_stock_level,
    description, created_at
)
SELECT
    barcode,
    CASE
        WHEN brand IS NOT NULL AND TRIM(brand) != ''
            THEN TRIM(brand) || ' ' || TRIM(name)
        ELSE TRIM(name)
        END,
    'Genel', model, supplier_id, 1,
    purchase_price, sale_price,
    0,       -- Stok stock_movements'tan hesaplanacak
    min_stock,
    description, created_at
FROM part;

-- D) SEED DATA: Cihaz Türleri ve Markalar
INSERT INTO device_types (id, name) VALUES
                                        (1, 'Telefon'), (2, 'Tablet'), (3, 'Bilgisayar'), (4, 'Akıllı Saat');

INSERT INTO device_brands (id, name) VALUES
                                         (1, 'Apple'), (2, 'Samsung'), (3, 'Xiaomi'), (4, 'Huawei'),
                                         (5, 'Lenovo'), (6, 'Asus'), (7, 'HP'), (8, 'Dell');

INSERT INTO device_type_brand (type_id, brand_id) VALUES
                                                      (1,1),(1,2),(1,3),(1,4),       -- Telefonlar
                                                      (2,1),(2,2),(2,3),(2,4),(2,5), -- Tabletler
                                                      (3,1),(3,5),(3,6),(3,7),(3,8), -- Bilgisayarlar
                                                      (4,1),(4,2),(4,3),(4,4);       -- Akıllı Saatler

-- Eski servis verilerinden ek tür/marka kurtarma
INSERT OR IGNORE INTO device_types (name)
SELECT DISTINCT TRIM(device_type)
FROM services
WHERE device_type IS NOT NULL AND TRIM(device_type) != '';

INSERT OR IGNORE INTO device_brands (name)
SELECT DISTINCT TRIM(device_brand)
FROM services
WHERE device_brand IS NOT NULL AND TRIM(device_brand) != '';

INSERT OR IGNORE INTO device_type_brand (type_id, brand_id)
SELECT DISTINCT dt.id, db.id
FROM services s
         JOIN device_types  dt ON dt.name = TRIM(s.device_type)
         JOIN device_brands db ON db.name = TRIM(s.device_brand)
WHERE s.device_type IS NOT NULL AND s.device_brand IS NOT NULL;

-- ---------------------------------------------------------
-- E) STOK HAREKETLERİ
-- ---------------------------------------------------------
-- [DÜZELTİLDİ] Trigger'larla çift sayım riski:
-- Triggerlar CREATE TABLE bloğundan SONRA tanımlanıyor; bu noktada henüz aktif değiller.
-- Bu INSERT'ler güvenle çalışır; trigger aktif olmadığından stock_quantity ikinci kez değişmez.
-- (Triggerlar script sonunda tanımlanmaktadır)

-- Girişler: Başlangıç stoku (eski stok + daha önce servis için kullanılan miktar)
-- [DÜZELTİLDİ] is_stock_tracked filtresi eklendi: takipsiz parçalar hesaba katılmaz
INSERT INTO stock_movements (
    product_id, warehouse_id, quantity,
    type, reference_type, reference_id, created_at
)
SELECT
    p.id,
    1,
    (p_eski.stock + COALESCE((
                                 SELECT SUM(ap.amount)
                                 FROM added_part ap
                                 WHERE ap.part_barcode = p.barcode
                                   AND ap.is_stock_tracked = 1   -- [DÜZELTİLDİ]
                             ), 0)),
    'IN', 'MIGRATION_INITIAL', NULL, p.created_at
FROM parts p
         JOIN part p_eski ON p.barcode = p_eski.barcode
WHERE p_eski.stock > 0
   OR EXISTS (
    SELECT 1 FROM added_part ap
    WHERE ap.part_barcode = p.barcode AND ap.is_stock_tracked = 1
);

-- Çıkışlar: Servis kullanımları
-- [DÜZELTİLDİ] is_stock_tracked ve orphan servis kontrolü eklendi
INSERT INTO stock_movements (
    product_id, warehouse_id, quantity,
    type, reference_type, reference_id, created_at
)
SELECT
    p.id, 1, ap.amount,
    'OUT', 'SERVICE_CONSUMPTION', ap.service_id, ap.created_at
FROM added_part ap
         JOIN parts p ON p.barcode = ap.part_barcode
WHERE ap.is_stock_tracked = 1; -- [DÜZELTİLDİ]

-- [KORUNDU] Geçici index: toplu UPDATE öncesinde tam tablo taramasını önler
CREATE INDEX temp_idx_stock_mov_product ON stock_movements(product_id);

-- Stok miktarlarını hareketlerden yeniden hesapla
-- NOT: Triggerlar henüz aktif olmadığından bu UPDATE doğrudan çalışır, çift sayım olmaz.
UPDATE parts
SET stock_quantity = (
    SELECT COALESCE(
                   SUM(CASE WHEN type = 'IN' THEN quantity ELSE -quantity END),
                   0
           )
    FROM stock_movements
    WHERE stock_movements.product_id = parts.id
);

DROP INDEX temp_idx_stock_mov_product;

-- ---------------------------------------------------------
-- F) CİHAZ AYRIŞTIRMA
-- ---------------------------------------------------------
-- [RİSK NOTU] Farklı marka/model cihazlar aynı safe_serial altında birleşebilir.
-- Migration öncesi kontrol sorgusu:
--   SELECT safe_serial, COUNT(DISTINCT device_brand||'|'||device_model)
--   FROM (SELECT *, CASE WHEN ... END AS safe_serial FROM services)
--   GROUP BY safe_serial HAVING COUNT(...) > 1;

-- [DÜZELTİLDİ] SELECT * yerine açık sütun listesi:
-- a) customer_id devices_new şemasında YOK, geçici tabloda work_orders için tutuldu
-- b) SELECT * + eklenen safe_serial hesaplaması çakışma riski taşır
CREATE TEMP TABLE temp_cleaned_serials AS
SELECT
    id,
    customer_id,       -- work_orders INSERT için; cihaz şemasına taşınmıyor
    device_type,
    device_brand,
    device_model,
    device_password,
    device_accessory,
    reported_fault,
    detected_fault,
    action_taken,
    urgency_status,
    service_status,
    warranty_date,
    delivery_at,
    created_at,
    CASE
        WHEN device_serial IS NULL
            OR TRIM(device_serial) IN (
                                       'alınamadı','alınacak','Gizli','Bilinmiyor',
                                       '- ALINAMADI -','Yok, Basit Kulaklık',
                                       '-','_','.','0','yok','YOK','Yok','na','N/A'
                )                                    THEN 'SN_UNKNOWN_' || id
        WHEN LENGTH(TRIM(device_serial)) < 3   THEN 'SN_UNKNOWN_' || id
        ELSE TRIM(device_serial)
        END AS safe_serial
FROM services;

-- Geçici tabloya erken index: GROUP BY ve JOIN performansı için
CREATE INDEX idx_tmp_safe_serial ON temp_cleaned_serials(safe_serial);
CREATE INDEX idx_tmp_id          ON temp_cleaned_serials(id);

-- Cihazları tekilleştirerek ekle
-- [DÜZELTİLDİ] customer_id KALDIRILDI (devices_new şemasında yok)
-- [DÜZELTİLDİ] GROUP BY daraltıldı; MIN(created_at) ile ilk kayıt alınır
INSERT INTO devices (device_type_id, brand_id, model, serial_no, password, accessory, created_at)
SELECT
    dt.id,
    db.id,
    cs.device_model,
    cs.safe_serial,
    cs.device_password,
    cs.device_accessory,
    MIN(cs.created_at)
FROM temp_cleaned_serials cs
         LEFT JOIN device_types dt ON dt.name = cs.device_type
         LEFT JOIN device_brands db ON db.name = cs.device_brand
GROUP BY cs.safe_serial; -- [RİSK] Bkz. yukarıdaki uyarı

-- İş emirlerini yeni device_id'ler ile aktar
-- [DÜZELTİLDİ] Orphan customer kaydı: LEFT JOIN ile NULL'a çekiliyor
-- [DÜZELTİLDİ] device_id NOT NULL: eşleşmeyen satırlar WHERE ile atlanıyor
INSERT INTO work_orders (
    id, customer_id, device_id,
    reported_fault, detected_fault,
    urgency_status, service_status,
    warranty_end_date, delivery_date, created_at
)
SELECT
    cs.id,
    CASE WHEN c.id IS NOT NULL THEN cs.customer_id ELSE NULL END, -- [DÜZELTİLDİ]
    d.id,
    cs.reported_fault,
    cs.detected_fault,
    cs.urgency_status,
    cs.service_status,
    cs.warranty_date,
    cs.delivery_at,
    cs.created_at
FROM temp_cleaned_serials cs
         LEFT JOIN customers_new c ON c.id = cs.customer_id
         LEFT JOIN devices   d ON d.serial_no = cs.safe_serial
WHERE d.id IS NOT NULL; -- [DÜZELTİLDİ] device_id NOT NULL kısıtını karşıla

DROP TABLE temp_cleaned_serials;

-- ---------------------------------------------------------
-- G) TAHSİLATLAR
-- ---------------------------------------------------------
-- [DÜZELTİLDİ] Orphan service_id önleme
INSERT INTO work_order_payments (
    service_id, amount, payment_type, note, payment_date, created_at
)
SELECT
    id, paid,
    CASE
        WHEN UPPER(payment_type) IN ('KART','KREDI_KARTI','CREDIT_CARD','CARD','KREDI KARTI','KREDİ KARTI') THEN 'CREDIT_CARD'
        WHEN UPPER(payment_type) IN ('HAVALE','EFT','TRANSFER')                                             THEN 'TRANSFER'
        WHEN UPPER(payment_type) IN ('DİĞER','OTHER','DIGER','ON_ACCOUNT')                                  THEN 'OTHER'
        ELSE 'CASH'
        END,
    'Eski Sistem Aktarımı',
    COALESCE(delivery_at, created_at, CURRENT_TIMESTAMP),
    CURRENT_TIMESTAMP
FROM services
WHERE paid > 0
  AND id IN (SELECT id FROM work_orders); -- [DÜZELTİLDİ]

-- ---------------------------------------------------------
-- H) İŞ EMRİ KALEMLERİ — PARÇALAR
-- ---------------------------------------------------------
-- [DÜZELTİLDİ] Orphan service_id önleme
INSERT INTO work_order_items (
    service_id, item_type, source_type,
    part_id, item_name, used_serial_no,
    quantity, purchase_price, unit_price
)
SELECT
    ap.service_id,
    'PART',
    CASE WHEN p.id IS NOT NULL THEN 'PRESET' ELSE 'MANUAL' END,
    p.id,
    COALESCE(ap.name, 'Bilinmeyen Parça'),
    ap.series_no,
    ap.amount,
    ap.purchase_price,
    ap.sale_price
FROM added_part ap
         LEFT JOIN parts p ON p.barcode = ap.part_barcode
WHERE ap.service_id IN (SELECT id FROM work_orders); -- [DÜZELTİLDİ]

-- ---------------------------------------------------------
-- I) İŞ EMRİ KALEMLERİ — İŞÇİLİKLER
-- ---------------------------------------------------------
-- [DÜZELTİLDİ] Orphan service_id önleme
INSERT INTO work_order_items (
    service_id, item_type, source_type,
    item_name, quantity, unit_price
)
SELECT
    id, 'LABOR', 'MANUAL',
    COALESCE(NULLIF(TRIM(action_taken), ''), 'Genel İşçilik'),
    1, labor_cost
FROM services
WHERE labor_cost > 0
  AND id IN (SELECT id FROM work_orders); -- [DÜZELTİLDİ]

-- ---------------------------------------------------------
-- J) İŞ EMRİ NOTLARI
-- ---------------------------------------------------------
-- [DÜZELTİLDİ] Orphan service_id önleme
INSERT INTO work_order_notes (service_id, note, created_at)
SELECT id, Notes, created_at
FROM services
WHERE Notes IS NOT NULL AND TRIM(Notes) != ''
  AND id IN (SELECT id FROM work_orders); -- [DÜZELTİLDİ]

INSERT INTO work_order_notes (service_id, note, created_at)
SELECT id, 'Tespit Edilen Arıza: ' || detected_fault, created_at
FROM services
WHERE detected_fault IS NOT NULL AND TRIM(detected_fault) != ''
  AND id IN (SELECT id FROM work_orders); -- [DÜZELTİLDİ]

-- ---------------------------------------------------------
-- K) HAZIR İŞÇİLİK KATALOĞU SEED DATA
-- ---------------------------------------------------------
INSERT INTO labors (name, description, category, default_price) VALUES
                                                                    ('Arıza Tespiti',                     'Cihazın sökülmesi ve arıza tespiti',    'Genel',   0.0),
                                                                    ('Format / İşletim Sistemi Kurulumu', 'Windows/MacOS kurulumu ve driverlar',   'Yazılım', 400.0),
                                                                    ('Genel Bakım ve Temizlik',           'Termal macun ve fan temizliği',          'Bakım',   250.0),
                                                                    ('Sıvı Teması Temizliği',             'Anakart oksit giderme',                  'Bakım',   600.0);

-- ---------------------------------------------------------
-- 5. ESKİ TABLOLARI SİL VE YENİLERİNİ ADLANDIR
--    [DÜZELTİLDİ] DROP komutları tüm INSERT işlemleri tamamlandıktan SONRA
-- ---------------------------------------------------------
DROP TABLE added_part;
DROP TABLE part;
DROP TABLE services;
DROP TABLE suppliers;
DROP TABLE customers;

ALTER TABLE customers_new RENAME TO customers;
ALTER TABLE suppliers_new RENAME TO suppliers;

-- ---------------------------------------------------------
-- 6. INDEX'LER (Tablolar rename edildikten sonra oluştur)
-- ---------------------------------------------------------
CREATE INDEX idx_customers_phone        ON customers(phone_number_1);
CREATE INDEX idx_services_customer      ON work_orders(customer_id);
CREATE INDEX idx_services_device        ON work_orders(device_id);
CREATE INDEX idx_service_items_service  ON work_order_items(service_id);
CREATE INDEX idx_service_payments_service ON work_order_payments(service_id);
CREATE INDEX idx_service_notes_service  ON work_order_notes(service_id);
CREATE INDEX idx_parts_barcode          ON parts(barcode);
CREATE INDEX idx_devices_serial         ON devices(serial_no);
CREATE INDEX idx_stock_movements_prod   ON stock_movements(product_id);

-- ---------------------------------------------------------
-- 7. TRIGGERLAR
-- ---------------------------------------------------------
-- [ÖNEMLİ NOT] Triggerlar kasıtlı olarak migration INSERT'lerinden SONRA tanımlanmaktadır.
-- Migration sırasında stock_quantity zaten manuel UPDATE ile doğru şekilde hesaplanmıştır.
-- Triggerlar AKTIF hale geçtikten sonra yapılan tüm yeni INSERT'leri otomatik yönetecektir.
-- Bu sıralama çift sayımı (double-counting) önler.

CREATE TRIGGER trg_stock_movement_in
    AFTER INSERT ON stock_movements
    WHEN NEW.type = 'IN'
BEGIN
    UPDATE parts
    SET stock_quantity = stock_quantity + NEW.quantity
    WHERE id = NEW.product_id;
END;

CREATE TRIGGER trg_stock_movement_out
    AFTER INSERT ON stock_movements
    WHEN NEW.type = 'OUT'
BEGIN
    UPDATE parts
    SET stock_quantity = stock_quantity - NEW.quantity
    WHERE id = NEW.product_id;
END;