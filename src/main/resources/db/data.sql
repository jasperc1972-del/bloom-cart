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
