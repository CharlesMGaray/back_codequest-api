package com.codequest.api.trilha;

import com.codequest.api.trilha.TrilhaDtos.ConclusaoRequest;
import com.codequest.api.trilha.TrilhaDtos.ConclusaoResponse;
import com.codequest.api.trilha.TrilhaDtos.TrilhaResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TrilhaController {

    private final TrilhaService trilhaService;

    public TrilhaController(TrilhaService trilhaService) {
        this.trilhaService = trilhaService;
    }

    @GetMapping("/trilhas")
    public List<TrilhaResponse> listar(@AuthenticationPrincipal String uid) {
        return trilhaService.listar(uid);
    }

    @PostMapping("/licoes/{licaoId}/concluir")
    public ConclusaoResponse concluir(
            @AuthenticationPrincipal String uid,
            @PathVariable String licaoId,
            @RequestBody(required = false) ConclusaoRequest body) {
        return trilhaService.concluirLicao(uid, licaoId, body);
    }
}
