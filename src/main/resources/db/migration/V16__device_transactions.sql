-- =========================================================================
-- V16__device_transactions.sql
-- 2.el alım-satım modülü: bir cihazın alım/satım olayları (servis olayları
-- work_orders'ta zaten tutuluyor, burada tekrar edilmiyor).
--
-- Cihaz merkezli tasarım: aynı cihaz birden fazla kez alınıp satılabilir
-- (device_id üzerinde tekillik kısıtı yok). "Şu an stokta mı" durumu ayrı
-- bir kolon olarak TUTULMUYOR; bir cihaz için en son (tarihe göre) satır
-- PURCHASE ise stokta, SALE ise satılmış kabul edilir (repository'de
-- MAX(transaction_date) ile türetilir).
-- =========================================================================

CREATE TABLE device_transactions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id         INTEGER NOT NULL,
    type              VARCHAR(10) NOT NULL,        -- 'PURCHASE' | 'SALE'
    customer_id       INTEGER NOT NULL,             -- PURCHASE: satıcı, SALE: alıcı
    transaction_date  TIMESTAMP NOT NULL,
    price             DECIMAL(12,2) DEFAULT 0.0,
    expertise_notes   TEXT,                         -- yalnızca PURCHASE
    warranty_months   INTEGER,                      -- yalnızca SALE
    note              TEXT,
    is_deleted        BOOLEAN DEFAULT 0,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id)   REFERENCES devices(id)   ON DELETE RESTRICT,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT
);

CREATE INDEX idx_device_transactions_device   ON device_transactions(device_id);
CREATE INDEX idx_device_transactions_customer ON device_transactions(customer_id);
CREATE INDEX idx_device_transactions_type     ON device_transactions(type);
