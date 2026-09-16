# Processamento manual e agendado — etapa atual

> Registro da etapa inicial. O processamento agora é paralelo; consulte `PARALLEL_PROCESSING.md` para configuração, fluxo e testes atuais. O endpoint continua aguardando o resumo do lote.

## Fluxo e decisões

1. `POST /orders/process` chama `OrderProcessor.processPending()`.
2. `OrderProcessingTransactions.reserveNext()` abre uma transação curta e busca um pedido `PENDING` com `FOR UPDATE SKIP LOCKED`. Outro processamento ignora a linha bloqueada.
3. A entidade passa para `PROCESSING` e incrementa `attemptCount`. O Hibernate persiste a alteração por dirty checking no commit, antes de devolver o pedido ao processor.
4. O processor chama o ERP fora da transação. Assim, a espera HTTP não segura o bloqueio nem a conexão do banco.
5. Outra transação grava `SUCCESS` ou `ERROR`. Erros HTTP não interrompem os próximos pedidos; erros ao persistir o resultado interrompem a execução e precisam de investigação.
6. O job chama o mesmo processor. Por padrão começa após 60 segundos e espera 60 segundos após o término de cada execução.

As transações ficam em outro bean para que as chamadas passem pelo proxy do Spring e `@Transactional` seja aplicado. Não há chamada interna de um método transacional para outro no mesmo objeto.

Nesta etapa o lote é sequencial e o endpoint responde **200 depois de concluir o lote**, com `processed`, `succeeded` e `failed`. HTTP 200 indica que a execução terminou; pedidos individuais podem ter falhado. O executor paralelo e o disparo em segundo plano serão implementados na próxima etapa.

## Configuração

| Variável | Padrão | Uso |
| --- | --- | --- |
| `ORDER_PROCESSING_BATCH_SIZE` | `20` | Máximo de pedidos por execução; entre 1 e 100 |
| `ORDER_PROCESSING_SCHEDULING_ENABLED` | `true` | Use `false` para testar apenas o endpoint |
| `ORDER_PROCESSING_INITIAL_DELAY` | `60000` | Espera inicial, em milissegundos |
| `ORDER_PROCESSING_FIXED_DELAY` | `60000` | Espera após cada execução agendada, em milissegundos |

Pedidos que excederem o lote ficam pendentes até outra execução. Somente `PENDING` é selecionado; `ERROR` e `SUCCESS` não são reenviados.

## Validação

No IntelliJ, execute todos os testes de `order-service/src/test/java`, ou rode `mvn test` na pasta do Order Service.

Novos testes: `OrderTest`, `OrderProcessorTest` e `OrderProcessingEntryPointsTest` (8 casos). Cobrem transições de status, tentativas, mensagem limitada, lote vazio, falha de ERP seguida de sucesso, limite de lote, falha ao gravar resultado, contrato HTTP e uso do mesmo processor pelo job. Esses testes usam mocks e não comprovam o bloqueio do PostgreSQL nem o temporizador real.

Para validar com PostgreSQL e ERP reais, em uma base de desenvolvimento:

1. Desative o agendamento e reinicie o Order Service. Suba o ERP Service.
2. Cadastre pedidos com identificadores novos, um normal e um iniciado por `FAIL-`.
3. Chame `POST http://localhost:8080/orders/process`, sem corpo.
4. Consulte `GET /orders`: espere `SUCCESS` no normal e `ERROR` no `FAIL-`, ambos com uma tentativa. Outros pedidos pendentes na base também fazem parte do lote.
5. Execute novamente: os pedidos já concluídos não devem ser reenviados.
6. Cadastre novos pedidos e dispare duas chamadas simultâneas ao endpoint. Confira nos logs do ERP que cada identificador foi recebido uma única vez, e no banco que `attemptCount` permanece 1.
7. Reative o agendamento, reinicie e cadastre outro pedido. Aguarde a execução e confira a mudança de status sem acionar o endpoint.

Execução automatizada neste ambiente: bloqueada pelo compilador Java (`Cannot close compiler resources` ao acessar JARs). Os 4 testes anteriores de `HttpErpClientTest` foram confirmados pelo usuário como aprovados no IntelliJ; os novos testes e o cenário de concorrência no PostgreSQL ainda precisam ser executados.

## Limites conhecidos

Se o processo cair depois da reserva, ou o ERP aceitar o pedido e a gravação de `SUCCESS` falhar, o pedido pode permanecer `PROCESSING`. Não é seguro reenviá-lo automaticamente: será necessário reconciliar o resultado com o ERP. Recuperação após queda e idempotência no destino são evoluções futuras. O bloqueio atual impede duas execuções de reservarem simultaneamente a mesma linha, mas não promete entrega exatamente uma vez após falhas.
