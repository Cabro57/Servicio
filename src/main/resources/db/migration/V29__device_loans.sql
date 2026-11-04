-- =========================================================================
-- V29__device_loans.sql
-- Emanet cihaz takibi. Tek tablo, iki yön:
--   OUT: dükkân müşteriye yedek cihaz verir (cihaz dükkânın, is_loaner = 1).
--        Depozito alınabilir; iade sırasında hasar bedeli depozitodan kesilir.
--   IN : müşteri cihazını dükkâna emanet bırakır (cihaz müşterinin). Depozito yok.
--
-- Kayıt açıkken returned_at NULL'dır; bir cihazın aynı anda tek açık emaneti olabilir.
-- Depozito para hareketleri payments tablosuna yazılır (customer_id NULL, tahsis
-- hedefi 'DEVICE_LOAN'): kasa raporunda görünür ama müşteri cari bakiyesini etkilemez.
-- =========================================================================

ALTER TABLE devices ADD COLUMN is_loaner BOOLEAN NOT NULL DEFAULT 0;

CREATE TABLE device_loans (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id             INTEGER NOT NULL,
    customer_id           INTEGER NOT NULL,
    work_order_id         INTEGER NULL,                  -- isteğe bağlı: emanet bu servis sürerken verildi
    direction             VARCHAR(10) NOT NULL,          -- 'OUT' | 'IN'
    given_at              TIMESTAMP NOT NULL,
    due_at                TIMESTAMP NULL,                -- OUT: en geç iade, IN: planlanan teslim
    deposit               DECIMAL(12,2) NOT NULL DEFAULT 0.0,
    deposit_payment_type  VARCHAR(20) NULL,
    condition_out         TEXT,                          -- teslim anındaki durum
    accessories           TEXT,
    note                  TEXT,
    returned_at           TIMESTAMP NULL,
    condition_in          TEXT,                          -- dönüşteki durum / hasar açıklaması
    damage_charge         DECIMAL(12,2) NOT NULL DEFAULT 0.0,
    deposit_refund        DECIMAL(12,2) NOT NULL DEFAULT 0.0,
    is_deleted            BOOLEAN NOT NULL DEFAULT 0,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id)     REFERENCES devices(id)     ON DELETE RESTRICT,
    FOREIGN KEY (customer_id)   REFERENCES customers(id)   ON DELETE RESTRICT,
    FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE SET NULL
);

CREATE INDEX idx_device_loans_device   ON device_loans(device_id);
CREATE INDEX idx_device_loans_customer ON device_loans(customer_id);
CREATE INDEX idx_device_loans_open     ON device_loans(returned_at, is_deleted);

-- Bir cihazın aynı anda yalnızca bir açık emaneti olabilir.
CREATE UNIQUE INDEX ux_device_loans_one_open ON device_loans(device_id)
    WHERE returned_at IS NULL AND is_deleted = 0;
