package com.loldatahub.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
