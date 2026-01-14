# WiseAd API Endpoints

This document lists all the API endpoints found in the project. You can use this to configure your Postman collection.

## Authentication (AuthController)

Base URL: `/api/auth`

| Method | Endpoint        | Description                                     |
| :----- | :-------------- | :---------------------------------------------- |
| `POST` | `/login`        | Login                                           |
| `POST` | `/signup`       | Sign up                                         |
| `POST` | `/refresh`      | Refresh JWT token                               |
| `GET`  | `/check-userid` | Check if User ID exists (Query param: `userId`) |
| `GET`  | `/check-email`  | Check if Email exists (Query param: `email`)    |

## User Management (UserController)

Base URL: `/api/users`

| Method | Endpoint           | Description                           |
| :----- | :----------------- | :------------------------------------ |
| `GET`  | `/me`              | Get current user info                 |
| `GET`  | `/{seq}`           | Get user by Sequence ID (Admin only)  |
| `GET`  | `/`                | Get list of users (Admin only)        |
| `PUT`  | `/me/password`     | Change password                       |
| `PUT`  | `/{userId}/status` | Update user status (Admin only)       |
| `POST` | `/find-id`         | Find User ID by email and person name |

## Admin (AdminController)

Base URL: `/api/admin`

| Method | Endpoint              | Description                   |
| :----- | :-------------------- | :---------------------------- |
| `POST` | `/account`            | Create admin account          |
| `GET`  | `/logs`               | Get action logs               |
| `GET`  | `/logs/{seq}`         | Get specific action log       |
| `GET`  | `/logs/download`      | Download action logs as Excel |
| `POST` | `/logs/phone-masking` | Log phone masking action      |
| `POST` | `/logs/download`      | Log download action           |
| `GET`  | `/level`              | Get user admin level          |

## Event/Survey Management (EventController)

Base URL: `/api/event`

| Method   | Endpoint                          | Description                       |
| :------- | :-------------------------------- | :-------------------------------- |
| `GET`    | `/`                               | Get list of events                |
| `GET`    | `/{eventSeq}`                     | Get event details                 |
| `POST`   | `/`                               | Create new event                  |
| `PUT`    | `/{eventSeq}`                     | Update event                      |
| `PATCH`  | `/{eventSeq}/status`              | Update event status               |
| `GET`    | `/{eventSeq}/statistics`          | Get survey statistics             |
| `GET`    | `/search/names`                   | Search event names (Autocomplete) |
| `GET`    | `/{eventSeq}/auth-keys`           | Get list of auth keys             |
| `POST`   | `/{eventSeq}/auth-keys`           | Add auth key                      |
| `DELETE` | `/{eventSeq}/auth-keys/{userKey}` | Delete auth key                   |

## Survey Participation (SurveyController)

Base URL: `/api/survey`

| Method | Endpoint                   | Description                    |
| :----- | :------------------------- | :----------------------------- |
| `GET`  | `/code/{eventCode}`        | Get survey by event code       |
| `GET`  | `/qr/{authCodeUrl}`        | Get survey by QR auth code URL |
| `GET`  | `/key/{userKey}`           | Get survey by user key         |
| `POST` | `/auth/general`            | Check general authentication   |
| `POST` | `/{eventSeq}/submit`       | Submit survey response         |
| `GET`  | `/{eventSeq}/participants` | Get list of participants       |
| `GET`  | `/{eventSeq}/absentees`    | Get list of absentees/lurkers  |

## Front Authentication (FrontAuthController)

Base URL: `/api/front/auth`

| Method | Endpoint          | Description                  |
| :----- | :---------------- | :--------------------------- |
| `POST` | `/qr/user`        | Create user from QR access   |
| `POST` | `/validate/phone` | Validate phone number        |
| `GET`  | `/check/phone`    | Check if phone number exists |
| `GET`  | `/keypad`         | Get virtual keypad data      |
| `POST` | `/keypad/decrypt` | Decrypt keypad input         |
| `GET`  | `/kcp/init`       | Initialize KCP auth          |
| `POST` | `/kcp/result`     | Process KCP auth result      |
| `GET`  | `/kcp/user`       | Find user by KCP auth result |

## Message Sending (MessageSendController)

Base URL: `/api/message/send`

| Method   | Endpoint                  | Description                        |
| :------- | :------------------------ | :--------------------------------- |
| `POST`   | `/`                       | Send SMS/LMS/MMS                   |
| `GET`    | `/history`                | Get send history                   |
| `GET`    | `/pending`                | Get pending scheduled messages     |
| `DELETE` | `/cancel/{mseq}`          | Cancel scheduled message           |
| `DELETE` | `/cancel/batch/{userKey}` | Cancel batch of scheduled messages |

## Message Templates (MessageTemplateController)

Base URL: `/api/message/template`

| Method   | Endpoint         | Description             |
| :------- | :--------------- | :---------------------- |
| `POST`   | `/`              | Create message template |
| `GET`    | `/`              | Get list of templates   |
| `GET`    | `/{templateSeq}` | Get template details    |
| `PUT`    | `/{templateSeq}` | Update template         |
| `DELETE` | `/{templateSeq}` | Delete template         |
| `PUT`    | `/reorder`       | Reorder templates       |

## Scheduled Messages (ScheduledMessageController)

Base URL: `/api/scheduled-messages`

| Method   | Endpoint             | Description                       |
| :------- | :------------------- | :-------------------------------- |
| `GET`    | `/`                  | Get list of scheduled messages    |
| `GET`    | `/{mSeq}`            | Get scheduled message details     |
| `PUT`    | `/{mSeq}/reschedule` | Reschedule message                |
| `DELETE` | `/`                  | Cancel scheduled messages (Batch) |

## Send History (SendHistoryController)

Base URL: `/api/history`

| Method   | Endpoint           | Description                   |
| :------- | :----------------- | :---------------------------- |
| `GET`    | `/send`            | Get send history              |
| `POST`   | `/send/download`   | Download send history (Excel) |
| `GET`    | `/optout`          | Get opt-out list              |
| `DELETE` | `/optout`          | Delete opt-out number         |
| `GET`    | `/optout/download` | Download opt-out list (Excel) |

## ARS & Opt-out (ArsController)

Base URL: `/` (Root) and `/api`

| Method   | Endpoint                      | Description                       |
| :------- | :---------------------------- | :-------------------------------- |
| `POST`   | `/ars/auto-reject`            | ARS Auto Reject (from ARS system) |
| `POST`   | `/ars/code-reject`            | ARS Code Reject (from ARS system) |
| `GET`    | `/api/blocked-senders`        | Get blocked senders list          |
| `DELETE` | `/api/blocked-senders`        | Delete blocked senders            |
| `GET`    | `/api/blocked-senders/check`  | Check if number is blocked        |
| `POST`   | `/api/blocked-senders/filter` | Filter blocked numbers            |

## Payment & Balance (PaymentController)

Base URL: `/api/payment`

| Method | Endpoint              | Description                               |
| :----- | :-------------------- | :---------------------------------------- |
| `GET`  | `/balance`            | Get current balance (legacy)              |
| `GET`  | `/balance/history`    | Get balance history (legacy)              |
| `GET`  | `/wallet/summary`     | Get wallet summary (CASH + POINT + BONUS) |
| `GET`  | `/wallet/lots`        | Get active lots (Point/Bonus with expiry) |
| `GET`  | `/transactions`       | Get transaction history (new)             |
| `POST` | `/charge`             | Charge balance (Admin)                    |
| `POST` | `/deduct`             | Deduct balance (Admin)                    |
| `GET`  | `/balance/check`      | Check if balance is sufficient            |
| `PUT`  | `/sms-price`          | Update SMS/LMS/MMS price (Admin)          |
| `GET`  | `/refund/preview`     | Preview refund (Query: txGroupId)         |
| `POST` | `/refund/{txGroupId}` | Process refund                            |
| `POST` | `/callback`           | Payment gateway callback                  |
| `GET`  | `/history`            | Get payment history                       |
| `GET`  | `/{tradeId}`          | Get payment by trade ID                   |

## Customer Company (CustomerCompanyController)

Base URL: `/api/company`

| Method | Endpoint             | Description                     |
| :----- | :------------------- | :------------------------------ |
| `POST` | `/list`              | Get company list                |
| `GET`  | `/selected/{userId}` | Get selected companies for user |
| `POST` | `/toggle`            | Toggle company selection        |
| `POST` | `/enroll`            | Enroll companies (Batch)        |
| `PUT`  | `/name`              | Update company name             |
| `GET`  | `/person/{userId}`   | Get contact person name         |
| `GET`  | `/corp/{userId}`     | Get corporation name            |

## Inquiry (InquiryController)

Base URL: `/api/inquiry`

| Method   | Endpoint              | Description                   |
| :------- | :-------------------- | :---------------------------- |
| `POST`   | `/`                   | Submit inquiry                |
| `GET`    | `/list`               | Get inquiry list (Admin)      |
| `GET`    | `/{inquiryId}`        | Get inquiry details           |
| `POST`   | `/{inquiryId}/answer` | Answer inquiry (Admin)        |
| `PUT`    | `/{inquiryId}/status` | Update inquiry status (Admin) |
| `DELETE` | `/{inquiryId}`        | Delete inquiry (Admin)        |
| `GET`    | `/pending/count`      | Get pending inquiry count     |

## Email (EmailController)

Base URL: `/api/email`

| Method | Endpoint        | Description                |
| :----- | :-------------- | :------------------------- |
| `POST` | `/verification` | Send verification email    |
| `POST` | `/send`         | Send general email (Admin) |

## Statistics (StatisticsController)

Base URL: `/api/statistics`

| Method | Endpoint         | Description                 |
| :----- | :--------------- | :-------------------------- |
| `GET`  | `/daily`         | Get daily statistics        |
| `GET`  | `/user`          | Get user statistics (Admin) |
| `GET`  | `/usage-summary` | Get usage summary           |
| `GET`  | `/monthly`       | Get monthly statistics      |
| `GET`  | `/period`        | Get period statistics       |

## File Upload (FileUploadController)

Base URL: `/api/file`

| Method   | Endpoint           | Description                     |
| :------- | :----------------- | :------------------------------ |
| `POST`   | `/mms`             | Upload MMS image                |
| `POST`   | `/bizreg`          | Upload business registration    |
| `POST`   | `/survey/question` | Upload survey question image    |
| `POST`   | `/survey/item`     | Upload survey item image        |
| `POST`   | `/survey/desc`     | Upload survey description image |
| `POST`   | `/survey/end`      | Upload survey end image         |
| `POST`   | `/template`        | Upload template image           |
| `DELETE` | `/template`        | Delete template image           |

## File Download (FileDownloadController)

Base URL: `/files`

| Method | Endpoint                        | Description                    |
| :----- | :------------------------------ | :----------------------------- |
| `GET`  | `/mmsfile/{folder}/{fileName}`  | Download MMS file              |
| `GET`  | `/survey/{folder}/{fileName}`   | Download survey image          |
| `GET`  | `/template/{folder}/{fileName}` | Download template image        |
| `GET`  | `/bizreg/{fileName}`            | Download business registration |
| `GET`  | `/qrcode/{fileName}`            | Download QR code               |

## Excel (ExcelController)

Base URL: `/api/excel`

| Method | Endpoint               | Description                    |
| :----- | :--------------------- | :----------------------------- |
| `GET`  | `/statistics/download` | Download statistics Excel      |
| `POST` | `/upload`              | Upload Excel file (Parse data) |
| `GET`  | `/template/{type}`     | Download Excel template        |

## Privacy Consent (PrivacyConsentController)

Base URL: `/api/privacy-consent`

| Method | Endpoint                                    | Description                      |
| :----- | :------------------------------------------ | :------------------------------- |
| `GET`  | `/download/user/{userSeq}/event/{eventSeq}` | Download single user consent PDF |
| `GET`  | `/download/event/{eventSeq}`                | Download event consent PDF (Zip) |
| `POST` | `/preview`                                  | Preview consent PDF              |

## Public/Health (HealthController)

Base URL: `/api/public`

| Method | Endpoint   | Description     |
| :----- | :--------- | :-------------- |
| `GET`  | `/health`  | Health check    |
| `GET`  | `/version` | Get API version |
