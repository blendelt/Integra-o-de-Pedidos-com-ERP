# Revisão da implementação — 16/09/2026

## Conclusão

A organização por camadas é adequada ao desafio. Revisados fontes, configurações, DTOs, migração e testes dos dois serviços, além do plano. A revisão de código não equivale a validação completa de execução ou concorrência.

## Correções aplicadas

- `OrderService` traduzia qualquer erro de integridade como duplicidade. Agora só traduz a constraint `uk_orders_external_id`; outras falhas preservam sua causa.
- Adicionado `@Digits(integer=17, fraction=2)` aos DTOs de entrada dos dois serviços, compatível com `NUMERIC(19,2)`, evitando arredondamento silencioso ou estouro da coluna.
- `OrderProcessor` captura apenas `ErpClientException`. Erros inesperados de programação interrompem o lote e permanecem visíveis, em vez de serem tratados como falhas normais de integração.
- `HttpErpClient` traduz também respostas ilegíveis para `ErpClientException`, sem expor o corpo da resposta.
- Configuração do ERP rejeita NaN e limite de atraso que causaria overflow.
- Removido teste placeholder que passava sem verificar nada. O teste de contexto real do Order Service continua pendente.

## SOLID

| Princípio | Aplicação |
| --- | --- |
| Responsabilidade única | Controller recebe HTTP; processor coordena lote; entidade protege transições; repository persiste; client integra. |
| Aberto/fechado | Outra implementação de `ErpClient` pode substituir HTTP sem alterar o lote. |
| Substituição de Liskov | Implementações de `ErpClient` devem preservar o contrato: aceitação válida ou exceção de integração. Uma interface sozinha não garante isso. |
| Segregação de interfaces | `ErpClient` oferece uma única operação; não há necessidade de interfaces para cada service. |
| Inversão de dependência | Processamento depende de `ErpClient`, não de RestClient. Dependências entram pelo construtor. |

O acoplamento a Spring/JPA e à entidade no contrato do client é uma escolha aceitável neste projeto pequeno. Separar domínio puro ou criar interfaces em todas as camadas acrescentaria complexidade sem necessidade atual.

## Design patterns

- **Adapter:** `HttpErpClient` adapta o contrato da aplicação ao protocolo HTTP do ERP.
- **Repository:** `OrderRepository` concentra acesso ao banco via Spring Data.
- **Service Layer:** controllers e job delegam a serviços.
- A entidade usa uma máquina de estados simples. Não é o padrão GoF State; criar uma classe por status não traria benefício agora.

Não adicionei Factory, Strategy, Observer ou Singleton manual apenas para mencionar padrões. Injeção e proxies transacionais já são fornecidos pelo Spring.

## Transações e concorrência

A reserva roda em outro bean, passando pelo proxy transacional. No fluxo atual, sem transação externa, PROCESSING é confirmado antes do HTTP. `FOR UPDATE SKIP LOCKED` evita duas reservas da mesma linha; após commit, o filtro PENDING a exclui. `@Version` protege alterações concorrentes da entidade, mas não substitui a reserva.

Não adicionar `@Transactional` ao lote inteiro: os métodos com propagação REQUIRED participariam da transação externa. Se surgirem chamadores transacionais, reavaliar essa fronteira explicitamente.

## Pendências e limites

1. O executor paralelo com limite de workers ainda não foi implementado. Chamadas manuais simultâneas não substituem esse requisito.
2. O endpoint aguarda o lote sequencial e pode exceder timeouts de proxies. Evoluir para disparo em segundo plano com o executor.
3. Validar bloqueio, commit, rollback e migração com PostgreSQL real. Mocks não comprovam esses comportamentos.
4. Queda após reserva, erro inesperado ou falha ao salvar resultado pode deixar PROCESSING. Recuperação exige reconciliação e/ou idempotência no ERP antes de retry automático.
5. Timeout não prova que o ERP deixou de executar. ERROR registra falha observada; o resultado remoto pode ser incerto. O simulador não persiste deduplicação no destino.
6. Listagem sem paginação é aceitável para a demonstração, mas deve evoluir com o volume.
7. Angular, Docker, testes completos e documentação final continuam pendentes conforme o plano.
8. Chamar o job diretamente em teste não valida o temporizador real. Timeouts configurados e concorrência também precisam de testes reais.

## Validação

Adicionados testes de regressão para constraint única versus outras falhas do banco, precisão monetária, erro de programação, JSON inválido, status não aceito e configuração inválida do ERP.

Tentado `mvn test` nos dois serviços com Java 17. Ambos pararam na compilação com `Cannot close compiler resources` ao acessar uma biblioteca Jackson. Nenhum teste novo foi confirmado como aprovado aqui. Executar as suítes dos dois módulos no IntelliJ. Os quatro testes antigos do cliente HTTP haviam sido confirmados pelo usuário antes destas alterações.
