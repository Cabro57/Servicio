-- =========================================================================
-- V22__device_access_credentials_cascade.sql
-- V14'te device_access_credentials.work_order_id FK'ı ON DELETE CASCADE olmadan
-- tanımlanmıştı: erişim bilgisi girilmiş bir servis kaydı silinmek istendiğinde
-- "FOREIGN KEY constraint failed" hatası alınıyordu. Erişim bilgisi servis kaydına
-- ait olduğundan (kayıt yoksa bilginin de anlamı yok) kayıtla birlikte silinmeli.
-- SQLite FK değiştirmeyi desteklemediği için tablo yeniden kuruluyor.
-- =========================================================================

CREATE TABLE device_access_credentials_new (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    work_order_id     INTEGER NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    access_type       VARCHAR(20) NOT NULL,
    secret_encrypted  TEXT NULL,
    set_at            TIMESTAMP NOT NULL,
    purged_at         TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO device_access_credentials_new (id, work_order_id, access_type, secret_encrypted, set_at, purged_at, created_at)
SELECT id, work_order_id, access_type, secret_encrypted, set_at, purged_at, created_at
FROM device_access_credentials;

DROP TABLE device_access_credentials;
ALTER TABLE device_access_credentials_new RENAME TO device_access_credentials;

CREATE INDEX idx_device_access_credentials_work_order ON device_access_credentials(work_order_id);

-- AUTOINCREMENT sayacı — id'ler açıkça kopyalandığı için sqlite_sequence otomatik ilerlemedi.
UPDATE sqlite_sequence SET seq = (SELECT COALESCE(MAX(id), 0) FROM device_access_credentials)
WHERE name = 'device_access_credentials';
