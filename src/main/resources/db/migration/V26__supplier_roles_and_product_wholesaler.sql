-- =====================================================================
-- V26 — Tedarikçi / toptancı rolleri
-- =====================================================================
-- Aynı firma kaydı parça tedarikçisi (servis), ürün toptancısı (POS) ya da ikisi birden olabilir.
-- Parça formunda yalnızca tedarikçi rolündekiler, ürün formunda yalnızca toptancılar seçilir.
-- Mevcut kayıtların hepsi parçalara bağlı olduğundan "SUPPLIER" (tedarikçi) ile başlar.

ALTER TABLE suppliers ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'SUPPLIER'; -- SUPPLIER | WHOLESALER | BOTH

-- Ürünün toptancısı (parts.supplier_id'nin ürün karşılığı).
ALTER TABLE products ADD COLUMN supplier_id INTEGER REFERENCES suppliers(id) ON DELETE SET NULL;

CREATE INDEX idx_products_supplier ON products(supplier_id);
