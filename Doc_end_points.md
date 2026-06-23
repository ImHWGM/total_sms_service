# WiseAd API 엔드포인트 목록

> 최종 업데이트: 2025-12-17
> 총 엔드포인트 수: **255개**

---

## 목차

1. [Public API](#1-public-api)
2. [Authentication (인증)](#2-authentication-인증)
3. [User (회원)](#3-user-회원)
4. [Admin (관리자)](#4-admin-관리자)
5. [Message - Send (문자 발송)](#5-message---send-문자-발송)
6. [Message - Template (템플릿)](#6-message---template-템플릿)
7. [Message - Multi (일반 문자)](#7-message---multi-일반-문자)
8. [Message - Ad (광고 문자)](#8-message---ad-광고-문자)
9. [Payment (결제/잔액)](#9-payment-결제잔액)
10. [Billing Statistics (과금 통계)](#10-billing-statistics-과금-통계)
11. [Event (이벤트/설문 관리)](#11-event-이벤트설문-관리)
12. [Survey (설문 조회/제출)](#12-survey-설문-조회제출)
13. [Survey Users (설문 참여자)](#13-survey-users-설문-참여자)
14. [Survey Answers (설문 응답)](#14-survey-answers-설문-응답)
15. [Front Auth (프론트 인증)](#15-front-auth-프론트-인증)
16. [Company (고객사)](#16-company-고객사)
17. [Statistics (통계)](#17-statistics-통계)
18. [History (발송 이력)](#18-history-발송-이력)
19. [Schedule (예약 메시지)](#19-schedule-예약-메시지)
20. [ARS (수신거부)](#20-ars-수신거부)
21. [Blocked Numbers (차단 번호)](#21-blocked-numbers-차단-번호)
22. [Inquiry (문의)](#22-inquiry-문의)
23. [Email (이메일)](#23-email-이메일)
24. [Email Unsubscribe (이메일 수신거부)](#24-email-unsubscribe-이메일-수신거부)
25. [File Upload (파일 업로드)](#25-file-upload-파일-업로드)
26. [File Download (파일 다운로드)](#26-file-download-파일-다운로드)
27. [Excel (엑셀)](#27-excel-엑셀)
28. [Privacy Consent (개인정보동의서)](#28-privacy-consent-개인정보동의서)

---

## 1. Public API

**파일**: `HealthController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/public/health` | healthCheck | 서버 상태 확인 | X | None |
| GET | `/api/public/version` | version | API 버전 정보 조회 | X | None |

---

## 2. Authentication (인증)

**파일**: `AuthController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/auth/login` | login | 로그인 | X | JSON: `{ "userId": "...", "userPass": "..." }` |
| POST | `/api/auth/signup` | signUp | 회원가입 | X | JSON: `{ "userId": "...", "userPass": "...", "userPassConfirm": "...", "corpName": "...", "corpAddr": "...", "bizNum": "...", "bizTel": "...", "person": "...", "phone": "...", "email": "...", "hintQuestion": "...", "hintAnswer": "..." }` |
| POST | `/api/auth/refresh` | refreshToken | 토큰 갱신 | X | JSON: `{ "refreshToken": "..." }` |
| GET | `/api/auth/check-userid` | checkUserId | 아이디 중복 확인 | X | Query: `userId` |
| GET | `/api/auth/check-email` | checkEmail | 이메일 중복 확인 | X | Query: `email` |
| POST | `/api/auth/validate-bizno` | validateBizNo | 사업자등록번호 유효성 검증 | X | JSON: `{ "bizNum": "..." }` |

---

## 3. User (회원)

**파일**: `UserController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/users/me` | getMyInfo | 내 정보 조회 | O | None |
| GET | `/api/users/{seq}` | getUserBySeq | 회원 정보 조회 | 관리자 | Path: `seq` |
| GET | `/api/users` | getUsers | 회원 목록 조회 | 관리자 | Query: `page`, `size` |
| PUT | `/api/users/me/password` | changePassword | 비밀번호 변경 | O | JSON: `{ "currentPassword": "...", "newPassword": "..." }` |
| PUT | `/api/users/{userId}/status` | updateStatus | 회원 상태 변경 | 관리자 | Path: `userId`, JSON: `{ "status": "승인/미승인/보류/탈퇴" }` |
| POST | `/api/users/find-id` | findUserId | 아이디 찾기 (레거시) | X | JSON: `{ "email": "...", "person": "..." }` |
| POST | `/api/users/find-id/request` | requestFindId | 아이디 찾기 1단계 (인증코드 발송) | X | JSON: `{ "corpName": "...", "person": "...", "phone": "..." }` |
| POST | `/api/users/find-id/verify` | verifyAndGetUserId | 아이디 찾기 2단계 (인증 후 아이디 반환) | X | JSON: `{ "email": "...", "code": "..." }` |
| POST | `/api/users/find-pw` | findPassword | 비밀번호 찾기 (힌트 검증 → 이메일 링크 발송) | X | JSON: `{ "userId": "...", "corpName": "...", "person": "...", "phone": "...", "hintQuestion": "...", "hintAnswer": "..." }` |
| GET | `/api/users/password/reset-validate` | validateResetToken | 비밀번호 재설정 토큰 검증 | X | Query: `token` |
| POST | `/api/users/password/reset-confirm` | resetPasswordWithToken | 비밀번호 재설정 확인 (새 비밀번호 설정) | X | JSON: `{ "token": "...", "newPassword": "..." }` |
| PUT | `/api/users/{userId}/password/reset` | resetPassword | 비밀번호 초기화 | 관리자 | Path: `userId`, JSON: `{ "newPassword": "..." }` |
| PUT | `/api/users/{userId}/unlock` | unlockAccount | 계정 잠금 해제 | 관리자 | Path: `userId` |
| PUT | `/api/users/me/password/extend` | extendPasswordExpiry | 비밀번호 만료일 연장 | O | None |
| PUT | `/api/users/me/password/expired` | changeExpiredPassword | 만료된 비밀번호 변경 | O | JSON: `{ "newPassword": "..." }` |
| DELETE | `/api/users/{seq}` | deleteUser | 회원 삭제 (단건) | 관리자 | Path: `seq` |
| DELETE | `/api/users` | deleteUsers | 회원 삭제 (일괄) | 관리자 | JSON: `{ "seqList": [1, 2, 3] }` |
| PUT | `/api/users/{seq}/info` | updateMemberInfo | 회원 정보 수정 | 관리자 | Path: `seq`, JSON: `{ "corpName": "...", "corpAddr": "...", "bizNum": "...", "bizTel": "...", "person": "...", "phone": "...", "email": "...", "userLevel": 10, "allowIpYn": "Y/N", "allowIp": "...", "status": "...", "callback": "..." }` |

---

## 4. Admin (관리자)

**파일**: `AdminController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/admin/account` | createAdminAccount | 관리자 계정 생성 | 관리자 | JSON: `{ "userId": "...", "userPass": "...", "userPassChk": "...", "corpName": "...", "corpAddr": "...", "bizNum": "...", "bizTel": "...", "person": "...", "phone": "...", "email": "...", "userLevel": 10/50/60/90/99, "hintQuestion": "...", "hintAnswer": "..." }` |
| GET | `/api/admin/logs` | getActionLogs | 액션 로그 목록 조회 | 관리자 | Query: `startDate`, `endDate`, `searchField`, `searchKeyword`, `actionType`, `page`, `size` |
| GET | `/api/admin/logs/{seq}` | getActionLog | 액션 로그 상세 조회 | 관리자 | Path: `seq` |
| GET | `/api/admin/logs/download` | downloadActionLogsExcel | 액션 로그 엑셀 다운로드 | 관리자 | Query: `startDate`, `endDate`, `searchField`, `searchKeyword`, `actionType` |
| POST | `/api/admin/logs/phone-masking` | logPhoneMaskingAction | 전화번호 마스킹 해제 로그 | 관리자 | JSON: `{ "action": "VIEW/EXCEL", "reason": "...", "pageNumber": 1 }` |
| POST | `/api/admin/logs/download` | logDownloadAction | 다운로드 액션 로그 | 관리자 | Query: `menuName`, `reason` |
| GET | `/api/admin/level` | getUserLevel | 권한 레벨 확인 | O | None |

---

## 5. Message - Send (문자 발송)

**파일**: `MessageSendController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params                                                                                                                                                                                                                                                                                         |
|--------|-----|---------|------|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| POST | `/api/message/send` | send | 문자 발송 (SMS/LMS/MMS) | O | JSON: `{ "msgType": "S/L/M", "callback": "...", "receivers": ["010-0000-0000", ...], "subject": "...", "text": "...", "sendType": "1(직접)/2(대량)", "requestTime": "yyyy-MM-ddTHH:mm:ss", "delDuplicateNum": true/false, "eventSeq": 0, "fileCnt": 0, "fileloc1": "...", "fileloc2": "...", "fileloc3": "..." }` |
| GET | `/api/message/send/history` | getHistory | 발송 이력 조회 | O | Query: `startDate`, `endDate`, `type` (A:메시지타입, B:수신번호, C:발신번호, D:결과, E:제목, F:내용, G:상태, H:등록자), `keyword`, `sendFailure` (1:실패만), `page`, `size`                                                                                                                                                              |
| GET | `/api/message/send/pending` | getPendingMessages | 예약 발송 대기 목록 조회 | O | Query: `page` (기본값: 1), `size` (기본값: 10)                                                                                                                                                                                                                                                                      |
| DELETE | `/api/message/send/cancel/{mseq}` | cancelMessage | 예약 발송 취소 (단건) | O | Path: `mseq`                                                                                                                                                                                                                                                                                                  |
| DELETE | `/api/message/send/cancel/batch/{userKey}` | cancelBatch | 예약 발송 취소 (배치) | O | Path: `userKey` (이벤트 등의 배치 식별자)                                                                                                                                                                                                                                                                               |
| POST | `/api/message/send/resend` | resendSurveyMessage | 설문 문자 재발송 (단건) | O | JSON: `{ "userSeq": 1, "subject": "...", "text": "...", "callback": "...", "useOriginalContent": true/false }`                                                                                                                                                                                                |
| POST | `/api/message/send/resend/batch` | resendSurveyMessageBatch | 설문 문자 재발송 (다건) | O | JSON: `{ "userSeqList": [1, 2, ...], "subject": "...", "text": "...", "callback": "...", "useOriginalContent": true/false }`                                                                                                                                                                                  |
| POST | `/api/message/send/resend/duplicate` | resendToDuplicates | 중복 번호 재발송 | O | JSON: `{ "eventSeq": 1, "duplicateReceivers": [{"phone": "...", "userSeq": 1, ...}, ...] }`                                                                                                                                                                                                                   |
| POST | `/api/message/send/duplicate/new` | sendNewToDuplicates | 중복 번호에 새 내용 발송 | O | JSON: `{ "eventSeq": 1, "duplicateReceivers": [...], "subject": "...", "text": "..." }`                                                                                                                                                                                                                       |

---

## 6. Message - Template (템플릿)

**파일**: `MessageTemplateController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/message/template` | create | 템플릿 생성 | O | JSON: `{ "sendingForm": "I/R", "msgType": "SMS/LMS/MMS", "subject": "...", "text": "...", "imagePath": "..." }` |
| POST | `/api/message/template/with-image` | createWithImage | MMS 템플릿 생성 (이미지) | O | Multipart: `msgType`, `subject`, `text`, `sendingForm`, `image` (File) |
| GET | `/api/message/template` | getList | 템플릿 목록 조회 | O | Query: `type` (s:설문, d:직접) |
| GET | `/api/message/template/survey` | getSurveyList | 설문용 템플릿 목록 조회 | O | None |
| GET | `/api/message/template/direct` | getDirectList | 직접발송용 템플릿 목록 조회 | O | None |
| GET | `/api/message/template/{templateSeq}` | getOne | 템플릿 상세 조회 | O | Path: `templateSeq` |
| PUT | `/api/message/template/{templateSeq}` | update | 템플릿 수정 | O | Path: `templateSeq`, JSON: `{ "sendingForm": "...", "msgType": "...", "subject": "...", "text": "...", "imagePath": "..." }` |
| DELETE | `/api/message/template/{templateSeq}` | delete | 템플릿 삭제 | O | Path: `templateSeq` |
| PUT | `/api/message/template/reorder` | reorder | 템플릿 순서 변경 | O | JSON: `[1, 3, 2, 5, ...]` (List of templateSeq) |
| PUT | `/api/message/template/survey/reorder` | reorderSurvey | 설문용 템플릿 순서 변경 | O | JSON: `[1, 3, 2, ...]` |
| PUT | `/api/message/template/direct/reorder` | reorderDirect | 직접발송용 템플릿 순서 변경 | O | JSON: `[1, 3, 2, ...]` |

---

## 7. Message - Multi (일반 문자)

**파일**: `MultiMessageController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/multi/send` | sendMessages | 일반 문자 발송 (JSON) | O | JSON: `{ "messageType": "SMS/LMS/MMS", "callback": "...", "subject": "...", "text": "...", "reqType": "direct/reserve", "reqDate": "...", "delDuplicateNum": "Y/N", "receivers": [{"phone": "...", "repChar01": "...", ...}], "fileLoc1": "...", "fileCnt": 0, "forceValidation": false }` |
| POST | `/api/multi/send/mms` | sendMmsMessages | 일반 문자 발송 (MMS) | O | Multipart: `messageType`, `callback`, `subject`, `text`, `reqType`, `reqDate`, `delDuplicateNum`, `receivers` (JSON String), `mmsFiles` (List<File>), `forceValidation` |

---

## 8. Message - Ad (광고 문자)

**파일**: `AdMessageController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/ad/msg/send` | sendAdMessage | 광고 문자 발송 (Multipart) | O | Multipart: `request` (JSON Part: `{ "reqType": "ip-direct/excel", "messageTypeIs": "SMS/LMS/MMS", "reqNum": "...", "sendTtl": "...", "sendTimeType": "direct/schedule", "reqDate": "...", "delDuplicateNum": "Y/N", "contTxt": "...", "recipients": [...] }`), `mmsFiles` (List<File>) |
| POST | `/api/ad/msg/send/json` | sendAdMessageJson | 광고 문자 발송 (JSON) | O | JSON: `{ "reqType": "ip-direct/excel", "messageTypeIs": "SMS/LMS/MMS", "reqNum": "...", "sendTtl": "...", "sendTimeType": "direct/schedule", "reqDate": "...", "delDuplicateNum": "Y/N", "contTxt": "...", "recipients": [{"recPhone": "...", "contTxt": "...", "repChar01": "..."}] }` |
| GET | `/api/ad/msg/check-night-time` | checkNightTimeRestriction | 야간 전송제한 시간 체크 | O | None |

---

## 9. Payment (결제/잔액)

**파일**: `PaymentController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/payment/balance` | getCurrentBalance | 현재 잔액 조회 | O | None |
| GET | `/api/payment/balance/history` | getBalanceHistory | 잔액 내역 조회 | O | Query: `page`, `size` |
| POST | `/api/payment/charge` | charge | 충전 | 관리자 | JSON: `{ "userId": "...", "amount": 10000, "comment": "...", "tradeId": "..." }` |
| POST | `/api/payment/deduct` | deduct | 차감 | 관리자 | Query: `userId`, `amount`, `comment` |
| GET | `/api/payment/balance/check` | checkBalance | 잔액 충분 여부 확인 | O | Query: `amount` |
| POST | `/api/payment/callback` | paymentCallback | 결제 결과 콜백 (PG) | X | Form Data (PG Dependent) |
| GET | `/api/payment/history` | getPaymentHistory | 결제 내역 조회 | O | Query: `page`, `size` |
| GET | `/api/payment/{tradeId}` | getPaymentByTradeId | 거래 ID로 결제 조회 | O | Path: `tradeId` |
| POST | `/api/payment/kg/noti` | kgPaymentNoti | KG 결제 알림 콜백 | X | Form Data (PG Dependent) |
| POST | `/api/payment/kg/result` | kgPaymentResult | KG 결제 결과 조회 | O | Query: `paymentResult` (Map) |

---

## 10. Billing Statistics (과금 통계)

**파일**: `BillingStatisticsController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/billing/statistics/daily` | getDailyStats | 일별 과금 통계 | O | Query: `userId`, `startDate` (yyyy-MM-dd), `endDate` (yyyy-MM-dd) |
| GET | `/api/billing/statistics/monthly` | getMonthlyStats | 월별 과금 통계 | O | Query: `userId`, `startDate`, `endDate` |
| GET | `/api/billing/statistics/service-type` | getStatsByServiceType | 서비스 타입별 통계 | O | Query: `userId`, `startDate`, `endDate` |
| POST | `/api/billing/statistics/users` | getStatsByUser | 사용자별 과금 통계 | 관리자 | JSON: `{ "userIds": ["...", ...], "startDate": "...", "endDate": "..." }` |
| GET | `/api/billing/statistics/summary` | getSummary | 과금 총계 조회 | O | Query: `userId`, `startDate`, `endDate` |
| GET | `/api/billing/statistics/current-month` | getCurrentMonthSummary | 현재 월 과금 요약 | O | Query: `userId` (Optional) |
| GET | `/api/billing/statistics/previous-month` | getPreviousMonthSummary | 이전 월 과금 요약 | O | Query: `userId` (Optional) |
| GET | `/api/billing/statistics/user/{userId}` | getUserSummary | 특정 사용자 과금 요약 | 관리자 | Path: `userId`, Query: `startDate`, `endDate` |
| GET | `/api/billing/statistics/my` | getMySummary | 내 과금 요약 | O | Query: `startDate`, `endDate` |
| GET | `/api/billing/statistics/trend/daily` | getDailyTrend | 일별 추이 (최근 N일) | O | Query: `userId`, `days` (Default: 30) |
| GET | `/api/billing/statistics/trend/monthly` | getMonthlyTrend | 월별 추이 (최근 N개월) | O | Query: `userId`, `months` (Default: 12) |
| GET | `/api/billing/statistics/recent` | getRecentTransactions | 최근 거래 내역 | O | Query: `operation` (P/M/R), `limit` (Default: 20) |

---

## 11. Event (이벤트/설문 관리)

**파일**: `EventController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/event` | getList | 이벤트 목록 조회 | O | Query: `eventType` (S:설문/P:수집), `surveyStatus`, `startDate`, `endDate`, `searchKeyword`, `sortField`, `sortOrder`, `page`, `size` |
| GET | `/api/event/{eventSeq}` | getDetail | 이벤트 상세 조회 | O | Path: `eventSeq` |
| POST | `/api/event` | create | 이벤트 생성 | O | JSON: `{ "eventName": "...", "eventType": "S/P", "startDate": "...", "endDate": "...", "auth": "None/General/Phone/Keypad/KCP", "questions": [...], ... }` |
| PUT | `/api/event/{eventSeq}` | update | 이벤트 수정 | O | Path: `eventSeq`, JSON: `{ ... }` (Same as Create) |
| PATCH | `/api/event/{eventSeq}/status` | updateStatus | 이벤트 상태 변경 | O | Path: `eventSeq`, Query: `status` (A:준비/P:진행/S:중지/F:종료) |
| GET | `/api/event/{eventSeq}/statistics` | getStatistics | 설문 통계 조회 | O | Path: `eventSeq` |
| GET | `/api/event/search/names` | searchEventNames | 이벤트명 검색 (자동완성) | O | Query: `keyword` |
| GET | `/api/event/{eventSeq}/auth-keys` | getAuthKeyList | 범용인증키 목록 조회 | O | Path: `eventSeq`, Query: `page`, `size` |
| POST | `/api/event/{eventSeq}/auth-keys` | addAuthKey | 범용인증키 추가 | O | Path: `eventSeq`, JSON: `{ "authCode": "...", "authKeyDesc": "..." }` |
| DELETE | `/api/event/{eventSeq}/auth-keys/{userKey}` | deleteAuthKey | 범용인증키 삭제 | O | Path: `eventSeq`, `userKey` |
| GET | `/api/event/{eventSeq}/auth-key-desc` | getAuthKeyDesc | 범용인증키 설명문구 조회 | O | Path: `eventSeq` |
| PUT | `/api/event/{eventSeq}/auth-key-desc` | updateAuthKeyDesc | 범용인증키 설명문구 수정 | O | Path: `eventSeq`, JSON: `{ "authKeyDesc": "..." }` |
| POST | `/api/event/{eventSeq}/user-keys` | generateUserKeys | 유저키 생성 | O | Path: `eventSeq`, Query: `count` |
| POST | `/api/event/auth-keys/excel` | uploadAuthKeyExcel | 범용인증코드 엑셀 업로드 | O | Multipart: `file` |
| GET | `/api/event/{eventSeq}/excel` | downloadEventResultExcel | 이벤트 결과 엑셀 다운로드 | O | Path: `eventSeq` |

---

## 12. Survey (설문 조회/제출)

**파일**: `SurveyController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/survey/code/{eventCode}` | getSurveyByEventCode | 이벤트 코드로 설문 조회 | X | Path: `eventCode` |
| GET | `/api/survey/qr/{authCodeUrl}` | getSurveyByAuthCodeUrl | QR코드 URL로 설문 조회 | X | Path: `authCodeUrl` |
| GET | `/api/survey/key/{userKey}` | getSurveyByUserKey | 사용자 키로 설문 조회 | X | Path: `userKey` |
| POST | `/api/survey/auth/general` | checkGeneralAuth | 범용인증 확인 | X | Query: `authCodeUrl`, `authCode` |
| POST | `/api/survey/{eventSeq}/submit` | submitSurvey | 설문 제출 | X | Path: `eventSeq`, JSON: `{ "userKey": "...", "userName": "...", "userPhone": "...", "answers": [{"questionSeq": 1, "answer": "...", "itemSeq": 1}, ...] }` |
| GET | `/api/survey/{eventSeq}/participants` | getParticipants | 참여자 목록 조회 | O | Path: `eventSeq` |
| GET | `/api/survey/{eventSeq}/absentees` | getAbsenteesAndLurkers | 미참여/접속자 목록 | O | Path: `eventSeq` |

---

## 13. Survey Users (설문 참여자)

**파일**: `SurveyUserController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/survey/users` | getUsersByEventSeq | 이벤트별 참여자 목록 | O | Query: `eventSeq`, `page`, `size` |
| GET | `/api/survey/users/completed` | getCompletedUsers | 설문 완료자 목록 | O | Query: `eventSeq` |
| GET | `/api/survey/users/absentees` | getAbsenteesAndLurkers | 미참여/접속자 목록 | O | Query: `eventSeq` |
| GET | `/api/survey/users/{userSeq}` | getUserBySeq | 참여자 상세 조회 (시퀀스) | O | Path: `userSeq` |
| GET | `/api/survey/users/key/{userKey}` | getUserByUserKey | 참여자 상세 조회 (키) | O | Path: `userKey` |
| GET | `/api/survey/users/count` | getParticipantCounts | 참여자 수 통계 | O | Query: `eventSeq` |
| POST | `/api/survey/users` | createUser | 참여자 등록 | O | JSON: `{ "eventSeq": 1, "userName": "...", "userPhone": "..." }` |
| POST | `/api/survey/users/batch` | createUsersBatch | 참여자 일괄 등록 | O | JSON: `{ "eventSeq": 1, "users": [{"userName": "...", "userPhone": "..."}, ...] }` |
| PUT | `/api/survey/users/{userSeq}` | updateUser | 참여자 정보 수정 | O | Path: `userSeq`, JSON: `{ "userName": "...", "userPhone": "...", ... }` |
| PATCH | `/api/survey/users/{userSeq}/resend-phone` | updateResendPhone | 재발송 전화번호 수정 | O | Path: `userSeq`, Query: `phone` |
| PATCH | `/api/survey/users/{userSeq}/payment-info` | updatePaymentInfo | 입금/출고 정보 수정 | O | Path: `userSeq`, JSON: `{ "depositDate": "...", "depositAmount": 1000, "shipmentDate": "..." }` |
| DELETE | `/api/survey/users/{userSeq}` | deleteUser | 참여자 삭제 | O | Path: `userSeq` |
| DELETE | `/api/survey/users/event/{eventSeq}` | deleteUsersByEventSeq | 이벤트 참여자 전체 삭제 | O | Path: `eventSeq` |
| GET | `/api/survey/users/auth/general/status` | checkGeneralAuthStatus | 범용인증 상태 확인 | O | Query: `eventSeq`, `authCode` |
| GET | `/api/survey/users/auth/general` | getUserByGeneralAuthCode | 범용인증으로 사용자 조회 | O | Query: `eventSeq`, `authCode` |
| GET | `/api/survey/users/check-phone` | existsByEventCodeAndPhone | 전화번호 중복 확인 | O | Query: `eventCode`, `phone` |
| GET | `/api/survey/users/validate-key` | validateUserKey | 사용자 키 유효성 검증 | O | Query: `userKey` |
| POST | `/api/survey/users/start-time` | recordStartTime | 설문 접속 시간 기록 | X | Query: `userKey` |
| POST | `/api/survey/users/auth-time` | recordAuthTime | 설문 인증 시간 기록 | X | Query: `userKey` |

---

## 14. Survey Answers (설문 응답)

**파일**: `SurveyAnswerController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/survey/answers/count` | getAnswerCount | 이벤트별 답변 수 조회 | O | Query: `eventSeq` |
| GET | `/api/survey/answers` | getAnswersByEvent | 이벤트별 전체 답변 목록 | O | Query: `eventSeq` |
| GET | `/api/survey/answers/user` | getAnswersByUser | 사용자별 답변 조회 | O | Query: `userSeq` |
| GET | `/api/survey/answers/question` | getAnswersByQuestion | 문항별 답변 조회 | O | Query: `questionSeq` |
| GET | `/api/survey/answers/statistics/question` | getQuestionStatistics | 문항별 응답 통계 | O | Query: `questionSeq` |
| GET | `/api/survey/answers/statistics` | getEventStatistics | 이벤트 전체 문항 통계 | O | Query: `eventSeq` |

---

## 15. Front Auth (프론트 인증)

**파일**: `FrontAuthController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/front/auth/qr/user` | createQrUser | QR 코드 접근 시 사용자 생성 | X | Query: `authCodeUrl` |
| POST | `/api/front/auth/validate/phone` | validatePhone | 휴대폰 번호로 사용자 검증 | X | JSON: `{ "eventCode": "...", "phone": "..." }` |
| GET | `/api/front/auth/check/phone` | checkPhoneExists | 휴대폰 번호 존재 확인 | X | Query: `eventCode`, `phone` |
| GET | `/api/front/auth/keypad` | getKeypadData | 가상 키패드 데이터 생성 | X | None |
| POST | `/api/front/auth/keypad/decrypt` | decryptKeypadInput | 키패드 입력값 복호화 (테스트) | X | Query: `keypadId`, `encryptedData` |
| GET | `/api/front/auth/kcp/init` | initKcpAuth | KCP 인증 시작 데이터 생성 | X | Query: `eventCode`, `userKey` |
| POST | `/api/front/auth/kcp/result` | processKcpAuthResult | KCP 인증 결과 처리 | X | JSON: `{ "site_cd": "...", "res_cd": "...", "res_msg": "...", "enc_info": "...", "ordr_idxx": "..." }` |
| GET | `/api/front/auth/kcp/user` | findUserByKcpAuth | KCP 인증으로 사용자 조회 | X | Query: `eventCode`, `phoneNo` |

---

## 16. Company (고객사)

**파일**: `CustomerCompanyController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/company/list` | getCompanyList | 고객사 목록 조회 (페이징) | O | JSON: `{ "type": "seqOpt/custCompNameOpt/...", "keyword": "...", "pageNum": 1, "amount": 20 }` |
| GET | `/api/company/selected/{userId}` | getSelectedCompanies | 선택된 고객사 목록 | O | Path: `userId` |
| POST | `/api/company/toggle` | toggleCompanySelection | 고객사 선택/해제 | O | JSON: `{ "userId": "...", "custCompName": "..." }` |
| POST | `/api/company/enroll` | enrollCompanies | 고객사 일괄 등록 | O | JSON: `{ "userId": "...", "custCompNames": ["...", ...] }` |
| PUT | `/api/company/name` | updateCompanyName | 고객사명 변경 | O | Query: `oldName`, `newName` |
| GET | `/api/company/person/{userId}` | getPersonByUserId | 담당자명 조회 | O | Path: `userId` |
| GET | `/api/company/corp/{userId}` | getCorpNameByUserId | 회사명 조회 | O | Path: `userId` |
| PUT | `/api/company/{seq}` | updateCompany | 고객사 정보 수정 | O | Path: `seq`, JSON: `{ "custCompName": "...", "custCompBizCode": "..." }` |
| GET | `/api/company/{seq}` | getCompanyBySeq | 고객사 상세 조회 | O | Path: `seq` |

---

## 17. Statistics (통계)

**파일**: `StatisticsController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/statistics/daily` | getDailyStats | 일별 통계 조회 | O | Query: `startDate`, `endDate`, `serviceType` |
| GET | `/api/statistics/user` | getUserStats | 사용자별 통계 (관리자) | 관리자 | Query: `startDate`, `endDate`, `userIds` (List) |
| GET | `/api/statistics/usage-summary` | getUsageSummary | 사용량 요약 (청구용) | O | Query: `startDate`, `endDate` |
| GET | `/api/statistics/monthly` | getMonthlyStats | 월별 통계 조회 | O | Query: `startDate`, `endDate`, `serviceType` |
| GET | `/api/statistics/period` | getPeriodStats | 기간별 통계 (관리자) | 관리자 | Query: `startDate`, `endDate`, `serviceType` |
| GET | `/api/statistics/period/daily` | getPeriodDailyStats | 기간별 일별 통계 | O | Query: `startDate`, `endDate`, `msgType`, `serviceType` |
| GET | `/api/statistics/period/daily/admin` | getPeriodDailyStatsAdmin | 기간별 일별 통계 (관리자) | 관리자 | Query: `startDate`, `endDate`, `userId`, `userIds`, `msgType`, `serviceType` |
| GET | `/api/statistics/period/day/{date}` | getDayStats | 특정 일자 상세 통계 | O | Path: `date`, Query: `msgType`, `serviceType` |
| GET | `/api/statistics/period/total` | getPeriodTotalStats | 기간 합계 통계 | O | Query: `startDate`, `endDate`, `msgType`, `serviceType` |
| GET | `/api/statistics/period/total/admin` | getPeriodTotalStatsAdmin | 기간 합계 통계 (관리자) | 관리자 | Query: `startDate`, `endDate`, `userId`, `userIds`, `msgType`, `serviceType` |
| GET | `/api/statistics/user-stats` | getUserStatsByServiceType | 사용자별 서비스 통계 | O | Query: `startDate`, `endDate`, `serviceType` (M/S/Q), `userIds` |
| GET | `/api/statistics/user-stats/msg` | getUserMsgStats | 사용자별 메시지 통계 | O | Query: `startDate`, `endDate`, `userId`, `userIds` |
| GET | `/api/statistics/user-stats/survey` | getUserSurveyStats | 사용자별 설문 통계 | O | Query: `startDate`, `endDate`, `userId`, `userIds` |
| GET | `/api/statistics/user-stats/qr` | getUserQrStats | 사용자별 QR 통계 | O | Query: `startDate`, `endDate`, `userId`, `userIds` |

---

## 18. History (발송 이력)

**파일**: `SendHistoryController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/history/send` | getSendHistory | 발송 이력 목록 조회 | O | Query: `startDate`, `endDate`, `type`, `keyword`, `sendResult` (success/failure), `page`, `size` |
| POST | `/api/history/send/download` | downloadSendHistory | 발송 이력 엑셀 다운로드 | O | Query: `startDate`, `endDate`, `type`, `keyword`, `sendResult` (success/failure), `reason` |
| GET | `/api/history/optout` | getOptOutList | 수신거부 목록 조회 | O | Query: `page`, `size` |
| DELETE | `/api/history/optout` | deleteOptOut | 수신거부 삭제 | O | JSON: `[{ "key": "value" }, ...]` |
| GET | `/api/history/optout/download` | downloadOptOut | 수신거부 엑셀 다운로드 | O | None |

---

## 19. Schedule (예약 메시지)

**파일**: `ScheduledMessageController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/scheduled-messages` | getScheduledMessages | 예약 메시지 목록 조회 | O | Query: `msgType`, `searchText`, `page`, `size` |
| GET | `/api/scheduled-messages/{mSeq}` | getScheduledMessageById | 예약 메시지 상세 조회 | O | Path: `mSeq` |
| PUT | `/api/scheduled-messages/{mSeq}/reschedule` | rescheduleMessage | 예약 시간 변경 | O | Path: `mSeq`, JSON: `{ "newScheduleTime": "yyyy-MM-ddTHH:mm:ss" }` |
| DELETE | `/api/scheduled-messages` | cancelScheduledMessages | 예약 메시지 삭제 (일괄) | O | JSON: `[1, 2, 3, ...]` (List of mSeqs) |

---

## 20. ARS (수신거부)

**파일**: `ArsController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/ars/auto-reject` | autoReject | 발신번호 자동등록형 수신거부 | X | Form: `tId`, `tTime`, `menuName`, `ani` |
| POST | `/ars/code-reject` | codeReject | 상점코드 입력형 수신거부 | X | Form: `tId`, `tTime`, `menuName`, `ani`, `dtmf1` |
| GET | `/api/blocked-senders` | getBlockedSenders | 수신거부 목록 조회 | O | Query: `storeCode`, `page`, `size` |
| DELETE | `/api/blocked-senders` | deleteBlockedSenders | 수신거부 삭제 | O | JSON: `[{"phoneNumber": "...", "storeCode": "..."}, ...]` |
| GET | `/api/blocked-senders/check` | checkBlocked | 수신거부 여부 확인 | O | Query: `ani`, `storeCode` |
| POST | `/api/blocked-senders/filter` | filterBlockedNumbers | 수신거부 번호 필터링 | O | Query: `storeCode`, Body: `["010...", ...]` |

---

## 21. Blocked Numbers (차단 번호)

**파일**: `BlockedNumberController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/blocked-numbers` | getBlockedNumbers | 수신거부 목록 (페이징) | O | Query: `storeCode`, `page`, `size` |
| GET | `/api/blocked-numbers/all` | getAllBlockedNumbers | 수신거부 목록 (전체) | O | Query: `storeCode` |
| GET | `/api/blocked-numbers/count` | countBlockedNumbers | 수신거부 건수 조회 | O | Query: `storeCode` |
| POST | `/api/blocked-numbers` | registerBlockedNumber | 수신거부 등록 (단건) | O | JSON: `{ "phoneNumber": "...", "storeCode": "...", "menuName": "..." }` |
| POST | `/api/blocked-numbers/batch` | registerBlockedNumbers | 수신거부 등록 (일괄) | O | JSON: `{ "phoneNumbers": ["...", ...], "storeCode": "...", "menuName": "..." }` |
| DELETE | `/api/blocked-numbers` | deleteBlockedNumber | 수신거부 삭제 (단건) | O | Query: `phoneNumber`, `storeCode` |
| DELETE | `/api/blocked-numbers/batch` | deleteBlockedNumbers | 수신거부 삭제 (암호화키) | O | JSON: `[{ "key": "value" }, ...]` |
| DELETE | `/api/blocked-numbers/batch-plain` | deleteBlockedNumbersByPhone | 수신거부 삭제 (평문) | O | JSON: `{ "phoneNumbers": ["...", ...], "storeCode": "..." }` |
| GET | `/api/blocked-numbers/check` | checkBlocked | 수신거부 여부 확인 | O | Query: `phoneNumber`, `storeCode` |
| POST | `/api/blocked-numbers/filter` | filterBlockedNumbers | 수신거부 번호 필터링 | O | JSON: `{ "storeCode": "...", "phoneNumbers": ["...", ...] }` |
| POST | `/api/blocked-numbers/available` | getAvailableNumbers | 발송 가능 번호 조회 | O | JSON: `{ "storeCode": "...", "phoneNumbers": ["...", ...] }` |
| GET | `/api/blocked-numbers/validate-store` | validateStoreCode | 상점코드 유효성 검사 | O | Query: `storeCode` |

---

## 22. Inquiry (문의)

**파일**: `InquiryController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/inquiry` | submitInquiry | 문의 등록 | X | JSON: `{ "companyName": "...", "applicantName": "...", "email": "...", "contact": "...", "inquiryType": 1, "content": "..." }` |
| GET | `/api/inquiry/list` | getInquiryList | 문의 목록 조회 | 관리자 | Query: `status`, `keyword`, `page`, `size` |
| GET | `/api/inquiry/{inquiryId}` | getInquiry | 문의 상세 조회 | 관리자 | Path: `inquiryId` |
| POST | `/api/inquiry/{inquiryId}/answer` | answerInquiry | 답변 등록 | 관리자 | Path: `inquiryId`, JSON: `{ "answer": "...", "sendEmail": true/false }` |
| PUT | `/api/inquiry/{inquiryId}/status` | updateStatus | 문의 상태 변경 | 관리자 | Path: `inquiryId`, Query: `status` |
| DELETE | `/api/inquiry/{inquiryId}` | deleteInquiry | 문의 삭제 | 관리자 | Path: `inquiryId` |
| GET | `/api/inquiry/pending/count` | getPendingCount | 대기중 문의 개수 | 관리자 | None |

---

## 23. Email (이메일)

**파일**: `EmailController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/email/verification` | sendVerificationCode | 인증 코드 발송 | X | JSON: `{ "email": "..." }` |
| POST | `/api/email/verification/verify` | verifyCode | 인증 코드 검증 | X | JSON: `{ "email": "...", "code": "..." }` |
| GET | `/api/email/verification/status` | getVerificationStatus | 인증 상태 조회 | X | Query: `email` |
| POST | `/api/email/verification/resend` | resendVerificationCode | 인증 코드 재발송 | X | JSON: `{ "email": "..." }` |
| POST | `/api/email/send` | sendEmail | 일반 이메일 발송 | 관리자 | JSON: `{ "to": "...", "cc": "...", "subject": "...", "content": "..." }` |

---

## 24. Email Unsubscribe (이메일 수신거부)

**파일**: `UnsubscribeController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/unsubscribe` | unsubscribeLegacy | 이메일 수신거부 (레거시) | X | Query: `email` |
| POST | `/api/unsubscribe` | unsubscribe | 이메일 수신거부 | X | JSON: `{ "email": "..." }` |
| GET | `/api/unsubscribe/check` | checkUnsubscribed | 수신거부 여부 확인 | X | Query: `email` |

---

## 25. File Upload (파일 업로드)

**파일**: `FileUploadController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| POST | `/api/file/mms` | uploadMmsFile | MMS 이미지 업로드 | O | Multipart: `file` |
| POST | `/api/file/bizreg` | uploadBizRegFile | 사업자등록증 업로드 | O | Multipart: `file` |
| POST | `/api/file/survey/question` | uploadSurveyQuestionImg | 설문 문항 이미지 (Base64) | O | Param: `eventSeq`, `questionSeq`, Body: Base64 String |
| POST | `/api/file/survey/item` | uploadSurveyItemImg | 설문 항목 이미지 (Base64) | O | Param: `eventSeq`, `questionSeq`, `order`, Body: Base64 String |
| POST | `/api/file/survey/desc` | uploadSurveyDescImg | 설문 설명 이미지 | O | Param: `eventSeq`, Multipart: `file` |
| POST | `/api/file/survey/end` | uploadSurveyEndImg | 설문 종료 이미지 | O | Param: `eventSeq`, Multipart: `file` |
| POST | `/api/file/template` | uploadTemplateImage | 템플릿 이미지 업로드 | O | Multipart: `file` |
| DELETE | `/api/file/template` | deleteTemplateImage | 템플릿 이미지 삭제 | O | Query: `path` |

---

## 26. File Download (파일 다운로드)

**파일**: `FileDownloadController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/files/mmsfile/{folder}/{fileName}` | serveMmsFile | MMS 파일 서빙 | X | Path params |
| GET | `/files/survey/{folder}/{fileName}` | serveSurveyFile | 설문 이미지 서빙 | X | Path params |
| GET | `/files/template/{folder}/{fileName}` | serveTemplateFile | 템플릿 이미지 서빙 | X | Path params |
| GET | `/files/bizreg/{fileName}` | serveBizRegFile | 사업자등록증 서빙 | X | Path params |
| GET | `/files/qrcode/{fileName}` | serveQrCodeFile | QR 코드 이미지 서빙 | X | Path params |
| GET | `/files/survey/qvey/qrcode/{fileName}` | serveQrCodeFileLegacy | QR 코드 서빙 (레거시) | X | Path params |

---

## 27. Excel (엑셀)

**파일**: `ExcelController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/excel/statistics/download` | downloadStatisticsExcel | 통계 Excel 다운로드 | O | Query: `startDate`, `endDate`, `serviceType` |
| POST | `/api/excel/upload` | uploadExcel | Excel 파일 업로드 | O | Multipart: `file`, Param: `startRow`, `columns` |
| GET | `/api/excel/template/{type}` | downloadTemplate | Excel 템플릿 다운로드 | O | Path: `type` (phone/survey) |
| GET | `/api/excel/survey/download` | downloadSurveyExcel | 설문조사 참여현황 Excel | O | Query: `eventSeq`, `keyword`, `searchType`, `submissionStatus`, `startDate`, `endDate`, `reason` |
| GET | `/api/excel/privacy/download` | downloadPrivacyExcel | 개인정보취합 참여현황 Excel | O | Query: `eventSeq`, `keyword`, `searchType`, `submissionStatus`, `startDate`, `endDate`, `reason` |

---

## 28. Privacy Consent (개인정보동의서)

**파일**: `PrivacyConsentController.java`

| Method | URL | 메서드명 | 설명 | 인증 | Request Body / Params |
|--------|-----|---------|------|------|-----------------------|
| GET | `/api/privacy-consent/download/user/{userSeq}/event/{eventSeq}` | downloadUserPrivacyConsent | 단건 동의서 PDF 다운로드 | O | Path params, Query: `includeSignature` |
| GET | `/api/privacy-consent/download/event/{eventSeq}` | downloadEventPrivacyConsentZip | 전체 PDF ZIP 다운로드 | O | Path params, Query: `includeSignature` |
| POST | `/api/privacy-consent/preview` | previewPrivacyConsent | 안내문 미리보기 PDF | O | JSON: `{ "title": "...", "content": "..." }` |

---

## Postman 테스트 가이드

### 계정 복구 API 테스트

#### 1. 아이디 찾기 플로우 (2단계)

**Step 1: 아이디 찾기 요청 (인증코드 발송)**

```http
POST {{base_url}}/api/users/find-id/request
Content-Type: application/json

{
    "corpName": "테스트기업",
    "person": "홍길동",
    "phone": "010-1234-5678"
}
```

> 💡 `person`과 `phone`은 평문으로 전송하며, 백엔드에서 암호화하여 DB와 비교합니다.

**응답 예시 (성공):**
```json
{
    "success": true,
    "code": 200,
    "message": "SUCCESS",
    "data": {
        "resultCode": 1,
        "message": "인증 코드가 발송되었습니다.",
        "maskedEmail": "te***@example.com",
        "maskedUserId": null
    }
}
```

**응답 예시 (실패 - 계정 없음):**
```json
{
    "success": true,
    "code": 200,
    "data": {
        "resultCode": 2,
        "message": "입력하신 정보와 일치하는 계정이 없습니다."
    }
}
```

---

**Step 2: 인증코드 검증 및 아이디 확인**

```http
POST {{base_url}}/api/users/find-id/verify
Content-Type: application/json

{
    "email": "test@example.com",
    "code": "ABC12345"
}
```

**응답 예시 (성공):**
```json
{
    "success": true,
    "code": 200,
    "data": {
        "resultCode": 1,
        "message": "아이디 조회가 완료되었습니다.",
        "maskedEmail": null,
        "maskedUserId": "tes*******"
    }
}
```

**응답 예시 (실패 - 인증코드 오류):**
```json
{
    "success": true,
    "code": 200,
    "data": {
        "resultCode": 3,
        "message": "인증 코드가 일치하지 않습니다."
    }
}
```

---

#### 2. 비밀번호 찾기 플로우 (3단계)

**Step 1: 비밀번호 찾기 요청 (재설정 링크 발송)**

```http
POST {{base_url}}/api/users/find-pw
Content-Type: application/json

{
    "userId": "testuser01",
    "corpName": "테스트기업",
    "person": "홍길동",
    "phone": "010-1234-5678",
    "hintQuestion": "첫 번째 애완동물 이름은?",
    "hintAnswer": "뽀삐"
}
```

> 💡 `person`과 `phone`은 평문으로 전송하며, 백엔드에서 암호화하여 DB와 비교합니다.

**응답 예시 (성공):**
```json
{
    "success": true,
    "code": 200,
    "data": {
        "resultCode": 1,
        "message": "비밀번호 재설정 링크가 이메일로 발송되었습니다.",
        "maskedEmail": "te***@example.com"
    }
}
```

**응답 예시 (실패 - 힌트 불일치):**
```json
{
    "success": true,
    "code": 200,
    "data": {
        "resultCode": 3,
        "message": "비밀번호 힌트가 일치하지 않습니다."
    }
}
```

---

**Step 2: 재설정 토큰 유효성 검증**

사용자가 이메일 링크 클릭 시 프론트엔드에서 호출:

```http
GET {{base_url}}/api/users/password/reset-validate?token=550e8400-e29b-41d4-a716-446655440000
```

**응답 예시 (유효):**
```json
{
    "success": true,
    "code": 200,
    "data": {
        "valid": true,
        "userId": "tes*******"
    }
}
```

**응답 예시 (만료):**
```json
{
    "success": false,
    "code": 400,
    "message": "만료된 토큰입니다."
}
```

---

**Step 3: 새 비밀번호 설정**

```http
POST {{base_url}}/api/users/password/reset-confirm
Content-Type: application/json

{
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "newPassword": "NewPass123!@"
}
```

**응답 예시 (성공):**
```json
{
    "success": true,
    "code": 200,
    "message": "비밀번호가 성공적으로 변경되었습니다."
}
```

**응답 예시 (실패 - 이미 사용된 토큰):**
```json
{
    "success": false,
    "code": 400,
    "message": "이미 사용된 토큰입니다."
}
```

---

#### Postman Environment Variables

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `base_url` | API 서버 주소 | `http://localhost:8100` |
| `encrypted_person` | AES256 암호화된 담당자명 | `Base64EncodedString` |
| `encrypted_phone` | AES256 암호화된 연락처 | `Base64EncodedString` |

#### 비밀번호 유효성 규칙

새 비밀번호는 다음 조건을 만족해야 합니다:
- 8~20자
- 영문 포함
- 숫자 포함
- 특수문자(`@$!%*#?&`) 포함

정규식: `^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,20}$`