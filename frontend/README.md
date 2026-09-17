# Frontend Angular

Abra esta pasta no Visual Studio Code. Use Node 24 LTS e npm.

```powershell
npm install
npm start
```

Acesse http://localhost:4200. Execute também Order Service na porta 8080 e ERP Service na 8081. O proxy de desenvolvimento encaminha /orders ao backend, evitando liberar CORS globalmente. Na futura imagem Docker, esse encaminhamento será feito pelo Nginx.

```powershell
npm run build
npm test -- --watch=false
```

Estrutura: orders/order.model.ts define contratos; orders/order.service.ts concentra HTTP; app.ts controla formulário, estado e atualização; app.html e app.css exibem a tela.

A listagem atualiza a cada 5 segundos sem sobrepor consultas; assinaturas são encerradas ao destruir a tela. Cadastros e processamento não têm retry automático. Se a resposta do lote se perder, o backend pode continuar trabalhando. O formulário aceita vírgula ou ponto decimal e limita o valor a 12 dígitos inteiros para evitar perda de precisão em centavos no JavaScript.

Testes cobrem validação, cadastro duplicado, bloqueio de clique repetido no processamento e recuperação da listagem após falha.

Status: implementação criada; build e testes precisam ser confirmados no terminal local. O ambiente do agente bloqueou o esbuild ao ler pastas superiores (Acesso negado), mesmo após autorização de leitura.
