package com.loldatahub.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TjStatsResponseParserTest {
    private final TjStatsResponseParser parser = new TjStatsResponseParser(new ObjectMapper());

    // ══════════════════════════════════════════════════════════════════════
    // 合法最小响应
    // ══════════════════════════════════════════════════════════════════════

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
                    "gameCount": 55,
                    "matchWinCount": 15,
                    "winningRate": 0.75,
                    "totalKills": 300,
                    "totalDeath": 200,
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
            assertThat(team.gameCount()).isEqualTo(55);
            assertThat(team.matchWinCount()).isEqualTo(15);
            assertThat(team.totalKills()).isEqualTo(300);
            assertThat(team.wardPlacedPerGameTeam()).isEqualByComparingTo("50.5");
        });
    }

    @Test
    void parsesPlayerStageWithPlayerId() {
        String json = """
                {
                  "success": true,
                  "data": [{
                    "playerId": 12345,
                    "teamId": 100,
                    "playerName": "JackeyLove",
                    "playerAvatar": "https://example.com/jkl.png",
                    "playerLocation": "AD",
                    "teamName": "TES",
                    "teamLogo": "https://example.com/tes.png",
                    "matchCount": 20,
                    "boCount": 55,
                    "mvpCount": 5,
                    "mvpVotes": 100,
                    "kda": 6.5,
                    "totalKills": 150,
                    "totalAssists": 200,
                    "totalDeath": 50,
                    "goldPerGame": 15000.0,
                    "creepScorePerGame": 320.5,
                    "wardPlacedPerGame": 20.0,
                    "wardKilledPerGame": 10.0,
                    "killParticipantPercent": 0.65,
                    "goldGapPerGame": 2000.0,
                    "damagePercent": 0.30,
                    "goldPercent": 0.25
                  }]
                }
                """;

        var players = parser.parsePlayerStage(json);

        assertThat(players).singleElement().satisfies(player -> {
            assertThat(player.playerId()).isEqualTo(12345L);
            assertThat(player.playerName()).isEqualTo("JackeyLove");
            assertThat(player.matchCount()).isEqualTo(20);
            assertThat(player.boCount()).isEqualTo(55);
            assertThat(player.totalKills()).isEqualTo(150);
            assertThat(player.goldPerGame()).isEqualByComparingTo("15000.0");
        });
    }

    @Test
    void parsesPlayerStageWithoutPlayerId() {
        String json = """
                {
                  "success": true,
                  "data": [{
                    "playerName": "Rookie",
                    "playerLocation": "MID",
                    "teamName": "TES",
                    "matchCount": 15,
                    "boCount": 40,
                    "mvpCount": 3,
                    "mvpVotes": 50,
                    "totalKills": 100,
                    "totalAssists": 180,
                    "totalDeath": 40
                  }]
                }
                """;

        var players = parser.parsePlayerStage(json);

        assertThat(players).singleElement().satisfies(player -> {
            assertThat(player.playerId()).isNull();
            assertThat(player.playerName()).isEqualTo("Rookie");
            assertThat(player.matchCount()).isEqualTo(15);
            assertThat(player.goldPerGame()).isNull();
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 结构校验 - validatedData
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class StructuralValidation {
        @Test
        void rejectsNullRawJson() {
            assertThatThrownBy(() -> parser.parseHeroStage(null))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("空响应");
        }

        @Test
        void rejectsBlankRawJson() {
            assertThatThrownBy(() -> parser.parseTeamStage("   "))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("空响应");
        }

        @Test
        void rejectsEmptyJson() {
            assertThatThrownBy(() -> parser.parsePlayerStage(""))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("空响应");
        }

        @Test
        void rejectsInvalidJson() {
            assertThatThrownBy(() -> parser.parseHeroStage("{not json}"))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("无效 JSON");
        }

        @Test
        void rejectsNonObjectRoot() {
            assertThatThrownBy(() -> parser.parseTeamStage("[1,2,3]"))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("根节点必须是对象");
        }

        @Test
        void rejectsSuccessFalse() {
            String json = """
                    {"success": false, "message": "权限不足"}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("返回失败")
                    .hasMessageContaining("权限不足");
        }

        @Test
        void rejectsMissingData() {
            String json = """
                    {"success": true}
                    """;
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("缺少 data");
        }

        @Test
        void rejectsNullData() {
            String json = """
                    {"success": true, "data": null}
                    """;
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("缺少 data");
        }

        @Test
        void acceptsSuccessFieldAbsent() {
            // success 字段不存在时视为成功（兼容不返回 success 的接口）
            String json = """
                    {"data": [{"teamId": 1, "teamName": "T1", "matchCount": 10, "gameCount": 25, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80}]}
                    """;
            var teams = parser.parseTeamStage(json);
            assertThat(teams).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HERO 结构校验
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class HeroStructuralValidation {
        @Test
        void rejectsNonObjectData() {
            String json = """
                    {"success": true, "data": [1,2,3]}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("data 必须是对象");
        }

        @Test
        void rejectsMissingList() {
            String json = """
                    {"success": true, "data": {"boCount": 10}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("data.list 必须是非空数组");
        }

        @Test
        void rejectsEmptyList() {
            String json = """
                    {"success": true, "data": {"boCount": 10, "list": []}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("data.list 必须是非空数组");
        }

        @Test
        void rejectsNullList() {
            String json = """
                    {"success": true, "data": {"boCount": 10, "list": null}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("data.list 必须是非空数组");
        }

        @Test
        void rejectsZeroBoCount() {
            String json = """
                    {"success": true, "data": {"boCount": 0, "list": [{"heroId": 1, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}]}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("boCount 必须大于 0");
        }

        @Test
        void rejectsNegativeBoCount() {
            String json = """
                    {"success": true, "data": {"boCount": -5, "list": [{"heroId": 1, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}]}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("boCount 必须大于 0");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEAM/PLAYER 结构校验 - 空数组
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class EmptyArrayRejection {
        @Test
        void rejectsEmptyTeamArray() {
            String json = """
                    {"success": true, "data": []}
                    """;
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("data 必须是非空数组");
        }

        @Test
        void rejectsEmptyPlayerArray() {
            String json = """
                    {"success": true, "data": []}
                    """;
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("data 必须是非空数组");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HERO 业务校验
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class HeroBusinessValidation {
        private String heroJson(String heroFields) {
            return """
                    {"success": true, "data": {"boCount": 20, "list": [%s]}}
                    """.formatted(heroFields);
        }

        @Test
        void rejectsZeroHeroId() {
            String json = heroJson("""
                    {"heroId": 0, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("heroId 必须大于 0");
        }

        @Test
        void rejectsNegativeHeroId() {
            String json = heroJson("""
                    {"heroId": -1, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("heroId 必须大于 0");
        }

        @Test
        void rejectsBothNameFieldsEmpty() {
            // heroName 和 heroCnName 都缺失 -> record 中都是 null
            String json = heroJson("""
                    {"heroId": 1, "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("heroName 和 heroCnName 不能同时为空");
        }

        @Test
        void acceptsOnlyHeroName() {
            String json = heroJson("""
                    {"heroId": 1, "heroName": "Annie", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}""");
            var payload = parser.parseHeroStage(json);
            assertThat(payload.heroes()).singleElement().satisfies(h ->
                    assertThat(h.heroName()).isEqualTo("Annie"));
        }

        @Test
        void rejectsWinningCountExceedsPickCount() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 3, "banCount": 0, "bpCount": 3, "winningCount": 5, "totalKills": 10, "totalDeath": 5, "totalAssists": 10}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("winningCount")
                    .hasMessageContaining("不能超过 pickCount");
        }

        @Test
        void rejectsBpCountMismatch() {
            // bpCount != pickCount + banCount
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 3, "bpCount": 7, "winningCount": 3, "totalKills": 10, "totalDeath": 5, "totalAssists": 10}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("bpCount")
                    .hasMessageContaining("必须等于");
        }

        @Test
        void rejectsPickCountExceedsBoCount() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 25, "banCount": 0, "bpCount": 25, "winningCount": 10, "totalKills": 50, "totalDeath": 30, "totalAssists": 60}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("pickCount")
                    .hasMessageContaining("不能超过 boCount");
        }

        @Test
        void rejectsBanCountExceedsBoCount() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 0, "banCount": 25, "bpCount": 25, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("banCount")
                    .hasMessageContaining("不能超过 boCount");
        }

        @Test
        void rejectsBpCountExceedsBoCount() {
            // bpCount > boCount even though pickCount + banCount <= boCount individually
            // This can't happen if bpCount == pickCount + banCount, but test the check
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 12, "banCount": 10, "bpCount": 22, "winningCount": 5, "totalKills": 20, "totalDeath": 10, "totalAssists": 30}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("bpCount")
                    .hasMessageContaining("不能超过 boCount");
        }

        @Test
        void rejectsDuplicateHeroId() {
            String json = """
                    {"success": true, "data": {"boCount": 20, "list": [
                      {"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 3, "bpCount": 8, "winningCount": 3, "totalKills": 10, "totalDeath": 5, "totalAssists": 15},
                      {"heroId": 1, "heroCnName": "安妮2", "pickCount": 2, "banCount": 1, "bpCount": 3, "winningCount": 1, "totalKills": 5, "totalDeath": 3, "totalAssists": 8}
                    ]}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("heroId 重复")
                    .hasMessageContaining("1");
        }

        @Test
        void rejectsNegativePickCount() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": -1, "banCount": 0, "bpCount": -1, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("pickCount 不能为负数");
        }

        @Test
        void rejectsNegativeTotalKills() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": -1, "totalDeath": 0, "totalAssists": 0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("totalKills 不能为负数");
        }

        @Test
        void rejectsMissingPrimitiveCountField() {
            // 防止上游字段改名后被 Jackson 静默转换为 primitive 默认值 0。
            String json = """
                    {"success": true, "data": {"boCount": 20, "list": [
                      {"heroId": 1, "heroCnName": "安妮"}
                    ]}}
                    """;
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("关键字段 pickCount");
        }

        @Test
        void rejectsWinningRateAboveOne() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 0, "bpCount": 5, "winningCount": 3, "winningRate": 1.5, "totalKills": 10, "totalDeath": 5, "totalAssists": 15}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("winningRate")
                    .hasMessageContaining("0..1");
        }

        @Test
        void rejectsPickRateBelowZero() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 0, "bpCount": 5, "winningCount": 3, "pickRate": -0.1, "totalKills": 10, "totalDeath": 5, "totalAssists": 15}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("pickRate")
                    .hasMessageContaining("0..1");
        }

        @Test
        void acceptsNullPercentages() {
            // 百分比字段为 null 时不应拒绝
            String json = """
                    {"success": true, "data": {"boCount": 20, "list": [
                      {"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 3, "bpCount": 8, "winningCount": 3, "totalKills": 10, "totalDeath": 5, "totalAssists": 15}
                    ]}}
                    """;
            var payload = parser.parseHeroStage(json);
            assertThat(payload.heroes()).singleElement().satisfies(h -> {
                assertThat(h.winningRate()).isNull();
                assertThat(h.pickRate()).isNull();
                assertThat(h.banRate()).isNull();
                assertThat(h.bPRate()).isNull();
            });
        }

        @Test
        void rejectsNegativeKda() {
            String json = heroJson("""
                    {"heroId": 1, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0, "kDA": -1.0}""");
            assertThatThrownBy(() -> parser.parseHeroStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("kDA 不能为负数");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEAM 业务校验
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class TeamBusinessValidation {
        private String teamJson(String teamFields) {
            String enrichedFields = teamFields.replaceFirst("\\{", "{\"gameCount\": 1000,");
            return """
                    {"success": true, "data": [%s]}
                    """.formatted(enrichedFields);
        }

        @Test
        void rejectsZeroTeamId() {
            String json = teamJson("""
                    {"teamId": 0, "teamName": "T1", "matchCount": 10, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("teamId 必须大于 0");
        }

        @Test
        void rejectsEmptyTeamName() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "", "matchCount": 10, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("teamName 不能为空");
        }

        @Test
        void rejectsNullTeamName() {
            String json = teamJson("""
                    {"teamId": 1, "matchCount": 10, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("teamName 不能为空");
        }

        @Test
        void rejectsZeroMatchCount() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 0, "matchWinCount": 0, "totalKills": 0, "totalDeath": 0}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("matchCount 必须大于 0");
        }

        @Test
        void rejectsMatchWinCountExceedsMatchCount() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": 15, "totalKills": 100, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("matchWinCount")
                    .hasMessageContaining("0..matchCount");
        }

        @Test
        void rejectsNegativeMatchWinCount() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": -1, "totalKills": 100, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("matchWinCount")
                    .hasMessageContaining("0..matchCount");
        }

        @Test
        void rejectsDuplicateTeamId() {
            String json = """
                    {"success": true, "data": [
                      {"teamId": 1, "teamName": "T1", "matchCount": 10, "gameCount": 25, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80},
                      {"teamId": 1, "teamName": "T2", "matchCount": 8, "gameCount": 20, "matchWinCount": 4, "totalKills": 80, "totalDeath": 60}
                    ]}
                    """;
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("teamId 重复")
                    .hasMessageContaining("1");
        }

        @Test
        void rejectsNegativeTotalKills() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": 5, "totalKills": -1, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("totalKills 不能为负数");
        }

        @Test
        void rejectsWinningRateAboveOne() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": 5, "winningRate": 1.5, "totalKills": 100, "totalDeath": 80}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("winningRate")
                    .hasMessageContaining("0..1");
        }

        @Test
        void rejectsNegativeGoldPerGameTeam() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80, "goldPerGameTeam": -1.0}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("goldPerGameTeam 不能为负数");
        }

        @Test
        void acceptsPrimitiveDefaultsAsZero() {
            // 缺失的场均字段被 Jackson 反序列化为 null（BigDecimal 是包装类型），应通过
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": 5, "totalKills": 100, "totalDeath": 80}""");
            var teams = parser.parseTeamStage(json);
            assertThat(teams).singleElement().satisfies(t -> {
                assertThat(t.winningRate()).isNull();
                assertThat(t.goldPerGameTeam()).isNull();
            });
        }

        @Test
        void rejectsMissingPrimitiveCountField() {
            String json = teamJson("""
                    {"teamId": 1, "teamName": "T1", "matchCount": 10, "matchWinCount": 5, "totalKills": 100}""");
            assertThatThrownBy(() -> parser.parseTeamStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("关键字段 totalDeath");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PLAYER 业务校验
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class PlayerBusinessValidation {
        private String playerJson(String playerFields) {
            String enrichedFields = playerFields.replaceFirst("\\{", "{\"boCount\": 1000,");
            return """
                    {"success": true, "data": [%s]}
                    """.formatted(enrichedFields);
        }

        @Test
        void rejectsZeroPlayerId() {
            String json = playerJson("""
                    {"playerId": 0, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerId 若存在必须大于 0");
        }

        @Test
        void rejectsNegativePlayerId() {
            String json = playerJson("""
                    {"playerId": -1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerId 若存在必须大于 0");
        }

        @Test
        void acceptsNullPlayerId() {
            String json = playerJson("""
                    {"playerName": "Rookie", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            var players = parser.parsePlayerStage(json);
            assertThat(players).singleElement().satisfies(p ->
                    assertThat(p.playerId()).isNull());
        }

        @Test
        void rejectsEmptyPlayerName() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerName 不能为空");
        }

        @Test
        void rejectsNullPlayerName() {
            String json = playerJson("""
                    {"playerId": 1, "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerName 不能为空");
        }

        @Test
        void rejectsZeroMatchCount() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 0, "mvpCount": 0, "mvpVotes": 0, "totalKills": 0, "totalAssists": 0, "totalDeath": 0}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("matchCount 必须大于 0");
        }

        @Test
        void acceptsMvpCountExceedingMatchCount() {
            // mvpCount 允许大于 matchCount（官网数据可能跨赛事累计）
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 5, "mvpCount": 10, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            var players = parser.parsePlayerStage(json);
            assertThat(players).singleElement().satisfies(p ->
                    assertThat(p.mvpCount()).isEqualTo(10));
        }

        @Test
        void rejectsInvalidPlayerLocation() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "playerLocation": "ADC", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerLocation 必须属于 TOP/JUG/MID/AD/SUP")
                    .hasMessageContaining("ADC");
        }

        @Test
        void rejectsInvalidPlayerLocationUnknown() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "playerLocation": "SUPPORT", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerLocation 必须属于 TOP/JUG/MID/AD/SUP");
        }

        @Test
        void acceptsValidPlayerLocations() {
            for (String loc : new String[]{"TOP", "JUG", "MID", "AD", "SUP"}) {
                String json = playerJson("""
                        {"playerId": 1, "playerName": "Player_%s", "playerLocation": "%s", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}"""
                        .formatted(loc, loc));
                var players = parser.parsePlayerStage(json);
                assertThat(players).singleElement().satisfies(p ->
                        assertThat(p.playerLocation()).isEqualTo(loc));
            }
        }

        @Test
        void acceptsNullPlayerLocation() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            var players = parser.parsePlayerStage(json);
            assertThat(players).singleElement().satisfies(p ->
                    assertThat(p.playerLocation()).isNull());
        }

        @Test
        void acceptsBlankPlayerLocation() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "playerLocation": "", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            var players = parser.parsePlayerStage(json);
            assertThat(players).singleElement().satisfies(p ->
                    assertThat(p.playerLocation()).isEmpty());
        }

        @Test
        void rejectsDuplicateIdentityByPlayerId() {
            String json = """
                    {"success": true, "data": [
                      {"playerId": 100, "playerName": "PlayerA", "matchCount": 10, "boCount": 25, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30},
                      {"playerId": 100, "playerName": "PlayerB", "matchCount": 8, "boCount": 20, "mvpCount": 1, "mvpVotes": 30, "totalKills": 60, "totalAssists": 80, "totalDeath": 25}
                    ]}
                    """;
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("选手身份重复")
                    .hasMessageContaining("id:100");
        }

        @Test
        void rejectsDuplicateIdentityByPlayerName() {
            String json = """
                    {"success": true, "data": [
                      {"playerName": "Rookie", "matchCount": 10, "boCount": 25, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30},
                      {"playerName": "rookie", "matchCount": 8, "boCount": 20, "mvpCount": 1, "mvpVotes": 30, "totalKills": 60, "totalAssists": 80, "totalDeath": 25}
                    ]}
                    """;
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("选手身份重复")
                    .hasMessageContaining("name:rookie");
        }

        @Test
        void rejectsPlayerNameEmptyAndPlayerIdInvalid() {
            // playerId 为 0（无效）且 playerName 为空 -> resolvePlayerIdentity 失败
            String json = playerJson("""
                    {"playerId": 0, "playerName": "", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("playerId 若存在必须大于 0");
        }

        @Test
        void rejectsNegativeTotalAssists() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": -1, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("totalAssists 不能为负数");
        }

        @Test
        void rejectsKillParticipantPercentAboveOne() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30, "killParticipantPercent": 1.5}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("killParticipantPercent")
                    .hasMessageContaining("0..1");
        }

        @Test
        void rejectsDamagePercentBelowZero() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30, "damagePercent": -0.1}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("damagePercent")
                    .hasMessageContaining("0..1");
        }

        @Test
        void acceptsGoldGapPerGameNegative() {
            // goldGapPerGame 允许负数
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30, "goldGapPerGame": -500.0}""");
            var players = parser.parsePlayerStage(json);
            assertThat(players).singleElement().satisfies(p ->
                    assertThat(p.goldGapPerGame()).isEqualByComparingTo("-500.0"));
        }

        @Test
        void rejectsNegativeGoldPerGame() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30, "goldPerGame": -1.0}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("goldPerGame 不能为负数");
        }

        @Test
        void rejectsNegativeKda() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30, "kda": -1.0}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("kda 不能为负数");
        }

        @Test
        void acceptsNullPercentages() {
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2, "mvpVotes": 50, "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            var players = parser.parsePlayerStage(json);
            assertThat(players).singleElement().satisfies(p -> {
                assertThat(p.killParticipantPercent()).isNull();
                assertThat(p.damagePercent()).isNull();
                assertThat(p.goldPercent()).isNull();
                assertThat(p.goldGapPerGame()).isNull();
            });
        }

        @Test
        void rejectsMissingMvpVotes() {
            // 该字段缺失会让未知值被写成 0，必须在发布前拒绝。
            String json = playerJson("""
                    {"playerId": 1, "playerName": "JackeyLove", "matchCount": 10, "mvpCount": 2,
                     "totalKills": 80, "totalAssists": 100, "totalDeath": 30}""");
            assertThatThrownBy(() -> parser.parsePlayerStage(json))
                    .isInstanceOf(TjStatsSourceException.class)
                    .hasMessageContaining("关键字段 mvpVotes");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 不含 Authorization 信息
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void exceptionMessagesDoNotContainAuthorization() {
        // 确保校验异常消息不泄露 Authorization
        String[] testCases = {
                "",
                "null",
                "{}",
                "[1,2,3]",
                """
                {"success": false}
                """,
                """
                {"success": true}
                """,
                """
                {"success": true, "data": []}
                """,
                """
                {"success": true, "data": {"boCount": 0, "list": []}}
                """,
                """
                {"success": true, "data": [{"teamId": 0, "teamName": "T1", "matchCount": 1, "matchWinCount": 0, "totalKills": 0, "totalDeath": 0}]}
                """,
                """
                {"success": true, "data": [{"playerName": "", "matchCount": 1, "mvpCount": 0, "mvpVotes": 0, "totalKills": 0, "totalAssists": 0, "totalDeath": 0}]}
                """
        };
        for (String json : testCases) {
            try {
                parser.parseHeroStage(json);
            } catch (TjStatsSourceException e) {
                assertThat(e.getMessage()).doesNotContain("Authorization");
            }
            try {
                parser.parseTeamStage(json);
            } catch (TjStatsSourceException e) {
                assertThat(e.getMessage()).doesNotContain("Authorization");
            }
            try {
                parser.parsePlayerStage(json);
            } catch (TjStatsSourceException e) {
                assertThat(e.getMessage()).doesNotContain("Authorization");
            }
        }
    }
}
