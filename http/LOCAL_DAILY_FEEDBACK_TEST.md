# 일일 피드백 로컬 통합 테스트

이 문서는 테스트 사진 한 장을 Cultivation Server에 올린 뒤, AI가 Presigned URL로 사진을 내려받아 Vision 분석을 수행하고 `growth_record`와 `daily_feedback`에 저장한 결과를 직접 확인하는 절차입니다.

요청은 [`daily_feedback_local.http`](./daily_feedback_local.http)에 실행 순서대로 준비되어 있습니다. IntelliJ IDEA의 HTTP Client에서 각 요청 왼쪽의 실행 버튼을 눌러 한 건씩 진행하세요.

## 1. 테스트가 증명하는 범위

정상 완료되면 다음을 확인한 것입니다.

1. AI에서 Vision Server로 multipart 사진을 보낼 수 있음
2. Cultivation Server가 사진을 MinIO에 저장함
3. 일별 사진 API가 `cultivationId`, `photoId`, 새 Presigned URL을 반환함
4. AI가 허용된 MinIO origin에서 사진을 다운로드함
5. Vision 결과가 `growth_record`에 저장됨
6. 일일 피드백이 생성되어 `daily_feedback`에 저장됨
7. 조회 API에서 피드백 본문과 생성 근거 `contextSnapshot`을 다시 읽을 수 있음
8. 같은 날짜를 재실행해도 새 피드백 행을 만들지 않고 `EXISTING`을 반환함

아직 Outbox가 연결되기 전이므로, 이 절차는 피드백 저장까지를 검증합니다. RabbitMQ 완료 이벤트의 신뢰성 있는 발행은 Outbox 구현 뒤 별도로 검증합니다.

## 2. 테스트 전 반드시 지킬 조건

- `feedbackDate`는 사진을 업로드하는 시점의 **Asia/Seoul 현재 날짜**여야 합니다.
- 같은 `cultivationId + feedbackDate`에는 사진을 **정확히 한 장만** 올립니다.
- 사진을 올리기 전에 같은 날짜의 `daily_feedback` 행이 없어야 합니다.
- 기본 테스트 대상은 `cultivationId=28`, 소유자 `X-User-Id=22`입니다.
- 실제 JPEG 파일을 사용하고, 크기는 8 MiB 이하여야 합니다.
- 일별 사진 조회 응답의 `presignedUrl`은 공유하거나 로그에 복사하지 않습니다.

Cultivation Server는 현재 재배지·날짜별 사진 한 장 제약을 DB에서 강제하지 않습니다. 반면 AI의 일일 Vision 분석은 같은 재배지에 사진이 둘 이상이면 잘못된 계약으로 판단합니다. 따라서 사진 업로드 요청을 실수로 두 번 실행했다면 그대로 배치를 실행하지 마세요.

또한 피드백이 이미 `EXISTING`인 뒤 사진을 추가해도 재실행 과정에서 Vision을 다시 분석하지 않습니다. 새 통합 테스트는 아직 피드백이 없는 날짜와 재배지 조합으로 진행해야 합니다.

## 3. 테스트 사진 준비

터미널에서 다음 명령을 실행합니다. 첫 번째 경로는 본인이 가진 실제 버섯 JPEG 경로로 바꾸세요.

```bash
mkdir -p /Users/kimminseo/NHN/Ai_server/http/local-test-images
cp /절대/경로/버섯사진.jpg /Users/kimminseo/NHN/Ai_server/http/local-test-images/mushroom.jpg
file /Users/kimminseo/NHN/Ai_server/http/local-test-images/mushroom.jpg
du -h /Users/kimminseo/NHN/Ai_server/http/local-test-images/mushroom.jpg
```

`file` 결과가 JPEG 이미지인지, 크기가 8 MiB 이하인지 확인합니다. `http/local-test-images/`는 `.gitignore`에 추가되어 사진이 Git에 올라가지 않습니다.

## 4. 필요한 서비스 실행

각 서비스는 별도 터미널에서 실행합니다. 이미 해당 포트로 정상 실행 중인 서비스는 다시 실행하지 않아도 됩니다.

### 4.1 Vision Server: 8000

```bash
cd /Users/kimminseo/NHN/Vision_server
source .venv/bin/activate
make doctor-mac
make verify-models
make run-cpu
```

`make run-cpu`가 종료되지 않고 요청을 기다리는 상태여야 합니다.

### 4.2 Cultivation Server: 8084

```bash
cd /Users/kimminseo/NHN/Cultivation_server
set -a
source /Users/kimminseo/NHN/Workspace_log/cultivation/.env
set +a
export SERVER_PORT=8084
export USER_SERVER_URL=http://localhost:9002
export AI_SERVER_URL=http://localhost:8080
./mvnw spring-boot:run
```

현재 Workspace 환경 파일의 `MINIO_URL`은 Cultivation과 AI가 함께 사용합니다. 두 서버가 받은 값의 scheme, host, 유효 port 중 하나라도 다르면 AI가 안전을 위해 사진 다운로드를 거부합니다.

### 4.3 Notification Server: 9005

일일 피드백은 Notification의 일별 원본 이벤트 통계를 조회하므로 Notification Server도 필요합니다.

```bash
cd /Users/kimminseo/NHN/Notification_server
set -a
source /Users/kimminseo/NHN/Workspace_log/notification/.env
set +a
export NOTIFICATION_DB_SCHEMA="$NOTIFICATION_DB_SCEMA"
export SERVER_PORT=9005

while IFS='=' read -r key value; do
  case "$key" in
    RABBITMQ_HOST|RABBITMQ_PORT|RABBITMQ_USERNAME|RABBITMQ_PASSWORD)
      export "$key=$value"
      ;;
  esac
done < /Users/kimminseo/NHN/Ai_server/.env

./mvnw spring-boot:run
```

`NOTIFICATION_DB_SCEMA`는 현재 Workspace 환경 파일의 오타 난 키이고, 애플리케이션은 `NOTIFICATION_DB_SCHEMA`를 요구하므로 프로세스 환경에서만 맞춰 줍니다. RabbitMQ 연결값은 AI와 동일한 브로커를 사용하되 값을 화면에 출력하지 않습니다.

### 4.4 AI Server: 8080

기존 AI 프로세스가 실행 중이면 먼저 정상 종료한 뒤, 로컬 Vision 주소를 **프로세스 환경변수로만** 덮어써서 다시 실행합니다. `.env`의 Kubernetes 서비스 주소는 수정하지 않습니다.

```bash
cd /Users/kimminseo/NHN/Ai_server
export VISION_SERVER_URL=http://localhost:8000
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`local` 프로필이 빠지면 `/api/test/vision`과 `/api/test/daily-feedbacks/**`가 등록되지 않습니다.

## 5. HTTP 파일 변수 확인

[`daily_feedback_local.http`](./daily_feedback_local.http) 맨 위에서 다음 세 값을 확인합니다.

```http
@cultivationId = 28
@ownerUserId = 22
@feedbackDate = 2026-09-02
```

특히 `feedbackDate`는 실제 실행일의 한국 날짜로 바꿉니다. 다른 재배지를 사용하려면 그 재배지의 실제 OWNER 또는 MEMBER 사용자 ID도 함께 바꿔야 사진 업로드 권한 검사를 통과합니다.

## 6. 요청 실행 순서

HTTP 파일의 요청을 1번부터 10번까지 차례대로 실행합니다.

1. Vision live 확인
2. Vision ready 확인
3. Notification health 확인
4. AI → Vision 직접 사진 분석 확인
5. 업로드 전 저장된 피드백이 없는지 확인
6. 업로드 전 오늘 사진과 전체 사진 중복 확인
7. Cultivation에 사진 한 장 업로드
8. 일별 사진 API에서 같은 `photoId` 확인
9. 일일 피드백 배치 실행
10. 저장된 피드백과 Vision 분석 결과 조회

각 응답 아래의 테스트 스크립트가 상태 코드와 핵심 계약을 자동 검사합니다. 10번 조회는 `DailyFeedbackPersistenceService`를 통해 DB에서 읽기 때문에, 정상 응답 자체가 `daily_feedback` 저장 결과의 확인입니다. `contextSnapshot.visionAnalysis.growthRecordId`가 양수이면 Vision 분석 결과도 `growth_record`에 저장된 것입니다.

11번 요청은 선택 사항입니다. 한 번 더 실행하면 테스트 재배지 결과가 `EXISTING`인지 확인하여 중복 저장 방지를 검증합니다.

## 7. 결과를 읽는 방법

배치 API는 모든 활성 재배지를 처리합니다. 따라서 다른 재배지의 데이터 계약 문제로 `failedCount`가 0보다 클 수 있습니다. 전체 `failedCount`만 보지 말고 `results`에서 테스트한 `cultivationId=28`의 상태를 확인하세요.

- 첫 실행: `CREATED`
- 같은 날짜 재실행: `EXISTING`
- `FAILED`: `failureStage`와 `exceptionType`을 확인하고 AI 로그의 같은 시각 예외를 조사

현재 알려진 데이터 문제로 일부 재배지는 Sensor Snapshot의 단위와 Cultivation 통계 단위가 달라 **재배지별 처리 결과**가 실패할 수 있습니다. 테스트 대상 28번이 `CREATED`이고 10번 조회가 통과한다면 그 개별 실패가 이번 사진 통합 경로의 실패를 뜻하지는 않습니다.

다만 Snapshot, 버섯 참조정보, Notification 통계, 일별 사진 목록처럼 모든 대상이 공유하는 선행 조회가 실패하면 배치 전체가 HTTP 500으로 중단됩니다. 특히 어느 활성 재배지든 같은 날짜 사진이 두 장 이상이면 AI가 일별 사진 계약 전체를 거부하므로 6번 검사가 이를 먼저 찾아냅니다.

## 8. 충돌하거나 실수했을 때

### 5번에서 이미 피드백이 있다고 나오는 경우

사진을 업로드하지 마세요. 이미 저장된 피드백은 재실행 시 `EXISTING`으로 반환되며 새 사진을 분석하지 않습니다. 피드백과 사진이 모두 없는 다른 유효한 재배지·날짜 조합을 선택합니다.

### 6번에서 이미 사진이 있다고 나오는 경우

같은 재배지에 두 번째 사진을 올리지 마세요. 기존 사진으로 배치를 실행할 수 있지만, 이미 같은 날짜의 피드백이 저장되어 있다면 Vision 재분석은 일어나지 않습니다. 가장 안전한 방법은 사진과 피드백이 모두 없는 다른 유효한 재배지·날짜 조합을 선택하는 것입니다.

### 7번 업로드 요청을 두 번 실행한 경우

배치를 실행하지 말고 먼저 담당 데이터와 참조 관계를 확인하세요. HTTP 파일에는 자동 DELETE 요청을 넣지 않았습니다. 이미 `growth_record`나 `daily_feedback.context_snapshot`이 사진 ID를 감사 근거로 보관할 수 있어, 무조건 사진부터 지우면 저장 근거가 깨질 수 있기 때문입니다.

### 9번 결과가 EXISTING인 경우

사진 업로드보다 먼저 같은 날짜의 `daily_feedback`이 생성된 상태입니다. 기존 행을 삭제하는 동작은 자동화하지 않았습니다. 새 날짜·재배지 조합을 사용하거나, 정말 테스트 데이터 삭제가 필요한지 DB 참조 상태를 먼저 확인한 뒤 결정하세요.

### AI가 사진 다운로드를 거부하는 경우

AI 로그에서 origin 불일치 여부를 확인합니다. 아래 두 설정이 동일한 origin이어야 합니다.

- Cultivation: `MINIO_URL`
- AI: `MINIO_URL`

Presigned URL 전체를 채팅, 이슈, 로그에 붙이지 말고 scheme/host/port만 비교하세요.

## 9. 보안과 Git 주의사항

- 테스트 사진은 `http/local-test-images/` 아래에만 둡니다.
- `.env`, Presigned URL, MinIO object key, API 키를 커밋하거나 공유하지 않습니다.
- IntelliJ의 HTTP 응답 기록은 `.idea/` 아래에 저장되며 이 저장소에서는 이미 Git 제외 대상입니다.
- HTTP 파일은 비밀값을 포함하지 않으므로 팀과 함께 사용할 수 있습니다.
