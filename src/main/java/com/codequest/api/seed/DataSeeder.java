package com.codequest.api.seed;

import com.codequest.api.trilha.TrilhaService;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.codequest.api.common.Futures.await;

/** Mantem o curriculo inicial atualizado sem apagar progresso dos usuarios. */
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
        WriteBatch batch = db.batch();
        List<TrilhaSeed> trilhas = curriculum();

        for (TrilhaSeed trilha : trilhas) {
            batch.set(db.collection(TrilhaService.TRILHAS).document(trilha.id()), Map.of(
                    "titulo", trilha.titulo(),
                    "descricao", trilha.descricao(),
                    "ordem", trilha.ordem()), SetOptions.merge());

            for (LicaoSeed licao : trilha.licoes()) {
                batch.set(db.collection(TrilhaService.LICOES).document(licao.id()), Map.of(
                        "trilhaId", trilha.id(),
                        "ordem", licao.ordem(),
                        "titulo", licao.titulo(),
                        "resumo", licao.resumo(),
                        "xpBonus", licao.xpBonus(),
                        "perguntas", IntStream.range(0, licao.perguntas().size())
                                .mapToObj(index -> toMap(licao.perguntas().get(index), licao.id(), index + 1))
                                .toList()),
                        SetOptions.merge());
            }
        }

        await(batch.commit());
        log.info("Curriculo sincronizado: {} trilhas e {} licoes.",
                trilhas.size(),
                trilhas.stream().mapToInt(trilha -> trilha.licoes().size()).sum());
    }

    static List<TrilhaSeed> curriculum() {
        return List.of(
                new TrilhaSeed("logica", 1, "Logica de Programacao",
                        "Aprenda a decompor problemas e usar variaveis, decisoes e repeticoes.",
                        List.of(
                                new LicaoSeed("logica-1", 1, "Variaveis e tipos",
                                        "Variaveis guardam dados identificados por nomes. Tipos como numero, texto e booleano ajudam a definir quais valores podem ser armazenados e como podem ser usados.",
                                        50, List.of(
                                        q("p1", "Qual tipo representa verdadeiro ou falso?", 0,
                                                "Booleano", "Inteiro", "Texto", "Decimal"),
                                        q("p2", "O que e uma variavel?", 2,
                                                "Uma instrucao que repete codigo", "Um erro de compilacao",
                                                "Um espaco nomeado que guarda um valor", "Um tipo de comentario"),
                                        q("p3", "Qual valor e um numero inteiro?", 1,
                                                "\"42\"", "42", "false", "3.14"))),
                                new LicaoSeed("logica-2", 2, "Condicionais",
                                        "Condicionais avaliam expressoes booleanas para escolher quais instrucoes executar. Use if e else para representar caminhos alternativos.",
                                        60, List.of(
                                        q("p1", "Qual estrutura executa um bloco somente quando uma condicao e verdadeira?", 1,
                                                "for", "if", "return", "import"),
                                        q("p2", "O que acontece no bloco else?", 2,
                                                "Ele sempre executa antes do if", "Ele repete a condicao",
                                                "Ele pode executar quando a condicao do if e falsa", "Ele declara uma variavel"),
                                        q("p3", "Qual operador logico significa \"e\"?", 0,
                                                "&&", "||", "!", "=="))),
                                new LicaoSeed("logica-3", 3, "Lacos de repeticao",
                                        "Lacos executam instrucoes varias vezes. For costuma ser usado quando ha uma contagem conhecida; while continua enquanto uma condicao permanecer verdadeira.",
                                        70, List.of(
                                        q("p1", "Qual laco e adequado quando a repeticao depende de uma condicao que pode mudar?", 2,
                                                "if", "switch", "while", "return"),
                                        q("p2", "O que normalmente evita um laco while infinito?", 1,
                                                "Remover a condicao", "Atualizar algo que possa tornar a condicao falsa",
                                                "Declarar todas as variaveis como texto", "Adicionar outro while"),
                                        q("p3", "Se um for percorre de 0 ate 3 sem incluir 3, quantas iteracoes executa?", 0,
                                                "3", "2", "4", "0"))),
                                new LicaoSeed("logica-4", 4, "Algoritmos e decomposicao",
                                        "Um algoritmo e uma sequencia finita de passos para resolver um problema. Dividir uma tarefa em partes menores facilita planejar, implementar e testar a solucao.",
                                        70, List.of(
                                        q("p1", "Qual e uma boa primeira etapa para resolver um problema de programacao?", 1,
                                                "Escrever codigo sem analisar o objetivo", "Entender as entradas, saidas e regras do problema",
                                                "Escolher uma cor para a interface", "Adicionar o maior numero possivel de variaveis"),
                                        q("p2", "Por que dividir um problema em partes menores ajuda?", 3,
                                                "Faz o programa executar sem testes", "Elimina a necessidade de nomes claros",
                                                "Garante que nao havera erros", "Facilita implementar e verificar cada parte"),
                                        q("p3", "O que caracteriza um algoritmo?", 0,
                                                "Uma sequencia finita e ordenada de passos", "Uma linguagem especifica",
                                                "Uma variavel global", "Um arquivo de imagem"))))),
                new TrilhaSeed("web", 2, "Fundamentos da Web",
                        "Crie paginas com HTML semantico, CSS e layouts responsivos.",
                        List.of(
                                new LicaoSeed("web-1", 1, "Estrutura com HTML",
                                        "HTML organiza o conteudo da pagina usando elementos. Titulos, paragrafos, links e imagens devem usar tags adequadas ao seu significado.",
                                        50, List.of(
                                        q("p1", "Qual tag cria um link?", 0, "<a>", "<link>", "<p>", "<img>"),
                                        q("p2", "Qual elemento representa o titulo principal da pagina?", 2, "<p>", "<title>", "<h1>", "<footer>"),
                                        q("p3", "Qual atributo de uma imagem fornece texto alternativo?", 1, "src", "alt", "href", "class"),
                                        q("p4", "Para que serve a tag <main>?", 3, "Criar um menu de navegacao", "Aplicar estilos", "Carregar JavaScript", "Identificar o conteudo principal da pagina"))),
                                new LicaoSeed("web-2", 2, "Estilo com CSS",
                                        "CSS controla a apresentacao dos elementos. Seletores escolhem o que estilizar, propriedades definem os estilos e o modelo de caixa organiza espacamento e dimensoes.",
                                        60, List.of(
                                        q("p1", "Qual propriedade muda a cor do texto?", 1, "background", "color", "font-size", "border"),
                                        q("p2", "Qual seletor CSS seleciona um elemento pela classe aviso?", 2, "#aviso", "aviso", ".aviso", "*aviso"),
                                        q("p3", "Qual propriedade cria espaco interno entre o conteudo e a borda?", 0, "padding", "margin", "outline", "gap"),
                                        q("p4", "Qual regra ajuda a adaptar estilos a diferentes larguras de tela?", 3, "keyframes", "font-face", "z-index", "@media"))),
                                new LicaoSeed("web-3", 3, "Layout e acessibilidade",
                                        "HTML semantico e layouts flexiveis tornam paginas mais acessiveis e adaptaveis. Flexbox e Grid ajudam a organizar conteudo sem depender de posicoes fixas.",
                                        70, List.of(
                                        q("p1", "Qual recurso CSS organiza itens em uma dimensao, em linha ou coluna?", 2, "float", "position: fixed", "Flexbox", "filter"),
                                        q("p2", "Por que usar elementos HTML semanticos?", 0, "Eles comunicam a estrutura e o papel do conteudo", "Eles substituem todo o CSS",
                                                "Eles impedem o uso de leitores de tela", "Eles executam consultas ao servidor"),
                                        q("p3", "Qual abordagem e mais adequada para uma pagina responsiva?", 1, "Fixar todos os elementos em pixels sem ajustes",
                                                "Usar layouts flexiveis e media queries", "Criar uma pagina diferente para cada usuario",
                                                "Remover o viewport do documento"),
                                        q("p4", "Qual propriedade CSS controla o espacamento entre itens de um layout Flexbox ou Grid?", 3,
                                                "opacity", "cursor", "text-decoration", "gap"))))),
                new TrilhaSeed("javascript", 3, "JavaScript",
                        "Aprenda a linguagem usada para adicionar comportamento e interatividade as paginas.",
                        List.of(
                                new LicaoSeed("javascript-1", 1, "Valores, variaveis e operadores",
                                        "JavaScript oferece valores como strings, numeros e booleanos. Use const para referencias que nao serao reatribuidas e let quando o valor precisar mudar.",
                                        60, List.of(
                                        q("q1", "Qual palavra-chave declara uma variavel que nao sera reatribuida?", 2, "var", "let", "const", "static"),
                                        q("q2", "Qual operador compara valor e tipo sem conversao implicita?", 1, "=", "===", "=>", "+="),
                                        q("q3", "Qual e o resultado de 2 + \"3\" em JavaScript?", 0, "\"23\"", "5", "23 como numero", "Um erro obrigatorio"),
                                        q("q4", "Qual valor representa explicitamente a ausencia intencional de um valor?", 3, "0", "false", "\"\"", "null"))),
                                new LicaoSeed("javascript-2", 2, "Funcoes e escopo",
                                        "Funcoes agrupam instrucoes reutilizaveis. Parametros recebem dados de entrada e return devolve um resultado ao codigo que chamou a funcao.",
                                        70, List.of(
                                        q("q1", "O que return faz em uma funcao?", 1, "Repete a funcao", "Devolve um valor e encerra a execucao da funcao",
                                                "Declara um parametro", "Mostra automaticamente uma janela"),
                                        q("q2", "Em uma funcao somar(a, b), o que sao a e b?", 3, "Valores de retorno", "Operadores", "Objetos globais", "Parametros"),
                                        q("q3", "Qual e uma forma valida de declarar uma funcao de seta?", 0, "const dobro = (n) => n * 2", "function => dobro(n)",
                                                "const dobro = > n * 2", "return (n) const dobro"),
                                        q("q4", "O que significa escopo local?", 2, "Uma variavel acessivel de qualquer arquivo", "Um tipo de dado",
                                                "Uma variavel acessivel apenas na regiao onde foi declarada", "Um evento do navegador"))),
                                new LicaoSeed("javascript-3", 3, "Arrays e iteracao",
                                        "Arrays armazenam colecoes ordenadas. Metodos como map transformam elementos, filter seleciona elementos e forEach executa uma acao para cada item.",
                                        70, List.of(
                                        q("q1", "Qual indice representa o primeiro elemento de um array?", 0, "0", "1", "-1", "length"),
                                        q("q2", "Qual metodo cria um novo array transformando cada elemento?", 2, "filter", "forEach", "map", "push"),
                                        q("q3", "Qual metodo seleciona apenas os elementos que atendem a uma condicao?", 1, "map", "filter", "join", "shift"),
                                        q("q4", "Como adicionar um elemento ao final de um array?", 3, "array.pop(valor)", "array.shift(valor)", "array.slice(valor)",
                                                "array.push(valor)"))))),
                new TrilhaSeed("git-github", 4, "Git e GitHub",
                        "Registre alteracoes com Git e aprenda a colaborar em repositorios no GitHub.",
                        List.of(
                                new LicaoSeed("git-1", 1, "Controle de versao",
                                        "Git registra o historico de alteracoes de um projeto. Repositorios podem ser locais ou remotos, e commits guardam pontos identificaveis desse historico.",
                                        50, List.of(
                                        q("q1", "Para que serve o Git?", 1, "Para estilizar paginas web", "Para controlar versoes e historico de arquivos",
                                                "Para hospedar imagens", "Para substituir uma linguagem de programacao"),
                                        q("q2", "Qual comando inicializa um repositorio Git local?", 0, "git init", "git start", "git create", "git open"),
                                        q("q3", "O que e um commit?", 3, "Uma copia do site publicada automaticamente", "Um servidor remoto",
                                                "Uma linguagem de consulta", "Um registro de alteracoes no historico"),
                                        q("q4", "Qual comando mostra o estado dos arquivos no repositorio?", 2, "git log --all", "git show", "git status", "git remote"))),
                                new LicaoSeed("git-2", 2, "Commits e branches",
                                        "Branches permitem desenvolver mudancas isoladamente. Um fluxo comum e revisar o status, preparar arquivos com add e registrar um commit com uma mensagem clara.",
                                        60, List.of(
                                        q("q1", "Qual comando prepara um arquivo para o proximo commit?", 2, "git commit arquivo", "git prepare arquivo", "git add arquivo", "git stage --new"),
                                        q("q2", "Por que criar uma branch?", 0, "Para trabalhar em mudancas isoladas sem alterar imediatamente a branch principal",
                                                "Para apagar o historico", "Para instalar dependencias", "Para renomear o repositorio remoto"),
                                        q("q3", "Qual comando cria e troca para uma nova branch?", 1, "git branch --delete nova", "git switch -c nova", "git commit -b nova", "git merge nova"),
                                        q("q4", "Qual e uma boa mensagem de commit?", 3, "mudancas", "update", "coisas novas", "Adiciona validacao do formulario de cadastro"))),
                                new LicaoSeed("git-3", 3, "GitHub e colaboracao",
                                        "GitHub hospeda repositorios Git e oferece ferramentas de colaboracao. Pull requests ajudam a discutir, revisar e integrar mudancas entre branches.",
                                        70, List.of(
                                        q("q1", "O que e um repositorio remoto?", 1, "Uma branch sem historico", "Uma copia do repositorio hospedada em outro local",
                                                "Um arquivo temporario", "Um tipo de commit"),
                                        q("q2", "Para que serve um pull request?", 0, "Propor e revisar mudancas antes de integra-las", "Apagar uma branch local",
                                                "Executar o projeto no navegador", "Criar uma senha para o Git"),
                                        q("q3", "Qual comando envia commits locais para um remoto?", 2, "git fetch", "git clone", "git push", "git status"),
                                        q("q4", "O que o comando git clone faz?", 3, "Apaga o repositorio remoto", "Cria um commit vazio", "Lista os arquivos alterados",
                                                "Copia um repositorio remoto para a maquina local"))))),
                new TrilhaSeed("react", 5, "React",
                        "Construa interfaces com componentes, propriedades, estado e listas.",
                        List.of(
                                new LicaoSeed("react-1", 1, "Componentes e JSX",
                                        "Componentes React sao funcoes reutilizaveis que retornam elementos de interface. JSX permite descrever essa interface usando uma sintaxe semelhante a HTML dentro do JavaScript.",
                                        60, List.of(
                                        q("q1", "O que e um componente funcional React?", 0, "Uma funcao que retorna elementos da interface", "Um arquivo CSS obrigatorio",
                                                "Uma consulta ao banco", "Um evento do navegador"),
                                        q("q2", "Como deve comecar o nome de um componente React?", 2, "Com letra minuscula", "Com um numero", "Com letra maiuscula", "Com underline obrigatorio"),
                                        q("q3", "Como inserir a expressao nome dentro do JSX?", 1, "Usando [nome]", "Usando {nome}", "Usando (nome)", "Usando <nome>"),
                                        q("q4", "Qual atributo JSX e usado para definir uma classe CSS?", 3, "class", "styleName", "cssClass", "className"))),
                                new LicaoSeed("react-2", 2, "Props e estado",
                                        "Props transmitem dados do componente pai para o filho. Estado armazena dados que podem mudar e, quando atualizado pelas ferramentas React, provoca uma nova renderizacao.",
                                        70, List.of(
                                        q("q1", "Qual e o uso principal de props?", 1, "Guardar estado global automaticamente", "Passar dados de um componente pai para um filho",
                                                "Executar consultas SQL", "Substituir todos os eventos"),
                                        q("q2", "Qual hook React e usado para estado local?", 0, "useState", "useRoute", "useFetch", "useClass"),
                                        q("q3", "Como atualizar corretamente um estado criado com useState?", 3, "Alterando diretamente a variavel", "Usando document.querySelector",
                                                "Recarregando a pagina", "Chamando a funcao atualizadora retornada pelo hook"),
                                        q("q4", "Props devem ser modificadas diretamente pelo componente filho?", 2, "Sim, sempre", "Somente dentro de useEffect",
                                                "Nao, o componente deve trata-las como valores recebidos", "Somente se forem strings"))),
                                new LicaoSeed("react-3", 3, "Eventos e listas",
                                        "Eventos como onClick permitem responder a interacoes. Ao renderizar uma colecao com map, cada elemento deve receber uma key estavel e unica entre seus irmaos.",
                                        70, List.of(
                                        q("q1", "Qual propriedade JSX registra um clique?", 2, "onclick", "click", "onClick", "handle-click"),
                                        q("q2", "Qual metodo e frequentemente usado para transformar itens de um array em elementos React?", 0, "map", "filter", "reduce", "sort"),
                                        q("q3", "Para que serve a propriedade key em uma lista React?", 1, "Para aplicar uma cor ao elemento", "Para identificar cada item de forma estavel entre renderizacoes",
                                                "Para tornar o componente publico", "Para substituir props"),
                                        q("q4", "Como passar uma funcao de manipulacao para onClick?", 3, "onClick={executar()}", "onClick=\"executar\"",
                                                "onClick=[executar]", "onClick={executar}"))))),
                new TrilhaSeed("apis", 6, "APIs e integracao",
                        "Consuma dados de servicos web, entenda HTTP e trate respostas assincronas.",
                        List.of(
                                new LicaoSeed("apis-1", 1, "HTTP e APIs REST",
                                        "Uma API define como sistemas trocam dados. Em HTTP, GET costuma consultar recursos, POST costuma enviar dados e os codigos de status comunicam o resultado da solicitacao.",
                                        60, List.of(
                                        q("q1", "Qual metodo HTTP e normalmente usado para consultar um recurso?", 1, "POST", "GET", "DELETE", "PATCH"),
                                        q("q2", "O que indica um status HTTP da familia 2xx?", 3, "Erro no cliente", "Erro no servidor", "Redirecionamento", "Sucesso na solicitacao"),
                                        q("q3", "Qual formato e muito usado para trocar dados em APIs web?", 0, "JSON", "PNG", "MP3", "CSS"),
                                        q("q4", "O que costuma identificar um endpoint de API?", 2, "A cor do botao", "A versao do navegador", "Uma URL que representa um recurso ou operacao", "Um nome de classe CSS"))),
                                new LicaoSeed("apis-2", 2, "Fetch e dados JSON",
                                        "A API fetch envia solicitacoes HTTP e retorna uma Promise. Respostas JSON tambem sao lidas de forma assincrona, por isso o codigo precisa aguardar cada etapa.",
                                        70, List.of(
                                        q("q1", "O que fetch retorna?", 0, "Uma Promise", "Um elemento HTML", "Um valor booleano sempre", "Um arquivo CSS"),
                                        q("q2", "Qual palavra-chave permite aguardar uma Promise dentro de uma funcao assincrona?", 2, "yield", "defer", "await", "static"),
                                        q("q3", "Como converter o corpo de uma resposta fetch para JSON?", 1, "response.toJSON()", "response.json()", "JSON.fetch(response)", "response.parse()"),
                                        q("q4", "Por que verificar response.ok?", 3, "Para converter a resposta em texto", "Para repetir automaticamente a solicitacao",
                                                "Para validar a senha do usuario", "Para saber se o status HTTP representa sucesso"))),
                                new LicaoSeed("apis-3", 3, "Erros e estados de carregamento",
                                        "Uma integracao robusta apresenta estados de carregamento, valida o status HTTP e comunica falhas. Try/catch pode tratar rejeicoes de Promises sem esconder erros do usuario.",
                                        70, List.of(
                                        q("q1", "O que try/catch permite fazer?", 1, "Ignorar todas as respostas HTTP", "Tratar erros que ocorram durante uma operacao",
                                                "Acelerar qualquer solicitacao", "Converter GET em POST"),
                                        q("q2", "O que uma interface deve mostrar enquanto aguarda uma resposta?", 0, "Um estado de carregamento apropriado", "Uma mensagem de sucesso antes da resposta",
                                                "Uma tela vazia sem contexto", "A resposta anterior como se fosse nova"),
                                        q("q3", "Qual pratica ajuda a depurar uma falha de API?", 3, "Esconder todos os erros", "Repetir a solicitacao sem limite",
                                                "Exibir dados inventados", "Registrar ou apresentar uma mensagem de erro util"),
                                        q("q4", "Uma resposta HTTP 404 normalmente indica:", 2, "Sucesso com conteudo", "Falha de autenticacao", "Recurso nao encontrado",
                                                "Erro de sintaxe JavaScript")))))
        );
    }

    static Map<String, Object> toMap(PerguntaSeed pergunta, String skillId, int dificuldade) {
        OpcaoSeed correta = pergunta.opcoes().stream()
                .filter(OpcaoSeed::correta)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pergunta sem alternativa correta: " + pergunta.id()));
        List<String> distratores = pergunta.opcoes().stream()
                .filter(opcao -> !opcao.correta())
                .map(OpcaoSeed::texto)
                .toList();
        return Map.of(
                "id", pergunta.id(),
                "enunciado", pergunta.enunciado(),
                "skillId", skillId,
                "dificuldade", dificuldade,
                "dica1", "Relembre o conceito pedido e descarte a alternativa que trata de \""
                        + distratores.get(0) + "\".",
                "dica2", "Agora compare cada opção com o papel descrito no enunciado; \""
                        + distratores.get(1) + "\" pertence a outro conceito.",
                "explicacao", "A resposta correta é \"" + correta.texto()
                        + "\". Ela corresponde ao conceito solicitado: " + pergunta.enunciado(),
                "opcoes", pergunta.opcoes().stream()
                        .map(opcao -> Map.of(
                                "id", opcao.id(),
                                "texto", opcao.texto(),
                                "correta", opcao.correta()))
                        .toList());
    }

    private static PerguntaSeed q(String id, String enunciado, int correta, String... textos) {
        List<OpcaoSeed> opcoes = java.util.stream.IntStream.range(0, textos.length)
                .mapToObj(index -> new OpcaoSeed(
                        String.valueOf((char) ('a' + index)),
                        textos[index],
                        index == correta))
                .toList();
        return new PerguntaSeed(id, enunciado, opcoes);
    }

    record TrilhaSeed(String id, int ordem, String titulo, String descricao, List<LicaoSeed> licoes) {
    }

    record LicaoSeed(String id, int ordem, String titulo, String resumo, int xpBonus, List<PerguntaSeed> perguntas) {
    }

    record PerguntaSeed(String id, String enunciado, List<OpcaoSeed> opcoes) {
    }

    record OpcaoSeed(String id, String texto, boolean correta) {
    }
}
