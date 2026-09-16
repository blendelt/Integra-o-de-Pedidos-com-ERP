# Processamento paralelo: decisões e fluxo

Este documento atualiza a etapa sequencial descrita em PROCESSING_GUIDE.md. A revisão em ARCHITECTURE_REVIEW.md é um registro anterior a esta implementação.

## 1. Limitar o trabalho simultâneo

`OrderExecutorConfig` cria um único `ThreadPoolTaskExecutor` gerenciado pelo Spring. Por padrão são quatro threads, configuráveis por `ORDER_PROCESSING_WORKERS` (1 a 16). A fila tem capacidade de 100 tarefas, suficiente para o lote máximo permitido. Não usamos o pool global do CompletableFuture nem criamos threads por pedido.

O limite vale por instância da aplicação, incluindo chamadas manuais e o job. Duas instâncias com quatro workers cada podem fazer até oito envios em conjunto.

## 2. Coordenar os lotes

`OrderProcessor.processPending()` é synchronized. Apenas um lote por instância submete tarefas por vez; outro disparo aguarda. Isso impede que requisições repetidas encham a fila. O monitor coordena os lotes, mas os workers do lote executam em paralelo. O endpoint mantém o contrato atual: aguarda o término e responde 200 com o resumo. Ainda não é um endpoint de disparo em segundo plano com resposta 202.

Sob grande volume de chamadas, essa espera ocupa threads HTTP. Uma evolução seria coalescer disparos ou retornar um identificador de execução em segundo plano. A solução atual é deliberadamente pequena para o desafio.

## 3. Reservar somente quando o worker começar

Cada tarefa chama `reserveNext()` dentro da thread do worker. O PostgreSQL seleciona um PENDING com `FOR UPDATE SKIP LOCKED`; a entidade passa a PROCESSING e incrementa a tentativa. A transação confirma antes da chamada ao ERP.

Um worker concorrente ignora a linha bloqueada. Depois do commit, o filtro PENDING também a exclui. Essa proteção vale mesmo para outros processos; synchronized sozinho não protegeria duas instâncias.

Tarefas na fila ainda não reservaram pedidos. Uma submissão rejeitada no encerramento não deixa, por si só, um pedido reservado. Lotes vazios fazem até batch-size consultas curtas; essa simplicidade evita pré-reservar pedidos sem worker disponível.

## 4. Executar HTTP e persistir resultado

O envio HTTP acontece sem transação aberta. Sucesso é gravado em nova transação; `ErpClientException` grava ERROR. Falha conhecida de um pedido não cancela os outros. Erro inesperado ou falha de banco propaga ao chamador após aguardar as tarefas aceitas; não é convertido em sucesso nem em falha comum do ERP.

## 5. Somar resultados

Cada worker devolve seu próprio `ProcessingResult`. `CompletableFuture.allOf` espera todos terminarem. Só então a thread coordenadora soma os resultados, sem contadores compartilhados pelos workers.

Exemplo: lote de 20, quatro workers. Até quatro pedidos estão sendo enviados ao mesmo tempo. Ao terminar um, a thread pega outra tarefa. O resumo pode ser `processed=20, succeeded=18, failed=2`.

## 6. Encerramento e limites

O executor tenta concluir tarefas no encerramento normal e espera até 120 segundos. Isso não garante conclusão em queda abrupta. PROCESSING órfão, resultado remoto incerto após timeout e reconciliação continuam sendo limitações documentadas. Não há retry automático.

## Testes

### Validação adicional da reserva

`skipsLockedOrderAndMakesItAvailableAgainAfterRollback` mantém uma transação aberta com o único pedido bloqueado. Uma segunda thread deve retornar sem pedido enquanto o bloqueio ainda existe. Depois, o teste força rollback e verifica PENDING e zero tentativas; uma nova reserva confirma uma tentativa e impede nova seleção do mesmo pedido. Assim o teste comprova especificamente SKIP LOCKED, rollback e exclusão após commit, sem depender apenas da contagem final de envios. A limpeza usa somente o banco isolado do container.

Última tentativa: o endpoint local do Docker não estava acessível e o Maven parou na compilação com `Cannot close compiler resources`. Portanto a execução deste teste continua pendente. Abra Docker Desktop, espere o engine iniciar e execute `OrderPostgresConcurrencyTest` no IntelliJ. Confirme dois testes aprovados, nenhum ignorado.

- `OrderProcessorTest`: cenários de lote e falhas, usando executor direto para resultados determinísticos.
- `OrderParallelProcessingTest`: threads reais e barreiras verificam que dois disparos compartilham o limite de dois workers e concluem os seis pedidos.
- `OrderPostgresConcurrencyTest`: Spring + Flyway + PostgreSQL 16 via Testcontainers. Dois coordenadores independentes disputam oito pedidos; verifica uma chamada por identificador, uma tentativa, status SUCCESS e reserva confirmada antes do HTTP. O ERP é mockado; não é teste HTTP ponta a ponta.

Recarregue o Maven no IntelliJ após a alteração do pom. Rode todos os testes do Order Service. Para o teste PostgreSQL, mantenha Docker em execução; a imagem postgres:16-alpine pode precisar ser baixada. O banco é temporário e isolado do banco local orders. Sem Docker, esse teste fica ignorado: isso não comprova a concorrência.

Tentativa de execução aqui: dependências resolvidas, mas compilação bloqueada novamente por `Cannot close compiler resources` em biblioteca Jackson. Não há confirmação de aprovação dos novos testes neste ambiente.
