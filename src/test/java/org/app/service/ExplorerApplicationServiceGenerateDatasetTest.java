package org.app.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerApplicationServiceGenerateDatasetTest {

    @Test
    void extractScriptFromResources_whenMissing_throwsClearMessage() {
        ExplorerApplicationService service = new ExplorerApplicationService();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.extractScriptFromResources("missing_embedder.py")
        );

        assertTrue(ex.getMessage().contains("Missing Python resource"));
    }
}
