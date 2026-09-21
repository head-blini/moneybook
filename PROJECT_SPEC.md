# MoneyBook --- Project Specification

Last updated: 2026-09-21

이 문서는 MoneyBook 가계부 앱 프로젝트의 현재 기준 사양(Source of Truth)이다.
새로운 기능이나 구조 변경을 논의할 때 실제 구현과 이 문서의 충돌 여부를 먼저
확인한다. 문서와 구현이 다르면 검증된 최신 구현 상태를 우선하며, 확정되지 않은
아이디어는 확정 사양과 구분한다.

---

## 1. 프로젝트 목표

MoneyBook은 두 명이 하나의 Household를 구성해 공동 자금과 개인 자금을 함께
관리하는 Android 가계부다.

* 일상적인 수입·지출을 쉽고 빠르게 기록한다.
* 가계의 자금 흐름을 직관적으로 파악한다.
* 공동 거래와 개인 거래를 분리하고, 개인 거래의 공개 범위를 보호한다.
* 우선순위는 다음과 같다.

  1. 사용 편의성
  2. 단순하고 안정적인 구조
  3. 유지보수성
  4. 실제 구현 가능성
  5. 이후 기능 확장이 가능한 데이터 구조

현재 MVP 사용자 흐름은 다음과 같다.

```text
로그인/가입 → Household 초기 설정 → 홈 → 거래 기록 → 거래 확인/관리
```

---

## 2. 현재 기술 스택

### Android

* Kotlin 2.2.21
* Jetpack Compose / Material 3
* Navigation Compose
* ViewModel
* Kotlin Coroutines
* Flow / StateFlow
* Hilt dependency injection
* Kotlin Serialization
* Gradle Kotlin DSL
* Android application/namespace: `com.moneybook`
* minSdk 26 / targetSdk 36 / compileSdk 36
* JVM 17

### Backend

* Supabase Auth
* Supabase PostgreSQL / PostgREST
* Row Level Security(RLS)
* PostgreSQL Function/RPC 및 trigger
* 버전 관리되는 Supabase migrations

Android에는 publishable key 또는 legacy anon key만 사용한다. Service Role Key,
데이터베이스 관리자 자격 증명 및 기타 서버 비밀은 포함하지 않는다.

---

## 3. 현재 앱 구조

현재 저장소는 단일 Android `app` 모듈이며 주요 패키지는 다음과 같다.

```text
com.moneybook
├── app
│   ├── navigation
│   └── di
├── core
│   ├── result
│   └── ui
├── data
│   ├── remote.supabase
│   └── repository
├── domain
│   ├── model
│   └── repository
└── feature
    ├── auth
    ├── onboarding
    ├── home
    ├── transaction
    ├── statistics
    └── settings
```

현재 구현의 호출 흐름은 다음과 같다.

```text
Compose UI → ViewModel(StateFlow) → Domain Repository interface
           → Supabase Repository implementation → Supabase
```

* UI와 ViewModel은 Supabase SDK를 직접 호출하지 않는다.
* Domain에 `AuthRepository`, `HouseholdRepository`, `CategoryRepository`,
  `TransactionRepository` interface가 있다.
* Data 계층의 Supabase Repository 구현은 Hilt `RepositoryModule`에서 singleton으로
  바인딩된다.
* 별도 UseCase 계층은 현재 코드에 구현되어 있지 않다. 실제 필요가 생기기 전에는
  계층을 형식적으로 추가하지 않는다.
* 앱 시작 시 저장된 Supabase Auth session과 Household membership을 확인해 Login,
  Household Setup, Home 중 하나로 이동한다.
* 로그인 이후 하단 내비게이션은 Home, Transactions, Add Transaction, Statistics,
  Settings로 구성된다.
* Statistics 화면은 현재 placeholder이며, Home의 금액 데이터는 정적 preview 상태다.

---

## 4. Backend / Database 상태

### 주요 테이블

현재 migrations에 다음 테이블이 정의되어 있다.

* `profiles`
* `households`
* `household_members`
* `invitations`
* `categories`
* `cards`
* `transactions`
* `transaction_refunds`
* `card_performance_excluded_categories`

### Household

* 한 사용자는 하나의 Household에만 가입할 수 있다.
* 역할은 `OWNER`, `MEMBER`이며 한 Household는 최대 2명으로 제한된다.
* Household 생성, 초대 생성, 초대 참여는 인증 사용자를 기준으로 하는 RPC를 사용한다.
* 초대 코드는 24시간 유효하고 한 번만 사용할 수 있다.
* Household 생성 시 기본 지출 카테고리 12개와 수입 카테고리 5개를 생성한다.
* 기존 Household에도 동일한 기본 카테고리를 중복 없이 backfill하는 migration이 있다.

### 거래와 환불

* `transactions.amount`, `transaction_refunds.amount`, 카드 목표/집계 금액은
  PostgreSQL `bigint`다. Android에서는 `Long`을 사용한다.
* 거래 유형은 `EXPENSE`, `INCOME`이다.
* 공개 범위는 `SHARED`, `PERSONAL`이다.
* 거래 상태는 `PENDING`, `CONFIRMED`, `CANCELED`다.
* 거래에는 nullable `card_id`와 `paid_by`가 유지된다.
* 삭제는 `deleted_at`을 기록하는 RPC 기반 soft delete다. 일반 조회와 집계에서는
  삭제된 거래를 제외한다.
* 환불은 원 거래를 변경하지 않고 `transaction_refunds`에 별도 이벤트로 저장한다.
* `PENDING`과 `CONFIRMED` 환불 합계는 원 거래 금액을 초과할 수 없다.
* `CONFIRMED` 환불 누계가 원 거래 금액과 같아지면 DB trigger가 원 거래를
  `CANCELED`로 변경한다. 그보다 작으면 원 거래는 `CONFIRMED`를 유지한다.
* `create_transaction_refund` RPC는 원 거래를 잠그고 접근 권한, 거래 유형/상태,
  삭제 여부, 양수 금액 및 idempotency key를 검증한다.
* `get_monthly_summary`, `get_card_performance`, `soft_delete_transaction`,
  `restore_transaction` RPC가 migrations에 정의되어 있다.

### RLS와 공개 범위

모든 주요 애플리케이션 테이블에 RLS가 활성화되어 있다.

* `SHARED` 거래는 같은 Household 구성원이 조회하고 수정할 수 있다.
* `PERSONAL` 거래는 `paid_by`가 현재 인증 사용자인 row만 조회하고 수정할 수 있다.
* 신규 수동 거래는 `created_by`와 `paid_by`가 모두 현재 인증 사용자일 때만
  INSERT할 수 있다. 따라서 현재 생성 흐름에서 개인 거래의 작성자와 결제자는 같다.
* 다른 Household의 거래에는 접근할 수 없다.
* 환불 조회 권한도 원 거래의 공개 범위를 따른다.
* `household_id`, `created_by`, `paid_by`, `scope`는 거래 생성 후 변경할 수 없는
  소유권 필드다.
* 개인 거래를 다른 구성원에게 내려받아 Android에서 숨기는 방식은 사용하지 않는다.
  공개 범위는 DB row 수준에서 강제한다.

Phase 2B-2 Android 거래 구현에서는 DB schema나 migration을 변경하지 않았다.
현재 거래/환불 스키마와 정책은 Phase 2B-1 migrations를 그대로 사용하며,
destructive migration은 없다. 기존 Supabase RPC와 원격 환불 trigger의 정상 동작은
Phase 2B-2 실기기 검증에서 확인됐다.

---

## 5. 핵심 제품 영역

### 인증

* 이메일/비밀번호 가입 및 로그인
* 로그아웃
* Supabase Auth session 저장 및 앱 재실행 시 복원
* Supabase 설정 누락, 미인증, Household 미가입, 사용 준비 완료 상태별 앱 진입 처리

Google 로그인은 현재 구현 범위가 아니다.

### Household

* Household 생성 및 OWNER 지정
* 24시간 단일 사용 초대 코드 생성
* 초대 코드로 MEMBER 참여
* Household 최대 2명 제한
* 두 구성원의 Household 및 membership 조회
* 다른 Household 데이터 접근 차단

### 거래

`Transaction` Domain model은 다음 필드를 현재 사용한다.

```text
id, householdId, createdBy, paidBy,
type, scope, amount, categoryId, cardId,
merchant, memo, transactionAt, status
```

현재 거래 영역은 다음을 지원한다.

* 수입/지출 생성
* 공동/개인 범위 선택
* 거래 유형에 맞는 활성 카테고리 선택
* 지출 거래의 카드 선택 또는 `카드 없음`
* 카드가 0개인 상태의 거래 등록
* Asia/Seoul 기준 월별 거래 조회
* 전체/공동/내 개인 필터
* 거래 상세 및 환불 이력 조회
* 허용 필드 수정
* soft delete 및 Snackbar 복구(Undo)
* 부분 환불과 전액 환불

월별 조회 결과에는 RLS로 허용된 row만 포함된다. 화면의 공동/개인 필터는 이 결과에
적용되며, 상대방 개인 거래를 받아서 숨기지 않는다.

### 결제자

* DB의 `transactions.paid_by` 필드는 유지한다.
* 신규 거래 생성 시 `created_by`, `paid_by`를 현재 로그인 사용자로 자동 지정한다.
* 사용자는 `내가 결제 / 배우자가 결제`를 직접 선택하지 않는다.
* 거래 목록, 등록, 수정 UI에 결제자 선택 문구를 노출하지 않는다.
* 수정 payload에는 `paid_by`가 없으므로 기존 값이 유지된다.
* DB trigger도 `paid_by` 변경을 차단한다.
* `paid_by`는 공개 범위, 카드 소유자 검증 및 향후 통계/정산 확장에 필요한 데이터로
  유지한다.

### 카드와 카테고리

* 거래 입력 시 Household의 활성 카테고리를 조회하고 거래 유형별로 표시한다.
* 활성 카드를 조회하며, 신규 거래 입력에서는 현재 로그인 사용자 소유 카드만
  선택지로 표시한다.
* 수입 거래는 `card_id = null`로 저장한다.
* 카드/카테고리 자체의 관리 화면은 현재 구현되어 있지 않다.

---

## 6. Phase 2B-2 완료 기능

### 거래 저장 및 조회

* 실제 Supabase INSERT 성공
* 저장 후 Transactions 화면으로 이동하고 월별 목록 자동 갱신
* 기존 저장 거래 재조회
* 앱 재실행 후 저장 데이터 유지
* 공동/개인 수입 및 지출 생성
* 거래 상세 조회

### 거래 수정

* Supabase UPDATE 성공
* UPDATE 응답을 배열로 디코딩한 뒤 단일 거래를 사용
* 성공 시 상세 선택 상태와 월별 목록을 즉시 갱신
* 앱 재실행 후 수정값 유지
* 실패 시 사용자용 오류 메시지 표시
* 저장 중 중복 동작을 막고 `저장 중…` 상태 표시
* 수정 시 거래 공개 범위와 소유권 필드 유지

### 삭제와 복구

* Supabase RPC를 통한 soft delete
* 삭제 직후 목록과 상세 상태 갱신
* Snackbar `복구` action을 통한 Undo
* 복구 시 서버 데이터를 다시 조회
* 복구하지 않은 삭제 상태가 앱 재실행 후에도 유지

### 환불

* 부분 환불
* 전액 환불
* 남은 결제금액을 초과하는 환불을 Android와 DB 양쪽에서 차단
* 부분 환불 후 원 거래 `CONFIRMED` 유지
* 확정 환불 누계가 원 금액에 도달하면 원 거래 `CANCELED`
* 환불 성공 후 거래 목록과 환불 이력을 Supabase에서 재조회
* 재조회한 최신 거래 목록, 선택 거래, 환불 이력을 한 번에 UI state에 반영
* 앱 재실행 후 환불 이력과 거래 상태 유지

### 공개 범위 실기기 검증

* 공동 수입/지출은 배우자 계정에서도 정상 노출
* 개인 수입/지출은 작성자 본인에게만 노출
* 배우자 계정에서 상대방의 `PERSONAL` 거래가 보이지 않음
* Household 및 PERSONAL 거래 제한은 결제자 UX 변경 후에도 유지

---

## 7. UX 원칙

* 거래 입력은 필요한 정보만 빠르게 입력할 수 있어야 한다.
* 카드가 없어도 거래 입력 흐름을 막지 않는다.
* 저장 또는 수정 중에는 진행 상태를 표시하고 중복 제출을 막는다.
* Repository/DB의 내부 오류나 stack trace를 사용자에게 그대로 노출하지 않는다.
* 삭제는 즉시 화면에 반영하되 Snackbar에서 되돌릴 기회를 제공한다.
* 원시 enum 문자열을 사용자 화면에 노출하지 않는다.

거래 상태 표시:

| 내부 상태 | 사용자 표시 |
| --- | --- |
| `CONFIRMED` | 확정 |
| `CANCELED` | 전액 환불 |
| `PENDING` | 처리 중 |

환불 이력 상태 표시:

| 내부 상태 | 사용자 표시 |
| --- | --- |
| `CONFIRMED` | 환불 완료 |
| `CANCELED` | 환불 취소 |
| `PENDING` | 환불 처리 중 |

---

## 8. 개발 원칙

* 기존 구조와 검증된 구현을 우선한다.
* 실제 구현 상태를 문서보다 먼저 확인한다.
* 현재 요구를 해결하지 않는 불필요한 abstraction을 추가하지 않는다.
* UI 또는 ViewModel에서 Supabase에 직접 접근하지 않는다.
* Composable은 state를 렌더링하고 event를 전달하는 역할에 집중한다.
* 상태는 가능한 한 immutable data와 StateFlow로 관리한다.
* 금액에는 `Float` 또는 `Double`을 사용하지 않고 `Long`/`bigint`를 사용한다.
* Android client를 신뢰하지 않으며 권한의 최종 경계는 Supabase RLS로 둔다.
* 개인 거래를 가져온 뒤 UI에서만 숨기지 않는다.
* DB schema 변경은 보수적으로 수행하고 migration으로 재현 가능하게 관리한다.
* destructive migration, RLS 비활성화, 임의 데이터 삭제를 금지한다.
* 신규 기능 구현 전 기존 기능과 구조의 중복 여부를 확인한다.
* 현재 phase와 무관한 대규모 refactor 또는 미래 기능 선행 구현을 하지 않는다.

---

## 9. 기능 추가 의사결정 기준

새 기능은 다음 순서로 판단한다.

1. 현재 사용자 문제와 실제 사용 흐름을 명확히 개선하는가?
2. 기존 Domain/Repository/DB 구조로 해결할 수 있는가?
3. 개인 거래 공개 범위와 Household 보안 경계를 유지하는가?
4. 금액 정확성, 환불, 삭제 및 재실행 후 영속성을 보존하는가?
5. 추가 dependency, schema 또는 계층이 지금 실제로 필요한가?
6. 단위 테스트, DB 테스트 또는 실기기 acceptance test로 검증 가능한가?

AI, OCR, Open Banking, 광고, 구독, 범용 결제수단 추상화 등은 구체적인 현재 요구와
검증 계획이 생기기 전에는 추가하지 않는다.

---

## 10. 구현 작업 방식

1. `ARCHITECTURE.md`, 현재 phase 문서, 관련 코드와 migrations를 확인한다.
2. 명시된 현재 phase 범위와 기존 구현의 충돌 여부를 확인한다.
3. Domain model과 Repository 계약을 먼저 확인한다.
4. UI → ViewModel → Repository → Supabase 의존 방향을 유지해 최소 범위로 구현한다.
5. DB 변경이 필요하면 versioned migration과 RLS/권한 검증을 함께 작성한다.
6. 관련 단위 테스트와 계측/DB 테스트를 실행한다.
7. 실제 Supabase 동작과 영속성 또는 계정 간 visibility가 중요한 기능은 실기기에서
   검증한다.
8. 빌드, 테스트, diff 및 변경 파일 범위를 확인한 뒤 완료 상태를 기록한다.

---

## 11. 테스트

### 자동화 검증

2026-09-21 현재 작업 트리에서 다음 결과를 확인했다.

* `./gradlew :app:testDebugUnitTest` — 29/29 통과
* `./gradlew :app:connectedDebugAndroidTest` — 9/9 통과
* `./gradlew :app:assembleDebug` — 성공
* 계측 기기 — Samsung SM-G986N / Android 13

단위 테스트는 앱 진입 routing, session 상태, 금액 정밀도, 거래 form 검증, 중복 저장
방지, 수입의 카드 미선택, 공동/개인 필터, 수정, 오류 상태, soft delete/restore,
부분/전액 환불, 초과 환불 차단 및 상태 한글 매핑을 포함한다.

계측 테스트는 로그인/Household UI, 주요 navigation, 테마, 거래 저장 action, 카드 0개
상태의 form 표시와 결제자 문구 미노출을 포함한다.

Phase 2B-1 데이터베이스 기반은 2026-09-21 기준 local pgTAP 128/128 통과,
전체 local migration 재적용 성공 및 database lint 오류 없음이 기록되어 있다.

### Phase 2B-2 실기기 acceptance test

Samsung SM-G986N / Android 13에서 다음을 완료했다.

* 공동 지출 생성 및 조회
* 개인 지출 생성 및 본인만 조회
* 공동 수입 생성 및 배우자 계정 조회
* 개인 수입 생성 및 배우자 계정 미노출
* 거래 수정과 재실행 후 수정값 유지
* 부분 환불과 전액 환불
* 거래/환불 상태 한글 표시
* 거래 삭제와 Snackbar Undo
* 삭제 후 재실행 시 미노출 유지
* 거래 UI의 결제자 문구 미노출
* 앱 재실행 후 기존 데이터 유지
* 앱 설치 및 실행, 실행 직후 crash 없음, 주요 logcat 오류 없음

---

## 12. 확정 사항

* 앱은 Kotlin/Compose 단일 Android 모듈이며 Hilt DI와 StateFlow 기반 ViewModel을
  사용한다.
* 현재 구현 계층은 UI → ViewModel → Repository → Supabase이며 별도 UseCase는 없다.
* Supabase Auth session과 Household 상태가 실제 앱 진입 경로를 결정한다.
* 한 사용자는 하나의 Household에 속하고, 한 Household는 OWNER와 MEMBER 최대 2명이다.
* 거래 Domain/Repository/Supabase 연동과 실제 INSERT/UPDATE/조회가 구현·검증됐다.
* 수입/지출, 공동/개인, 카테고리, nullable 카드, 월별 목록, 필터, 상세, 수정,
  soft delete/Undo 및 환불을 지원한다.
* `SHARED`는 Household 구성원에게, `PERSONAL`은 현재 작성자/결제자 본인에게만
  row 수준에서 노출된다.
* 신규 거래의 `created_by`와 `paid_by`는 현재 로그인 사용자로 자동 지정되고,
  결제자는 사용자가 직접 선택하지 않는다.
* `paid_by`는 DB에 유지되고 수정 시 변경되지 않는다.
* 거래 및 환불 상태는 한글로 매핑하며 raw enum 값을 사용자에게 노출하지 않는다.
* 금액 저장은 Android `Long`, PostgreSQL `bigint` 기반이다.
* Phase 2B-2에서는 schema/migration 변경이나 destructive migration이 없다.
* Phase 2B-2 자동화 테스트와 실기기 acceptance test가 완료됐다.

---

## 13. 추가 확인이 필요한 사항

다음 항목은 현재 코드에서 완료 상태로 확인되지 않았으므로 확정 기능으로 보지 않는다.

* Android 결제 알림 listener, provider parser, 중복 방지 및 자동 거래 등록
* merchant rule 기반 자동 분류와 사용자 수정 학습 흐름
* Statistics 화면과 `get_monthly_summary` RPC의 실제 Android 연동
* Home 화면의 실제 거래/집계 데이터 연동
* 카드 생성·수정·비활성화 관리 UI
* 카드 실적 화면과 `get_card_performance` RPC의 실제 Android 연동
* Realtime 구독 사용 여부와 필요한 사용자 시나리오
* Room 등 로컬 cache 또는 offline-first 전략의 실제 필요성
* Google 로그인, import 및 기타 후속 phase 기능의 구체 범위

이 항목들은 요구사항, UX, 보안 경계와 테스트 방법을 별도로 확정한 뒤 구현한다.

---

## 14. 문서 관리 규칙

* 이 문서는 현재 구현의 기준 사양이며 아이디어 목록이 아니다.
* 코드, migrations, 자동화 테스트 및 실기기 검증 결과를 확인한 내용만 확정 사항으로
  기록한다.
* 구현과 문서가 충돌하면 최신 검증 구현을 우선하고 문서를 갱신한다.
* 아직 구현 또는 검증되지 않은 내용은 `추가 확인이 필요한 사항`에 둔다.
* phase 완료 또는 구조/보안/데이터 모델 변경 시 관련 섹션과 `Last updated`를 함께
  갱신한다.
* 같은 내용을 여러 문서에서 다르게 유지하지 않는다. 세부 설계는
  `ARCHITECTURE.md`, phase 범위는 해당 phase 문서를 참고하되 현재 제품 사양은 이
  문서를 기준으로 한다.
* 문서 갱신 전후에 `git diff`와 `git status`로 의도하지 않은 파일 변경이 없는지
  확인한다.
