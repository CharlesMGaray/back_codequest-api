package com.codequest.api.ranking;

import com.codequest.api.usuario.Usuario;
import com.codequest.api.usuario.UsuarioRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    public record RankingItem(int posicao, String usuarioId, String nome, long xpTotal, int nivelAtual) {
    }

    private final UsuarioRepository usuarios;

    public RankingController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<RankingItem> ranking(@RequestParam(defaultValue = "10") int limite) {
        int limiteSeguro = Math.clamp(limite, 1, 100);
        List<Usuario> top = usuarios.topPorXp(limiteSeguro);
        return IntStream.range(0, top.size())
                .mapToObj(i -> {
                    Usuario u = top.get(i);
                    return new RankingItem(i + 1, u.id(), u.nome(), u.xpTotal(), u.nivelAtual());
                })
                .toList();
    }
}
