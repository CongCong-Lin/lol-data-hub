package com.loldatahub.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PublicCatalogPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PublicCatalogConfiguration.class)
            .withPropertyValues(
                    "lol-datahub.catalog.visible-events[0].season-id=237",
                    "lol-datahub.catalog.visible-events[0].stage-ids[0]=114",
                    "lol-datahub.catalog.stage-name-overrides[237-114]=第三赛段淘汰赛"
            );

    @Test
    void bindsStageNameOverrideWithCompositeStageKey() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            PublicCatalogProperties properties = context.getBean(PublicCatalogProperties.class);
            assertThat(properties.stageNameOverrides())
                    .containsEntry("237-114", "第三赛段淘汰赛");
            assertThat(properties.displayStageName(237, 114, "2026赛季季后赛"))
                    .isEqualTo("第三赛段淘汰赛");
        });
    }
}
