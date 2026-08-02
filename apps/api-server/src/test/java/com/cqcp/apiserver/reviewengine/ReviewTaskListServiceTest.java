package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReviewTaskListServiceTest {

    private final ReviewTaskListRepository repository =
            Mockito.mock(ReviewTaskListRepository.class);
    private final ReviewTaskListService service = new ReviewTaskListService(repository);

    @Test
    void normalizesAndEscapesSearchWithoutSqlConcatenation() {
        service.getTasks(2, 20, "completed", "  合同%_\\  ");

        verify(repository).findPage(
                eq(2),
                eq(20),
                eq(ReviewTaskListModels.StatusGroup.COMPLETED),
                eq("%合同\\%\\_\\\\%"));
    }

    @Test
    void rejectsInvalidPageSizeAndStatusGroup() {
        assertThatThrownBy(() -> service.getTasks(-1, 20, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getTasks(0, 101, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getTasks(0, 20, "SUCCESS", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPaginationWhoseOffsetWouldOverflow() {
        assertThatThrownBy(() -> service.getTasks(Integer.MAX_VALUE, 100, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超出支持范围");

        verifyNoInteractions(repository);
    }
}
