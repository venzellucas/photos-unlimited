# Photos Unlimited

[English](README.md) · **Português (Brasil)** · [Español](README.es.md) ·
[简体中文](README.zh-Hans.md) · [日本語](README.ja.md)

*Esta tradução foi feita com um modelo de linguagem e pode ficar atrasada em relação ao
README em inglês, que é a referência.*

Um pequeno aplicativo Android independente para enviar arquivos a uma biblioteca do Google
Fotos. Escolha pastas, faça backup de fotos e vídeos originais e continue usando o app
oficial Google Fotos para navegar e editar a sua biblioteca.

**Android 13+ · Licença MIT**

[Site](https://venzellucas.github.io/photos-unlimited/) ·
[Baixar APK](https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk) ·
[Notas da versão](https://github.com/venzellucas/photos-unlimited/releases/latest)

**Verifique o APK.** Cada versão é assinada com o certificado cuja impressão digital
SHA-256 é
`cbd14272c6aaa0568df8cc8be4ec10be9c6a2ebc0354eb202b5a05d8cda74bce`. O Android recusa
qualquer atualização que não seja assinada com esta chave. Cada versão também lista o
checksum do APK em `SHA256SUMS.txt`. Para relatar um problema de segurança, veja
[SECURITY.md](SECURITY.md).

## O que ele faz

- Seleciona pastas de fotos e vídeos para o backup dos arquivos originais.
- Executa backups manuais ou deixa o Android agendar o trabalho automático, com
  preferências de rede sem tarifação por uso e de carregamento.
- Mostra o progresso do arquivo atual, o histórico de envios, correspondências já
  existentes e arquivos que precisam de uma nova tentativa.
- Usa telas nativas do Material, temas claro/escuro, cores do papel de parede, um ícone
  adaptativo e a foto de perfil do Google em cache, quando disponível.
- Verifica os metadados do Google Fotos para inspecionar o uso de armazenamento informado,
  com filtros por tipo de mídia e por armazenamento cobrado ou desconhecido. Este recurso é
  uma prévia experimental.
- Permite usar o app em English, Português (Brasil), Español, 简体中文 ou 日本語. Ele segue
  o idioma do telefone; escolha outro em Configurações → **Idioma**.

Não é necessário root, ROM personalizada, microG, chave de API nem um servidor do
Photos Unlimited.

## Instalar e conectar

1. Baixe o APK assinado da versão. O Android pode pedir que você permita a instalação a
   partir do navegador. Instale sobre uma versão existente para preservar sua configuração.
2. Conecte sua conta pela página de configuração do Google no app. Se o Google rejeitar o
   login incorporado, use a alternativa do token de configuração pelo navegador, descrita
   na tela de conexão. Informe esse token somente no app; nunca o publique em uma issue ou
   mensagem.
3. Permita o acesso a todas as fotos e vídeos e à localização da mídia. Esta última é
   necessária para ler os bytes completos do arquivo original, incluindo a localização
   incorporada.
4. Escolha as pastas. Os arquivos que já estão nessas pastas são incluídos no primeiro
   backup.
5. Desative o backup automático no app oficial Google Fotos e toque em **Fazer backup
   agora**. Isso também ativa o backup automático no Photos Unlimited.

O app consulta o GitHub uma vez por dia enquanto está aberto e mostra um cartão quando há
uma nova versão. Desative isso em Configurações → **Verificar atualizações**. Se o botão da
conta mostrar uma inicial, abra-o e escolha **Atualizar foto do perfil**. A consulta do
perfil usa um token separado, somente leitura, no máximo uma vez por dia; se ela falhar, a
credencial de envio não é invalidada. O seletor avançado de contas do telefone é um
recurso de diagnóstico, não uma alternativa de login para o backup.

## Limites do MVP

O app usa o protocolo móvel não documentado do Google, adaptado do GPMC e do gotohp, de
xob0t. Ele solicita tratamento em qualidade original com um perfil de envio de Pixel.
**Um envio aceito não é prova de isenção de cota.** O Google controla a contabilização do
armazenamento e pode alterar ou rejeitar os métodos. Verifique envios de amostra novos,
originais que possam ser baixados e o uso real da cota antes de depender do app. Itens
correspondentes que já existem na biblioteca são ignorados e mantêm a sua contabilização.

A verificação de armazenamento abrange o Google Fotos, não o Drive nem o Gmail.
Contabilização desconhecida não é zero. O total soma os bytes cobrados informados para os
registros verificados e pode diferir do uso de toda a conta. Itens compartilhados,
versões alternativas, omissões e alterações simultâneas podem afetar a cobertura. A
contabilização em tempo real não está totalmente validada.

## Liberar espaço

Itens que já usam o armazenamento do Google podem ser substituídos por uma cópia enviada
por este app: toque em um item em **Armazenamento** ou selecione vários (**Selecionar
todos os elegíveis** funciona em todas as páginas). O Google identifica a mídia pelo hash
do conteúdo e exclui pela mesma chave; por isso, o original precisa ter sumido antes que
uma substituta idêntica possa existir. Assim, cada item:

1. É verificado no registro do Google (nenhum outro item pode compartilhar seus bytes).
2. Recebe uma cópia do telefone, se presente, ou baixada do Google, aceita somente se o
   SHA-1 for igual ao hash que o Google registrou para o envio original. Isso comprova um
   arquivo idêntico byte a byte, de modo que os metadados incorporados (data EXIF, GPS,
   câmera, localização do vídeo) são mantidos exatamente.
3. É movido para a lixeira e excluído permanentemente; em seguida, a cópia é enviada
   novamente.
4. É confirmado somente quando o Google informa 0 byte cobrado para a substituta; legenda,
   favorito e arquivamento são restaurados.

Propriedades de segurança:

- Cada etapa é registrada em um diário antes de ser executada; uma falha é retomada com
  segurança. Se a cópia mudar antes da exclusão, o original é restaurado da lixeira. Uma
  substituta cobrada ou qualquer item que precise de atenção interrompe toda a execução.
- A cópia fica em `Pictures/Photos Unlimited/Pending upload/`, e não no armazenamento
  privado do app, de modo que desinstalar ou limpar o app não pode perdê-la. Os nomes dos
  arquivos trazem o SHA-1 e um hash curto da conta; uma nova instalação comprova cada
  sobra pelo hash e a conclui (nunca em uma conta diferente). Somente os arquivos que este
  app gravou são concluídos automaticamente; arquivos deixados por uma instalação anterior
  são listados primeiro para a sua aprovação, e arquivos gravados por outros apps são
  ignorados. Uma cópia temporária só é removida quando o Google mostra um item ativo e fora
  da lixeira com o mesmo checksum. Uma notificação avisa para não desinstalar enquanto
  houver algo somente no telefone, trocas de conta esperam até que isso termine, e a
  caixa de diálogo de desinstalação do Android oferece a opção de manter os dados.
- Logo antes de excluir, o item é consultado de novo e ainda precisa ser elegível, a
  movimentação para a lixeira precisa estar visível no registro do Google, e somente um
  item por vez fica entre a exclusão e uma substituta confirmada.
- **Desative primeiro o backup do Google Fotos.** Enquanto o backup dele estiver ativado,
  o Google Fotos exclui a cópia do telefone de tudo o que for removido da nuvem, em todos
  os dispositivos da conta
  ([Ajuda do Google](https://support.google.com/photos/answer/6128858)). O Liberar espaço
  pede isso e, como rede de segurança, devolve ao telefone uma cópia removida, idêntica
  byte a byte.
- Itens em **Economia de armazenamento** só são substituídos pelo original quando o
  arquivo original ainda está no telefone (comprovado pelo hash registrado pelo Google); a
  cópia reduzida do Google nunca é usada no lugar. Isso também atualiza envios reduzidos
  gratuitos anteriores a 2021.
- **Fotos com movimento** (Pixel, Samsung) são arquivos únicos e mantêm o movimento; o
  Google extrai de novo o mesmo vídeo incorporado. As **Live Photos do iPhone** mantêm o
  vídeo em um arquivo separado, por isso são recusadas, em vez de perderem o movimento.

Perdidos na substituição: álbuns, cópias em álbuns compartilhados, comentários, grupos de
rostos e edições de data/localização ou outras feitas dentro do Google Fotos. Outros
dispositivos com o backup do Google Fotos ativado ainda podem excluir as próprias cópias.
Testado ao vivo em uma conta de teste (fotos, vídeos, itens em Economia de armazenamento,
uma Foto com movimento e um vídeo interrompido no meio da execução e recuperado depois que
os dados do app foram limpos); ainda não testado em uma biblioteca real grande.

### Lembrete sobre o backup do Google Fotos

Dois apps de backup no mesmo telefone disputam entre si, e as cópias do Google Fotos usam
armazenamento. O Android não permite que apps leiam as configurações do Google Fotos;
por isso, enquanto o backup automático estiver ativado, o app pergunta a cada abertura até
você confirmar que o backup do Google Fotos está desativado, com um link direto para a
página Backup dele, e só pergunta de novo se depois notar uma foto nova enviada primeiro
por outro app.

O Android controla o agendamento em segundo plano. Regras de rede, bateria, carregamento
e inatividade podem atrasar o trabalho. Forçar a parada do app interrompe o trabalho
automático até que ele seja aberto novamente. Transferências de arquivos interrompidas
reiniciam o arquivo. Vídeos muito grandes podem exigir uma execução manual em uma conexão
estável. RAW, HDR e edições precisam de validação específica por formato; arquivos
separados de foto/vídeo (Live Photos do iPhone) não são reunidos em pares.

## Privacidade

Veja [PRIVACY.md](PRIVACY.md). As credenciais são criptografadas no armazenamento privado
do app; as fotos são enviadas diretamente ao Google. Não há servidor de contas pertencente
ao projeto. Nunca inclua credenciais, cookies, tokens de configuração nem fotos privadas
em relatórios de bugs.

## Compilação

Use o JDK 21 e a plataforma/build tools 37 do Android SDK. Configure `ANDROID_HOME` ou um
arquivo `local.properties` não versionado. O repositório inclui um Gradle wrapper.

```sh
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

O APK de depuração fica em `app/build/outputs/apk/debug/`. Compilações de depuração não
conseguem atualizar uma versão assinada instalada. Execute a instrumentação somente em um
emulador de teste descartável:

```sh
ANDROID_SERIAL=YOUR_TEST_EMULATOR ./gradlew :app:connectedDebugAndroidTest
```

Para compilações de versão, forneça um keystore externo usando `PHOTOS_KEYSTORE` e
`PHOTOS_SIGN_PASSWORD`, defina `ANDROID_HOME` e execute
`RELEASE_TAG=vX.Y.Z tools/build-release.sh`, em que `X.Y.Z` é o `versionName` em
`app/build.gradle`. O APK e o respectivo `SHA256SUMS.txt` são gravados em `dist/X.Y.Z/`.
Nunca faça commit desses valores nem do keystore. Preserve a chave de assinatura das
versões entre as atualizações.

As máscaras da biblioteca podem ser regeneradas com `tools/generate-library-templates.py`
usando `blackboxprotobuf` e o checkout fixado do GPMC listado abaixo.

## Validação

Cada versão passa nas suítes de testes da JVM e de instrumentação do Android, no lint sem
erros e em uma atualização assinada no próprio aparelho a partir da versão anterior. A
versão 1.0.0 também teve uma revisão de segurança independente (os problemas encontrados
foram corrigidos antes do lançamento) e execuções reais do Liberar espaço, conforme descrito
acima. Os testes cobrem manipulação do protocolo, envios confirmados, interrupção, filas
persistidas, separação de contas, paginação de metadados, aparência e isolamento do cache
da foto de perfil. As credenciais e as respostas de rede dos testes são sintéticas. As
telas claras/escuras e com texto grande foram inspecionadas. Essas verificações não provam
isenção real de cota nem a contabilização completa da biblioteca.

## Traduções

O app, este README e o site estão disponíveis em inglês, português do Brasil, espanhol,
chinês simplificado e japonês. As traduções foram feitas com um modelo de linguagem e
podem ficar atrasadas em relação ao texto em inglês, que é a referência. Correções são
bem-vindas como pull requests: o texto do app fica em
`app/src/main/res/values-*/strings*.xml`, e os READMEs e as páginas do site traduzidos
ficam ao lado dos em inglês.

## Créditos e licença

Adaptações do protocolo licenciadas sob a MIT:

- [xob0t/gpmc](https://github.com/xob0t/gpmc), revisão
  `94b1b267ca6819da542d4330a7dd216747eb0437`.
- [xob0t/gotohp](https://github.com/xob0t/gotohp), revisão
  `2a5c1b9678281b6a2ff08661a53ea1dc6b1e2edc`.

O aviso original é mantido em
[app/src/main/assets/XOB0T-LICENSE.txt](app/src/main/assets/XOB0T-LICENSE.txt) e na tela
Sobre do app. O Android e as bibliotecas incluídas mantêm as próprias licenças.
Veja [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Google, Google Fotos, Android e Pixel são marcas registradas de seus respectivos
proprietários. O Photos Unlimited é independente e não é afiliado ao Google.
