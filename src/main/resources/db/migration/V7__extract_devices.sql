-- =========================================================================
-- V7__extract_devices.sql
-- Kurumsal Dönüşüm, Seed Data, Parça Refactoring ve Veri Temizliği
-- =========================================================================

-- ---------------------------------------------------------
-- 1. DİNAMİK CİHAZ AYARLARI (DeviceSettings'in DB'ye Taşınması)
-- ---------------------------------------------------------
CREATE TABLE device_types (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE device_brands (
                               id INTEGER PRIMARY KEY AUTOINCREMENT,
                               name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE device_type_brand (
                                   type_id INTEGER,
                                   brand_id INTEGER,
                                   PRIMARY KEY (type_id, brand_id),
                                   FOREIGN KEY (type_id) REFERENCES device_types(id) ON DELETE CASCADE,
                                   FOREIGN KEY (brand_id) REFERENCES device_brands(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------
-- 2. HAZIR İŞÇİLİKLER TABLOSU (Labor)
-- ---------------------------------------------------------
CREATE TABLE labors (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(255) NOT NULL,
                        description TEXT,
                        category VARCHAR(100),
                        default_price DECIMAL(12,2) DEFAULT 0.0,
                        is_deleted BOOLEAN DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------
-- 3. TEMEL TABLOLAR
-- ---------------------------------------------------------
CREATE TABLE customers_new (
                               id INTEGER PRIMARY KEY AUTOINCREMENT,
                               customer_type VARCHAR(20) DEFAULT 'NORMAL',
                               business_name VARCHAR(100),
                               first_name VARCHAR(50) NOT NULL,
                               last_name VARCHAR(50) NOT NULL,
                               identity_no VARCHAR(11) UNIQUE,
                               tax_number VARCHAR(50) UNIQUE,
                               tax_office VARCHAR(50),
                               phone_number_1 VARCHAR(20) NOT NULL,
                               phone_number_2 VARCHAR(20),
                               email VARCHAR(100),
                               address VARCHAR(255),
                               note TEXT,
                               is_deleted BOOLEAN DEFAULT 0,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suppliers_new (
                               id INTEGER PRIMARY KEY AUTOINCREMENT,
                               name VARCHAR(100) NOT NULL,
                               business_name VARCHAR(100),
                               tax_number VARCHAR(50),
                               tax_office VARCHAR(50),
                               email VARCHAR(100),
                               phone VARCHAR(20),
                               address VARCHAR(255),
                               note TEXT,
                               is_deleted BOOLEAN DEFAULT 0,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- YENİ PARTS TABLOSU (Brand ve DeviceType silindi, Category eklendi)
CREATE TABLE parts (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       barcode VARCHAR(50) UNIQUE NOT NULL,
                       name VARCHAR(255) NOT NULL,
                       category VARCHAR(100) DEFAULT 'Genel',
                       model_compatibility TEXT,
                       supplier_id INTEGER,
                       purchase_price DECIMAL(12,2) DEFAULT 0.0,
                       sale_price DECIMAL(12,2) DEFAULT 0.0,
                       stock_quantity INTEGER DEFAULT 0,
                       min_stock_level INTEGER DEFAULT 0,
                       description TEXT,
                       is_deleted BOOLEAN DEFAULT 0,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       FOREIGN KEY (supplier_id) REFERENCES suppliers_new(id) ON DELETE SET NULL
);

CREATE TABLE devices_new (
                             id INTEGER PRIMARY KEY AUTOINCREMENT,
                             customer_id INTEGER,
                             device_type VARCHAR(50) NOT NULL,
                             brand VARCHAR(50) NOT NULL,
                             model VARCHAR(100) NOT NULL,
                             serial_no VARCHAR(100) UNIQUE,
                             password VARCHAR(50),
                             accessory TEXT,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (customer_id) REFERENCES customers_new(id) ON DELETE SET NULL
);

CREATE TABLE services_new (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              customer_id INTEGER,
                              device_id INTEGER NOT NULL,
                              technician_id INTEGER,
                              reported_fault TEXT,
                              detected_fault TEXT,
                              action_taken TEXT,
                              urgency_status VARCHAR(20) DEFAULT 'NORMAL',
                              service_status VARCHAR(20) DEFAULT 'PENDING',
                              warranty_end_date TIMESTAMP,
                              delivery_date TIMESTAMP,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (customer_id) REFERENCES customers_new(id) ON DELETE RESTRICT,
                              FOREIGN KEY (device_id) REFERENCES devices_new(id) ON DELETE RESTRICT
);

CREATE TABLE service_items (
                               id INTEGER PRIMARY KEY AUTOINCREMENT,
                               service_id INTEGER NOT NULL,
                               item_type VARCHAR(20) NOT NULL,
                               source_type VARCHAR(20) NOT NULL,
                               part_id INTEGER,
                               labor_id INTEGER,
                               item_name VARCHAR(255) NOT NULL,
                               used_serial_no VARCHAR(100),
                               quantity INTEGER DEFAULT 1,
                               purchase_price DECIMAL(12,2) DEFAULT 0.0,
                               unit_price DECIMAL(12,2) NOT NULL,
                               tax_rate DECIMAL(5,2) DEFAULT 0.0,
                               FOREIGN KEY (service_id) REFERENCES services_new(id) ON DELETE CASCADE,
                               FOREIGN KEY (part_id) REFERENCES parts(id) ON DELETE SET NULL,
                               FOREIGN KEY (labor_id) REFERENCES labors(id) ON DELETE SET NULL
);

CREATE TABLE service_payments (
                                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                                  service_id INTEGER NOT NULL,
                                  amount DECIMAL(12,2) NOT NULL,
                                  payment_type VARCHAR(20) NOT NULL,
                                  note TEXT,
                                  payment_date TIMESTAMP,
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  FOREIGN KEY (service_id) REFERENCES services_new(id) ON DELETE CASCADE
);

CREATE TABLE service_notes (
                               id INTEGER PRIMARY KEY AUTOINCREMENT,
                               service_id INTEGER NOT NULL,
                               technician_id INTEGER,
                               note TEXT NOT NULL,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (service_id) REFERENCES services_new(id) ON DELETE CASCADE,
                               FOREIGN KEY (technician_id) REFERENCES users(id) ON DELETE SET NULL
);


-- ---------------------------------------------------------
-- 4. VERİ TAŞIMA VE OTOMATİK DOLDURMA (MIGRATION)
-- ---------------------------------------------------------

-- A) MÜŞTERİLER VE TEDARİKÇİLER
INSERT INTO customers_new (id, customer_type, business_name, first_name, last_name, identity_no, phone_number_1, phone_number_2, email, address, note, created_at)
SELECT id, status, business_name, name, surname,
       CASE
           WHEN id_no IS NULL OR TRIM(id_no) = '' THEN NULL
           WHEN TRIM(id_no) GLOB '*[^0-9]*' THEN NULL
           WHEN LENGTH(TRIM(id_no)) NOT IN (10, 11) THEN NULL
           ELSE TRIM(id_no)
           END,
       phone_number_1, phone_number_2, email, address, note, created_at FROM customers;

INSERT INTO suppliers_new (id, name, business_name, tax_number, tax_office, email, phone, address, note, created_at)
SELECT id, name, business_name, tax_no, tax_office, email, phone, address, note, created_at FROM suppliers;

-- B) PARÇALAR (Marka ismi parçanın ismine entegre ediliyor)
INSERT INTO parts (barcode, name, category, model_compatibility, supplier_id, purchase_price, sale_price, stock_quantity, min_stock_level, description, created_at)
SELECT barcode,
       CASE
           WHEN brand IS NOT NULL AND TRIM(brand) != '' THEN TRIM(brand) || ' ' || TRIM(name)
           ELSE TRIM(name)
           END,
       'Genel', model, supplier_id, purchase_price, sale_price, stock, min_stock, description, created_at
FROM part;

-- C) HAZIR BAŞLANGIÇ VERİLERİ (SEED DATA: Türler ve Markalar)
INSERT INTO device_types (id, name) VALUES (1, 'Telefon'), (2, 'Tablet'), (3, 'Bilgisayar'), (4, 'Akıllı Saat');
INSERT INTO device_brands (id, name) VALUES (1, 'Apple'), (2, 'Samsung'), (3, 'Xiaomi'), (4, 'Huawei'), (5, 'Lenovo'), (6, 'Asus'), (7, 'HP'), (8, 'Dell');

-- Tür-Marka Eşleştirmeleri (Apple -> Telefon, Tablet, Bilgisayar, Saat vb.)
INSERT INTO device_type_brand (type_id, brand_id) VALUES
                                                      (1, 1), (1, 2), (1, 3), (1, 4), -- Telefonlar (Apple, Samsung, Xiaomi, Huawei)
                                                      (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), -- Tabletler
                                                      (3, 1), (3, 5), (3, 6), (3, 7), (3, 8), -- Bilgisayarlar (Apple, Lenovo, Asus, HP, Dell)
                                                      (4, 1), (4, 2), (4, 3), (4, 4); -- Akıllı Saatler

-- Eskiden kalan farklı marka/türleri de kaybetmemek için içeri alıyoruz
INSERT OR IGNORE INTO device_types (name)
SELECT DISTINCT TRIM(device_type) FROM services WHERE device_type IS NOT NULL AND TRIM(device_type) != '';

INSERT OR IGNORE INTO device_brands (name)
SELECT DISTINCT TRIM(device_brand) FROM services WHERE device_brand IS NOT NULL AND TRIM(device_brand) != '';

INSERT OR IGNORE INTO device_type_brand (type_id, brand_id)
SELECT DISTINCT dt.id, db.id
FROM services s
         JOIN device_types dt ON dt.name = TRIM(s.device_type)
         JOIN device_brands db ON db.name = TRIM(s.device_brand)
WHERE s.device_type IS NOT NULL AND s.device_brand IS NOT NULL;

-- D) CİHAZLARI AYRIŞTIR VE SERVİSLERİ BAĞLA (GLOBAL STANDART & SQLITE UYUMLU)

-- 1. ADIM: Her iki INSERT işleminde kullanabilmek için CTE yerine GEÇİCİ TABLO oluşturuyoruz.
CREATE TEMP TABLE temp_cleaned_serials AS
SELECT
    *,
    CASE
        -- Boş, null veya anlamsız kısa karakterler için global etiket
        WHEN device_serial IS NULL OR TRIM(device_serial) IN ('', '-', '_', '.', '0', 'yok', 'YOK', 'Yok', 'na', 'N/A') THEN 'SN_UNKNOWN_' || id
        -- En az 3 karakterli, anlamlı bir veri mi?
        WHEN LENGTH(TRIM(device_serial)) < 3 THEN 'SN_UNKNOWN_' || id
        ELSE TRIM(device_serial)
        END as safe_serial
FROM services;

-- 2. ADIM: Cihazları Tekilleştirerek Ekle (Geçici tablodan okuyarak)
INSERT INTO devices_new (customer_id, device_type, brand, model, serial_no, password, accessory, created_at)
SELECT
    customer_id,
    device_type,
    device_brand,
    device_model,
    safe_serial,
    device_password,
    device_accessory,
    created_at
FROM temp_cleaned_serials
GROUP BY safe_serial;

-- 3. ADIM: Servis Kayıtlarını Yeni ID'ler ile Güncelle (Geçici tablodan okuyarak)
INSERT INTO services_new (id, customer_id, device_id, reported_fault, detected_fault, action_taken, urgency_status, service_status, warranty_end_date, delivery_date, created_at)
SELECT
    cs.id,
    cs.customer_id,
    d.id,
    cs.reported_fault,
    cs.detected_fault,
    cs.action_taken,
    cs.urgency_status,
    cs.service_status,
    cs.warranty_date,
    cs.delivery_at,
    cs.created_at
FROM temp_cleaned_serials cs
         LEFT JOIN devices_new d ON d.serial_no = cs.safe_serial;

-- 4. ADIM: Belleği temiz tutmak için geçici tabloyu sil (Opsiyonel ama iyi pratiktir)
DROP TABLE temp_cleaned_serials;

-- E) TAHSİLATLAR
INSERT INTO service_payments (service_id, amount, payment_type, note, payment_date, created_at)
SELECT id, paid,
       CASE
           WHEN UPPER(payment_type) IN ('KART', 'KREDI_KARTI', 'CREDIT_CARD', 'CARD', 'KREDI KARTI', 'KREDİ KARTI') THEN 'CREDIT_CARD'
           WHEN UPPER(payment_type) IN ('HAVALE', 'EFT', 'TRANSFER') THEN 'TRANSFER'
           WHEN UPPER(payment_type) IN ('DİĞER', 'OTHER', 'DIGER', 'ON_ACCOUNT') THEN 'OTHER'
           ELSE 'CASH'
           END,
       'Eski Sistem Aktarımı', COALESCE(delivery_at, created_at, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
FROM services WHERE paid > 0;

-- F) SERVICE_ITEMS (Eski Eklenen Parçalar -> items tablosuna, yeni Parts tablosu ile ilişkilendirilerek aktarılır)
INSERT INTO service_items (service_id, item_type, source_type, part_id, item_name, used_serial_no, quantity, purchase_price, unit_price)
SELECT ap.service_id, 'PART',
       CASE WHEN p.id IS NOT NULL THEN 'PRESET' ELSE 'MANUAL' END,
       p.id, COALESCE(ap.name, 'Bilinmeyen Parça'), ap.series_no, ap.amount, ap.purchase_price, ap.sale_price
FROM added_part ap
         LEFT JOIN parts p ON p.barcode = ap.part_barcode;

-- G) SERVICE_ITEMS (Eski İşçilikler)
INSERT INTO service_items (service_id, item_type, source_type, item_name, quantity, unit_price)
SELECT id, 'LABOR', 'MANUAL', COALESCE(NULLIF(TRIM(action_taken), ''), 'Genel İşçilik'), 1, labor_cost
FROM services WHERE labor_cost > 0;

-- H) SERVICE_NOTES (Teknisyen Notları)
INSERT INTO service_notes (service_id, note, created_at)
SELECT id, Notes, created_at FROM services WHERE Notes IS NOT NULL AND TRIM(Notes) != '';

INSERT INTO service_notes (service_id, note, created_at)
SELECT id, 'Tespit Edilen Arıza: ' || detected_fault, created_at FROM services WHERE detected_fault IS NOT NULL AND TRIM(detected_fault) != '';

-- I) HAZIR İŞÇİLİK KATALOĞU SEED DATA
INSERT INTO labors (name, description, category, default_price) VALUES ('Arıza Tespiti', 'Cihazın sökülmesi ve arıza tespiti', 'Genel', 0.0);
INSERT INTO labors (name, description, category, default_price) VALUES ('Format / İşletim Sistemi Kurulumu', 'Windows/MacOS kurulumu ve driverlar', 'Yazılım', 400.0);
INSERT INTO labors (name, description, category, default_price) VALUES ('Genel Bakım ve Temizlik', 'Termal macun ve fan temizliği', 'Bakım', 250.0);
INSERT INTO labors (name, description, category, default_price) VALUES ('Sıvı Teması Temizliği', 'Anakart oksit giderme', 'Bakım', 600.0);

-- ---------------------------------------------------------
-- 5. ESKİ TABLOLARI SİL VE YENİLERİNİ ADLANDIR
-- ---------------------------------------------------------
DROP TABLE added_part;
DROP TABLE part;
DROP TABLE services;
DROP TABLE suppliers;
DROP TABLE customers;

ALTER TABLE customers_new RENAME TO customers;
ALTER TABLE suppliers_new RENAME TO suppliers;
ALTER TABLE devices_new RENAME TO devices;
ALTER TABLE services_new RENAME TO services;

CREATE INDEX idx_customers_phone ON customers(phone_number_1);
CREATE INDEX idx_services_customer ON services(customer_id);
CREATE INDEX idx_services_device ON services(device_id);
CREATE INDEX idx_service_items_service ON service_items(service_id);
CREATE INDEX idx_service_payments_service ON service_payments(service_id);
CREATE INDEX idx_service_notes_service ON service_notes(service_id);