package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamCombinationStatisticsQuery;
import com.loldatahub.domain.statistics.TeamCombinationType;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamCombinationStatisticsMapper;
import com.loldatahub.infrastructure.model.TeamCombinationAggregateRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamCombinationStatisticsServiceTest {
    private TeamCombinationStatisticsMapper mapper;
    private SystemStateMapper stateMapper;
    private TeamCombinationStatisticsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TeamCombinationStatisticsMapper.class);
        stateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        service = new TeamCombinationStatisticsService(
                mapper, stateMapper, redis, new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void calculatesTeamSpecificPickAndWinningRates() {
        List<StageKey> stages = List.of(new StageKey(237, 102), new StageKey(239, 28));
        when(mapper.findCollectedStageKeys(stages)).thenReturn(stages);
        when(stateMapper.currentDataVersion()).thenReturn(12L);
        when(mapper.aggregate(stages, "JUN", "MID", 2)).thenReturn(List.of(
                row(1, "TES", 62, "孙悟空", 84, "阿卡丽", 4, 10, 3),
                row(1, "TES", 5, "赵信", 61, "奥莉安娜", 2, 10, 2)
        ));

        var result = service.query(new TeamCombinationStatisticsQuery(
                stages, TeamCombinationType.MID_JUNGLE, 2, "winningRate", SortDirection.DESC));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items().getFirst().firstChampionName()).isEqualTo("赵信");
        assertThat(result.items().getFirst().pickRate()).isEqualByComparingTo("0.200000");
        assertThat(result.items().getFirst().winningRate()).isEqualByComparingTo("1.000000");
        assertThat(result.items().get(1).pickRate()).isEqualByComparingTo("0.400000");
        assertThat(result.items().get(1).winningRate()).isEqualByComparingTo("0.750000");
        verify(mapper).aggregate(stages, "JUN", "MID", 2);
    }

    @Test
    void rejectsStageWithoutNormalizedLineups() {
        List<StageKey> stages = List.of(new StageKey(237, 102), new StageKey(239, 28));
        when(mapper.findCollectedStageKeys(stages)).thenReturn(List.of(new StageKey(237, 102)));

        assertThatThrownBy(() -> service.query(new TeamCombinationStatisticsQuery(
                stages, TeamCombinationType.BOT_SUPPORT, 1, "pickCount", SortDirection.DESC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("239:28");
    }

    private static TeamCombinationAggregateRow row(long teamId, String teamName,
                                                    long firstId, String firstName,
                                                    long secondId, String secondName,
                                                    long picks, long games, long wins) {
        return new TeamCombinationAggregateRow(
                teamId, teamName, null, firstId, firstName, null, null,
                secondId, secondName, null, null, picks, games, wins);
    }
}
