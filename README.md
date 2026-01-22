# WiseAd 백엔드 (Backend)

## 📝 프로젝트 개요
WiseAd는 SMS/LMS/MMS 메시지 발송, 설문 조사 관리, 이벤트 참여 추적을 통합적으로 관리하는 종합 광고 및 메시징 플랫폼입니다. 결제를 위한 정교한 지갑 시스템(Wallet), KCP/KG모빌리언스를 통한 본인 인증 및 결제 연동, 그리고 상세한 통계 리포트 기능을 포함하고 있습니다.

이 프로젝트는 **Java 21**과 **Spring Boot 3**를 기반으로 하며, **MyBatis**를 사용하여 데이터베이스를 제어합니다.

## 🛠 기술 스택

### 핵심 프레임워크
- **언어:** Java 21
- **프레임워크:** Spring Boot 3.4.12 (Web, Security, Validation, Actuator)
- **데이터베이스 접근:** MyBatis 3.0.4
- **보안:** Spring Security + JWT (jjwt 0.12.6)

### 데이터베이스
- **주 데이터베이스:** MySQL / MariaDB

### 주요 라이브러리 및 도구
- **빌드 도구:** Maven
- **생산성:** Lombok
- **엑셀 처리:** Apache POI 5.3.0
- **PDF 생성:** Apache PDFBox 3.0.4
- **HTML 파싱:** Jsoup 1.18.1 (사업자 등록번호 검증 등)
- **QR 코드:** ZXing 3.5.3
- **모니터링:** Micrometer (Prometheus)

### 외부 연동
- **KCP:** 본인 인증 (CtCli-1.0.7.jar)
- **KG 모빌리언스:** 결제 및 인증 (kgMobilians.jar)

## ✨ 주요 기능

### 1. 인증 및 보안 (Authentication & Security)
- JWT 기반 인증 (로그인, 회원가입, 토큰 갱신).
- KCP 및 KG모빌리언스 연동을 통한 실명 및 본인 인증.
- 관리자(Admin)와 일반 사용자(User) 권한 관리.

### 2. 메시징 시스템 (Messaging System)
- SMS, LMS, MMS 발송 기능.
- 예약 발송 및 결과 확인.
- 메시지 템플릿 관리.
- 발송 이력 및 상태 추적.
- 수신 거부(Opt-out) 및 차단 번호 관리.

### 3. 이벤트 및 설문 관리 (Event & Survey Management)
- 마케팅 이벤트 생성 및 관리.
- QR 코드를 이용한 설문 조사 시스템.
- 참여자 추적 (체크인, 미참여자 확인).
- 실시간 설문 응답 통계.

### 4. 지갑 및 결제 시스템 (Wallet & Payment)
- 다중 자산 지갑: **CASH** (실결제 금액), **POINT** (적립금), **BONUS** (프로모션).
- 충전, 차감 및 잔액 조회.
- 상세 거래 내역 및 환불 처리.

### 5. 관리자 기능 (Administration)
- 사용자 계정 관리 및 상태 모니터링.
- 시스템 활동 로그 (전화번호 마스킹 해제 로그 포함).
- 1:1 문의 및 고객 지원 시스템.
- 일별/월별 사용량 통계 대시보드.

## 🚀 시작하기

### 사전 준비 사항
- JDK 21 설치
- Maven 설치
- MySQL 또는 MariaDB 실행 중

### 설치 및 설정 방법

1. **저장소 클론 (Clone)**
   ```bash
   git clone <repository-url>
   cd BE_WiseAd
   ```

2. **데이터베이스 설정**
   - 데이터베이스 생성 (예: `wisead`).
   - `sql/wisead.sql` 및 관련 스크립트를 실행하여 스키마를 생성합니다.
   - `src/main/resources/application-local.properties` 파일에서 DB 정보를 수정합니다:
     ```properties
     spring.datasource.url=jdbc:mysql://localhost:3306/wisead
     spring.datasource.username=사용자이름
     spring.datasource.password=비밀번호
     ```

3. **의존성 설치**
   *참고: 이 프로젝트는 `libs/` 폴더에 있는 로컬 JAR 파일(CtCli, kgMobilians)을 사용합니다. `pom.xml` 설정에 의해 빌드 시 자동으로 로컬 메이븐 저장소에 설치됩니다.*
   ```bash
   mvn clean install
   ```

4. **애플리케이션 실행**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

## 📂 프로젝트 구조

```
BE_WiseAd/
├── libs/                  # 외부 로컬 JAR 파일 (KCP, KG모빌리언스)
├── sql/                   # 데이터베이스 DDL/DML 스크립트
├── src/
│   ├── main/
│   │   ├── java/kr/wisead/   # 소스 코드
│   │   │   ├── controller/   # API 엔드포인트
│   │   │   ├── service/      # 비즈니스 로직
│   │   │   ├── mapper/       # MyBatis 인터페이스
│   │   │   ├── dto/          # 데이터 전송 객체
│   │   │   ├── config/       # 스프링 설정
│   │   │   └── ...
│   │   └── resources/
│   │       ├── mapper/       # MyBatis XML 매퍼 파일
│   │       ├── application*.properties # 환경별 설정 파일
│   │       └── ...
│   └── test/              # 단위 및 통합 테스트
├── pom.xml                # Maven 빌드 설정
└── API_ENDPOINTS.md       # 상세 API 문서
```

## 📚 API 문서
사용 가능한 모든 API 엔드포인트와 요청 파라미터에 대한 정보는 다음 문서를 참조하세요:
[**API_ENDPOINTS.md**](./API_ENDPOINTS.md)

## 🔧 설정 프로파일 (Profiles)
- **local:** 로컬 개발 환경.
- **dev:** 개발 서버 환경.
- **prod:** 운영 서버 환경.

## 📝 로그 (Logging)
로그 설정은 `src/main/resources/logback-spring.xml`에서 관리하며, 로그 파일은 프로젝트 루트의 `logs/` 폴더에 생성됩니다.