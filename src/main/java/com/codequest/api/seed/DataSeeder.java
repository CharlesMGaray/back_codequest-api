package com.codequest.api.seed;

import com.codequest.api.trilha.TrilhaService;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.codequest.api.common.Futures.await;

/** Popula trilhas e licoes de exemplo quando a colecao "trilhas" esta vazia. */
@Component
@ConditionalOnProperty(name = "codequest.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final Firestore db;

    public DataSeeder(Firestore db) {
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!await(db.collection(TrilhaService.TRILHAS).limit(1).get()).isEmpty()) {
            return;
        }

        WriteBatch batch = db.batch();

        trilha(batch, "logica", 1, "Logica de Programacao",
                "Fundamentos para pensar como programador: variaveis, condicoes e repeticoes.");
        licao(batch, "logica-1", "logica", 1, "Variaveis e tipos",
                "Como guardar valores na memoria e quais tipos de dados existem.", 50, List.of(
                        pergunta("p1", "Qual tipo representa verdadeiro ou falso?",
                                "Booleano", true, "Inteiro", false, "Texto", false),
                        pergunta("p2", "O que e uma variavel?",
                                "Um espaco nomeado que guarda um valor", true,
                                "Um tipo de laco", false, "Um erro de compilacao", false)));
        licao(batch, "logica-2", "logica", 2, "Condicionais",
                "Tomando decisoes com if, else e operadores logicos.", 60, List.of(
                        pergunta("p1", "Qual estrutura executa um bloco somente se a condicao for verdadeira?",
                                "if", true, "for", false, "return", false)));
        licao(batch, "logica-3", "logica", 3, "Lacos de repeticao",
                "Repetindo instrucoes com for e while sem se perder.", 70, List.of(
                        pergunta("p1", "Qual laco e mais indicado quando nao se sabe quantas repeticoes havera?",
                                "while", true, "if", false, "switch", false)));

        trilha(batch, "web", 2, "Desenvolvimento Web",
                "HTML, CSS e JavaScript para construir suas primeiras paginas.");
        licao(batch, "web-1", "web", 1, "Estrutura com HTML",
                "Tags, atributos e a estrutura basica de um documento.", 50, List.of(
                        pergunta("p1", "Qual tag cria um link?",
                                "<a>", true, "<link>", false, "<p>", false)));
        licao(batch, "web-2", "web", 2, "Estilo com CSS",
                "Seletores, cores e o modelo de caixa.", 60, List.of(
                        pergunta("p1", "Qual propriedade muda a cor do texto?",
                                "color", true, "background", false, "font-size", false)));

        await(batch.commit());
        log.info("Seed concluido: trilhas e licoes de exemplo criadas no Firestore.");
    }

    private void trilha(WriteBatch batch, String id, int ordem, String titulo, String descricao) {
        batch.set(db.collection(TrilhaService.TRILHAS).document(id),
                Map.of("titulo", titulo, "descricao", descricao, "ordem", ordem));
    }

    private void licao(WriteBatch batch, String id, String trilhaId, int ordem, String titulo, String resumo,
                       int xpBonus, List<Map<String, Object>> perguntas) {
        batch.set(db.collection(TrilhaService.LICOES).document(id), Map.of(
                "trilhaId", trilhaId,
                "ordem", ordem,
                "titulo", titulo,
                "resumo", resumo,
                "xpBonus", xpBonus,
                "perguntas", perguntas));
    }

    private static Map<String, Object> pergunta(String id, String enunciado,
                                                String o1, boolean c1, String o2, boolean c2, String o3, boolean c3) {
        return Map.of(
                "id", id,
                "enunciado", enunciado,
                "opcoes", List.of(
                        Map.of("id", "a", "texto", o1, "correta", c1),
                        Map.of("id", "b", "texto", o2, "correta", c2),
                        Map.of("id", "c", "texto", o3, "correta", c3)));
    }
}
