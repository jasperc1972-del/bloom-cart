-- 朵拉花店資料表（依 PRD 5.2，並補上 PRD 功能需要但 DDL 未列的欄位，見 README）

-- 1. 會員表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,                       -- BCrypt 雜湊
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',           -- 補充：MEMBER / ADMIN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 花卉商品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL UNIQUE,             -- 對應圖片路徑 /images/products/{product_code}/
    name VARCHAR(100) NOT NULL,
    flower_type VARCHAR(50) NOT NULL,
    flower_meaning TEXT,
    occasions VARCHAR(255),                               -- 補充：適用場景，逗號分隔（求婚,情人節）
    care_instructions TEXT,                               -- 補充：照顧注意事項
    base_price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    main_image_url VARCHAR(255) DEFAULT '/images/products/placeholder_flower.webp',
    status TINYINT DEFAULT 1,                             -- 1 上架, 0 下架
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 購物車明細表（會員）
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    wrapping_option VARCHAR(50) DEFAULT 'STANDARD',       -- STANDARD 牛皮紙 / FRENCH 法式霧面歐雅紙
    card_message VARCHAR(255),                            -- 卡片附言（程式限制 100 字）
    card_signature VARCHAR(50),                           -- 補充：署名
    anonymous TINYINT NOT NULL DEFAULT 0,                 -- 補充：1 = 匿名送花
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 訂單主檔
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    delivery_date DATE NOT NULL,
    delivery_time_slot VARCHAR(30),
    card_message VARCHAR(255),                            -- 整筆訂單卡片快照（各品項卡片彙整）
    status VARCHAR(20) DEFAULT 'PENDING',                 -- PENDING, PAID, DELIVERING, COMPLETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 訂單商品明細（下單當下快照）
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100),                            -- 補充：品名快照
    price DECIMAL(10, 2) NOT NULL,                        -- 花卉基礎單價快照
    wrapping_fee DECIMAL(10, 2) NOT NULL DEFAULT 0,       -- 補充：包裝加價快照
    quantity INT NOT NULL,
    wrapping_option VARCHAR(50),
    card_message VARCHAR(255),                            -- 補充：該品項卡片快照
    card_signature VARCHAR(50),
    anonymous TINYINT NOT NULL DEFAULT 0,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
