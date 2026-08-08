package com.loldatahub.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TjStatsResponseParserTest {
    private final TjStatsResponseParser parser = new TjStatsResponseParser(new ObjectMapper());

    @Test
    void parsesAdditiveHeroCountersUsedForCrossStageAggregation() {
        String json = """
                {
                  "success": true,
                  "data": {
                    "boCount": 20,
                    "updatedAt": 1748345653,
                    "gameVersion": ["15.10"],
                    "list": [{
                      "heroId": 1,
                      "heroCnName": "安妮",
                      "pickCount": 5,
                      "banCount": 3,
                      "bpCount": 8,
                      "winningCount": 3,
                      "totalKills": 10,
                      "totalDeath": 8,
                      "totalAssists": 20
                    }]
                  }
                }
                """;

        var payload = parser.parseHeroStage(json);

        assertThat(payload.sampleBaseCount()).isEqualTo(20);
        assertThat(payload.heroes()).singleElement().satisfies(hero -> {
            assertThat(hero.pickCount()).isEqualTo(5);
            assertThat(hero.banCount()).isEqualTo(3);
            assertThat(hero.winningCount()).isEqualTo(3);
        });
    }

    @Test
    void parsesTeamStageAsArray() {
        String json = """
                {
                  "success": true,
                  "data": [{
                    "teamId": 100,
                    "teamName": "TES",
                    "teamLogo": "https://example.com/tes.png",
                    "matchCount": 20,
                    "matchWinCount": 15,
                    "winningRate": 0.75,
                    "totalKills": 300,
                    "killPerGameTeam": 15.0,
                    "totalDeath": 200,
                    "deathPerGameTeam": 10.0,
                    "wardPlacedPerGameTeam": 50.5,
                    "wardKilledPerGameTeam": 20.3,
                    "goldPerGameTeam": 60000.0,
                    "baronKillPerGameTeam": 1.2,
                    "drakeKillPerGameTeam": 2.5
                  }]
                }
                """;

        var teams = parser.parseTeamStage(json);

        assertThat(teams).singleElement().satisfies(team -> {
            assertThat(team.teamId()).isEqualTo(100);
            assertThat(team.teamName()).isEqualTo("TES");
            assertThat(team.matchCount()).isEqualTo(20);
            assertThat(team.matchWinCount()).isEqualTo(15);
            assertThat(team.totalKills()).isEqualTo(300);
            assertThat(team.wardPlacedPerGameTeam()).isEqualByComparingTo("50.5");
        });
    }
}
