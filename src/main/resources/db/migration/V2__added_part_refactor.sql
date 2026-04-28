-- V2__added_part_refactor.sql

-- 1. Eski tabloyu yeniden adlandır
ALTER TABLE added_part RENAME TO added_part_old;

-- 2. Yeni tabloyu oluştur
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

-- 3. Eski added_part verilerini part tablosuyla birleştirerek taşı
INSERT INTO added_part (
    service_id,
    series_no,
    brand,
    name,
    supplier_id,
    device_type,
    model,
    amount,
    purchase_price,
    sale_price,
    warranty_period,
    purchase_date,
    description,
    created_at
)
SELECT
    ap.service_id,
    ap.series_no,
    p.brand,
    COALESCE(ap.name, p.name),
    p.supplier_id,
    p.device_type,
    p.model,
    ap.amount,
    COALESCE(ap.purchase_price, p.purchase_price),
    COALESCE(ap.selling_price, p.sale_price),
    p.warranty_period,
    p.purchase_date,
    p.description,
    COALESCE(ap.added_date, p.created_at, CURRENT_TIMESTAMP)
FROM added_part_old ap
LEFT JOIN part p ON ap.barcode = p.barcode;

-- 4. Eski tabloyu sil
DROP TABLE added_part_old;

-- 5. created_at formatını yyyy-MM-ddTHH:mm:ss biçimine dönüştür
UPDATE added_part
SET created_at = REPLACE(created_at, ' ', 'T')
WHERE created_at LIKE '% %';

UPDATE part
SET created_at = REPLACE(created_at, ' ', 'T')
WHERE created_at LIKE '% %';

UPDATE suppliers
SET created_at =
    CASE
        WHEN created_at LIKE '__/__/____%' THEN
            substr(created_at, 7, 4) || '-' || substr(created_at, 4, 2) || '-' || substr(created_at, 1, 2) ||
            'T' || substr(created_at, 12)
        WHEN created_at LIKE '% %' THEN
            REPLACE(created_at, ' ', 'T')
        ELSE
            created_at
    END;

UPDATE customers
SET created_at =
    CASE
        WHEN created_at LIKE '__/__/____%' THEN
            substr(created_at, 7, 4) || '-' || substr(created_at, 4, 2) || '-' || substr(created_at, 1, 2) ||
            'T' || substr(created_at, 12)
        WHEN created_at LIKE '% %' THEN
            REPLACE(created_at, ' ', 'T')
        ELSE
            created_at
    END;
