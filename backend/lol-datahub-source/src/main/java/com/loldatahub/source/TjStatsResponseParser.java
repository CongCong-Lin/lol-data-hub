package com.loldatahub.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.HeroStatSourceRecord;
import com.loldatahub.source.model.SeasonSourceRecord;
import com.loldatahub.source.model.SeasonStagesSourceRecord;

import java.time.Instant;
import java.util.List;

public class TjStatsResponseParser {
    private final ObjectMapper objectMapper;

    public TjStatsResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SeasonSourceRecord> parseSeasons(String rawJson) {
        JsonNode data = validatedData(rawJson);
        return objectMapper.convertValue(
                data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, SeasonSourceRecord.class)
        );
    }

    public SeasonStagesSourceRecord parseStages(String rawJson) {
        return objectMapper.convertValue(validatedData(rawJson), SeasonStagesSourceRecord.class);
    }

    public HeroStagePayload parseHeroStage(String rawJson) {
        JsonNode data = validatedData(rawJson);
        List<HeroStatSourceRecord> heroes = objectMapper.convertValue(
                data.path("list"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, HeroStatSourceRecord.class)
        );
        long updatedAt = data.path("updatedAt").asLong(0L);
        JsonNode gameVersion = data.path("gameVersion");
        if (gameVersion.isMissingNode() || gameVersion.isNull()) {
            gameVersion = objectMapper.createArrayNode();
        }
        return new HeroStagePayload(
                data.path("boCount").asLong(),
                updatedAt > 0 ? Instant.ofEpochSecond(updatedAt) : null,
                gameVersion,
                heroes
        );
    }

    private JsonNode validatedData(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root.has("success") && !root.path("success").asBoolean()) {
                throw new TjStatsSourceException("赛事官网接口返回失败：" + root.path("message").asText("未知错误"));
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new TjStatsSourceException("赛事官网接口响应缺少 data 字段");
            }
            return data;
        } catch (JsonProcessingException exception) {
            throw new TjStatsSourceException("赛事官网接口返回了无效 JSON", exception);
        }
    }
}
