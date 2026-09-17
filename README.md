# Integração de pedidos com ERP

Aplicação de teste técnico com Java 17, Spring Boot 3.5.6, Angular 21 e PostgreSQL 17. Permite cadastrar pedidos, integrar com um ERP simulado, acompanhar status e corrigir pedidos com erro.

## Executar com Docker

Pré-requisitos: Git, Docker Desktop com containers Linux e Docker Compose v2. No Windows, habilite virtualização e o backend WSL2. A primeira construção precisa de internet para baixar imagens e dependências. Não é necessário instalar Java, Maven ou Node para executar via Docker.

```powershell
git clone https://github.com/blendelt/Integra-o-de-Pedidos-com-ERP.git
cd Integra-o-de-Pedidos-com-ERP
docker compose up --build -d --wait
docker compose ps
```

Abra **http://localhost:8088**. Os quatro serviços devem ficar healthy.

| Serviço | Endereço no computador |
|---|---|
| Frontend/Nginx | http://localhost:8088 |
| Order Service | http://localhost:18080/orders |
| ERP Service | http://localhost:18081/health |
| PostgreSQL | localhost:5433 |

Banco, usuário e senha de desenvolvimento: `orders`. As portas são diferentes das usadas na execução pela IDE. O PostgreSQL do Docker usa um volume próprio e não copia os pedidos do banco local. Flyway cria o esquema e Hibernate valida sua compatibilidade.

```powershell
docker compose logs --tail=100 order-service erp-service frontend
docker compose restart
docker compose down
```

O volume `postgres-data` preserva os pedidos. Não use `down -v` se quiser mantê-los. Alterar a senha no ambiente não muda automaticamente a senha de um volume já inicializado.

## Configuração

As variáveis podem ser definidas no terminal ou em um `.env` na raiz (ignorado pelo Git). Veja os padrões completos em [compose.yaml](compose.yaml).

| Variável | Padrão | Função |
|---|---|---|
| DB_PASSWORD | orders | Senha de desenvolvimento |
| POSTGRES_PORT / ORDER_PORT / ERP_PORT / FRONTEND_PORT | 5433 / 18080 / 18081 / 8088 | Portas externas |
| ORDER_PROCESSING_WORKERS | 4 | Máximo de envios simultâneos por instância |
| ORDER_PROCESSING_BATCH_SIZE | 20 | Máximo de pedidos por execução |
| ORDER_PROCESSING_SCHEDULING_ENABLED | true | Ativa o job |
| ORDER_PROCESSING_FIXED_DELAY | 60000 | Intervalo após cada execução, em ms |
| ORDER_PROCESSING_INITIAL_DELAY | 60000 | Espera inicial do job, em ms |
| ERP_CONNECT_TIMEOUT / ERP_READ_TIMEOUT | 2s / 3s | Limites do cliente HTTP |
| ERP_MINIMUM_DELAY_MS / ERP_MAXIMUM_DELAY_MS | 500 / 2000 | Atraso simulado |
| ERP_FAILURE_PREFIX | FAIL- | Prefixo que provoca HTTP 500 |
| ERP_RANDOM_FAILURE_RATE | 0.0 | Probabilidade opcional de falha, entre 0 e 1 |

As URLs internas usam nomes dos serviços Docker: Order Service acessa `postgres:5432` e `erp-service:8081`; Nginx encaminha `/orders` para `order-service:8080`.

## Arquitetura e decisões

```text
Navegador -> Nginx (Angular + proxy) -> Order Service -> ERP Service
                                           |
                                       PostgreSQL
```

Cada serviço Java tem seu próprio projeto Maven e container. Os pacotes são separados por controller, DTO, entity, repository, service, client, processor, config e exception conforme a responsabilidade. `ErpClient` separa o processamento da implementação HTTP. Não há relacionamento JPA entre serviços.

O pedido possui ID interno, externalId único, cliente, valor decimal, status, datas, tentativas, último erro e versão otimista. Estados: `PENDING -> PROCESSING -> SUCCESS/ERROR`. A consulta de existência melhora a mensagem de duplicidade; a restrição UNIQUE no PostgreSQL protege também inserções concorrentes.

### Paralelismo, concorrência e transações

O processador usa CompletableFuture e ThreadPoolTaskExecutor com 4 workers por padrão e fila limitada. Cada execução admite até 20 tarefas. O método coordenador é sincronizado para admitir um lote por vez na mesma instância, mas os pedidos dentro do lote são processados em paralelo.

Cada worker reserva um pedido com `SELECT ... FOR UPDATE SKIP LOCKED` numa transação curta e confirma PROCESSING antes de chamar o ERP. Outras instâncias pulam registros bloqueados e não selecionam pedidos já reservados. A chamada HTTP ocorre fora da transação; sucesso ou falha é persistido numa nova transação curta. Assim, não mantemos bloqueios e conexões de banco durante a espera externa.

O job e o endpoint manual usam o mesmo processador. O endpoint espera o lote terminar e devolve seu resumo; não é uma API de jobs assíncronos. O Nginx espera até 120 segundos. Se aumentar muito o lote ou os atrasos, reavalie esse limite. Timeout no navegador não garante cancelamento do trabalho.

### Falhas e reprocessamento

O ERP simula atrasos e falha para identificadores iniciados por FAIL-. Erros esperados do ERP geram ERROR e não interrompem os outros pedidos. São persistidas categorias seguras de erro, sem gravar respostas completas ou dados de clientes. Não há retry automático após timeout, pois o destino pode ter aceitado o pedido.

Em ERROR, a tela permite corrigir e reenfileirar. A transação bloqueia o registro, valida a versão recebida e a unicidade do externalId. O ID interno e as tentativas são preservados; a tentativa só aumenta ao reservar novamente. O operador precisa confirmar que o ERP não aceitou o pedido. Essa confirmação é manual e não substitui reconciliação automática.

O ERP simulado guarda aceitações em memória: a repetição do mesmo externalId com os mesmos dados retorna a aceitação anterior; dados diferentes retornam 409. Essa proteção se perde ao reiniciar o ERP e não representa garantia durável de processamento exatamente uma vez.

## API

| Método e caminho | Resultado |
|---|---|
| POST /orders | Cria PENDING; 201, validação 400, duplicidade 409 |
| GET /orders | Lista pedidos, mais recentes primeiro |
| POST /orders/process | Processa um lote; resumo processed/succeeded/failed |
| POST /orders/{id}/retry | Corrige ERROR e devolve PENDING; conflito de versão/status ou identificador 409 |
| POST /erp/orders | Simula aceitação, atraso ou falha |
| GET /health (ERP) | Saúde do simulador |

Cadastro:

```json
{"externalId":"ERP-12345","customerName":"Empresa ABC","totalValue":1500.00}
```

Reprocessamento (use a versão atual retornada pela listagem):

```json
{"order":{"externalId":"ERP-CORRIGIDO","customerName":"Empresa ABC","totalValue":1500.00},"version":2,"confirmedNotIntegrated":true}
```

## Demonstrar os fluxos

1. Cadastre um pedido normal e outro com externalId `FAIL-DEMO-01`.
2. Clique em Processar pendentes e acompanhe SUCCESS/ERROR e tentativas.
3. Tente repetir um externalId: deve receber conflito, sem criar outro pedido.
4. No pedido com falha determinística, use Editar e reprocessar, corrija o prefixo e confirme a não integração. Processe novamente e confira o mesmo ID interno com mais uma tentativa.
5. Cadastre outro pedido e aguarde o job, sem usar o botão. O intervalo padrão é 60 segundos após o término de cada execução.
6. Cadastre vários pedidos para observar os workers nos logs. Os testes de concorrência descritos abaixo exercitam coordenadores independentes e bloqueios reais.
7. Reinicie com `docker compose restart` e confirme que os pedidos continuam listados.

## Desenvolvimento e testes

Para executar fora do Docker: JDK17, Maven3.9, Node24 LTS, npm e PostgreSQL. Crie previamente o banco orders e o usuário orders. Os serviços usam por padrão PostgreSQL local na porta5432, Order Service8080 e ERP8081. DB_URL, DB_USERNAME, DB_PASSWORD e ERP_BASE_URL podem ser ajustados por ambiente.

Em terminais separados, a partir da raiz:

```powershell
mvn -f erp-service/pom.xml spring-boot:run
mvn -f order-service/pom.xml spring-boot:run
```

Frontend:

```powershell
cd frontend
npm ci
npm start
```

Abra http://localhost:4200. O proxy de desenvolvimento encaminha a API para localhost8080.

Testes Java, na raiz:

```powershell
mvn -f order-service/pom.xml test
mvn -f erp-service/pom.xml test
```

O teste OrderPostgresConcurrencyTest precisa de Docker disponível e usa PostgreSQL isolado via Testcontainers; pode ser ignorado automaticamente quando Docker não está acessível. Verifique o relatório de testes, incluindo os ignorados.

Frontend, dentro de frontend:

```powershell
npm run build
npm test -- --watch=false
```

Há testes de domínio, validação, duplicidade, cliente HTTP, timeout real, paralelismo, concorrência PostgreSQL, reprocessamento e simulador ERP. Os 5 testes Angular cobrem confirmação de reenvio, formulário inválido, duplicidade, bloqueio de clique duplo e recuperação da listagem.

### Evidências e pendências

Em 17/09/2026, o desenvolvedor confirmou os testes Java aprovados; o resultado enviado registrou build Angular concluído e 5 testes aprovados. O fluxo de edição e reprocessamento foi validado pela tela na execução local. O build apresentou aviso não bloqueante de CSS: 4,87kB para orçamento de aviso de 4kB.

Os arquivos Docker passaram em `docker compose config --quiet`. A validação completa em containers (incluindo job e persistência após reinício) ainda precisa ser registrada. Não confundir testes locais aprovados com aprovação integral da execução Docker.

## Melhorias para produção

- Recuperação de pedidos presos em PROCESSING após queda, usando reserva com prazo e reconciliação antes de reenviar.
- Idempotência durável no ERP e consulta de situação para resolver resultados incertos.
- Histórico de alterações e tentativas; hoje há contador e último erro, não auditoria completa.
- Endpoint assíncrono de processamento e acompanhamento de jobs.
- Autenticação, autorização, gestão de segredos, TLS e políticas de retenção de dados.
- Métricas, tracing, readiness dedicado (atualmente o Order Service usa GET /orders no healthcheck).
- Paginação, filtros, limites globais de concorrência entre instâncias e revisão de carga.
- Fixação de imagens por digest e pipeline de CI com testes e build das imagens.

## Uso de IA e apresentação

IA foi utilizada como apoio no desenvolvimento, explicações, revisão e elaboração de testes e documentação. A apresentação deve detalhar as ferramentas efetivamente usadas e como suas sugestões foram revisadas e verificadas. Consulte [VIDEO_GUIDE.md](VIDEO_GUIDE.md) para o roteiro; a gravação com tela e rosto ainda é uma etapa manual de entrega.

Documentação complementar: [Docker](DOCKER.md), [paralelismo](PARALLEL_PROCESSING.md), [processamento](PROCESSING_GUIDE.md), [falhas](ERP_FAILURES.md), [reprocessamento](REPROCESSING.md) e [plano](IMPLEMENTATION_PLAN.md).
