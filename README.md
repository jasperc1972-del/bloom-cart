# 朵拉花店 Dora Floral

🎬 **展示影片**

- 最新：https://youtu.be/btPZnIv2l9g （多收件人下單、付款人、通知信與送達流程）
- 上一版：https://www.youtube.com/watch?v=StpHBfucj7Y


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
| 指定送達日期 + 時段（09:00-12:00 / 14:00-18:00） | `ShipmentRequest`、`DeliveryTimeSlot`、`checkout.html` |
| 多位收件人：一次結帳送到不同地址，依收件人拆成多張訂單（共用結帳批號） | `CheckoutRequest`、`OrderServiceImpl.checkout`、`checkout.html` |
| 付款人與收件人分開記錄，可同一人付全部或每位收件人不同付款人 | `ShipmentRequest`、`orders.payer_*` |
| 待付款通知信（同付款人合併一封，含匯款資訊）、收款確認信、送達通知信 | `OrderMailService`、`OrderMailListener`（交易提交後非同步寄送） |
| 結帳交易（扣庫存防超賣 + 寫訂單 + 清購物車） | `OrderServiceImpl.checkout`（`@Transactional`）+ `ProductMapper.decreaseStock` |
| 主圖 / 細節圖路徑與 placeholder 容錯 | `ProductImageService`（後端判斷）+ `common.js imgTag`（前端 onerror） |
| 金額 DECIMAL(10,2)、order_items 價格快照 | `schema.sql`、`OrderItem` |
| 管理員：上下架、價格庫存、訂單明細、配送狀態 | `AdminController`、`admin.html` |

### 防超賣做法

`UPDATE products SET stock = stock - ? WHERE id = ? AND status = 1 AND stock >= ?`
一行 SQL 同時判斷與扣減，InnoDB 會鎖住該列；回傳 0 就代表庫存不足，丟例外讓整筆交易回滾。
扣庫存前依商品 id 排序，讓同時進行的交易用相同順序鎖定，避免死結。

## 多收件人下單、付款人與通知信

一次結帳可以把不同花束送到不同地址，付款人可以是同一人，也可以每位收件人各自不同；
訂單從下單到送達，系統會在關鍵節點自動寄信給付款人。

### 下單情境

| 情境 | 結帳頁操作 | 結果 |
|---|---|---|
| 一束花、一位收件人 | 照常填收件人與付款人 | 1 張訂單 |
| 多束花送給**不同收件人**、**同一人付款** | 按「＋ 新增收件人」，右側每束花用「送給」下拉選單指定收件人；保持勾選「所有收件人由同一位付款人付款」 | 依收件人拆成多張訂單，共用一個結帳批號；付款人只收到**一封合併的**待付款通知 |
| 多束花送給不同收件人、**不同人付款** | 取消勾選「同一位付款人」，每位收件人底下各填付款人姓名、電話、Email | 拆成多張訂單；每位付款人各收到一封自己的待付款通知 |
| 每位收件人送達時間不同 | 每位收件人各選送達日期與時段 | 各訂單分開配送、分開推進狀態 |

結帳時會擋下的狀況（整筆不成立、不扣庫存）：

- 有收件人沒分配到任何花、或有花沒指定收件人
- 同一束花同時分給兩位收件人
- 送來不屬於自己購物車的項目（例如另一個分頁已結帳）
- 任一位收件人的送達日期超出可預約範圍、時段不合法
- 任一商品庫存不足（回 409，並告知目前剩餘數量）

> 「送給」是以購物車的**每一項**分配。同一款花要送兩個人，請分兩次加入購物車（或選不同包裝），讓它變成兩項。

### 訂單狀態與通知信

狀態只能往後推進，由管理員在「管理後台 → 訂單管理」逐步按下：

```
待付款 ──[標記已付款]──▶ 已付款 ──[開始配送]──▶ 配送中 ──[標記已送達]──▶ 已送達
```

| 時機 | 寄給 | 信件內容 |
|---|---|---|
| 會員下單成功 | 付款人 | **待付款通知**：訂單明細、應付總額、匯款資訊（匯款備註請填訂單編號）；同一付款人多張訂單合併一封 |
| 管理員按「標記已付款」 | 付款人 | **已收到款項**：確認收款，花禮將於指定時間送達 |
| 管理員按「開始配送」 | — | 不寄信 |
| 管理員按「標記已送達」 | 付款人 | **花禮已送達**：送達的收件人與明細，並附上隨花卡片內容（匿名卡片不顯示署名） |

- 寄信在**交易提交成功後、以非同步執行緒**進行：下單回應不必等寄信；交易回滾的訂單不會寄出。
- 寄信失敗（SMTP 連不上、密碼錯誤）只記錄 log，**不影響訂單**；log 不印收件人 Email。
- 沒有 `config/mail.yml` 或 `app.mail.from` 留空時不寄信，網站照常運作。
- 請一律從管理後台推進狀態；直接在資料庫改 `orders.status` 不會寄信，也會跳過「只能往後推進」的檢查。

### 寄信設定（Gmail）

1. Google 帳戶 → 安全性 → 開啟「兩步驟驗證」→「應用程式密碼」，建立一組 16 碼密碼
   （不是 Gmail 登入密碼，也不是本網站的密碼）。
2. 複製 [`mail-example.yml`](mail-example.yml) 為 `config/mail.yml`，填入寄件 Gmail、應用程式密碼、`app.mail.from`，
   以及要寫在待付款通知裡的匯款資訊（選填）。`config/` 已在 .gitignore，**不會上傳**。
3. 重新啟動網站才會讀到新設定。

## 補充欄位（PRD 功能需要、但 DDL 沒列）

| 資料表 | 欄位 | 原因 |
|---|---|---|
| users | `role` | 區分會員 / 管理員 |
| products | `occasions`、`care_instructions` | PRD 4.1「適用場景」「照顧注意事項」 |
| cart_items | `card_signature`、`anonymous` | PRD 4.2「署名或匿名送花」 |
| orders | `batch_no` | 同一次結帳拆出的多張訂單共用的結帳批號 |
| orders | `payer_name`、`payer_phone`、`payer_email` | 付款人（可與會員、收件人不同）；Email 用來寄付款通知 |
| order_items | `product_name`、`wrapping_fee`、`card_message`、`card_signature`、`anonymous` | 下單快照，不受日後商品資料變動影響 |

`orders.card_message` 存放各品項卡片的彙整快照（超過 255 字截斷，完整內容在 `order_items`）。

已經建好的舊資料庫不用手動改：網站啟動時 `SchemaMigrator` 會檢查 `orders` 表，缺少上面的新欄位就自動 `ALTER TABLE` 補上。

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
    batch_no VARCHAR(64),                                 -- 結帳批號：同一次結帳拆出的訂單共用
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,                  -- 收件人與下單會員分開記錄
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    payer_name VARCHAR(50),                               -- 付款人（可與會員、收件人不同）
    payer_phone VARCHAR(20),
    payer_email VARCHAR(100),                             -- 付款通知信寄送對象
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
   （選用）要寄訂單通知信：複製 `mail-example.yml` 為 `config/mail.yml`，填入 SMTP 帳號（Gmail 用「應用程式密碼」）與匯款資訊。
   沒有這個檔案網站照常運作，只是不寄信。
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
- 商品沒有圖檔時會自動顯示 `placeholder_flower.webp`（placeholder 容錯）

## 單元測試（JUnit 5）

共 **100 個測試**，使用 JUnit 5 + Mockito + AssertJ（`spring-boot-starter-test` 內建，不需額外套件）。
Mapper 全部以 Mockito 模擬，**執行測試不需要 MySQL，也不會動到任何資料**。

```powershell
mvn test
```

看到 `Tests run: 100, Failures: 0` 與 `BUILD SUCCESS` 即全部通過。
（過程中會出現一段 `ERROR ... DB password wrong` 的紀錄，那是測試「系統錯誤不洩漏內部訊息」時故意製造的例外，屬正常現象。）

### 測試檔案一覽

測試位於 `src/test/java/com/dora/flowerstore/`，每個測試都以 `@DisplayName` 標註中文說明。

| 測試類別 | 數量 | 驗證重點 |
|---|---|---|
| `service/impl/CartServiceImplTest` | 19 | 訪客 / 會員購物車、相同選項合併數量、庫存上限、卡片附言 100 字上限、匿名不留署名、單項小計與總額、不可修改他人項目、登入合併時超量自動調降至庫存 |
| `service/impl/OrderServiceImplTest` | 27 | 結帳金額（含包裝加價）、依商品 id 排序扣庫存、庫存不足回 409 且不建立訂單、配送日期範圍與邊界值、配送時段、明細價格快照；**多收件人拆單**（各自金額 / 地址 / 付款人、共用結帳批號）、同款花分給兩人合併扣庫存、漏分配 / 重複分配 / 他人購物車項目都擋下；狀態只能往後推進、已付款與已送達才發通知事件、取消 / 刪除時是否加回庫存 |
| `controller/ApiControllerTest` | 17 | 登入攔截器（未登入 401、非管理員 403）、`@Valid` 欄位驗證訊息（含巢狀收件人、付款人 Email 格式、至少一位收件人）、JSON 格式錯誤、業務例外狀態碼、未預期錯誤不洩漏內部訊息 |
| `service/OrderMailServiceTest` | 8 | 同付款人合併一封、不同付款人各一封、匯款資訊有無、未設定 SMTP 不寄、寄送失敗不往外拋、收款確認與送達通知內容、舊訂單無 Email 不寄 |
| `service/impl/AuthServiceImplTest` | 5 | 註冊時密碼 BCrypt 雜湊、帳號重複（含同時註冊被 UNIQUE 擋下）、登入更換 Session ID 並合併訪客購物車、登入失敗不洩漏帳號是否存在 |
| `enums/EnumsTest` | 10 | 包裝選項與加價、訂單狀態順序、配送時段 |
| `service/impl/ProductServiceImplTest` | 8 | 下架商品 404、場合格式正規化（全形逗號、頓號、重複）、篩選選項、商品代碼重複、改代碼時主圖路徑同步 |
| `service/ProductImageServiceTest` | 6 | 缺圖回 placeholder、細節圖遇缺號即停、拒絕外部網址與路徑穿越、存在檢查快取 |
| `support/MybatisPlusTestSupport` | — | 共用工具：不啟動 Spring 時手動初始化 MyBatis-Plus 的 Entity 欄位對應，讓 `LambdaQueryWrapper` 可在單元測試中使用 |

### 測試設計說明

- **Service 層**：以 `@Mock` 模擬 Mapper，專注驗證業務規則；用 `ArgumentCaptor` 檢查寫入資料庫的內容、`InOrder` 檢查扣庫存與刪除的先後順序。
- **Controller 層**：以 standalone `MockMvc` 組裝 Controller + `AuthInterceptor` + `GlobalExceptionHandler`，不載入完整 Spring Context。
- **參數化測試**：日期邊界、狀態倒退等情境用 `@ParameterizedTest` + `@CsvSource` 一次涵蓋多組輸入。
- **驗證測試有效**：曾刻意把結帳金額中的包裝加價移除，測試立即失敗（預期 4940、實際 4640），確認測試能抓到計算錯誤。

## API 一覽

| 方法 | 路徑 | 權限 |
|---|---|---|
| GET | `/api/products?keyword=&occasion=&flowerType=` | 公開 |
| GET | `/api/products/{id}`、`/api/products/filters`、`/api/meta` | 公開 |
| GET / POST | `/api/cart`、`/api/cart/add` | 訪客 / 會員 |
| PUT / DELETE | `/api/cart/items/{id}` | 訪客 / 會員 |
| POST | `/api/auth/register`、`/api/auth/login`、`/api/auth/logout` | 公開 |
| GET | `/api/auth/me` | 公開 |
| POST | `/api/orders/checkout`（body 見下方） | 會員 |
| GET / DELETE | `/api/orders`、`/api/orders/{orderNo}`（DELETE = 取消待付款訂單） | 會員 |
| GET / POST / PUT | `/api/admin/products[/{id}]` | 管理員 |
| GET / PUT / DELETE | `/api/admin/orders`、`/api/admin/orders/{id}/status`、`/api/admin/orders/{id}` | 管理員 |

結帳 body：每位收件人一個 `shipment`，`cartItemIds` 是購物車項目 id，每一項必須剛好分給一位收件人；回傳拆出的訂單陣列。

```json
{
  "shipments": [
    {
      "recipientName": "收件人 A", "recipientPhone": "0900-000-001", "shippingAddress": "測試市 A 路 1 號",
      "deliveryDate": "2026-12-01", "deliveryTimeSlot": "09:00-12:00",
      "payerName": "付款人", "payerPhone": "0900-000-009", "payerEmail": "payer@example.com",
      "cartItemIds": [11]
    },
    {
      "recipientName": "收件人 B", "recipientPhone": "0900-000-002", "shippingAddress": "測試市 B 路 2 號",
      "deliveryDate": "2026-12-03", "deliveryTimeSlot": "14:00-18:00",
      "payerName": "付款人", "payerPhone": "0900-000-009", "payerEmail": "payer@example.com",
      "cartItemIds": [12]
    }
  ]
}
```

## 商品圖片

放在 `src/main/resources/static/images/products/{product_code}/`：

- `main.webp`：主圖，建議 1:1 正方形、最長邊 800px 左右
- `detail_1.webp`、`detail_2.webp`…：細節圖，依序編號（遇到缺號就不再往下找）
- 只認 `.webp` 格式；換圖後需重新 `mvn package`（圖片會打包進 jar），瀏覽器按 `Ctrl + F5` 重新整理

目前 9 款商品的主圖與紅玫瑰細節圖 1 已換成花卉照片，其餘細節圖仍是 `tools/generate_images.py` 畫的示範插圖。
