-- =========================================================================
-- V17__add_currency_support.sql
-- Parça alış/satış fiyatları artık USD/EUR gibi bir dövizle girilebiliyor.
--
-- exchange_rates: güncel kur tablosu (tek satır/döviz, üzerine yazılır).
-- Kur geçmişi bilinçli olarak tutulmuyor — parça satırına kaydedilen
-- purchase_price_original/purchase_currency + o anki TL karşılığı
-- (purchase_price) zaten "alım anındaki değer" izini bırakıyor.
--
-- parts.purchase_price/sale_price (TL) kanonik alan olarak kalıyor; yeni
-- kolonlar sadece orijinal döviz tutarını/cinsini saklıyor.
-- =========================================================================

CREATE TABLE exchange_rates (
    currency_code TEXT PRIMARY KEY,
    currency_name TEXT NOT NULL,
    rate          NUMERIC NOT NULL DEFAULT 0,
    source        TEXT NULL,
    updated_at    TIMESTAMP NULL
);

INSERT INTO exchange_rates (currency_code, currency_name, rate, source, updated_at) VALUES
    ('USD', 'Amerikan Doları', 0, NULL, NULL),
    ('EUR', 'Euro', 0, NULL, NULL);

ALTER TABLE parts ADD COLUMN purchase_currency TEXT NOT NULL DEFAULT 'TRY';
ALTER TABLE parts ADD COLUMN purchase_price_original NUMERIC NULL;
ALTER TABLE parts ADD COLUMN sale_currency TEXT NOT NULL DEFAULT 'TRY';
ALTER TABLE parts ADD COLUMN sale_price_original NUMERIC NULL;
