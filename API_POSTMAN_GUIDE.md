# Postman API Testing Guide

This document provides a comprehensive guide to testing the WiseAd API using Postman.

## Global Configuration
*   **Base URL**: `http://localhost:8080` (Set this as a variable `{{baseUrl}}` in Postman)
*   **Headers**:
    *   `Content-Type`: `application/json` (Unless specified otherwise, e.g., for file uploads)
    *   `Authorization`: `Bearer {{accessToken}}` (Required for most endpoints after login)

---

## 1. Authentication & User (`/api/auth`, `/api/users`, `/api/email`)

### 1.1 Email Verification (Pre-Signup/Find ID)
Before signing up or finding ID, you typically verify the email address.

**Step 1: Send Verification Code**
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/email/verification?email=test@example.com`
*   **Body**: (Empty or None)

**Step 2: Verify Code**
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/email/verification/verify?email=test@example.com&code=12345678`
*   **Body**: (Empty or None)
*   **Response**: `true` if valid.

### 1.2 Sign Up
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/auth/signup`
*   **Body**:
    ```json
    {
        "userId": "newuser",
        "userPass": "Password123!",
        "userPassConfirm": "Password123!",
        "corpName": "WiseAd Corp",
        "corpAddr": "Seoul, Korea",
        "bizNum": "123-45-67890",
        "bizTel": "02-1234-5678",
        "person": "John Doe",
        "phone": "010-1234-5678",
        "email": "test@example.com",
        "hintQuestion": "Q01",
        "hintAnswer": "Answer"
    }
    ```

### 1.3 Login
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/auth/login`
*   **Body**:
    ```json
    {
        "userId": "newuser",
        "userPass": "Password123!"
    }
    ```
    *   **Note**: Copy `accessToken` from the response to your Postman environment variables.

### 1.4 Refresh Token
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/auth/refresh`
*   **Body**:
    ```json
    {
        "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
    }
    ```

### 1.5 Find ID
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/users/find-id`
*   **Body**:
    ```json
    {
        "email": "test@example.com",
        "person": "John Doe"
    }
    ```

### 1.6 Check Duplicates
*   **Check User ID**: `GET {{baseUrl}}/api/auth/check-userid?userId=testuser`
*   **Check Email**: `GET {{baseUrl}}/api/auth/check-email?email=test@example.com`

---

## 2. Message Sending (`/api/message/send`, `/api/multi`)

### 2.1 Send SMS/LMS/MMS (Legacy)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/message/send`
*   **Body**:
    ```json
    {
        "msgType": "S", 
        "callback": "02-1234-5678",
        "receivers": [
            "010-1111-2222",
            "010-3333-4444"
        ],
        "subject": "Message Subject (LMS/MMS)",
        "text": "This is a test message.",
        "sendType": "1",
        "requestTime": null, 
        "delDuplicateNum": true,
        "fileCnt": 0
    }
    ```
    *   `msgType`: "S" (SMS), "L" (LMS), "M" (MMS)
    *   `requestTime`: Use `2025-12-25T10:00:00` for scheduled sending.

### 2.2 Send Multi Message (New - SMS/LMS/MMS with Variable Replacement)
**Option A: Direct JSON (No Files)**
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/multi/send`
*   **Body**:
    ```json
    {
        "messageType": "SMS",
        "callback": "02-1234-5678",
        "subject": "Subject for LMS",
        "text": "Hello [VAR1], your code is [VAR2].",
        "reqType": "direct",
        "delDuplicateNum": "N",
        "receivers": [
            {
                "phone": "010-1111-2222",
                "repChar01": "John",
                "repChar02": "12345"
            },
            {
                "phone": "010-3333-4444",
                "repChar01": "Jane",
                "repChar02": "67890"
            }
        ]
    }
    ```
    *   `messageType`: "SMS", "LMS", "MMS"
    *   `reqType`: "direct" (Immediate), "reserve" (Scheduled)
    *   `reqDate`: "2025-12-31 23:59:59" (Required if `reqType` is "reserve")

**Option B: With MMS Files (Multipart)**
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/multi/send/mms`
*   **Header**: `Content-Type: multipart/form-data`
*   **Body (form-data)**:
    *   `messageType`: "MMS"
    *   `callback`: "02-1234-5678"
    *   `subject`: "Image Message"
    *   `text`: "Check this image."
    *   `receivers`: `[{"phone":"010-1111-2222", "repChar01":"User"}]` (JSON String)
    *   `mmsFiles`: (File upload - Select 1~3 images)

### 2.3 Get Send History
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/message/send/history?startDate=2025-01-01T00:00:00&endDate=2025-01-31T23:59:59&page=1&size=20`

---

## 3. Event & Survey Management (`/api/event`, `/api/survey`)

### Create Event (Survey)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/event`
*   **Body**:
    ```json
    {
        "eventName": "Customer Survey 2025",
        "eventEmphasisYn": "Y",
        "eventDesc": "Description here",
        "eventType": "SURVEY",
        "startDate": "20250101",
        "endDate": "20251231",
        "status": "P",
        "privacyPolicyYn": "Y",
        "privacyPolicyTtl": "Privacy Policy",
        "privacyPolicyDesc": "Details...",
        "auth": "NONE",
        "qrCode": "Y",
        "questions": [
            {
                "questionType": "CHOICE",
                "questionTitle": "Satisfaction Level",
                "order": 1,
                "items": [
                    { "itemTitle": "High", "order": 1 },
                    { "itemTitle": "Low", "order": 2 }
                ]
            }
        ]
    }
    ```

### Submit Survey Response
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/survey/{eventSeq}/submit`
*   **Body**:
    ```json
    {
        "userKey": "USER_UNIQUE_KEY_FROM_DB",
        "userName": "Jane Doe",
        "userPhone": "010-9876-5432",
        "answers": [
            {
                "questionSeq": 1,
                "questionType": "CHOICE",
                "itemSeq": 101,
                "answer": "High"
            }
        ]
    }
    ```

---

## 4. Survey User Management (`/api/survey/users`)

### Add Participant (Single)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/survey/users`
*   **Body**:
    ```json
    {
        "eventSeq": 1,
        "userName": "Tester01",
        "userPhone": "010-1234-1234",
        "userEmail": "test@test.com",
        "address": "Seoul",
        "address2": "Gangnam"
    }
    ```

### Add Participants (Batch)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/survey/users/batch?eventSeq=1`
*   **Body**:
    ```json
    [
        {
            "eventSeq": 1,
            "userName": "User A",
            "userPhone": "010-1111-1111"
        },
        {
            "eventSeq": 1,
            "userName": "User B",
            "userPhone": "010-2222-2222"
        }
    ]
    ```

### Get Participants List
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/survey/users?eventSeq=1`

### Update Payment Info
*   **Method**: `PATCH`
*   **URL**: `{{baseUrl}}/api/survey/users/{userSeq}/payment-info?depositDate=2025-01-15`

---

## 5. Survey Answers & Statistics (`/api/survey/answers`)

### Get Answer Statistics (Question Level)
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/survey/answers/statistics/question?eventSeq=1&questionSeq=1`

### Get All Answers by Event
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/survey/answers?eventSeq=1`

---

## 6. Message Templates (`/api/message/template`)

### Create Template
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/message/template`
*   **Body**:
    ```json
    {
        "sendingForm": "I",
        "msgType": "L",
        "subject": "Promo",
        "text": "Hello, check this out.",
        "imagePath": "/uploads/template/sample.jpg"
    }
    ```

---

## 7. Admin & Logs (`/api/admin`)

### Create Admin Account
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/admin/account`
*   **Body**:
    ```json
    {
        "userId": "subadmin",
        "userPass": "Pass123!",
        "userPassChk": "Pass123!",
        "corpName": "Sub Company",
        "person": "Manager Lee",
        "phone": "010-5555-5555",
        "userLevel": 50,
        "email": "lee@sub.com"
    }
    ```

### Search Action Logs
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/admin/logs?startDate=2025-01-01&endDate=2025-01-31&actionType=LOGIN`

---

## 8. Payment & Balance (`/api/payment`)

### Charge Balance
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/payment/charge`
*   **Body**:
    ```json
    {
        "userId": "targetUser",
        "amount": 100000,
        "comment": "Wire Transfer",
        "tradeId": "TR_20250101_001"
    }
    ```

### Get Balance History
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/payment/balance/history?page=1&size=10`

---

## 9. Customer Company (`/api/company`)

### Search Companies
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/company/list`
*   **Body**:
    ```json
    {
        "type": "custCompNameOpt",
        "keyword": "Samsung",
        "pageNum": 1,
        "amount": 20
    }
    ```

### Toggle Selection
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/company/toggle`
*   **Body**:
    ```json
    {
        "userId": "user1",
        "custCompName": "Partner A",
        "chkedYn": "Y"
    }
    ```

---

## 10. ARS & Opt-out (`/ars`, `/api/history`)

### ARS Auto Reject (Simulated)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/ars/auto-reject`
*   **Headers**: No Auth required (or specific system auth)
*   **Body**:
    ```json
    {
        "tId": "T123456",
        "tTime": "20250101120000",
        "menuName": "0801234567",
        "ani": "01012345678"
    }
    ```

### Get Opt-out List (Legacy)
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/history/optout?page=1&size=10`

---

## 11. Blocked Numbers Management (`/api/blocked-numbers`)

| Method | Endpoint   | Description                  |
| :----- | :--------- | :--------------------------- |
| `GET`  | `/`        | 수신거부 목록 조회 (페이징) |
| `GET`  | `/all`     | 수신거부 목록 전체 조회      |
| `GET`  | `/count`   | 수신거부 건수 조회           |
| `POST` | `/`        | 수신거부 등록 (단건)         |
| `POST` | `/batch`   | 수신거부 등록 (일괄)         |
| `DELETE`| `/`       | 수신거부 삭제 (단건)         |
| `DELETE`| `/batch`  | 수신거부 삭제 (일괄)         |
| `GET`  | `/check`   | 수신거부 여부 확인           |
| `POST` | `/filter`  | 차단된 번호 필터링           |
| `POST` | `/available`| 발송 가능 번호 조회          |

### Get Blocked Numbers
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/blocked-numbers?storeCode=STORE001&page=1&size=20`

### Register Blocked Number (Single)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/blocked-numbers`
*   **Body**:
    ```json
    {
        "phoneNumber": "010-1234-5678",
        "storeCode": "STORE001",
        "menuName": "080-1234-5678"
    }
    ```

### Register Blocked Numbers (Batch)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/blocked-numbers/batch`
*   **Body**:
    ```json
    {
        "phoneNumbers": ["010-1111-2222", "010-3333-4444"],
        "storeCode": "STORE001",
        "menuName": "080-1234-5678"
    }
    ```

### Delete Blocked Number (Single)
*   **Method**: `DELETE`
*   **URL**: `{{baseUrl}}/api/blocked-numbers?phoneNumber=010-1234-5678&storeCode=STORE001`

### Delete Blocked Numbers (Batch - Plain)
*   **Method**: `DELETE`
*   **URL**: `{{baseUrl}}/api/blocked-numbers/batch-plain`
*   **Body**:
    ```json
    {
        "phoneNumbers": ["010-1111-2222", "010-3333-4444"],
        "storeCode": "STORE001"
    }
    ```

### Filter/Check Available Numbers
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/blocked-numbers/available`
*   **Body**:
    ```json
    {
        "phoneNumbers": ["010-1111-2222", "010-3333-4444"],
        "storeCode": "STORE001"
    }
    ```
    *   **Response**: Returns list of numbers *NOT* blocked.

---

## 12. Privacy Consent (`/api/privacy-consent`)

### Preview Consent Form
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/privacy-consent/preview`
*   **Body**:
    ```json
    {
        "title": "Privacy Agreement",
        "content": "<h1>Agreement</h1><p>We collect your data...</p>"
    }
    ```

### Download PDF
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/privacy-consent/download/user/{userSeq}/event/{eventSeq}`

---

## 13. File Upload (`/api/file`)

### Upload MMS Image
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/file/mms`
*   **Headers**: Remove `Content-Type` (let Postman set `multipart/form-data`)
*   **Body**:
    *   Key: `file` (Type: File) -> Select image file

### Upload Template Image
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/file/template`
*   **Body**:
    *   Key: `file` (Type: File) -> Select image file

---

## 14. Inquiry (`/api/inquiry`)

### Submit Inquiry
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/inquiry`
*   **Body**:
    ```json
    {
        "companyName": "Tech Corp",
        "applicantName": "Developer",
        "email": "dev@tech.com",
        "contact": "010-0000-0000",
        "inquiryType": 1,
        "content": "API usage question."
    }
    ```

---

## 15. Statistics (`/api/statistics`, `/api/billing/statistics`)

### 15.1 General Statistics

#### Get Daily Stats
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/statistics/daily?startDate=2025-01-01&endDate=2025-01-07&serviceType=SMS`

#### Get Usage Summary
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/statistics/usage-summary?startDate=2025-01-01&endDate=2025-01-31`

#### Get Period Daily Stats (Loop/Detailed)
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/statistics/period/daily?startDate=2025-01-01&endDate=2025-01-07`

#### Get User Stats by Service Type (M/S/Q)
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/statistics/user-stats?startDate=2025-01-01&endDate=2025-01-31&serviceType=M`
    *   `serviceType`: M (Message), S (Survey), Q (QR)

### 15.2 Billing Statistics

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/billing/statistics/daily` | 일별 과금 통계 조회 |
| `GET` | `/api/billing/statistics/monthly` | 월별 과금 통계 조회 |
| `GET` | `/api/billing/statistics/service-type` | 서비스 타입별 과금 통계 조회 |
| `POST` | `/api/billing/statistics/users` | 사용자별 과금 통계 조회 |
| `GET` | `/api/billing/statistics/summary` | 과금 총계 조회 |
| `GET` | `/api/billing/statistics/current-month` | 현재 월 과금 요약 (대시보드) |
| `GET` | `/api/billing/statistics/previous-month` | 전월 과금 요약 |
| `GET` | `/api/billing/statistics/my` | 내 과금 요약 |
| `GET` | `/api/billing/statistics/trend/daily` | 일별 과금 추이 (최근 30일) |
| `GET` | `/api/billing/statistics/trend/monthly` | 월별 과금 추이 (최근 12개월) |

#### Get Daily Billing Stats
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/billing/statistics/daily?startDate=2025-01-01&endDate=2025-01-31`

#### Get Monthly Billing Stats
*   **Method**: `GET`
*   **URL**: `{{baseUrl}}/api/billing/statistics/monthly?startDate=2025-01-01&endDate=2025-12-31`

#### Get User Billing Stats (POST)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/billing/statistics/users`
*   **Body**:
    ```json
    {
        "startDate": "2025-01-01",
        "endDate": "2025-01-31",
        "userIds": ["user1", "user2"]
    }
    ```