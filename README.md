# CodeQuest API

API Spring Boot que usa **Firebase Authentication** (login/cadastro) e **Cloud Firestore** (dados).

## Configuração

No [Firebase Console](https://console.firebase.google.com):

1. **Authentication > Método de login**: ative **E-mail/senha**.
2. **Firestore Database**: crie o banco.
3. **Configurações do projeto > Contas de serviço > Gerar nova chave privada**: baixe o JSON.
   Guarde-o **fora do repositório** (ele dá acesso total ao projeto).
4. **Configurações do projeto > Geral**: copie o *ID do projeto* e a *Chave de API da Web*.

Variáveis de ambiente:

| Variável | Descrição |
|---|---|
| `FIREBASE_PROJECT_ID` | ID do projeto |
| `FIREBASE_CREDENTIALS_PATH` | Caminho do JSON da service account (ou use `GOOGLE_APPLICATION_CREDENTIALS`) |
| `FIREBASE_WEB_API_KEY` | Chave de API da Web (usada no login por e-mail/senha) |
| `CODEQUEST_SEED_ENABLED` | `true` (padrão) sincroniza o currículo inicial de trilhas e lições ao iniciar a API |

O currículo inicial contém seis trilhas, com seis questões por lição: cinco são
selecionadas para cada quiz e uma fica como questão extra. Cada banco inclui
questões fáceis, médias e difíceis. As trilhas e lições mantidas pelo seed são
atualizadas ao iniciar a API, inclusive quando já existem dados no Firestore; o
progresso dos usuários fica preservado. Defina `CODEQUEST_SEED_ENABLED=false` para
desativar essa sincronização.

```bash
export FIREBASE_PROJECT_ID=meu-projeto
export FIREBASE_CREDENTIALS_PATH=$HOME/.secrets/codequest-firebase.json
export FIREBASE_WEB_API_KEY=AIza...
mvn spring-boot:run
```

### Rodando com os emuladores (sem tocar no projeto real)

```bash
npx firebase-tools emulators:start --only auth,firestore --project demo-codequest
# em outro terminal
FIREBASE_PROJECT_ID=demo-codequest FIREBASE_WEB_API_KEY=fake \
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 \
mvn spring-boot:run
```

## Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/api/auth/registrar` | – | `{nome, email, senha, perfil}` → token + usuário |
| POST | `/api/auth/login` | – | `{email, senha}` → token + usuário |
| POST | `/api/auth/refresh` | – | `{refreshToken}` → novo token (o token do Firebase expira em 1h) |
| POST | `/api/auth/recuperar-senha` | – | `{email}` → solicita e-mail de redefinição de senha pelo Firebase |
| GET | `/api/progresso` | ****** Domínio, tentativas e próxima revisão por habilidade |
| POST | `/api/licoes/{id}/responder` | ****** Valida uma alternativa, aplica até três tentativas e atualiza o domínio |
| GET | `/api/auth/me` | Bearer | Perfil do usuário logado |
| GET | `/api/trilhas` | Bearer | Trilhas com lições, perguntas e flag `concluida` |
| POST | `/api/licoes/{id}/concluir` | Bearer | Valida as cinco respostas selecionadas e soma o XP uma única vez (transação) |
| GET | `/api/ranking?limite=10` | Bearer | Top usuários por XP |

Erros sempre retornam `{"message": "..."}`.
O endpoint de resposta recebe `{questionId, optionId, tentativa}`; envie tentativa
`1`, `2` ou `3` em sequência para cada questão. A conclusão recebe
`{respostas: {"questionId": "optionId", ...}}` com as cinco questões do quiz.

## Estrutura no Firestore

```
usuarios/{uid}                             nome, email, perfil, xpTotal, nivelAtual, criadoEm
usuarios/{uid}/licoesConcluidas/{licaoId}  trilhaId, xpGanho, concluidaEm
trilhas/{trilhaId}                         titulo, descricao, ordem
licoes/{licaoId}                           trilhaId, ordem, titulo, resumo, xpBonus,
                                           perguntas[{id, enunciado, skillId, dificuldade,
                                                      opcoes[{id, texto, correta}]}]
usuarios/{uid}/habilidades/{skillId}       dominio, nivelDominio, tentativas, acertos,
                                           ultimaRespostaEm, proximaRevisao
usuarios/{uid}/tentativas/{tentativaId}    questionId, licaoId, skillId, correta,
                                           dificuldade, respondidaEm
usuarios/{uid}/questoesRespondidas/{id}    tentativa, resolvida, correta, opcaoId
```

Nível = `xpTotal / 100 + 1`. O domínio começa em 50 e é atualizado por média móvel
exponencial após cada resposta. A seleção do quiz usa o domínio médio da trilha:
abaixo de 60 prioriza questões fáceis, de 60 a 79 prioriza médias e a partir de 80
prioriza difíceis. Cinco questões são exibidas por lição; a questão extra permanece
no banco. O XP da lição é dividido entre as cinco respostas: acertos na primeira,
segunda e terceira tentativa rendem 100%, 60% e 30% da parte da questão; erros rendem
zero. A API recomenda revisar em 1 dia para domínio abaixo de 60, em 3 dias abaixo
de 80 e em 7 dias para domínio igual ou superior a 80. As questões são enviadas sem
o gabarito; a correção é feita no backend. Respostas erradas exibem duas dicas
progressivas; após o terceiro erro, a API revela a resposta e a explicação. Só a API
acessa o Firestore (via
Admin SDK), então as regras de segurança do Firestore podem bloquear todo acesso direto:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} { allow read, write: if false; }
  }
}
```
