# 朵拉花店 Dora Floral

🎬 **展示影片**：https://www.youtube.com/watch?v=dhz7wtV6JHY

花卉購物車網站，依《朵拉花店購物車網站 PRD》實作。

- Java 21（record DTO、Virtual Threads）
- Spring Boot 3.3、MVC 分層（Controller → Service → Mapper）
- MyBatis-Plus 3.5、MySQL 8.x
- 前端：原生 HTML/CSS/JS 呼叫 RESTful JSON API

## 功能對照 PRD

| PRD 需求 | 實作位置 |
|---|---|
| 訪客瀏覽、依花語 / 場合 / 花種篩選 | `ProductController`、`index.html` |
| 訪客 Session 購物車 | `CartServiceImpl`（userId 為 null 時存 HttpSession） |
| 登入時合併 Session 購物車到資料庫 | `AuthServiceImpl.login` → `CartService.mergeGuestCart` |
| 卡片附言（上限 100 字）、署名 / 匿名 | `AddToCartRequest`、`CartServiceImpl.Options` |
| 包裝加價：牛皮紙 0 元、法式霧面歐雅紙 +150 | `WrappingOption` |
| 單項總價 = (基礎單價 + 包裝加價) × 數量 | `CartServiceImpl.getCart`、`OrderServiceImpl.checkout` |
| 指定送達日期 + 時段（09:00-12:00 / 14:00-18:00） | `CheckoutRequest`、`DeliveryTimeSlot`、`checkout.html` |
| 結帳交易（扣庫存防超賣 + 寫訂單 + 清購物車） | `OrderServiceImpl.checkout`（`@Transactional`）+ `ProductMapper.decreaseStock` |
| 主圖 / 細節圖路徑與 placeholder 容錯 | `ProductImageService`（後端判斷）+ `common.js imgTag`（前端 onerror） |
| 金額 DECIMAL(10,2)、order_items 價格快照 | `schema.sql`、`OrderItem` |
| 管理員：上下架、價格庫存、訂單明細、配送狀態 | `AdminController`、`admin.html` |

### 防超賣做法

`UPDATE products SET stock = stock - ? WHERE id = ? AND status = 1 AND stock >= ?`
一行 SQL 同時判斷與扣減，InnoDB 會鎖住該列；回傳 0 就代表庫存不足，丟例外讓整筆交易回滾。
扣庫存前依商品 id 排序，讓同時進行的交易用相同順序鎖定，避免死結。

## 補充欄位（PRD 功能需要、但 DDL 沒列）

| 資料表 | 欄位 | 原因 |
|---|---|---|
| users | `role` | 區分會員 / 管理員 |
| products | `occasions`、`care_instructions` | PRD 4.1「適用場景」「照顧注意事項」 |
| cart_items | `card_signature`、`anonymous` | PRD 4.2「署名或匿名送花」 |
| order_items | `product_name`、`wrapping_fee`、`card_message`、`card_signature`、`anonymous` | 下單快照，不受日後商品資料變動影響 |

`orders.card_message` 存放各品項卡片的彙整快照（超過 255 字截斷，完整內容在 `order_items`）。

## 資料庫結構

資料庫名稱：`dora_floral`（MySQL 8.x、InnoDB、utf8mb4）

**最快的建法**：用 MySQL Workbench 開啟專案根目錄的 [`database.sql`](database.sql)，按 ⚡ 全部執行，
就會建立資料庫、5 張資料表與 9 款種子商品。（網站首次啟動時也會自動建立，兩者擇一即可。）

### 資料表一覽

| 資料表 | 用途 | 關聯 |
|---|---|---|
| `users` | 會員與管理員 | — |
| `products` | 花卉商品 | — |
| `cart_items` | 會員購物車（訪客購物車存在 Session，不進資料庫） | → `users`、`products` |
| `orders` | 訂單主檔：收件資訊、指定送達日期時段 | → `users` |
| `order_items` | 訂單明細：下單當下的價格、包裝、卡片快照 | → `orders`、`products` |

```
users ──< cart_items >── products
  │                         │
  └──< orders ──< order_items >┘
```

### CREATE TABLE

```sql
CREATE DATABASE IF NOT EXISTS dora_floral
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE dora_floral;

-- 1. 會員表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,                       -- BCrypt 雜湊
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',           -- MEMBER / ADMIN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 花卉商品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL UNIQUE,             -- 對應圖片路徑 /images/products/{product_code}/
    name VARCHAR(100) NOT NULL,
    flower_type VARCHAR(50) NOT NULL,                     -- 花種
    flower_meaning TEXT,                                  -- 花語
    occasions VARCHAR(255),                               -- 適用場景，逗號分隔（求婚,情人節）
    care_instructions TEXT,                               -- 照顧注意事項
    base_price DECIMAL(10, 2) NOT NULL,                   -- 金額一律 DECIMAL，避免浮點誤差
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
    card_signature VARCHAR(50),                           -- 署名
    anonymous TINYINT NOT NULL DEFAULT 0,                 -- 1 = 匿名送花
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 訂單主檔
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,                 -- 對外訂單號（yyyyMMddHHmm + 4 位流水號）
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,                  -- 收件人與下單會員分開記錄
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    delivery_date DATE NOT NULL,                          -- 指定送達日期
    delivery_time_slot VARCHAR(30),                       -- 指定時段
    card_message VARCHAR(255),                            -- 各品項卡片彙整快照
    status VARCHAR(20) DEFAULT 'PENDING',                 -- PENDING, PAID, DELIVERING, COMPLETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 訂單商品明細（下單當下快照，不受日後改價影響）
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100),                            -- 品名快照
    price DECIMAL(10, 2) NOT NULL,                        -- 花卉基礎單價快照
    wrapping_fee DECIMAL(10, 2) NOT NULL DEFAULT 0,       -- 包裝加價快照
    quantity INT NOT NULL,
    wrapping_option VARCHAR(50),
    card_message VARCHAR(255),                            -- 該品項卡片快照
    card_signature VARCHAR(50),
    anonymous TINYINT NOT NULL DEFAULT 0,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 9 款種子商品的 `INSERT` 請見 [`database.sql`](database.sql) 最後一段。
> 刪除訂單時須先刪 `order_items` 再刪 `orders`（外鍵限制），網站的刪除功能已在同一交易內依此順序處理。

## 執行方式

需求：JDK 21、Maven 3.9+、MySQL 8.x（啟動時自動建立 `dora_floral` 資料庫與資料表）

1. 在 MySQL 建立一個只能存取 `dora_floral` 的專用帳號：
   ```sql
   CREATE USER '<帳號>'@'<主機>' IDENTIFIED BY '<密碼>';
   GRANT ALL PRIVILEGES ON dora_floral.* TO '<帳號>'@'<主機>';
   ```
2. 複製 `application-example.yml` 為 `config/application.yml`，填入網站埠號、資料庫連線、帳號密碼與管理員帳密。
   `config/` 已在 .gitignore，**帳號、密碼、主機、埠號只存在本機，不會上傳**。
3. 在專案根目錄編譯並啟動（需在根目錄執行，才讀得到 `config/`），JAVA_HOME 需指向 JDK 21：
   ```powershell
   mvn -DskipTests package
   & "$env:JAVA_HOME\bin\java.exe" -jar target\flowerstore-1.0.0.jar
   ```
4. 用瀏覽器開啟 `http://<主機>:<網站埠號>`

### 一鍵啟動（Windows）

設定好 `config/application.yml`（含 `app.site-url`）後，雙擊 **`啟動朵拉花店.bat`** 即可：

- 自動尋找 JDK 21；程式碼有更新會先用 Maven 重新編譯
- 網站就緒後自動開啟瀏覽器；網站已在執行則直接開瀏覽器
- 關閉黑色視窗即停止網站

- 用 `config/application.yml` 設定的管理員帳號登入後，頁首會出現「管理後台」
- 「粉芍藥典雅花束」刻意沒有圖檔，用來展示 placeholder 容錯

## API 一覽

| 方法 | 路徑 | 權限 |
|---|---|---|
| GET | `/api/products?keyword=&occasion=&flowerType=` | 公開 |
| GET | `/api/products/{id}`、`/api/products/filters`、`/api/meta` | 公開 |
| GET / POST | `/api/cart`、`/api/cart/add` | 訪客 / 會員 |
| PUT / DELETE | `/api/cart/items/{id}` | 訪客 / 會員 |
| POST | `/api/auth/register`、`/api/auth/login`、`/api/auth/logout` | 公開 |
| GET | `/api/auth/me` | 公開 |
| POST | `/api/orders/checkout` | 會員 |
| GET | `/api/orders`、`/api/orders/{orderNo}` | 會員 |
| GET / POST / PUT | `/api/admin/products[/{id}]` | 管理員 |
| GET / PUT | `/api/admin/orders`、`/api/admin/orders/{id}/status` | 管理員 |

## 商品圖片

放在 `src/main/resources/static/images/products/{product_code}/`：

- `main.webp`：主圖，1:1、800x800
- `detail_1.webp`、`detail_2.webp`…：細節圖，依序編號

目前的圖是 `tools/generate_images.py` 畫的示範插圖，上線前請換成實拍照片。
