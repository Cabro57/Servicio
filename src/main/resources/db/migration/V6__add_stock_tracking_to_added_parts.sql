-- added_part tablosuna stok referansını ve takip bayrağını ekliyoruz
ALTER TABLE added_part ADD COLUMN part_barcode TEXT;
ALTER TABLE added_part ADD COLUMN is_stock_tracked INTEGER DEFAULT 0;

CREATE TABLE IF NOT EXISTS users (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     name TEXT NOT NULL,
     surname TEXT,
     email TEXT UNIQUE NOT NULL,
     password TEXT NOT NULL,
     business_name TEXT,
     phone_number TEXT,
     profile_picture TEXT,
     created_at TEXT NOT NULL
);