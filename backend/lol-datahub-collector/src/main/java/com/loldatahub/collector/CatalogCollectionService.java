package com.loldatahub.collector;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.domain.catalog.Stage;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogCollectionService {
    private final TjStatsClient client;
    private final TjStatsResponseParser parser;
    private final CatalogMapper catalogMapper;

    public CatalogCollectionService(TjStatsClient client,
                                    TjStatsResponseParser parser,
                                    CatalogMapper catalogMapper) {
        this.client = client;
        this.parser = parser;
        this.catalogMapper = catalogMapper;
    }

    @Transactional
    public CatalogSyncResult sync(Long selectedSeasonId) {
        var seasons = parser.parseSeasons(client.fetchSeasons());
        seasons.forEach(item -> catalogMapper.upsertSeason(new Season(
                item.seasonId(), item.seasonName(), SourceTimeParser.parse(item.startTime()),
                SourceTimeParser.parse(item.endTime()), item.openStatus()
        )));

        List<Long> targetSeasonIds = selectedSeasonId == null
                ? seasons.stream().filter(item -> item.openStatus()).map(item -> item.seasonId()).toList()
                : List.of(selectedSeasonId);
        int stageCount = 0;
        for (Long seasonId : targetSeasonIds) {
            var seasonStages = parser.parseStages(client.fetchStages(seasonId), seasonId);
            if (seasons.stream().noneMatch(item -> item.seasonId() == seasonId)) {
                catalogMapper.upsertSeason(new Season(seasonId, seasonStages.seasonName(), null, null, false));
            }
            for (var item : seasonStages.stageInfos()) {
                catalogMapper.upsertStage(new Stage(
                        seasonId, item.stageId(), item.stageName(), SourceTimeParser.parse(item.startTime()),
                        SourceTimeParser.parse(item.endTime())
                ));
                stageCount++;
            }
        }
        return new CatalogSyncResult(seasons.size(), stageCount, targetSeasonIds);
    }

    public record CatalogSyncResult(int seasonCount, int stageCount, List<Long> syncedSeasonIds) {
    }
}
