# 外掛開發與使用指南

## 外掛是什麼

外掛是一個 JSON 檔案，宣告一個或多個工具。AI 判斷何時呼叫工具並填寫參數，DevCodex 依你寫的範本送出 HTTP 請求，再把回應交還給 AI。

適合接入：天氣查詢、Home Assistant、n8n 工作流程、自建 API、內部系統。

## 外掛與技能的區別

| 類型 | 作用 | 格式 |
| --- | --- | --- |
| 外掛 | 給 AI 增加一隻手：呼叫外部 HTTP 介面 | JSON |
| 技能 | 給 AI 增加一份知識：方法與流程說明 | SKILL.md |

兩者可以配合：技能說明做事的方法，外掛提供呼叫的工具。

## 快速開始

複製下面的 JSON，在外掛頁點右下角按鈕，選擇貼上安裝即可。

```json
{
  "id": "weather-demo",
  "name": "天氣查詢",
  "version": "1.0.0",
  "author": "你的名字",
  "description": "查詢城市天氣",
  "tools": [
    {
      "name": "get_weather",
      "description": "使用者詢問某城市的天氣時呼叫",
      "parameters": "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市英文名，如 Beijing\"}},\"required\":[\"city\"]}",
      "endpoint": {
        "method": "GET",
        "url": "https://wttr.in/{{city}}?format=3"
      }
    }
  ]
}
```

安裝後在對話裡說「查一下 Beijing 的天氣」，AI 會自動呼叫 get_weather。

## 欄位說明

### 外掛層

| 欄位 | 必填 | 說明 |
| --- | --- | --- |
| id | 是 | 唯一識別碼，同 id 再次安裝會覆蓋舊版本 |
| name | 是 | 顯示名稱 |
| version | 否 | 版本號，預設 1.0.0 |
| author | 否 | 作者 |
| description | 否 | 外掛說明 |
| enabled | 否 | 是否啟用，預設 true |
| tools | 是 | 工具清單，至少 1 個 |

### 工具層（tools 的每一項）

| 欄位 | 必填 | 說明 |
| --- | --- | --- |
| name | 是 | 工具名稱，英文，不能含空格 |
| description | 否 | 寫清楚何時該用，AI 靠它決定是否呼叫 |
| parameters | 否 | JSON Schema 的字串形式，預設 {} |
| endpoint | 是 | HTTP 請求設定 |

### 端點層（endpoint）

| 欄位 | 必填 | 說明 |
| --- | --- | --- |
| url | 是 | 請求位址，支援 {{參數名}} |
| method | 否 | GET、POST、PUT、DELETE，預設 POST |
| headers | 否 | 請求標頭，值裡支援 {{參數名}} |
| bodyTemplate | 否 | 請求主體範本，用於 POST 與 PUT |
| resultPath | 否 | 從 JSON 回應裡擷取結果的路徑 |

## 參數寫法

parameters 是一個字串，裡面放 JSON Schema，內部的雙引號要寫成 `\"` 轉義：

```json
"{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名\"}},\"required\":[\"city\"]}"
```

每個參數都寫 description，AI 依靠它填對值。parameters 解析失敗時，工具會退化成沒有參數。

## 範本規則

- {{參數名}} 會被 AI 填寫的參數值原樣替換，不做 URL 編碼。
- URL 裡的空格會自動轉成 %20，其他特殊字元需要保證參數值本身合法。
- 參數名與 {{}} 裡的名字必須完全一致，未符合的預留位置會原樣保留。
- GET 與 DELETE 不送出請求主體，參數只能透過 URL 與 headers 裡的預留位置傳遞，沒有寫預留位置的參數會被捨棄。
- 其他方法（POST、PUT、PATCH 等）沒寫 bodyTemplate 時，請求主體是 AI 填寫的參數 JSON；寫了就依範本送出。
- 請求主體的 Content-Type 固定為 application/json。

帶請求標頭和請求主體的例子：

```json
"endpoint": {
  "method": "POST",
  "url": "https://api.example.com/v1/notes",
  "headers": { "Authorization": "Bearer YOUR_KEY" },
  "bodyTemplate": "{\"title\": \"{{title}}\", \"text\": \"{{text}}\"}"
}
```

## 結果擷取 resultPath

- 留空：回傳完整回應，最多保留前 8000 個字元。
- 填 data.items：依點號逐層讀取 JSON 物件裡的欄位。
- 只支援物件欄位，不支援陣列索引。
- 路徑中途不存在或回應不是 JSON 時，回傳完整回應（同樣最多 8000 個字元）。
- 命中的值以 JSON 文字回傳：字串欄位會帶雙引號，物件與陣列回傳其 JSON 內容，結果同樣最多 8000 個字元。
- 請求失敗時，AI 收到 HTTP 狀態碼加回應前 500 個字元，或以 ERROR 開頭的例外訊息。

## 安裝與驗證

支援三種方式：從檔案安裝、輸入 URL 下載、貼上 JSON。安裝時會檢查：

1. id 與 name 不能為空。
2. 至少宣告一個工具。
3. 每個工具都要有 name 與 endpoint.url。
4. 工具名稱不能含空格。

同一外掛內的工具名稱請自行避免重複，不同外掛之間的工具名稱也不要衝突，否則 AI 無法區分。

## 偵錯流程

1. 儲存 JSON 後安裝，確認外掛頁出現該外掛且開關已開啟。
2. 在對話裡明確說出要做的事，例如「用 get_weather 查 Beijing 的天氣」。
3. 展開工具卡片，檢查 AI 填寫的參數與回應內容。
4. 回傳 HTTP 4xx，檢查 headers 與參數；回傳 ERROR，檢查 URL 與網路。
5. 修改 JSON 後用相同 id 重新安裝，即可覆蓋。

## 撰寫建議

1. description 寫清楚何時使用，不要只寫介面是什麼。
2. API Key 放在 headers 裡，不要拼進 URL。
3. 回應很大時設定 resultPath，減少 AI 讀取的內容。
4. 一個工具只做一件事，參數盡量少。
5. 外掛 JSON 以明文保存在應用程式私有目錄，不要分享帶真實金鑰的檔案。

## 技能（SKILL.md）格式

技能是 Markdown 檔案，開頭用 frontmatter 寫名稱與簡介：

```markdown
---
name: translate
description: 中英互譯，使用者要求翻譯時使用
---
將使用者內容翻譯成中文，若已是中文則譯成英文，只輸出譯文。
```

- name 必填，description 建議填寫。
- description 常駐系統提示詞，正文由 AI 在需要時透過 skill_read 讀取。
- 在設定頁的技能入口安裝，也可以從 GitHub 儲存庫匯入。
