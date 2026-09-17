# Edição e reprocessamento

Pedidos em ERROR exibem **Editar e reprocessar**. É possível corrigir identificador externo, cliente e valor. Antes de salvar, o operador confirma que consultou o ERP e que o pedido não foi integrado. Um timeout não prova que a operação falhou no destino.

O botão **Salvar e reenfileirar** chama `POST /orders/{id}/retry`. O corpo contém `order` (os três campos editáveis), `version` e `confirmedNotIntegrated: true`. O registro permanece com o mesmo ID interno e contador de tentativas; retorna a PENDING. O agendador ou **Processar pendentes** realiza o envio. A tentativa só aumenta quando o processamento começa.

Dentro de uma transação, a aplicação bloqueia a linha, verifica ERROR e a versão recebida, valida a unicidade do externalId e aplica a edição. Assim, uma segunda edição com dados antigos é rejeitada. Pedidos PROCESSING ou SUCCESS não podem ser editados por este fluxo. Não foi necessária migração: a coluna version já existia.

O simulador ERP lembra aceitações por externalId em memória: repetir os mesmos dados retorna a aceitação anterior; dados diferentes para um ID já aceito retornam 409. Essa proteção não sobrevive ao reinício do ERP. A confirmação manual não equivale a reconciliação automática; em produção, seriam necessários registro durável e consulta de situação no destino. Alterar externalId de um pedido aceito pode duplicar a operação.

Não há histórico completo de alterações: mantemos contador e último erro até a próxima tentativa.

## Validação manual

1. Reinicie Order Service e ERP Service para carregar as alterações.
2. Cadastre FAIL-TESTE e processe; ele deve ficar em ERROR.
3. Clique em Editar e reprocessar, altere para ERP-TESTE e corrija cliente/valor.
4. Confira no ERP que não houve aceitação e marque a confirmação.
5. Salve: o mesmo ID interno volta a PENDING, mantendo o contador.
6. Processe: deve ficar SUCCESS e o contador aumentar em uma unidade.
7. Tente salvar a mesma edição em duas abas: a segunda deve receber conflito.

Testes adicionados: OrderRetryTest, ErpOrderServiceTest e cenário de confirmação/edição no app.spec.ts.
