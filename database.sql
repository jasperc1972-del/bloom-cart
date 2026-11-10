-- =====================================================================
-- 朵拉花店 Dora Floral：完整資料庫建立腳本
-- 用法：在 MySQL Workbench 開啟本檔，按 ⚡ 全部執行即可
-- 內容：建立資料庫 → 5 張資料表 → 9 款種子商品
-- 註：網站啟動時也會自動執行同樣的建表與種子資料（src/main/resources/db/），
--     兩者擇一即可；重複執行不會出錯（IF NOT EXISTS / INSERT IGNORE）
-- 管理員帳號由網站首次啟動時依 config/application.yml 自動建立，不在此檔
-- =====================================================================

CREATE DATABASE IF NOT EXISTS dora_floral
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE dora_floral;

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
    batch_no VARCHAR(64),                                 -- 補充：結帳批號，同一次結帳拆出的訂單共用
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    payer_name VARCHAR(50),                               -- 補充：付款人姓名
    payer_phone VARCHAR(20),                              -- 補充：付款人電話
    payer_email VARCHAR(100),                             -- 補充：付款通知信寄送對象
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


-- ===================== 種子商品資料 =====================
-- 種子商品資料；product_code 唯一，INSERT IGNORE 讓重複啟動不會重複寫入
-- FLOWER-PEONY-PINK 刻意不放圖檔，用來展示 placeholder 容錯機制
INSERT IGNORE INTO products (product_code, name, flower_type, flower_meaning, occasions, care_instructions, base_price, stock, main_image_url, status) VALUES
('FLOWER-ROSE-RED', '經典紅玫瑰花束', '紅玫瑰', '熱烈的愛、真心告白。紅玫瑰是最直接的愛情宣言。', '求婚,情人節,紀念日', '每天換水並斜剪花莖 1 公分；避免陽光直射與冷氣出風口。', 1680.00, 30, '/images/products/FLOWER-ROSE-RED/main.webp', 1),
('FLOWER-TULIP-PINK', '粉鬱金香春日花束', '粉鬱金香', '幸福、愛的開始。粉色鬱金香象徵溫柔的祝福。', '情人節,生日,告白', '鬱金香會向光彎曲屬正常現象；水位維持 5 公分即可。', 1280.00, 25, '/images/products/FLOWER-TULIP-PINK/main.webp', 1),
('FLOWER-SUNFLOWER', '向日葵陽光花束', '向日葵', '忠誠、勇往直前、陽光般的祝福。', '畢業,開幕,探病', '花頭較重，花瓶需穩固；每 2 天換水一次。', 980.00, 40, '/images/products/FLOWER-SUNFLOWER/main.webp', 1),
('FLOWER-LILY-WHITE', '白色香水百合', '香水百合', '純潔、百年好合、高雅。', '婚禮,開幕,紀念日', '花開後摘除花粉避免沾染衣物；香氣濃郁，避免放在臥室。', 1580.00, 20, '/images/products/FLOWER-LILY-WHITE/main.webp', 1),
('FLOWER-CARNATION', '康乃馨感恩花束', '康乃馨', '母愛、感恩、溫馨。', '母親節,生日,探病', '去除浸在水中的葉子以免腐爛；放置陰涼處可延長花期。', 880.00, 50, '/images/products/FLOWER-CARNATION/main.webp', 1),
('FLOWER-HYDRANGEA-BLUE', '藍色繡球花束', '繡球花', '希望、團聚、真誠的感謝。', '畢業,生日,感謝', '繡球很愛喝水，花頭可噴水保濕；萎軟時可整束浸水 30 分鐘。', 1380.00, 15, '/images/products/FLOWER-HYDRANGEA-BLUE/main.webp', 1),
('FLOWER-ORCHID', '蝴蝶蘭開幕盆栽', '蝴蝶蘭', '幸福飛來、事業興旺。', '開幕,喬遷,升遷', '每 7 至 10 天澆水一次，水苔乾了再澆；放在明亮散射光處。', 2880.00, 10, '/images/products/FLOWER-ORCHID/main.webp', 1),
('FLOWER-BABYSBREATH', '滿天星夢幻花束', '滿天星', '真心喜歡、思念、純潔的心。', '告白,畢業,生日', '滿天星可自然風乾成乾燥花，保存時間更長。', 780.00, 35, '/images/products/FLOWER-BABYSBREATH/main.webp', 1),
('FLOWER-PEONY-PINK', '粉芍藥典雅花束', '芍藥', '富貴、美好的情意、羞澀的愛。', '婚禮,母親節,紀念日', '含苞時放溫水中可加速開花；花期短，建議收到 3 天內欣賞。', 1880.00, 12, '/images/products/FLOWER-PEONY-PINK/main.webp', 1);
