package com.codequest.api.trilha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveMasteryTest {

    @Test
    void acertosConsistentesElevamDominioEUmErroReduz() {
        int dominio = 50;
        dominio = AdaptiveMastery.update(dominio, true);
        assertEquals(65, dominio);
        dominio = AdaptiveMastery.update(dominio, true);
        assertEquals(76, dominio);
        dominio = AdaptiveMastery.update(dominio, true);
        assertEquals(83, dominio);

        assertEquals(58, AdaptiveMastery.update(dominio, false));
    }

    @Test
    void classificaDominioEDefineRevisaoEspacada() {
        assertEquals("REVISAR", AdaptiveMastery.level(59));
        assertEquals("EM_DESENVOLVIMENTO", AdaptiveMastery.level(60));
        assertEquals("DOMINADA", AdaptiveMastery.level(80));

        assertEquals(1, AdaptiveMastery.reviewIntervalDays(59));
        assertEquals(3, AdaptiveMastery.reviewIntervalDays(60));
        assertEquals(7, AdaptiveMastery.reviewIntervalDays(80));
    }
}
