-- =========================================================================
-- V10__part_categories_dictionary.sql
-- Parça kategorisini (serbest metin) cihaz türü/marka gibi veritabanı
-- sözlüğüne taşır — Ayarlar'dan ekle/düzenle/sil yapılabilsin diye.
-- =========================================================================

CREATE TABLE part_categories (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) UNIQUE NOT NULL
);

-- Mevcut serbest-metin kategori değerlerinden sözlüğü doldur
INSERT INTO part_categories (name)
SELECT DISTINCT TRIM(category) FROM parts
WHERE category IS NOT NULL AND TRIM(category) != '';

-- Eski varsayılan değer her zaman sözlükte bulunsun
INSERT OR IGNORE INTO part_categories (name) VALUES ('Genel');

ALTER TABLE parts ADD COLUMN category_id INTEGER REFERENCES part_categories(id) ON DELETE SET NULL;

UPDATE parts
SET category_id = (SELECT id FROM part_categories pc WHERE pc.name = TRIM(parts.category))
WHERE category IS NOT NULL AND TRIM(category) != '';

-- Kolonu düşürmeden önce ona bağlı index'i kaldırmak gerekiyor (SQLite kısıtı)
DROP INDEX IF EXISTS idx_parts_category;
ALTER TABLE parts DROP COLUMN category;

CREATE INDEX idx_parts_category_id ON parts(category_id);
