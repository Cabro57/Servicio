-- Services Tablosu
-- Sadece rakamdan oluşan ve içinde '-' (tire) işareti OLMAYANLARI timestamp kabul et
UPDATE services
SET created_at = REPLACE(datetime(created_at / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE created_at GLOB '[0-9]*' AND created_at NOT LIKE '%-%';

UPDATE services
SET delivery_at = REPLACE(datetime(delivery_at / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE delivery_at GLOB '[0-9]*' AND delivery_at NOT LIKE '%-%';

UPDATE services
SET warranty_date = REPLACE(datetime(warranty_date / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE warranty_date GLOB '[0-9]*' AND warranty_date NOT LIKE '%-%';

UPDATE services
SET maintenance_date = REPLACE(datetime(maintenance_date / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE maintenance_date GLOB '[0-9]*' AND maintenance_date NOT LIKE '%-%';

-- Diğer Tablolar
UPDATE customers
SET created_at = REPLACE(datetime(created_at / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE created_at GLOB '[0-9]*' AND created_at NOT LIKE '%-%';

UPDATE suppliers
SET created_at = REPLACE(datetime(created_at / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE created_at GLOB '[0-9]*' AND created_at NOT LIKE '%-%';

UPDATE part
SET created_at = REPLACE(datetime(created_at / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE created_at GLOB '[0-9]*' AND created_at NOT LIKE '%-%';

UPDATE part
SET purchase_date = date(purchase_date / 1000, 'unixepoch', 'localtime')
WHERE purchase_date GLOB '[0-9]*' AND purchase_date NOT LIKE '%-%';

UPDATE added_part
SET created_at = REPLACE(datetime(created_at / 1000, 'unixepoch', 'localtime'), ' ', 'T')
WHERE created_at GLOB '[0-9]*' AND created_at NOT LIKE '%-%';