-- Hazır işçilikleri cihaz türüne göre filtreleyebilmek için.
-- NULL = Genel (tüm cihaz türlerinde geçerli)
ALTER TABLE labors ADD COLUMN device_type_id INTEGER REFERENCES device_types(id) ON DELETE SET NULL;