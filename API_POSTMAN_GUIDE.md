# Postman API Testing Guide

This document provides the details needed to test the WiseAd API using Postman.

## Global Configuration
*   **Base URL**: `http://localhost:8100` (twisead-api.epopkon.com 예정)
*   **Headers**:
    *   `Content-Type`: `application/json`
    *   `Authorization`: `Bearer {{accessToken}}` (Add this to the collection's Authorization tab or individual requests after login)

---

## 1. Authentication (`/api/auth`)

### Login
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/auth/login`
*   **Body**:
    ```json
    {
        "userId": "admin",
        "userPass": "password123!"
    }
    ```

### Sign Up
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
        "email": "john@example.com",
        "hintQuestion": "Q01",
        "hintAnswer": "Answer"
    }
    ```

### Refresh Token
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/auth/refresh`
*   **Body**:
    ```json
    {
        "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
    }
    ```

---

## 2. Message Sending (`/api/message/send`)

### Send SMS/LMS/MMS
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
        "subject": "Message Subject",
        "text": "This is a test message.",
        "sendType": "1",
        "requestTime": null, 
        "delDuplicateNum": true
    }
    ```
    *   `msgType`: "S" (SMS), "L" (LMS), "M" (MMS)
    *   `requestTime`: Format `2025-12-25T10:00:00` for scheduled sending

---

## 3. Event & Survey (`/api/event`, `/api/survey`)

### Create Event (Survey)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/event`
*   **Body**:
    ```json
    {
        "eventName": "Customer Satisfaction Survey",
        "eventEmphasisYn": "Y",
        "eventDesc": "Please fill out this survey.",
        "eventType": "SURVEY",
        "startDate": "20250101",
        "endDate": "20251231",
        "status": "P",
        "privacyPolicyYn": "Y",
        "privacyPolicyTtl": "Privacy Policy",
        "privacyPolicyDesc": "We collect...",
        "auth": "NONE",
        "qrCode": "Y",
        "questions": [
            {
                "questionType": "CHOICE",
                "questionTitle": "How satisfied are you?",
                "order": 1,
                "items": [
                    { "itemTitle": "Very Satisfied", "order": 1 },
                    { "itemTitle": "Satisfied", "order": 2 }
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
        "userKey": "USER_UNIQUE_KEY",
        "userName": "Jane Doe",
        "userPhone": "010-9876-5432",
        "answers": [
            {
                "questionSeq": 1,
                "questionType": "CHOICE",
                "itemSeq": 101,
                "answer": "Very Satisfied"
            },
            {
                "questionSeq": 2,
                "questionType": "TEXT",
                "answer": "Great service!"
            }
        ]
    }
    ```

### Add Auth Key (General Auth)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/event/{eventSeq}/auth-keys`
*   **Body**:
    ```json
    {
        "authCode": "AUTH1234",
        "authKeyDesc": "VIP Customer Key"
    }
    ```

---

## 4. Message Templates (`/api/message/template`)

### Create Template
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/message/template`
*   **Body**:
    ```json
    {
        "sendingForm": "I",
        "msgType": "L",
        "subject": "Promotion Template",
        "text": "Hello, this is a promotion message.",
        "imagePath": "/uploads/template/img01.jpg"
    }
    ```

---

## 5. Scheduled Messages (`/api/scheduled-messages`)

### Reschedule Message
*   **Method**: `PUT`
*   **URL**: `{{baseUrl}}/api/scheduled-messages/{mSeq}/reschedule`
*   **Body**:
    ```json
    {
        "newScheduleTime": "2025-12-30T15:00:00"
    }
    ```

---

## 6. Admin (`/api/admin`)

### Create Admin Account
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/admin/account`
*   **Headers**: `Authorization: Bearer {{adminToken}}`
*   **Body**:
    ```json
    {
        "userId": "manager1",
        "userPass": "Manager123!",
        "userPassChk": "Manager123!",
        "corpName": "Sub Corp",
        "person": "Manager Kim",
        "phone": "010-5555-6666",
        "userLevel": 50,
        "email": "manager@example.com"
    }
    ```

### Log Phone Masking
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/admin/logs/phone-masking`
*   **Body**:
    ```json
    {
        "action": "UNMASK",
        "reason": "Customer verification",
        "pageNumber": "1"
    }
    ```

---

## 7. Payment (`/api/payment`)

### Charge Balance (Admin)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/payment/charge`
*   **Body**:
    ```json
    {
        "userId": "user1",
        "amount": 50000,
        "comment": "Bonus charge",
        "tradeId": "TID_123456789"
    }
    ```

---

## 8. Inquiry (`/api/inquiry`)

### Submit Inquiry
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/inquiry`
*   **Body**:
    ```json
    {
        "companyName": "My Company",
        "applicantName": "Tester",
        "email": "tester@example.com",
        "contact": "010-1111-2222",
        "inquiryType": 1,
        "content": "I have a question about the API."
    }
    ```

### Answer Inquiry (Admin)
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/inquiry/{inquiryId}/answer`
*   **Body**:
    ```json
    {
        "answer": "Here is the answer to your question.",
        "sendEmail": true
    }
    ```

---

## 9. Company Management (`/api/company`)

### Toggle Company Selection
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/company/toggle`
*   **Body**:
    ```json
    {
        "userId": "user1",
        "custCompName": "Target Company",
        "chkedYn": "Y"
    }
    ```

---

## 10. Front Auth (`/api/front/auth`)

### Validate Phone
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/front/auth/validate/phone`
*   **Body**:
    ```json
    {
        "phone": "010-1234-5678",
        "eventCode": "EVENT_001"
    }
    ```

### KCP Auth Result
*   **Method**: `POST`
*   **URL**: `{{baseUrl}}/api/front/auth/kcp/result`
*   **Body**:
    ```json
    {
        "siteCd": "S1234",
        "ordrIdxx": "ORDER_001",
        "certNo": "CERT_123",
        "encCertData2": "ENCRYPTED_DATA...",
        "eventCode": "EVENT_001"
    }
    ```
