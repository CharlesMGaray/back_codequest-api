package com.codequest.api.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurriculumDataTest {

    @Test
    void cadaTrilhaTemDezAQuinzeQuestoesDeMultiplaEscolha() {
        var trilhas = DataSeeder.curriculum();

        assertEquals(6, trilhas.size());
        for (var trilha : trilhas) {
            int totalQuestoes = trilha.licoes().stream()
                    .mapToInt(licao -> licao.perguntas().size())
                    .sum();

            assertTrue(totalQuestoes >= 10 && totalQuestoes <= 15,
                    () -> trilha.titulo() + " tem " + totalQuestoes + " questoes");
            assertTrue(!trilha.licoes().isEmpty(), trilha.titulo() + " precisa ter licoes");

            for (var licao : trilha.licoes()) {
                for (var pergunta : licao.perguntas()) {
                    assertEquals(4, pergunta.opcoes().size(), pergunta.enunciado());
                    assertEquals(1, pergunta.opcoes().stream().filter(DataSeeder.OpcaoSeed::correta).count(),
                            pergunta.enunciado());
                    var seeded = DataSeeder.toMap(pergunta, licao.id(), 1);
                    assertTrue(((String) seeded.get("dica1")).length() > 0, pergunta.enunciado());
                    assertNotEquals(seeded.get("dica1"), seeded.get("dica2"),
                            pergunta.enunciado());
                    assertTrue(((String) seeded.get("explicacao")).contains(
                                    pergunta.opcoes().stream().filter(DataSeeder.OpcaoSeed::correta)
                                            .findFirst().orElseThrow().texto()),
                            pergunta.enunciado());
                }
            }
        }
    }
}
