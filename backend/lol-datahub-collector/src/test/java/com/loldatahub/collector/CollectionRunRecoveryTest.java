package com.loldatahub.collector;

import com.loldatahub.infrastructure.mapper.CollectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CollectionRunRecoveryTest {

    private CollectionMapper collectionMapper;
    private CollectionRunRecovery recovery;

    @BeforeEach
    void setUp() {
        collectionMapper = mock(CollectionMapper.class);
        recovery = new CollectionRunRecovery(collectionMapper);
    }

    @Test
    void recoverStaleRuns_mapperCalled() {
        when(collectionMapper.recoverStaleRunningRuns()).thenReturn(3);

        recovery.recoverStaleRuns();

        verify(collectionMapper).recoverStaleRunningRuns();
    }

    @Test
    void recoverStaleRuns_zeroRecovered_mapperStillCalled() {
        when(collectionMapper.recoverStaleRunningRuns()).thenReturn(0);

        recovery.recoverStaleRuns();

        verify(collectionMapper).recoverStaleRunningRuns();
    }

    @Test
    void recoverStaleRuns_mapperThrows_exceptionPropagates() {
        when(collectionMapper.recoverStaleRunningRuns()).thenThrow(new RuntimeException("DB 连接失败"));

        assertThatThrownBy(() -> recovery.recoverStaleRuns())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 连接失败");

        verify(collectionMapper).recoverStaleRunningRuns();
    }
}
