package com.codequest.api.trilha;

import java.util.List;

public final class TrilhaDtos {

    private TrilhaDtos() {
    }

    public record TrilhaResponse(String id, String titulo, String descricao, int ordem, List<LicaoResponse> licoes) {
    }

    public record LicaoResponse(String id, String trilhaId, int ordem, String titulo, String resumo, int xpBonus,
                                boolean concluida, List<PerguntaResponse> perguntas) {
    }

    public record PerguntaResponse(String id, String enunciado, String skillId, int dificuldade,
                                   int tentativasUsadas, boolean resolvida, Boolean ultimaCorreta,
                                   String ultimaOpcaoId, String dicaAtual, String explicacao,
                                   String alternativaCorretaId, List<OpcaoResponse> opcoes) {
    }

    public record OpcaoResponse(String id, String texto, Boolean correta) {
    }

    public record ConclusaoRequest(java.util.Map<String, String> respostas) {
    }

    public record RespostaRequest(String questionId, String optionId, int tentativa) {
    }

    public record RespostaResponse(String questionId, boolean correta, String skillId, String licaoId,
                                   String trilhaId, String habilidade, int dominio, String nivelDominio,
                                   int tentativas, int tentativaQuestao, boolean resolvida,
                                   String dica, String explicacao, String alternativaCorretaId,
                                   int diasAteRevisao, String proximaRevisao) {
    }

    public record HabilidadeResponse(String skillId, String licaoId, String trilhaId, String nome,
                                    int dominio, String nivelDominio, int tentativas, int acertos,
                                    String proximaRevisao) {
    }

    public record ConclusaoResponse(String licaoId, int xpGanho, long xpTotal, int nivelAtual) {
    }
}
