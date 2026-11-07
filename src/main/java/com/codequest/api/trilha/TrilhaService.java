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

        Set<String> concluidas = await(concluidasFuture).getDocuments().stream()
                .map(DocumentSnapshot::getId)
                .collect(Collectors.toSet());

        Map<String, List<LicaoResponse>> licoesPorTrilha = await(licoesFuture).getDocuments().stream()
                .map(doc -> toLicao(doc, concluidas.contains(doc.getId())))
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
        if (request == null || request.questionId() == null || request.optionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selecione uma alternativa para responder.");
        }

        DocumentReference licaoRef = db.collection(LICOES).document(licaoId);
        DocumentReference usuarioRef = db.collection(UsuarioRepository.COLECAO).document(uid);
        DocumentReference tentativaRef = tentativas(uid).document();

        return await(db.runTransaction(tx -> {
            DocumentSnapshot licao = tx.get(licaoRef).get();
            DocumentSnapshot usuario = tx.get(usuarioRef).get();
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
            int dominio = AdaptiveMastery.update(dominioAtual, correta);
            long tentativas = (habilidadeAtual.exists() ? longOf(habilidadeAtual.getLong("tentativas")) : 0) + 1;
            long acertos = (habilidadeAtual.exists() ? longOf(habilidadeAtual.getLong("acertos")) : 0)
                    + (correta ? 1 : 0);
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
                    "dificuldade", dificuldade,
                    "respondidaEm", FieldValue.serverTimestamp()));
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
                if (request == null || request.respostas() == null
                        || request.respostas().size() != perguntas.size()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Responda corretamente a todas as perguntas.");
                }

                for (var p : perguntas) {
                    String pId = String.valueOf(p.get("id"));
                    String selectedOpId = request.respostas().get(pId);
                    List<Map<String, Object>> opcoes = (List<Map<String, Object>>) p.get("opcoes");
                    boolean acertou = opcoes != null && opcoes.stream().anyMatch(o ->
                            String.valueOf(o.get("id")).equals(selectedOpId) && Boolean.TRUE.equals(o.get("correta")));
                    if (!acertou) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Uma ou mais respostas estao incorretas.");
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

    @SuppressWarnings("unchecked")
    private static LicaoResponse toLicao(DocumentSnapshot doc, boolean concluida) {
        List<Map<String, Object>> perguntas = (List<Map<String, Object>>) doc.get("perguntas");
        List<PerguntaResponse> perguntasDto = perguntas == null ? List.of() : perguntas.stream()
                .map(p -> {
                    List<Map<String, Object>> opcoes = (List<Map<String, Object>>) p.get("opcoes");
                    List<OpcaoResponse> opcoesDto = opcoes == null ? List.of() : opcoes.stream()
                            .map(o -> new OpcaoResponse(String.valueOf(o.get("id")), (String) o.get("texto")))
                            .toList();
                    return new PerguntaResponse(
                            String.valueOf(p.get("id")),
                            (String) p.get("enunciado"),
                            String.valueOf(p.getOrDefault("skillId", doc.getId())),
                            intOfNumber(p.get("dificuldade")),
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
}
