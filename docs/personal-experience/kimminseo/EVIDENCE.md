# AI 일일 피드백 근거 목록

## 조사 범위와 재현 방법

- 조사일: 2026-09-14.
- 저장소: 이 문서가 포함된 `Ai_server` 저장소. 아래 소스 경로는 저장소 루트를 기준으로 한다.
- 기준: `develop`, `406099c20ddb2d81c900fbc60c446d73f8e21fe9`.
- 조사 시작 시 `git status --short`는 빈 결과였다.
- 수행: 소스·테스트 읽기, 제한된 `git log/show`, 파일 검색.
- 미수행: `fetch`, 소스 수정, 테스트 실행, DB·Broker·운영 API 호출, 배포 확인.
- 아래 행 번호는 위 revision 기준이다. 경로는 `Ai_server` 상대 경로다.
- 커밋 저자는 개인 구현의 출처를 보강하지만 전체 파일의 독점 기여, 수작업 작성 여부, 실행 성공을 증명하지 않는다.

읽기 전용 재확인 예: `git -C /경로/Ai_server show 406099c:src/main/java/site/yesaido/ai_server/service/DailyFeedbackProcessor.java`.

## DF-E01. 기여·변경 이력

| 날짜 | 커밋 | 저자 표기 | 확인 범위 | 해석 제한 |
|---|---|---|---|---|
| 2026-08-25 | `d08fc68` | kjs31602 | 기존 RabbitMQ 알림 기능·Producer 기반 | 메시징 기반 전체를 김민서 구현으로 쓰지 않음 |
| 2026-08-31 | `464beab`, `fffa91e` | {kim75503} | Vision 결과 저장 연동 | 이후 팀원 리팩터가 있음 |
| 2026-09-02 | `7af231a` | {kim75503} | 일일 피드백 저장 | 커밋 제목만으로 독립 운영 완료를 뜻하지 않음 |
| 2026-09-02 | `b17c1d9` | {kim75503} | 일일 피드백 저장·요약 | 실제 흐름은 현재 소스와 함께 확인 |
| 2026-09-03 | `bfff1c2` | {kim75503} | 알림·Outbox 경로 변경 | 기존 팀 메시징 기반과 구분 |
| 2026-09-03 | `aefcedf` | {kim75503} | 운영 조회 Controller·DTO·서비스·관련 테스트 추가 | 인증 시스템 전체 구현이 아님 |
| 2026-09-04 | `cde30d4` | {kim75503} | unit 필수 인자·호출부·테스트 수정 | 배치 모든 장애를 단독으로 해결했다고 단정하지 않음 |
| 2026-09-08 | `6470579` | Poly-Etilen | ADMIN 일일 피드백 조회 보완 | 팀원 후속 작업 |
| 2026-09-10 | `bf6b6aa` | kjs31602 | Vision 이미지 다운로드 관련 application 설정 변경 | 팀원 후속 작업; 민감 연결값은 이 문서에 복사하지 않음 |
| 2026-09-10 | `406099c` | kjs31602 | 센서 데이터 없는 경우의 인사이트 개선; 현재 HEAD | 김민서 일일 피드백 담당과 별도 영역 |

`git log`에서 챗봇·인사이트 변경이 함께 보인다. 현재 AI 저장소 전체를 개인 담당으로 합산하지 않는다. 작업 에이전트 이름만으로 9/14 리팩터나 이벤트 계약 변경 완료를 추정하지 않는다.

## DF-E02. unit 계약

- `src/main/java/site/yesaido/ai_server/client/CultivationClient.java:139` 이하: rolling Trend 계약, `:153`의 `@RequestParam("unit")`.
- `src/main/java/site/yesaido/ai_server/service/DailySensorStatisticsService.java:81`: `channel.unit()` 포함 실제 호출.
- `src/main/java/site/yesaido/ai_server/service/SensorChannelStatisticsCalculator.java:18`: 재배지/EUI/타입/단위의 채널 식별 설명; `:99` 동일 가중 평균 계산.
- `src/test/java/site/yesaido/ai_server/service/DailySensorStatisticsServiceTest.java:54` 주변: 단위 포함 Mock 호출.
- 커밋 `cde30d4` diff에서 선언 한 인자, 호출 한 인자, stub 한 인자가 같이 바뀐 것을 확인했다.

## DF-E03. 소유자 식별·사용자 조회 인가

- `src/main/java/site/yesaido/ai_server/service/CultivationOwnerService.java:10` 이하: 내부 배치 전용 ADMIN 임시 계약 주석; `findOwnerUserId`에서 OWNER 0명/복수 명 실패.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackQueryService.java:48` 이하: 요청 검증→권한 확인→기존 피드백 조회. 현재 `role` 전달은 팀원 후속 수정 포함.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackQueryServiceTest.java`: 조회 권한·존재 여부 관련 테스트.
- `aefcedf` 추가 파일 목록과 현재 코드 재확인. 과거 UI 403 원인은 아래 DF-H01과 구분.

## DF-E04. 배치 대상·시간·통계

- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackBatchService.java:24` 이하: 공통 조회 1회, 실행 시점 Snapshot, 순차 반복, 공통 실패 vs 개별 실패 설명. `execute`의 Snapshot 기반 `targetIds` 사용.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackScheduledBatchService.java:58` 이하: Clock 기준 전날 계산, 부분 실패 시 예외. CronJob 자체의 exactly-once 보장을 증명하지 않음.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackContextCollector.java:24` 이하: 공통 자료 재사용, owner 재사용, 다른 필수 데이터 수집 후 Vision 호출.
- `src/main/java/site/yesaido/ai_server/client/CultivationClient.java:139` 이하: 조회 시점 rolling 24시간, 15분 구간 평균.
- `src/main/java/site/yesaido/ai_server/service/SensorChannelStatisticsCalculator.java:80` 이하: 집계점 최소/최대/합계/평균 계산.
- `src/main/resources/prompts/daily_feedback_system.st`의 `[시간 기준]`, `[환경 유지율 작성 규칙]`, `[Notification 통계 작성 규칙]`: 서로 다른 기준과 0/null 구분, 알림/제어 이벤트 의미. 프롬프트는 AI 측 계약 설명이며 외부 서비스 운영 설정을 새로 확인한 자료는 아니다.

## DF-E05. 원본 Snapshot과 전송 복사본

- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackProcessor.java`: `createContextSnapshot(context)`가 생성 요청 전에 수행되고 `contextSnapshot`을 엔티티에 보존.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackPromptContextSanitizer.java:36` 이하: 민감 필드명 집합; `sanitize`, `copyObjectNode`, `copyArrayNode`로 새 복사본 생성.
- 동일 파일 `isSensitiveFieldName`: deviceEui 예외 유지, Id/_id/-id 및 지정 필드 제거.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackGenerationService.java:141` 이하: 정제 후 프롬프트용 센서 표시값 반올림. 범위는 minimum/average/maximum이며 Vision 확률·집계점 수·원본 Snapshot은 변경하지 않는다.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackPromptContextSanitizerTest.java`, `DailyFeedbackGenerationServiceTest.java`: 관련 동작 테스트가 존재. 실행 결과로 집계하지 않음.

## DF-E06. Spring AI 호출과 출력 검증

- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackGenerationService.java`: `renderPrompts`의 PromptTemplate, `callAndValidate`의 ChatClient 호출과 Validator 위임; `generateWithFallback`의 선택적 Ollama ChatClient.
- 위 클래스 Javadoc: 팀원 Ollama embedding/PGVector 구성과 독립적인 선택적 채팅 fallback임을 명시.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutputValidator.java:27` 이하: 다섯 REQUIRED_HEADINGS, 금지 패턴, 줄바꿈 정규화, 제목 순서와 섹션 본문 검증.
- `src/main/resources/prompts/daily_feedback_system.st`, `daily_feedback_user.st`: 계산된 데이터 기반 한국어 Markdown 설명, 모르는 원인·병명 생성 금지.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackOutputValidatorTest.java`, `DailyFeedbackGenerationServiceTest.java`: 계약 테스트 존재.
- 코드에서 의미적 품질 점수·사용자 유용성 검증·완전한 수치 대조를 수행하는 것으로 읽히지 않는다. 테스트 개수=모델 정확도가 아니다.

## DF-E07. 사진·Vision 상태와 재사용

- `src/main/java/site/yesaido/ai_server/service/DailyVisionAnalysisService.java:20` 이하: 날짜/재배지별 최대 한 장 응답 계약. `indexPhotosByCultivationId`에서 재배지 중복·photoId 중복 거부.
- 동일 파일 `analyzeIfPresent`: 사진 없으면 Optional.empty, 있으면 VisionRelay 후 저장 결과 ID 검사.
- `src/main/java/site/yesaido/ai_server/service/VisionRelayService.java:51` 이하: 기존 사진 ID 분석 기록 조회→있으면 반환→없으면 다운로드/모델→응답 검증→saveAndFlush; 동시 무결성 충돌 시 기존 기록 재조회.
- 동일 파일 `validateVisionResponse`, `validateHealthProbabilities`: SUCCESS/NO_MUSHROOM_DETECTED 및 HEALTHY/DISEASE_SUSPECTED/UNCERTAIN 계약. 확률은 유한한 [0,1]인지 검사. 이 검사만으로 보정된 임상적 병해 확률이나 모델 정확성을 보장하지 않음.
- `src/main/java/site/yesaido/ai_server/storage/image/PresignedImageDownloader.java:48`: 다운로드 진입; `:179` 만료 검사; `:207` scheme/host/port 일치; `:80` 실제 읽기 크기 제한 주석/구현.
- `src/test/java/site/yesaido/ai_server/service/DailyVisionAnalysisServiceTest.java:137`, `:157`, `:167`: 사진 있음/없음/ID 불일치 사례.
- `src/test/java/site/yesaido/ai_server/service/VisionRelayServiceTest.java`: 기존 결과·잘못된 응답·멱등 저장 관련 사례.

## DF-E08. 외부 호출·저장 트랜잭션과 멱등성

- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackProcessor.java:68`: process `NOT_SUPPORTED`; 기존 피드백 확인을 외부 수집보다 먼저 수행.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackPersistenceService.java`: `saveOrGet`, AtomicWriter 예외 후 기존 행 확인 및 없으면 원래 DataIntegrityViolationException 전파.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackAtomicWriter.java:58`: 별도 Bean의 `REQUIRES_NEW`, 피드백 flush→이벤트 생성→Outbox flush.
- `src/main/java/site/yesaido/ai_server/entity/DailyFeedback.java:18`: cultivation_id+feedback_date UNIQUE 선언.
- `src/main/java/site/yesaido/ai_server/entity/DailyFeedbackOutbox.java:36`: event_id, daily_feedback_id UNIQUE 선언.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackProcessorTest.java:127`: 기존 피드백 생략; `:181` 신규 순서; `:476` 트랜잭션 정책.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackPersistenceServiceTest.java:320`: 경쟁 저장 충돌; `:398` 무관한 무결성 오류; `:465` 트랜잭션 선언.
- Mock 기반 테스트/애너테이션 검사와 실제 PostgreSQL 동시 트랜잭션 실험을 구분한다. 이번 조사는 전자 소스 확인이며 운영 DB 제약은 미확인이다.

## DF-E09. Outbox 선점·상태

- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutboxClaimService.java:18` 이하: REQUIRES_NEW에서 조회→claim→flush→불변 snapshot 반환.
- `src/main/java/site/yesaido/ai_server/repository/DailyFeedbackOutboxRepository.java:48` 이하: `PENDING`, next_attempt_at, LIMIT, `FOR UPDATE SKIP LOCKED`.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutboxStateService.java:26` 이하: attemptCount를 선점 토큰으로 사용; 행 잠금 뒤 현재 SENDING과 시도 횟수 검사.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRelayService.java`: NOT_SUPPORTED 발행, 항목별 실패 격리, 최대값 있는 지수 backoff(`:367` 이하).
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRelayServiceTest.java:448`, `:530`, `:609`: initial backoff, capped exponential backoff, 최대 시도 실패.

## DF-E10. Broker 확인

- `src/main/java/site/yesaido/ai_server/rabbitmq/AiNotificationProducer.java:81` 부근: `sendDailyFeedbackConfirmed`.
- 동일 메서드: `CorrelationData(publicationAttemptId)`→convertAndSend→Confirm future 제한시간 대기→returned 검사→ACK 검사.
- `src/main/resources/application.yml:73`: correlated confirm; `:74` returns; `:76` mandatory. 해당 연결 정보는 근거 문서에 복사하지 않음.
- `src/main/java/site/yesaido/ai_server/config/RabbitMqConfig.java:21`: Boot 자동 Template 구성 활용 주석. 설정이 적용되는 실제 배포 revision은 이번에 확인하지 않음.
- `src/test/java/site/yesaido/ai_server/rabbitmq/AiNotificationProducerTest.java:58`, `:191`, `:241`, `:293`, `:330`, `:377`: ACK, NACK, 반환, timeout, future 오류, interrupt.

## DF-E11. 발행 후 DB 실패와 복구

- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRelayService.java:22` 이하: at-least-once, DB/Broker 사이 분산 트랜잭션 없음, PUBLISHED 저장 실패 시 SENDING 유지.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRecoveryService.java:18` 이하: stale SENDING을 PENDING/FAILED로 복구; 재발행 가능성 설명.
- `src/main/java/site/yesaido/ai_server/rabbitmq/event/DailyFeedbackGeneratedEventFactory.java:107`: namespace+재배지+날짜로 eventId 원문 생성; `:109` UUID.nameUUIDFromBytes; `:111` 현재 feedbackUrl은 **상대 경로**.
- `src/main/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRelayService.java:209`: outboxId:eventId:attemptCount 형태 시도 식별자.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRelayServiceTest.java:684`: 발행 성공 뒤 상태 저장 실패 사례.
- `src/test/java/site/yesaido/ai_server/service/DailyFeedbackOutboxRecoveryServiceTest.java`: 복구 조건 테스트 존재.
- 과거 대화의 절대 URL과 현재 상대 경로는 같은 형식이라고 가정하지 않는다. Notification/프론트 측 최종 URL 결합은 이 조사에서 재확인하지 않았다.

## DF-E12. Vision 피드백 내용 개선 제안의 상태

- 사용자 전달: 팀원이 일일 피드백의 Vision 설명이 부실하다고 평가함.
- 실제 문제 문장·해당 context_snapshot·대표 사진은 이 피드백과 함께 제공되지 않았다.
- 현재 소스에서 원본 analysisData 유지→정제 후 LLM Context 전달 확인. 모델명/좌표 제거와 상태/수/점수/경고 보존은 ‘결과를 한 줄로 축약해 버리는 중간 로직’과 다르다.
- 근거·의미·다음 행동 구조, 분석 사진/영역 표시, 상태별 설명과 고정 품질 평가 사례는 **제안**이며 이 작업에서 구현·효과 검증하지 않았다.
- 모델 지원 범위와 데이터 한계의 원본 근거는 별도 Vision 경험 문서를 우선한다.

## DF-H01. 대화에서만 확인한 2026-09-04 통합 기록

이 항목은 사용자가 붙여 넣은 로그·스크린샷을 다시 정리한 것이다. 현재 운영 환경을 재검증한 결과가 아니다. 원본에는 접속 정보 등이 포함됐으므로 여기에는 안전한 집계와 단계만 기록한다.

| 관찰 | 확인 가능한 사실 | 확인 불가능한 확대 해석 |
|---|---|---|
| 권한 오류와 OWNER 내부 조회 | 당시 대상 재배의 접근 거부 기록 및 소유자 사용자로 상세 조회 성공 | 모든 권한 오류의 원인·브라우저 로그인 주체 확정 |
| Cultivation OutOfMemoryError 및 재시작 | Java heap space 로그, 재시작 후 rollout 성공 기록 | 메모리 누수 원인 확정·영구 해결 |
| 수동 Job 응답 | feedbackDate 2026-09-03, target 21, created 11, existing 10, failed 0 | 장기 성공률 100%, 모든 대상 Vision 분석 성공 |
| Job 상태 | Complete 1/1, duration 2m30s 기록 | 평균 처리시간 또는 모델 한 장 추론시간 |
| Outbox 조회 이미지 | 신규 11행 PUBLISHED, 시도 횟수 1, 발행 시각 기록 | 정확히 한 번 전체 전달 보장 |
| Notification 조회 이미지 | 대응 source event의 Notification 11행 존재 | 모두 사용자 외부 채널에 도착 |
| delivery 조회 이미지 | 한 Telegram SENT, provider message ID 존재; 나머지 결과 null | 모든 null이 발송 실패 또는 구독 없음이라고 확정 |
| 사용자 설명 | 본인은 Telegram 구독자가 아니었음 | 다른 사용자 구독·수신 상태 확정 |
| 프론트 403/404·터널 오류 | 당시 접근 경로에서 각 오류가 관찰됨 | 현재 배포 정상/장애를 단정하거나 백엔드 생성 실패로 동일시 |

원본 이미지 파일이 이동·삭제될 수 있으므로, 향후 외부 제출용 포트폴리오에는 비밀값을 가린 별도 증거 캡처를 추가한다. 이 문서에는 토큰·비밀번호·내부 IP·서명 URL을 넣지 않았다.
