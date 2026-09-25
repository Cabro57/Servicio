-- =========================================================================
-- V24__category_scope_and_orphan_brands.sql
-- 1) part_categories hem parçalar hem ürünler tarafından kullanılıyor (V21). Her kategori
--    artık hangi katalogda seçilebileceğini söyleyen bir kapsam taşır:
--    PART (yalnızca parça), PRODUCT (yalnızca ürün), BOTH (ikisi de).
-- 2) Eski ayarlar ekranı markayı türden "ayırırken" markanın kendisini silmiyordu; hiçbir türe
--    bağlı olmayan ve hiçbir cihazda kullanılmayan yetim markalar temizlenir.
-- =========================================================================

ALTER TABLE part_categories ADD COLUMN scope TEXT NOT NULL DEFAULT 'BOTH'
    CHECK (scope IN ('PART', 'PRODUCT', 'BOTH'));

-- Mevcut kullanıma göre kapsam: yalnızca ürünlerde kullanılan kategori PRODUCT, yalnızca
-- parçalarda kullanılan PART olur. Hiç kullanılmayan ya da iki tarafta da kullanılan BOTH kalır.
UPDATE part_categories SET scope = 'PRODUCT'
WHERE id IN (SELECT category_id FROM products WHERE category_id IS NOT NULL)
  AND id NOT IN (SELECT category_id FROM parts WHERE category_id IS NOT NULL);

UPDATE part_categories SET scope = 'PART'
WHERE id IN (SELECT category_id FROM parts WHERE category_id IS NOT NULL)
  AND id NOT IN (SELECT category_id FROM products WHERE category_id IS NOT NULL);

DELETE FROM device_brands
WHERE id NOT IN (SELECT brand_id FROM device_type_brand)
  AND id NOT IN (SELECT brand_id FROM devices);
