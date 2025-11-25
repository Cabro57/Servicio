-- 1. Yeni SERVICES tablosunu oluştur (customer_id INTEGER ve NULL olabilir)
CREATE TABLE services_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER, -- Artık zorunlu değil (NULL olabilir)
    created_at TEXT,
    delivery_at TEXT,
    device_type TEXT,
    device_brand TEXT,
    device_model TEXT,
    device_serial TEXT,
    device_password TEXT,
    device_accessory TEXT,
    labor_cost REAL,
    paid REAL,
    payment_type TEXT,
    warranty_date TEXT,
    maintenance_date TEXT,
    reported_fault TEXT,
    detected_fault TEXT,
    action_taken TEXT,
    urgency_status TEXT,
    service_status TEXT,
    Notes TEXT,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- 2. Verileri services -> services_new tablosuna taşı
-- (LEFT JOIN ile olmayan müşteri ID'lerini NULL yapıyoruz)
INSERT INTO services_new (
    id, customer_id, created_at, delivery_at, device_type, device_brand, device_model,
    device_serial, device_password, device_accessory, labor_cost, paid, payment_type,
    warranty_date, maintenance_date, reported_fault, detected_fault, action_taken,
    urgency_status, service_status, Notes
)
SELECT
    s.id,
    c.id, -- Müşteri varsa ID gelir, yoksa NULL gelir (Otomatik temizlik)
    s.created_at, s.delivery_at, s.device_type, s.device_brand, s.device_model,
    s.device_serial, s.device_password, s.device_accessory, s.labor_cost, s.paid, s.payment_type,
    s.warranty_date, s.maintenance_date, s.reported_fault, s.detected_fault, s.action_taken,
    s.urgency_status, s.service_status, s.Notes
FROM services s
LEFT JOIN customers c ON s.customer_id = c.id;

-- 3. ENGELİ KALDIR: added_part tablosunu geçici bir tabloya yedekle
CREATE TABLE added_part_temp AS SELECT * FROM added_part;

-- 4. Bağımlı tabloyu (added_part) SİL
DROP TABLE added_part;

-- 5. Eski services tablosunu SİL
DROP TABLE services;

-- 6. Yeni tabloyu isimlendir
ALTER TABLE services_new RENAME TO services;

-- 7. added_part tablosunu TEKRAR OLUŞTUR
CREATE TABLE added_part (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    service_id INTEGER NOT NULL,
    series_no TEXT,
    brand TEXT,
    name TEXT NOT NULL,
    supplier_id INTEGER,
    device_type TEXT,
    model TEXT,
    amount INTEGER DEFAULT 1,
    purchase_price REAL NOT NULL DEFAULT 0.0,
    sale_price REAL NOT NULL DEFAULT 0.0,
    warranty_period INTEGER,
    purchase_date TEXT,
    description TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    FOREIGN KEY (service_id) REFERENCES services(id)
);

-- 8. Yedeklenen parça verilerini GÜVENLİ ŞEKİLDE geri yükle
-- Sadece Services tablosunda ID'si var olan servislerin parçalarını al
-- Sadece Suppliers tablosunda ID'si var olan (veya boş olan) tedarikçilerin parçalarını al
INSERT INTO added_part
SELECT * FROM added_part_temp
WHERE service_id IN (SELECT id FROM services)
  AND (supplier_id IS NULL OR supplier_id IN (SELECT id FROM suppliers));

-- 9. Geçici tabloyu temizle
DROP TABLE added_part_temp;