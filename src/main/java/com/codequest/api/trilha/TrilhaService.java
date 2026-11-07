package com.codequest.api.trilha;

import com.codequest.api.common.ApiException;
import com.codequest.api.trilha.TrilhaDtos.ConclusaoResponse;
import com.codequest.api.trilha.TrilhaDtos.HabilidadeResponse;
import com.codequest.api.trilha.TrilhaDtos.LicaoResponse;
import com.codequest.api.trilha.TrilhaDtos.OpcaoResponse;
import com.codequest.api.trilha.TrilhaDtos.PerguntaResponse;
import com.codequest.api.trilha.TrilhaDtos.RespostaRequest;
import com.codequest.api.trilha.TrilhaDtos.RespostaResponse;
import com.codequest.api.trilha.TrilhaDtos.TrilhaResponse;
import com.codequest.api.usuario.Usuario;
import com.codequest.api.usuario.UsuarioRepository;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.codequest.api.common.Futures.await;

/**
 * Estrutura no Firestore:
 * <pre>
 * trilhas/{trilhaId}                       titulo, descricao, ordem
 * licoes/{licaoId}                         trilhaId, ordem, titulo, resumo, xpBonus, perguntas[]
 * usuarios/{uid}/licoesConcluidas/{licaoId} trilhaId, xpGanho, concluidaEm
 * </pre>
 */
@Service
public class TrilhaService {

    public static final String TRILHAS = "trilhas";
    public static final String LICOES = "licoes";
    public static final String CONCLUIDAS = "licoesConcluidas";
    public static final String HABILIDADES = "habilidades";
    public static final String TENTATIVAS = "tentativas";

    private final Firestore db;

    public TrilhaService(Firestore db) {
        this.db = db;
    }

    public List<TrilhaResponse> listar(String uid) {
        var trilhasFuture = db.collection(TRILHAS).get();
        var licoesFuture = db.collection(LICOES).get();
        var concluidasFuture = concluidas(uid).get();
        var questionStatesFuture = questoesRespondidas(uid).get();

        Set<String> concluidas = await(concluidasFuture).getDocuments().stream()
                .map(DocumentSnapshot::getId)
                .collect(Collectors.toSet());
        Map<String, DocumentSnapshot> questionStates = await(questionStatesFuture).getDocuments().stream()
                .collect(Collectors.toMap(DocumentSnapshot::getId, doc -> doc));

        Map<String, List<LicaoResponse>> licoesPorTrilha = await(licoesFuture).getDocuments().stream()
                .map(doc -> toLicao(doc, concluidas.contains(doc.getId()), questionStates))
                .sorted(Comparator.comparingInt(LicaoResponse::ordem))
                .collect(Collectors.groupingBy(LicaoResponse::trilhaId));

        return await(trilhasFuture).getDocuments().stream()
                .map(doc -> new TrilhaResponse(
                        doc.getId(),
                        doc.getString("titulo"),
                        doc.getString("descricao"),
                        intOf(doc.getLong("ordem")),
                        licoesPorTrilha.getOrDefault(doc.getId(), List.of())))
                .sorted(Comparator.comparingInt(TrilhaResponse::ordem))
                .toList();
    }

    public List<HabilidadeResponse> listarProgresso(String uid) {
        return await(habilidades(uid).get()).getDocuments().stream()
                .map(doc -> {
                    var proximaRevisao = doc.getTimestamp("proximaRevisao");
                    return new HabilidadeResponse(
                            doc.getString("skillId"),
                            doc.getString("licaoId"),
                            doc.getString("trilhaId"),
                            doc.getString("nome"),
                            intOf(doc.getLong("dominio")),
                            doc.getString("nivelDominio"),
                            intOf(doc.getLong("tentativas")),
                            intOf(doc.getLong("acertos")),
                            proximaRevisao == null ? null : proximaRevisao.toDate().toInstant().toString());
                })
                .sorted(Comparator.comparingInt(HabilidadeResponse::dominio))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public RespostaResponse registrarResposta(String uid, String licaoId, RespostaRequest request) {
        if (request == null || request.questionId() == null || request.optionId() == null
                || request.tentativa() < 1 || request.tentativa() > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selecione uma alternativa para responder.");
        }

        DocumentReference licaoRef = db.collection(LICOES).document(licaoId);
        DocumentReference usuarioRef = db.collection(UsuarioRepository.COLECAO).document(uid);
        DocumentReference tentativaRef = tentativas(uid).document();
        String questionStateId = licaoId + "_" + request.questionId();
        DocumentReference questionStateRef = questoesRespondidas(uid).document(questionStateId);

        return await(db.runTransaction(tx -> {
            DocumentSnapshot licao = tx.get(licaoRef).get();
            DocumentSnapshot usuario = tx.get(usuarioRef).get();
            DocumentSnapshot questionState = tx.get(questionStateRef).get();
            if (!licao.exists()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Licao nao encontrada.");
            }
            if (!usuario.exists()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Usuario nao encontrado.");
            }

            List<Map<String, Object>> perguntas = (List<Map<String, Object>>) licao.get("perguntas");
            Map<String, Object> pergunta = perguntas == null ? null : perguntas.stream()
                    .filter(item -> request.questionId().equals(String.valueOf(item.get("id"))))
                    .findFirst()
                    .orElse(null);
            if (pergunta == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Questao nao encontrada nesta licao.");
            }

            List<Map<String, Object>> opcoes = (List<Map<String, Object>>) pergunta.get("opcoes");
            Map<String, Object> opcao = opcoes == null ? null : opcoes.stream()
                    .filter(item -> request.optionId().equals(String.valueOf(item.get("id"))))
                    .findFirst()
                    .orElse(null);
            if (opcao == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Alternativa invalida.");
            }

            String skillId = String.valueOf(pergunta.getOrDefault("skillId", licaoId));
            DocumentReference habilidadeRef = habilidades(uid).document(skillId);
            DocumentSnapshot habilidadeAtual = tx.get(habilidadeRef).get();

            boolean correta = Boolean.TRUE.equals(opcao.get("correta"));
            int dominioAtual = habilidadeAtual.exists() ? intOf(habilidadeAtual.getLong("dominio")) : 50;
            int tentativaAnterior = questionState.exists() ? intOf(questionState.getLong("tentativa")) : 0;
            boolean resolvidaAntes = questionState.exists() && Boolean.TRUE.equals(questionState.getBoolean("resolvida"));
            if (resolvidaAntes || request.tentativa() != tentativaAnterior + 1) {
                throw new ApiException(HttpStatus.CONFLICT, "Atualize a pergunta antes de tentar novamente.");
            }

            int tentativaAtual = request.tentativa();
            boolean resolvida = correta || tentativaAtual == 3;
            int dominio = resolvida
                    ? AdaptiveMastery.update(dominioAtual, correta, tentativaAtual)
                    : dominioAtual;
            long tentativas = (habilidadeAtual.exists() ? longOf(habilidadeAtual.getLong("tentativas")) : 0)
                    + (resolvida ? tentativaAtual : 0);
            long acertos = (habilidadeAtual.exists() ? longOf(habilidadeAtual.getLong("acertos")) : 0)
                    + (correta && resolvida ? 1 : 0);
            int diasAteRevisao = AdaptiveMastery.reviewIntervalDays(dominio);
            Instant agora = Instant.now();
            Date proximaRevisao = Date.from(agora.plus(diasAteRevisao, ChronoUnit.DAYS));
            String trilhaId = licao.getString("trilhaId");
            String nomeHabilidade = licao.getString("titulo");
            int dificuldade = intOfNumber(pergunta.get("dificuldade"));
            String proximaRevisaoIso = proximaRevisao.toInstant().toString();

            tx.set(tentativaRef, Map.of(
                    "questionId", request.questionId(),
                    "licaoId", licaoId,
                    "skillId", skillId,
                    "correta", correta,
                    "tentativa", tentativaAtual,
                    "resolvida", resolvida,
                    "dificuldade", dificuldade,
                    "respondidaEm", FieldValue.serverTimestamp()));
            tx.set(questionStateRef, Map.of(
                    "skillId", skillId,
                    "tentativa", tentativaAtual,
                    "resolvida", resolvida,
                    "correta", correta,
                    "opcaoId", request.optionId(),
                    "atualizadaEm", FieldValue.serverTimestamp()));
            if (resolvida) {
                tx.set(habilidadeRef, Map.of(
                        "skillId", skillId,
                        "licaoId", licaoId,
                        "trilhaId", trilhaId,
                        "nome", nomeHabilidade,
                        "dominio", dominio,
                        "nivelDominio", AdaptiveMastery.level(dominio),
                        "tentativas", tentativas,
                        "acertos", acertos,
                        "ultimaRespostaEm", FieldValue.serverTimestamp(),
                        "proximaRevisao", proximaRevisao));
            }

            return new RespostaResponse(
                    request.questionId(),
                    correta,
                    skillId,
                    licaoId,
                    trilhaId,
                    nomeHabilidade,
                    dominio,
                    AdaptiveMastery.level(dominio),
                    Math.toIntExact(tentativas),
                    tentativaAtual,
                    resolvida,
                    resolvida ? null : stringOf(pergunta.get(tentativaAtual == 1 ? "dica1" : "dica2")),
                    resolvida && !correta ? stringOf(pergunta.get("explicacao")) : null,
                    resolvida && !correta ? String.valueOf(
                            opcoes.stream()
                                    .filter(item -> Boolean.TRUE.equals(item.get("correta")))
                                    .map(item -> item.get("id"))
                                    .findFirst()
                                    .orElse(""))
                            : null,
                    diasAteRevisao,
                    proximaRevisaoIso);
        }));
    }

    /** Conclui a licao uma unica vez por usuario, somando o XP de forma atomica. */
    public ConclusaoResponse concluirLicao(String uid, String licaoId) {
        return concluirLicao(uid, licaoId, null);
    }

    @SuppressWarnings("unchecked")
    public ConclusaoResponse concluirLicao(String uid, String licaoId, TrilhaDtos.ConclusaoRequest request) {
        DocumentReference licaoRef = db.collection(LICOES).document(licaoId);
        DocumentReference usuarioRef = db.collection(UsuarioRepository.COLECAO).document(uid);
        DocumentReference conclusaoRef = concluidas(uid).document(licaoId);

        return await(db.runTransaction(tx -> {
            DocumentSnapshot licao = tx.get(licaoRef).get();
            DocumentSnapshot usuario = tx.get(usuarioRef).get();
            DocumentSnapshot conclusao = tx.get(conclusaoRef).get();

            if (!licao.exists()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Licao nao encontrada.");
            }
            if (!usuario.exists()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Usuario nao encontrado.");
            }
            if (conclusao.exists()) {
                throw new ApiException(HttpStatus.CONFLICT, "Licao ja concluida.");
            }

            List<Map<String, Object>> perguntas = (List<Map<String, Object>>) licao.get("perguntas");
            if (perguntas != null && !perguntas.isEmpty()) {
                for (var p : perguntas) {
                    String pId = String.valueOf(p.get("id"));
                    DocumentSnapshot resposta = tx.get(
                            questoesRespondidas(uid).document(licaoId + "_" + pId)).get();
                    if (!resposta.exists() || !Boolean.TRUE.equals(resposta.getBoolean("resolvida"))) {
                        throw new ApiException(HttpStatus.BAD_REQUEST,
                                "Responda cada pergunta antes de concluir a licao.");
                    }
                }
            }

            int xpGanho = intOf(licao.getLong("xpBonus"));
            Long xpAtual = usuario.getLong("xpTotal");
            long xpTotal = (xpAtual == null ? 0 : xpAtual) + xpGanho;
            int nivel = Usuario.nivelPara(xpTotal);

            tx.set(conclusaoRef, Map.of(
                    "trilhaId", String.valueOf(licao.getString("trilhaId")),
                    "xpGanho", xpGanho,
                    "concluidaEm", FieldValue.serverTimestamp()));
            tx.update(usuarioRef, Map.of("xpTotal", xpTotal, "nivelAtual", nivel));

            return new ConclusaoResponse(licaoId, xpGanho, xpTotal, nivel);
        }));
    }

    private com.google.cloud.firestore.CollectionReference concluidas(String uid) {
        return db.collection(UsuarioRepository.COLECAO).document(uid).collection(CONCLUIDAS);
    }

    private com.google.cloud.firestore.CollectionReference habilidades(String uid) {
        return db.collection(UsuarioRepository.COLECAO).document(uid).collection(HABILIDADES);
    }

    private com.google.cloud.firestore.CollectionReference tentativas(String uid) {
        return db.collection(UsuarioRepository.COLECAO).document(uid).collection(TENTATIVAS);
    }

    private com.google.cloud.firestore.CollectionReference questoesRespondidas(String uid) {
        return db.collection(UsuarioRepository.COLECAO).document(uid).collection("questoesRespondidas");
    }

    @SuppressWarnings("unchecked")
    private static LicaoResponse toLicao(
            DocumentSnapshot doc,
            boolean concluida,
            Map<String, DocumentSnapshot> questionStates) {
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) doc.get("perguntas");
        List<PerguntaResponse> perguntasDto = perguntas == null ? List.of() : perguntas.stream()
                .map(p -> {
                    List<Map<String, Object>> opcoes = (List<Map<String, Object>>) p.get("opcoes");
                    List<OpcaoResponse> opcoesDto = opcoes == null ? List.of() : opcoes.stream()
                            .map(o -> new OpcaoResponse(
                                    String.valueOf(o.get("id")),
                                    (String) o.get("texto"),
                                    concluida ? Boolean.TRUE.equals(o.get("correta")) : null))
                            .toList();
                    return new PerguntaResponse(
                            String.valueOf(p.get("id")),
                            (String) p.get("enunciado"),
                            String.valueOf(p.getOrDefault("skillId", doc.getId())),
                            intOfNumber(p.get("dificuldade")),
                            questionStates.containsKey(doc.getId() + "_" + p.get("id"))
                                    ? intOf(questionStates.get(doc.getId() + "_" + p.get("id")).getLong("tentativa"))
                                    : 0,
                            questionStates.containsKey(doc.getId() + "_" + p.get("id"))
                                    && Boolean.TRUE.equals(questionStates.get(doc.getId() + "_" + p.get("id")).getBoolean("resolvida")),
                            questionStates.containsKey(doc.getId() + "_" + p.get("id"))
                                    ? questionStates.get(doc.getId() + "_" + p.get("id")).getBoolean("correta")
                                    : null,
                            questionStates.containsKey(doc.getId() + "_" + p.get("id"))
                                    ? questionStates.get(doc.getId() + "_" + p.get("id")).getString("opcaoId")
                                    : null,
                            questionHint(p, questionStates.get(doc.getId() + "_" + p.get("id"))),
                            questionExplanation(p, questionStates.get(doc.getId() + "_" + p.get("id"))),
                            correctOptionId(p, questionStates.get(doc.getId() + "_" + p.get("id"))),
                            opcoesDto);
                })
                .toList();

        return new LicaoResponse(
                doc.getId(),
                doc.getString("trilhaId"),
                intOf(doc.getLong("ordem")),
                doc.getString("titulo"),
                doc.getString("resumo"),
                intOf(doc.getLong("xpBonus")),
                concluida,
                perguntasDto);
    }

    private static int intOf(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static long longOf(Long value) {
        return value == null ? 0 : value;
    }

    private static int intOfNumber(Object value) {
        return value instanceof Number number ? number.intValue() : 1;
    }

    private static String stringOf(Object value) {
        return value instanceof String text ? text : "";
    }

    private static String questionHint(Map<String, Object> question, DocumentSnapshot state) {
        if (state == null || Boolean.TRUE.equals(state.getBoolean("resolvida"))) {
            return null;
        }
        int attempt = intOf(state.getLong("tentativa"));
        return stringOf(question.get(attempt == 1 ? "dica1" : "dica2"));
    }

    private static String questionExplanation(Map<String, Object> question, DocumentSnapshot state) {
        return state != null
                && Boolean.TRUE.equals(state.getBoolean("resolvida"))
                && !Boolean.TRUE.equals(state.getBoolean("correta"))
                ? stringOf(question.get("explicacao"))
                : null;
    }

    @SuppressWarnings("unchecked")
    private static String correctOptionId(Map<String, Object> question, DocumentSnapshot state) {
        if (state == null
                || !Boolean.TRUE.equals(state.getBoolean("resolvida"))
                || Boolean.TRUE.equals(state.getBoolean("correta"))) {
            return null;
        }
        List<Map<String, Object>> opcoes = (List<Map<String, Object>>) question.get("opcoes");
        return opcoes == null ? null : opcoes.stream()
                .filter(opcao -> Boolean.TRUE.equals(opcao.get("correta")))
                .map(opcao -> String.valueOf(opcao.get("id")))
                .findFirst()
                .orElse(null);
    }
}
