-- Müşteri tablosu
CREATE TABLE IF NOT EXISTS customers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    business_name TEXT,
    name TEXT NOT NULL,
    surname TEXT NOT NULL,
    phone_number_1 TEXT,
    phone_number_2 TEXT,
    id_no TEXT,
    address TEXT,
    email TEXT,
    status TEXT,
    note TEXT,
    created_at TEXT
);

-- Tedarikçi Tablosu
CREATE TABLE IF NOT EXISTS suppliers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    business_name TEXT,
    id_no TEXT,
    tax_no TEXT,
    tax_office TEXT,
    email TEXT,
    phone TEXT,
    address TEXT,
    note TEXT,
    created_at TEXT
);

-- Parça Tablosu
CREATE TABLE IF NOT EXISTS part (

    barcode TEXT PRIMARY KEY,         -- Ürün barkodu

    brand TEXT,              -- Ürün markası
    name TEXT NOT NULL,               -- Ürün adı
    supplier_id INTEGER,

    device_type TEXT,                 -- Cihaz türü (örn: dizüstü, masaüstü, yazıcı)
    model TEXT,                       -- Uyumlu marka/model bilgileri (örn: "Lenovo IdeaPad 3, HP Pavilion 15")

    purchase_price REAL NOT NULL DEFAULT 0.0,    -- Alış fiyatı
    sale_price REAL NOT NULL DEFAULT 0.0,    -- Alış fiyatı
    stock INTEGER NOT NULL DEFAULT 0,-- Mevcut stok miktarı
    min_stock INTEGER DEFAULT 0,     -- Minimum stok uyarısı için eşik değer

    warranty_period INTEGER,            -- Garanti süresi (örn: "12 Ay", "6 Ay")
    purchase_date TEXT,              -- ISO formatlı tarih: "YYYY-MM-DD"
    description TEXT,                -- Açıklama veya notlar

    created_at TEXT NOT NULL,         -- ISO timestamp: "YYYY-MM-DD HH:MM:SS"

    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE IF NOT EXISTS services (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- Müşteri Bilgileri
    customer_id INTEGER NOT NULL,
    created_at TEXT,
    delivery_at TEXT,

    -- Cihaz Bilgileri
    device_type TEXT,
    device_brand TEXT,
    device_model TEXT,
    device_serial TEXT,
    device_password TEXT,
    device_accessory TEXT,

    -- Fiyat Bilgileri
    labor_cost REAL,
    paid REAL,
    payment_type TEXT,

    -- Garanti Bakım Bilgileri
    warranty_date TEXT,
    maintenance_date TEXT,

    -- Arıza ve İşlem Bilgileri
    reported_fault TEXT,
    detected_fault TEXT,
    action_taken TEXT,

    -- Durum
    urgency_status TEXT,
    service_status TEXT,

    Notes TEXT,

    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- Servise Eklenen Parça Tablosu
CREATE TABLE IF NOT EXISTS added_part (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    service_id INTEGER NOT NULL,     -- Hangi servis kaydına ait
    barcode TEXT NOT NULL,           -- Hangi parçadan kullanıldı
    amount INTEGER NOT NULL,         -- Kullanılan/Satılan miktar
    price REAL NOT NULL,             -- Satıştaki birim fiyatı

    FOREIGN KEY (barcode) REFERENCES part(barcode),
    FOREIGN KEY (service_id) REFERENCES service(id)
);


