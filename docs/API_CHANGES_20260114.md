# API 변경사항 안내 (2026-01-14)

## 1. 버그 수정

### `/api/company/enroll` - 고객사 일괄등록

**변경 내용:**

- `userId`가 null이거나 빈 값인 경우 400 에러 반환 (기존: 500 에러)
- `custCompNames` 리스트 내 null/빈 문자열 자동 필터링
- 중복 고객사명 자동 제거

**프론트엔드 영향:** 없음 (기존 요청 형식 그대로 사용)

---

### `/api/users/{seq}/info` - 계정 정보 수정

**변경 내용:**

- `person` 필드 암호화 처리 추가
- **null 필드는 기존 값 유지** (부분 업데이트 지원)

**프론트엔드 영향:**

- 이제 일부 필드만 전송해도 나머지 필드가 null로 덮어쓰이지 않음
- 기존 전체 필드 전송 방식도 그대로 동작

---

## 2. 신규 API

### `PUT /api/payment/sms-price` - 문자 요금 설정

**권한:** 관리자 전용 (`ADMIN`)

**Request Body:**

```typescript
interface SmsPriceRequest {
  userId: string; // 대상 사용자 ID (필수)
  smsPrice?: number; // SMS 단가 (선택, null이면 기존값 유지)
  lmsPrice?: number; // LMS 단가 (선택)
  mmsPrice?: number; // MMS 단가 (선택)
}
```

**Response:**

```typescript
interface BalanceResponse {
  seq: number;
  userId: string;
  balance: number;
  totalBalance: number;
  operation: string; // "U" (Update)
  comment: string; // "문자 요금 설정 변경"
  smsPrice: number;
  lmsPrice: number;
  mmsPrice: number;
  regDate: string;
}
```

**사용 예시:**

```typescript
await api.put("/api/payment/sms-price", {
  userId: "testuser",
  smsPrice: 12.1,
  lmsPrice: 36.3,
  mmsPrice: 121,
});
```

---

### `GET /api/payment/balance/history/{userId}` - 사용자별 잔액 이력 조회

**권한:** 관리자 전용 (`ADMIN`)

**Path Parameter:**

- `userId`: 조회할 사용자 ID

**Query Parameters:**

- `page`: 페이지 번호 (기본값: 1)
- `size`: 페이지 크기 (기본값: 10)

**Response:**

```typescript
interface PaginatedResponse<BalanceResponse> {
  content: BalanceResponse[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}
```

**사용 예시:**

```typescript
// 기업관리 상세 페이지에서 특정 기업의 요금 이력 조회
const response = await api.get(`/api/payment/balance/history/${userId}`, {
  params: { page: 1, size: 10 },
});
```

---

## 3. 프론트엔드 타입 추가 필요

`src/api/types.ts`에 다음 타입 추가:

```typescript
// 문자 요금 설정 요청
export interface SmsPriceRequest {
  userId: string;
  smsPrice?: number | null;
  lmsPrice?: number | null;
  mmsPrice?: number | null;
}
```

`src/api/payment.ts`에 다음 함수 추가:

```typescript
/**
 * Update SMS price - PUT /api/payment/sms-price
 * Admin only
 */
export const updateSmsPrice = async (
  data: SmsPriceRequest
): Promise<BalanceResponse> => {
  const response = await api.put<ApiResponse<BalanceResponse>>(
    "/api/payment/sms-price",
    data
  );
  return response.data.data;
};

/**
 * Get balance history by userId - GET /api/payment/balance/history/{userId}
 * Admin only
 */
export const getBalanceHistoryByUserId = async (
  userId: string,
  page?: number,
  size?: number
): Promise<PaginatedResponse<BalanceResponse>> => {
  const response = await api.get<
    ApiResponse<PaginatedResponse<BalanceResponse>>
  >(`/api/payment/balance/history/${userId}`, { params: { page, size } });
  return response.data.data;
};
```

---

## 문의

백엔드 관련 문의사항이 있으시면 연락주세요.
