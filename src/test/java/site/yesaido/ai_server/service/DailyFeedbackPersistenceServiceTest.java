package site.yesaido.ai_server.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.ai_server.entity.DailyFeedback;
import site.yesaido.ai_server.repository.DailyFeedbackRepository;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceResult;
import site.yesaido.ai_server.service.DailyFeedbackPersistenceService.PersistenceStatus;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFeedbackPersistenceServiceTest {

    private static final Long CULTIVATION_ID = 10L;
    private static final LocalDate FEEDBACK_DATE =
            LocalDate.of(2026, 9, 1);

    @Mock
    private DailyFeedbackRepository dailyFeedbackRepository;

    private DailyFeedbackPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new DailyFeedbackPersistenceService(
                dailyFeedbackRepository
        );
    }

    @Test
    @DisplayName("동일한 경작지와 날짜의 기존 피드백을 조회한다")
    void findsExistingFeedback() {
        DailyFeedback existing =
                feedback("기존 일일 피드백입니다.");

        when(
                dailyFeedbackRepository
                        .findByCultivationIdAndFeedbackDate(
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
        ).thenReturn(Optional.of(existing));

        Optional<DailyFeedback> actual =
                service.findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        assertThat(actual)
                .containsSame(existing);

        verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
    }

    @Test
    @DisplayName("저장된 피드백이 없으면 빈 Optional을 반환한다")
    void returnsEmptyWhenFeedbackDoesNotExist() {
        when(
                dailyFeedbackRepository
                        .findByCultivationIdAndFeedbackDate(
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
        ).thenReturn(Optional.empty());

        Optional<DailyFeedback> actual =
                service.findExisting(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        assertThat(actual).isEmpty();

        verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );
    }

    @Test
    @DisplayName("조회할 cultivationId가 null이면 Repository를 호출하지 않는다")
    void rejectsNullCultivationIdWhenFinding() {
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> service.findExisting(
                                null,
                                FEEDBACK_DATE
                        )
                );

        assertThat(exception)
                .hasMessage(
                        "cultivationId는 null이 아니며 0보다 커야 합니다."
                );

        verifyNoInteractions(dailyFeedbackRepository);
    }

    @Test
    @DisplayName("조회할 cultivationId가 0 이하이면 Repository를 호출하지 않는다")
    void rejectsNonPositiveCultivationIdWhenFinding() {
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> service.findExisting(
                                0L,
                                FEEDBACK_DATE
                        )
                );

        assertThat(exception)
                .hasMessage(
                        "cultivationId는 null이 아니며 0보다 커야 합니다."
                );

        verifyNoInteractions(dailyFeedbackRepository);
    }

    @Test
    @DisplayName("조회할 feedbackDate가 null이면 Repository를 호출하지 않는다")
    void rejectsNullFeedbackDateWhenFinding() {
        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> service.findExisting(
                                CULTIVATION_ID,
                                null
                        )
                );

        assertThat(exception)
                .hasMessage(
                        "feedbackDate는 null일 수 없습니다."
                );

        verifyNoInteractions(dailyFeedbackRepository);
    }

    @Test
    @DisplayName("저장 후보가 null이면 Repository를 호출하지 않는다")
    void rejectsNullCandidate() {
        NullPointerException exception =
                catchThrowableOfType(
                        NullPointerException.class,
                        () -> service.saveOrGet(null)
                );

        assertThat(exception)
                .hasMessage(
                        "candidate는 null일 수 없습니다."
                );

        verifyNoInteractions(dailyFeedbackRepository);
    }

    @Test
    @DisplayName("이미 ID가 있는 엔티티는 신규 저장 후보로 받지 않는다")
    void rejectsCandidateThatAlreadyHasId() {
        DailyFeedback persistedCandidate =
                mock(DailyFeedback.class);

        when(persistedCandidate.getId())
                .thenReturn(100L);

        IllegalArgumentException exception =
                catchThrowableOfType(
                        IllegalArgumentException.class,
                        () -> service.saveOrGet(
                                persistedCandidate
                        )
                );

        assertThat(exception)
                .hasMessage(
                        "candidate는 아직 저장되지 않은 신규 엔티티여야 합니다."
                );

        verifyNoInteractions(dailyFeedbackRepository);
    }

    @Test
    @DisplayName("동일 키의 기존 피드백이 있으면 저장하지 않고 EXISTING으로 반환한다")
    void returnsExistingFeedbackWithoutSaving() {
        DailyFeedback candidate =
                feedback("새로 생성했지만 저장되지 않아야 하는 내용");

        DailyFeedback existing =
                feedback("DB에 먼저 저장되어 있던 내용");

        when(
                dailyFeedbackRepository
                        .findByCultivationIdAndFeedbackDate(
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
        ).thenReturn(Optional.of(existing));

        PersistenceResult actual =
                service.saveOrGet(candidate);

        assertThat(actual.feedback())
                .isSameAs(existing);

        assertThat(actual.status())
                .isEqualTo(PersistenceStatus.EXISTING);

        verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        verify(
                dailyFeedbackRepository,
                never()
        ).saveAndFlush(candidate);
    }

    @Test
    @DisplayName("기존 피드백이 없으면 saveAndFlush로 저장하고 CREATED로 반환한다")
    void savesNewFeedback() {
        DailyFeedback candidate =
                feedback("새로 생성한 일일 피드백");

        DailyFeedback saved =
                feedback("DB가 반환한 일일 피드백");

        when(
                dailyFeedbackRepository
                        .findByCultivationIdAndFeedbackDate(
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
        ).thenReturn(Optional.empty());

        when(
                dailyFeedbackRepository
                        .saveAndFlush(candidate)
        ).thenReturn(saved);

        PersistenceResult actual =
                service.saveOrGet(candidate);

        assertThat(actual.feedback())
                .isSameAs(saved);

        assertThat(actual.status())
                .isEqualTo(PersistenceStatus.CREATED);

        InOrder orderedCalls =
                inOrder(dailyFeedbackRepository);

        orderedCalls.verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        orderedCalls.verify(dailyFeedbackRepository)
                .saveAndFlush(candidate);

        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("동시 저장 UNIQUE 충돌 후 기존 행을 찾아 EXISTING으로 반환한다")
    void recoversExistingFeedbackAfterConcurrentInsert() {
        DailyFeedback candidate =
                feedback("경합에서 저장되지 않은 후보");

        DailyFeedback concurrentlySaved =
                feedback("다른 Pod가 먼저 저장한 피드백");

        DataIntegrityViolationException conflict =
                new DataIntegrityViolationException(
                        "unique constraint violation"
                );

        when(
                dailyFeedbackRepository
                        .findByCultivationIdAndFeedbackDate(
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
        ).thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentlySaved));

        when(
                dailyFeedbackRepository
                        .saveAndFlush(candidate)
        ).thenThrow(conflict);

        PersistenceResult actual =
                service.saveOrGet(candidate);

        assertThat(actual.feedback())
                .isSameAs(concurrentlySaved);

        assertThat(actual.status())
                .isEqualTo(PersistenceStatus.EXISTING);

        InOrder orderedCalls =
                inOrder(dailyFeedbackRepository);

        orderedCalls.verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        orderedCalls.verify(dailyFeedbackRepository)
                .saveAndFlush(candidate);

        orderedCalls.verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        orderedCalls.verifyNoMoreInteractions();

        verify(
                dailyFeedbackRepository,
                times(2)
        ).findByCultivationIdAndFeedbackDate(
                CULTIVATION_ID,
                FEEDBACK_DATE
        );
    }

    @Test
    @DisplayName("DB 무결성 오류 후 동일 키 행이 없으면 원래 예외를 다시 던진다")
    void rethrowsUnrelatedDatabaseIntegrityFailure() {
        DailyFeedback candidate =
                feedback("저장 실패 후보");

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        "unrelated database constraint failure"
                );

        when(
                dailyFeedbackRepository
                        .findByCultivationIdAndFeedbackDate(
                                CULTIVATION_ID,
                                FEEDBACK_DATE
                        )
        ).thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        when(
                dailyFeedbackRepository
                        .saveAndFlush(candidate)
        ).thenThrow(databaseFailure);

        DataIntegrityViolationException actual =
                catchThrowableOfType(
                        DataIntegrityViolationException.class,
                        () -> service.saveOrGet(candidate)
                );

        assertThat(actual)
                .isSameAs(databaseFailure);

        InOrder orderedCalls =
                inOrder(dailyFeedbackRepository);

        orderedCalls.verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        orderedCalls.verify(dailyFeedbackRepository)
                .saveAndFlush(candidate);

        orderedCalls.verify(dailyFeedbackRepository)
                .findByCultivationIdAndFeedbackDate(
                        CULTIVATION_ID,
                        FEEDBACK_DATE
                );

        orderedCalls.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("저장 서비스는 외부 트랜잭션에 참여하지 않는 NOT_SUPPORTED 정책을 사용한다")
    void declaresNotSupportedTransactionPropagation() {
        Transactional transactional =
                DailyFeedbackPersistenceService.class
                        .getAnnotation(Transactional.class);

        assertThat(transactional)
                .isNotNull();

        assertThat(transactional.propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
    }

    private DailyFeedback feedback(String content) {
        return DailyFeedback.builder()
                .cultivationId(CULTIVATION_ID)
                .feedbackDate(FEEDBACK_DATE)
                .hasVisionAnalysis(false)
                .content(content)
                .contextSnapshot(
                        JsonNodeFactory.instance
                                .objectNode()
                                .put(
                                        "cultivationId",
                                        CULTIVATION_ID
                                )
                                .put(
                                        "feedbackDate",
                                        FEEDBACK_DATE.toString()
                                )
                )
                .build();
    }
}
