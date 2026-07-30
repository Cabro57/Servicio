-- =========================================================================
-- V15__create_app_settings.sql
-- İşletmeye ait ayarlar (barkod öneki, cihaz erişim kodu silme süresi,
-- otomatik kilit süresi) artık config.json'da değil veritabanında tutuluyor.
--
-- Gerekçe: bunlar makineye değil İŞLETMEYE ait değerler — veritabanı yedeğine
-- girmeli, yedek geri yüklendiğinde geri gelmeli ve ileride ortak bir arka
-- uçla paylaşılabilmeli. Makineye özel tercihler (tema, pencere durumu, tablo
-- sayfa boyutları, yedek klasörü) config.json'da kalmaya devam ediyor.
--
-- Eski config.json değerleri uygulama açılışında tek seferlik, idempotent
-- olarak buraya taşınır (bkz. ConfigMigrations + AppSettingService).
--
-- Not: V14'te ertelenen "devices.password kolonunu düşürme" adımı hâlâ
-- bekliyor; gerçek üründe bir süre çalıştığı doğrulanınca ayrı bir
-- migration olarak yapılacak.
-- =========================================================================

CREATE TABLE app_settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
