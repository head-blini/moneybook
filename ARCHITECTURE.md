# MoneyBook Android Architecture

## 1. Project Goal

MoneyBook은 두 명이 함께 사용하는 Android 가계부다.

핵심 목표는 사용자가 가계부를 매번 직접 작성하지 않아도 Android 결제 알림을 기반으로 거래가 자동 기록되고, 개인 지출과 공동생활비를 분리해서 관리할 수 있게 하는 것이다.

핵심 개념:

* 사용자 2명
* 하나의 Household
* 개인 거래와 공동 거래 분리
* 결제자와 비용 귀속 분리
* Android 알림 기반 자동 거래 수집
* Supabase 기반 사용자 인증 및 데이터 동기화
* RLS 기반 데이터 접근 통제

---

# 2. Technology Stack

## Android

* Kotlin
* Jetpack Compose
* Material 3
* Navigation Compose
* ViewModel
* Kotlin Coroutines
* Flow / StateFlow
* Hilt
* Kotlin Serialization

## Backend

Supabase

* Auth
* PostgreSQL
* Row Level Security
* Realtime
* PostgreSQL Functions/RPC

별도의 FastAPI/Node 백엔드는 MVP에서 사용하지 않는다.

필요성이 명확해졌을 때만 추가한다.

---

# 3. Architecture

기본 구조는 다음과 같다.

UI

↓

ViewModel

↓

UseCase

↓

Repository

↓

Supabase / Local Data Source

의존성 방향은 항상 내부 Domain 방향을 유지한다.

UI가 Supabase SDK를 직접 호출해서는 안 된다.

ViewModel 역시 Supabase SDK에 직접 의존해서는 안 된다.

---

# 4. Package Structure

```text
com.moneybook

app/
    MoneyBookApplication
    MainActivity
    navigation/

core/
    common/
    ui/
    model/
    result/

data/
    remote/
        supabase/

    repository/

domain/
    model/

    repository/

    usecase/

feature/
    auth/
    onboarding/
    home/
    transaction/
    statistics/
    settings/

notification/
    listener/
    processor/

    parser/

    dedup/

    classifier/
```

각 feature는 가능하면 독립적으로 유지한다.

---

# 5. Domain Models

## User

```text
User

id
displayName
```

## Household

```text
Household

id
name
createdBy
```

## HouseholdMember

```text
HouseholdMember

householdId
userId
role
```

Role:

```text
OWNER
MEMBER
```

MVP에서는 Household 최대 인원을 2명으로 제한한다.

---

# 6. Transaction

가장 중요한 Domain Model이다.

```text
Transaction

id
householdId

createdBy
paidBy

type
scope

amount

categoryId
paymentMethodId

merchant
memo

transactionAt

source
status

createdAt
updatedAt
```

TransactionType:

```text
EXPENSE
INCOME
```

TransactionScope:

```text
PERSONAL
SHARED
```

TransactionSource:

```text
MANUAL
NOTIFICATION
IMPORT
```

TransactionStatus:

```text
PENDING
CONFIRMED
CANCELED
```

금액은 floating point를 사용하지 않는다.

KRW는 Long으로 저장한다.

예:

```text
54,900원

amount = 54900L
```

---

# 7. Privacy Model

MoneyBook의 핵심 보안 요구사항이다.

SHARED 거래:

* 같은 Household의 두 사용자 모두 상세 조회 가능

PERSONAL 거래:

* 거래 소유자만 상세 조회 가능
* 상대방은 개별 거래 정보를 조회할 수 없음

다른 Household:

* 모든 접근 금지

이 정책은 Android UI가 아니라 Supabase RLS에서 강제해야 한다.

클라이언트는 신뢰하지 않는다.

---

# 8. Personal Spending Aggregation

상대방의 PERSONAL 거래 row를 Android에 전달해서 합계를 계산해서는 안 된다.

예:

```text
User A PERSONAL

스타벅스 6,500
게임 70,000
쇼핑 120,000
```

User B는 위 row를 읽을 수 없다.

필요한 경우 PostgreSQL Function/RPC가 서버 측에서 합계를 계산한다.

반환:

```text
personalTotal = 196500
```

개별 merchant/category/memo 정보는 반환하지 않는다.

---

# 9. Repository

Domain layer에 interface를 정의한다.

예:

```text
AuthRepository

signIn()
signOut()
currentUser()
```

```text
HouseholdRepository

createHousehold()
joinHousehold()
getHousehold()
getMembers()
```

```text
TransactionRepository

createTransaction()
updateTransaction()
deleteTransaction()

getTransactions()
getSharedTransactions()
getPersonalTransactions()
```

Data layer가 이를 구현한다.

---

# 10. UI State

ViewModel은 StateFlow 기반으로 화면 상태를 제공한다.

예:

```text
HomeUiState

Loading

Success(
    totalExpense,
    sharedExpense,
    categories,
    recentTransactions
)

Error
```

Composable 내부에서 직접 비즈니스 로직을 수행하지 않는다.

---

# 11. Navigation

주요 destination:

```text
Splash
Login

HouseholdSetup
CreateHousehold
JoinHousehold

NotificationPermission

Home

Transactions
TransactionDetail
AddTransaction

Statistics

Settings
```

로그인 및 Household 상태에 따라 시작 destination을 결정한다.

---

# 12. Bottom Navigation

로그인 이후 기본 Navigation:

```text
Home

Transactions

Add Transaction

Statistics

Settings
```

Add Transaction은 중앙 action으로 표현할 수 있다.

---

# 13. Notification Architecture

결제 자동수집은 UI 코드와 완전히 분리한다.

```text
Android Notification

        ↓

NotificationListener

        ↓

NotificationProcessor

        ↓

Provider Parser

        ↓

ParsedTransaction

        ↓

Deduplicator

        ↓

MerchantClassifier

        ↓

TransactionRepository
```

Parser interface:

```text
NotificationParser

canParse(notification)

parse(notification)
```

카드사/은행별 구현:

```text
SamsungCardParser

ShinhanCardParser

HyundaiCardParser

...
```

새로운 금융사를 추가할 때 기존 Parser를 수정하지 않고 새로운 구현을 추가할 수 있어야 한다.

---

# 14. Automatic Classification

MVP에서는 AI를 사용하지 않는다.

MerchantRule 기반으로 처리한다.

예:

```text
쿠팡
    ↓
SHARED
CHILDCARE
```

사용자가 자동분류 결과를 수정하면:

```text
이번만 변경

앞으로도 변경
```

두 가지 선택지를 제공할 수 있다.

---

# 15. Error Handling

Repository에서 발생하는 예외를 UI에 직접 노출하지 않는다.

공통 결과 모델을 사용한다.

예:

```text
Result<T>

Success<T>

Error(
    code,
    message
)
```

사용자에게 내부 SQL 오류, Supabase 오류, stack trace 등을 노출하지 않는다.

---

# 16. Offline Strategy

MVP에서는 복잡한 offline-first architecture를 구현하지 않는다.

Supabase를 Source of Truth로 사용한다.

네트워크 장애에 대한 기본적인 loading/error/retry UX만 제공한다.

Room 기반 offline cache는 실제 필요성이 확인된 이후 추가한다.

---

# 17. Security Principles

다음 원칙을 반드시 지킨다.

* Service Role Key를 Android 앱에 포함하지 않는다.
* Supabase anon/publishable key만 클라이언트에 사용한다.
* 모든 민감한 테이블에 RLS를 활성화한다.
* Household membership을 DB에서 검증한다.
* PERSONAL transaction은 소유자 외 SELECT 금지.
* UPDATE/DELETE 권한도 RLS에서 검증한다.
* 초대코드는 Household 조회 권한으로 사용하지 않는다.
* 전체 카드번호를 저장하지 않는다.
* 클라이언트가 전달한 householdId/userId를 신뢰하지 않는다.
* 입력값은 DB constraint와 서버 정책에서도 검증한다.

---

# 18. Testing

최소 테스트 범위:

## Unit

* ViewModel
* UseCase
* Transaction validation
* Notification parser
* Deduplication
* Merchant classification

## Security

Supabase RLS 테스트:

* User A → A PERSONAL 조회 성공
* User B → A PERSONAL 조회 실패
* User A → SHARED 조회 성공
* User B → SHARED 조회 성공
* 다른 Household → 조회 실패
* 다른 Household → INSERT 실패
* 다른 Household → UPDATE 실패
* 다른 Household → DELETE 실패

보안 테스트는 기능 테스트와 동일하게 중요하게 취급한다.

---

# 19. MVP Non-Goals

v0.1에서는 구현하지 않는다.

* AI 금융 상담
* 투자자산 관리
* 주식 포트폴리오
* 자동매매
* 은행 Open Banking 직접 연동
* 카드사 API 직접 연동
* 영수증 OCR
* Web/iOS
* 복잡한 정산 시스템
* 다중 Household
* 3명 이상 Household
* 외화 회계

기능 추가는 명시적인 요구사항 변경 없이 진행하지 않는다.

