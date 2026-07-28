-- =========================================================================
-- V14__create_device_access_credentials.sql
-- Cihaz ekran kilidi erişim bilgisi (PIN/şifre/desen) artık Device'ta değil,
-- bağımsız bir tabloda, servis kaydına (work_order) bağlı olarak tutuluyor.
-- Değer DB'de şifreli (AES-GCM) saklanır; teslimattan belirli süre sonra
-- uygulama tarafından kalıcı olarak silinir (bkz. DeviceAccessCredentialService).
--
-- devices.password kolonu bu migration'da BİLİNÇLİ OLARAK silinmiyor — veri
-- önce (uygulama açılışında, tek seferlik) bu yeni tabloya taşınacak, bir
-- sonraki sürümde (V15) kolon düşürülecek.
-- =========================================================================

CREATE TABLE device_access_credentials (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    work_order_id     INTEGER NOT NULL REFERENCES work_orders(id),
    access_type       VARCHAR(20) NOT NULL,
    secret_encrypted  TEXT NULL,
    set_at            TIMESTAMP NOT NULL,
    purged_at         TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_access_credentials_work_order ON device_access_credentials(work_order_id);
