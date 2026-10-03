# Kafka-leverans och lokal processcache

Förmånen arbetar fortfarande med Java-objekt. Processmotorn känner sitt
process-id och använder detta som korrelations-id:

```java
var yrkande = yrkanden.lasProcess(processId);
// Verksamhetslogik ändrar objektet.
yrkande = yrkanden.lagra(processId, yrkande);
```

`lasLeverans(dataleveransId)` hämtar ett specifikt historiskt tillstånd.
`las(objektId)` finns kvar som läsning av objektets senaste tillstånd.
Den äldre `lagra(yrkande)` använder objekt-id som korrelations-id; processintegration
ska använda den uttryckliga överlagringen ovan. Varje lagring ger en ny UUID version 7 för
dataleveransen, även när objektets innehåll och innehållsversion är oförändrade.
Demon använder en enda yrkandeidentitet per process. Försök att byta identitet på
en redan lagrad process avvisas av cachen.

## Representation och metadata

JSON serialiseras och signeras centralt, och kontrolleras före leverans.
Kafka-värdet är exakt dessa UTF-8-bytes. Ingen JSON-envelope läggs runt dokumentet.
Korrelations-id är Kafka-nyckel och håller processens meddelanden på samma partition.

| Header | Innehåll |
| --- | --- |
| `dataleverans-id` | UUID version 7, UTF-8 |
| `korrelations-id` | Processmotorns process-id, UTF-8 |
| `objekt-id` | Modellobjektets identitet, UTF-8 |
| `objekt-version` | Dokumentets objektversion, UTF-8 |
| `forvantad-version` | Version före lagringsanropet, UTF-8 |
| `skapad` | Leveransens UTC-tid, UTF-8 |
| `signatur` | Binär RSA-PSS-signatur för det JCS-kanoniserade JSON-dokumentet |
| `signatur-algoritm` | `JCS+RSA-PSS-SHA512` |

Signaturen skyddar JSON-dokumentet; dessa transportmetadata ingår inte i den.
RSA-PSS använder slump, så samma JSON kan få olika signaturer. Signaturen används
varken som unik nyckel eller som grund för deduplicering.

[SQL-schemat](../ffa-persistence/src/main/resources/cache.sql) använder
`dataleverans_id` som primärnyckel. Korrelations-id är indexerat och inte unikt.
`dokument` är `text`, inte `jsonb`: cachen tolkar eller omformar inte JSON.
Signaturen lagras separat som `bytea`.

Varje rad har leveranstid `skapad`, lokal lagringstid `lagrad` och en monoton
`ordning`. Senaste tillstånd väljs med högst `objekt_version` och därefter
`ordning`, så att återställning av ett äldre backenddokument inte skymmer nyare
lokalt processtillstånd. Lika tidsstämplar eller en justerad klocka ger därmed
inte tvetydig ordning. Tiderna finns kvar för sökning
och spårning. `kafka_publicerad`, `kafka_publicerad_tid`, `leveransforsok` och
`senaste_fel` beskriver leveransstatus. Återförsök flyttar inte en gammal rad framåt
i processhistoriken.

## Två lagringslägen

Kafka och PostgreSQL har inte en gemensam atomisk transaktion. En Kafka-transaktion
omfattar Kafka; den kan inte rulla tillbaka en committad PostgreSQL-transaktion.
Demon använder därför två namngivna policyer i stället för att utlova atomisk
commit mellan systemen.

### STRIKT (standard)

1. Öppna PostgreSQL-transaktion och lås processen och objektidentiteten.
2. Kontrollera identitet, förväntad objektversion och att processen inte har väntande leveranser.
3. Börja Kafka-transaktionen, skicka JSON och headers och invänta kvitto på sändningen.
4. Skriv exakt samma dokument och signatur lokalt som en väntande leverans och committa PostgreSQL.
5. Committa Kafka. Konsumenter måste använda `read_committed`.
6. Registrera bekräftad publicering i en separat databastransaktion och returnera objektet.

Sändningskvittot i steg 3 är inte ett commitkvitto. Misslyckas lokal lagring
aborteras Kafka-transaktionen. Misslyckas Kafka efter lokal commit finns den nya
versionen beständigt i cachen och kan återförsökas med samma leverans-id.
`Leveransfel` anger id, positiv lokal commitbekräftelse (`lokaltLagrat`) och
positiv Kafka-commitbekräftelse (`kafkaKvitterat`). Ett negativt värde bevisar
inte att motsvarande system saknar data: ett commitkvitto kan ha förlorats.

Vanlig läsning per process eller objekt, och nya lagringar, blockeras i strikt
läge medan processen har obekräftade leveranser. Ingen äldre version lämnas ut
som ersättning. `lasLeverans(id)` och `lasStatus(id)` är däremot tillgängliga för
infrastrukturens diagnos och återhämtning. Efter återförsök eller positiv
backendbekräftelse kan processen fortsätta från den nya versionen.

Sessionslås på den fysiska PostgreSQL-anslutningen bevarar process- och objekt-
samordningen även över den inre databascommitten. Anslutningen stängs efter
försöket, vilket släpper låsen. En krasch lämnar den väntande raden som beständig
spärr. Återförsök använder transaktionslås på samma processnyckel och hoppar över
ett pågående strikt försök. Om anslutningspool införs måste sessionslåsen släppas
uttryckligen innan anslutningen återlämnas.

Fel när den lokala statusflaggan uppdateras efter bekräftad Kafka-commit loggas;
de kan inte rulla tillbaka de två redan lyckade lagringarna. Raden kan avstämmas
eller återförsökas. Tills dess kan en senare strikt processaktivitet blockeras.

`KAFKA_FORST` accepteras fortfarande som äldre konfigurationsnamn, men betyder
nu samma sak som `STRIKT`. Den gamla ordningen med Kafka-publicering före
beständig cache används inte längre.

En `KafkaPublicerare` utför en Kafka-transaktion åt gången och delar därför
inte en pågående transaktion mellan parallella processer. Återförsöksbatchens
databasarbetare kan arbeta parallellt, men publicering genom samma producent
serialiseras. Högre publiceringskapacitet kräver separata producentinstanser
med egna transaktions-id:n; det ingår inte i denna PoC.

### LOKAL_RESILIENS

1. Lås och kontrollera versionen i PostgreSQL-transaktionen.
2. Skriv dokument, signatur och metadata som en väntande leverans i samma transaktion.
3. Committa den lokala kopian.
4. Försök skicka väntande leveranser i ursprunglig lagringsordning.
5. Efter kvitto markeras leveransen som publicerad.

Ett Kafka-fel hindrar inte framgångsrik lokal lagring. Återläsning av processens
senaste tillstånd inkluderar även väntande leveranser. Ett databasfel före commit
returneras som fel och leder inte till någon Kafka-publicering.

Väntande leveranser överlever omstart. Varje lokal lagring försöker skicka högst
100 väntande rader för den egna processen. Ett processlås samordnar skribenter och
återförsök mellan instanser. Ett separat objektlås hindrar att samma objekt knyts
till två processer samtidigt. Oberoende processer delar inte ett topiclås.

`skickaVantande(maxAntal)` fördelar batchen mellan processer, med de äldsta
väntande leveranserna först inom varje process. Upp till åtta processer hanteras
parallellt. En upptagen process hoppas över och kan försöka igen nästa gång.
Ett leveransfel stoppar senare leveranser **inom den felande processen**; övriga
processer fortsätter. Metoden rapporterar fel efter att övriga försök slutförts,
så ett undantag betyder inte att hela batchen misslyckats. Tidigare kvitton behålls.
Återförsök använder alltid ursprungligt id, JSON och signatur.

Om processen kraschar efter Kafka-kvitto men före uppdaterad lokal flagga kan
samma dataleverans skickas igen. Leveransgarantin är därför minst en gång.
Producentens idempotens skyddar dess interna nätverksåterförsök, inte replay
efter en omstart. Dubletter accepteras i det befintliga masterdataflödet enligt
beskrivningen nedan; separat deduplicering behöver därför inte införas i förmånen.
`Aterforsoksarbetare` kör återförsök var femte sekund medan demoprocessen lever.
Demon är ett kortlivat program; efter avslut krävs nästa programstart eller det
uttryckliga återförsökskommandot. En långlivad värd, exempelvis processmotorn,
kan behålla arbetaren under hela sin livstid. Stäng arbetaren före Kafka-publiceraren.

## Återställning från backend vid cachemiss

`Dokumentkalla` beskriver backendens läsning per objekt-id, korrelations-id och
dataleverans-id. Källan returnerar den ursprungliga `Dataleverans` med signerad
JSON och metadata, eller `null` när tillståndet saknas. Transportfel ska rapporteras
som fel, inte som att data saknas. Anslut den i infrastruktursammansättningen:

```java
var yrkanden = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, cache,
        backend, signeringsnyckel, verifieringsnyckel);
```

Kärnan läser backend endast när den lokala cachen saknar den begärda identiteten.
Den kontrollerar söknyckel, signatur, dokumenttyp, modellidentitet och objektversion,
och migrerar före Java-bindning. Först därefter anropas `cache.aterstall(leverans)`.
Återställning behåller ursprungligt leverans-id, JSON, signatur och leveranstid;
den skapar ingen ny Kafka-leverans. Backenddata betraktas som redan levererat,
med `kafka_publicerad=true` och noll lokala publiceringsförsök. En redan befintlig
lokal rad avstäms också till bekräftad rådatalagring om samma leveransdata matchar.
Samma id med annat data avvisas.
Nyare lokal version, även en väntande sådan, har företräde framför äldre backenddata.

Alla lagringsadaptrar måste implementera hela `Dokumentlager`-kontraktet. Den
äldre skrivmetoden som tappade leveransmetadata finns inte längre i körkoden.
Bakåtkompatibla metoder för att lägga in råa testfixturer finns enbart i teststödet.

Det finns ingen anslutning till företagets verkliga backend i denna miljö.
Den vanliga demon använder därför fortfarande konstruktorn utan backend.
Den valfria [hela-kedjan-demon](pipeline.md) använder `RestDokumentkalla` mot
`BackendRestServer`, som läser den lokala RustFS/PostgreSQL-backenden.
Återställningsvägen testas både med en testkälla och genom verklig HTTP, Kafka,
PostgreSQL och RustFS. REST-kontraktet är PoC:ens eget lokala kontrakt; det är
inte en implementation av företagets befintliga backend-API. Ingen backend i
primärminne har införts i den körbara lösningen.

## Det efterföljande masterdataflödet

I det befintliga systemet lagras inkommande JSON i object store med
dataleverans-id som nyckel. Ett PostgreSQL-index möjliggör uppslag med
korrelations-id för återläsning till förmånsprocessen. Den nya lokala cachen
snabbar upp denna återläsning; object store och dess index finns längre bak i
kedjan och implementeras inte av denna cacheadapter.

Backend kan få samma JSON med samma dataleverans-id presenterat inför skrivning
till object store flera gånger. Den grundläggande invarianten är att **ett
dataleverans-id alltid är associerat med samma data**. Ett id får inte återanvändas
för ändrat innehåll. Följande hanteringar är möjliga i backend:

- Skriv indexinformationen i backendens PostgreSQL-databas före object store.
  Ett upprepat skrivförsök eller en konflikt kan accepteras om det går att
  bekräfta att rätt data redan finns i object store. En indexrad i sig är inte
  bevis för att objektskrivningen lyckats.
- Hantera en redan befintlig nyckel vid skrivning till object store som en
  accepterad upprepning, förutsatt att den befintliga nyckeln hör till samma data.
  Andra skrivfel, eller samma nyckel med annat innehåll, är inte en sådan upprepning.

Detta beskriver backendens möjliga felhantering, inte ytterligare steg i den
lokala cacheadaptern. En möjlig framtida utformning vore innehållsadresserad
lagring (CAS), där dataleverans-id är SHA-256 av en entydigt definierad
JSON-representation och hashen signeras. Då skulle identiteten knytas direkt
till innehållet. Det gör vi inte nu: demon tilldelar UUID version 7 och signerar
JSON-dokumentet enligt den befintliga policyn. UUID:t är inte en innehållshash.
Objektversion och lokal lagringsordning används fortsatt för senaste tillstånd;
UUID:t ersätter inte versionskontroll eller ordning vid återförsök.

Inlevererade processtillstånd bearbetas även till en graf. Övriga konsumenter
läser från grafen, inte direkt från object store. Enligt det befintliga systemets
kontrakt ger samma verksamhetsdata samma graf och en upprepad leverans ändrar
därför inte grafens tillstånd. Det finns inget krav på att förmånssystemet ska
spara dataleverans-id eller ha en egen databas.

Ett återförsök från outbox använder samma dataleverans-id och uppdaterar den
befintliga cacheradens leveransstatus. Ett nytt lagringsanrop ger däremot en ny
dataleverans och en ny cacherad, även om verksamhetsinnehållet är oförändrat.
Båda fallen accepteras. Grafprojektionen ska utgå från modellens identiteter och
verksamhetsinnehåll; leverans-id och transporttider ska inte göra oförändrat
innehåll till en ny verksamhetsförändring.

Dublettolerans gäller samma tillstånd. Ordningen mellan olika tillstånd är en
separat egenskap: outbox skickar i lokal lagringsordning och korrelations-id som
Kafka-nyckel håller en process på samma partition. Hantering av eventuella äldre
tillstånd vid återspelning i efterföljande led hör till masterdataflödets kontrakt.

## Leveransmilstolpar och avstämning

`lasStatus(id)` visar lokala observationstider för följande milstolpar:

| Steg | Positivt bevis |
| --- | --- |
| `LOKALT_LAGRAD` | Den lokala leveransen är beständig. |
| `KAFKA_PUBLICERAD` | Kafka-commit är bekräftad. |
| `RADATA_LAGRADE` | Rätt JSON och metadata finns beständigt i både objektlager och backendens sökindex. |
| `GRAFBEHANDLAD` | Grafsteget är klart: versionen har tillämpats, fanns redan eller har ersatts av en nyare. |

Betrodd infrastruktur kan anropa `bekrafta(leverans, steg)` efter ett positivt
REST-kvitto eller en observation från kedjan. Hela leveransen måste matcha den
lokala raden: id, korrelations-id, objekt-id, versioner, tid, JSON och signatur.
Kafka-kvittenser hanteras i stället genom `bekrafta(kvittens)`, som kontrollerar
leveransens fingeravtryck och sparar kvittot i inkorgen, även om cacheposten saknas.
Bekräftelser är monotona och kan inte sänka status eller skriva om dokumentet.
Ett grafkvitto fastställer även rådatalagring och tidigare Kafka-publicering.
Att observera grafens **indatatopic** fastställer däremot bara rådatalagring,
inte slutförd grafhantering. Ett misslyckat uppslag fastställer inget steg.

Återställning genom `Dokumentkalla` använder samma positiva rådatabevis efter
kärnans dokumentverifiering. Den kan därför lösa ett osäkert publiceringsutfall
utan en ny Kafka-leverans. Den valfria pipeline-demon har ett lokalt REST-
gränssnitt och en separat kvittotopic där rådata- och grafsteget publicerar
positiva kvitton. `Kvittenskonsument` lagrar dem i en beständig inkorg och
uppdaterar cachemilstolpar före offsetcommit. Saknade cacheposter stäms av mot
inkorgen vid återställning. Se [REST och kvittokanal](pipeline.md) för kontrakt,
konsumentgrupper och behörighetsantaganden. Företagets verkliga REST-tjänst är
fortfarande inte ansluten.

Milstolpen avser en viss leverans, inte processens senaste tillstånd. Förmånen
behöver inte invänta rådatalagring eller grafbehandling när rätt tillstånd finns
lokalt och den valda lagringspolicyn tillåter fortsättning.

## Formell granskning av leveransprotokollet

[TLA+-modellen](../spec/delivery/README.md) följer lokal commit, Kafka-commit,
rådatalagring, grafbehandling och fördröjda bekräftelser. Den kontrollerar
krascher, förlorade kvitton, återförsök, strikt processpaus, atomisk
vidareleverans med källoffset och grafens versionsskydd. Den kompletterande [kvittensmodellen](../spec/delivery/Receipts.tla) följer
kvittotopic, inkorg, offsetcommit, cacheförlust och återställning som separata
tillstånd. Den binder kvittot till en innehållstoken fristående från objektversion.
Modellernas koppling till Java-koden beskrivs i [modellöversikten](../spec/delivery/README.md);
de utgör inte en mekanisk förfiningskontroll. `./scripts/test-models.sh` kör modellkontrollerna
med en Java-JAR.

Det tidigare motexemplet med publicerad version 2 och kvarvarande lokal version
1 används inte längre som accepterad begränsning. `strict-ordering` är nu en
positiv kontroll; integrationstestet kräver att version 2 behålls och processen
pausas tills leveransen återhämtats. Negativa kontroller visar varför den nya
commit-ordningen, korrekta bekräftelser och grafens versionsskydd behövs.

[Backendmodellen](../spec/backend/README.md) fördjupar de separata skrivningarna
i objektlager och index. För det överenskomna steg N−1 gäller varianten som
kräver båda lagringarna före vidarepublicering. Varianten med objektbekräftelse
före index och hållbar indexreparation finns kvar som en undersökt alternativ
policy, inte som det valda flödet. Modellerna är inte mekaniskt sammansatta och
bevisar inte att Java-koden är en korrekt förfining av hela kedjan.

## Köra och konfigurera

```sh
docker compose up -d --wait postgres kafka
docker compose run --rm topic
mvn -q -Pdemo,graph verify
```

PostgreSQL och Kafka körs som separata tjänster i samma Compose-miljö. Data ligger
i Docker-volymer och finns kvar efter `docker compose stop`. Den lokala topicen
`ffa.hundbidrag` har en partition och en replika för utveckling; `acks=all` ger
alltså inte redundans i denna miljö.

Fortsätt samma process vid nästa programstart:

```sh
FFA_PROCESS_ID=hundbidrag-process-17 mvn -q -Pdemo verify
```

Kör samma kommando igen: demon återläser och uppdaterar det befintliga yrkandet.
För resilient lagring och senare återförsök:

```sh
FFA_LEVERANSLAGE=LOKAL_RESILIENS mvn -q -Pdemo verify
mvn -q -Pdemo -Dffa.demo.action=--aterforsok verify
```

| Miljövariabel | Standard |
| --- | --- |
| `FFA_JDBC` | `jdbc:postgresql://localhost:15432/ffa` |
| `FFA_DB_USER` / `FFA_DB_PASSWORD` | `ffa` / `ffa-demo` (lokalt utvecklingsexempel) |
| `FFA_KAFKA` | `localhost:19092` |
| `FFA_TOPIC` | `ffa.hundbidrag` |
| `FFA_LEVERANSLAGE` | `STRIKT` (standard) eller `LOKAL_RESILIENS` |
| `FFA_PROCESS_ID` | Nytt process-id per demokörning om inget anges |
| `FFA_NYCKLAR` | `.demo/nycklar` |

En annan förmån konfigurerar en egen topic, som behöver skapas före körning.
Utvecklingsnyckeln sparas utanför Git och `target`; radera eller ersätt den inte
om gamla signerade cacherader ska kunna verifieras. Nyckelrotation och identifiering
av olika betrodda nycklar behöver förvaltad hantering inför produktion.

Inspektera cachehistoriken:

```sh
docker compose exec postgres psql -U ffa -d ffa -c 'SELECT dataleverans_id, korrelations_id, objekt_version, skapad, lagrad, kafka_publicerad, radata_lagrade_tid, grafbehandlad_tid FROM ffa_dataleverans ORDER BY ordning;'
```

Förbered den lokala Docker-miljön och kör integrationstester:

```sh
./scripts/test-integration.sh
```

Skriptet kan anropas från valfri katalog och förutsätter att Docker-daemonen
är startad. Det hämtar vid behov avbildningarna, startar Kafka, PostgreSQL och
Neo4j (Compose-profilen `graph-tests`), väntar på hälsokontrollerna och skapar `ffa.hundbidrag` om topicen saknas.
Om förberedelsen misslyckas startas inte Maven-testerna. Testanslutningarna sätts
till projektets lokala Compose-tjänster, även om andra `FFA_*`-anslutningar finns
i den anropande miljön.

Vanliga enhetstester och graftester körs tillsammans med integrationstesterna.
Extra Maven-argument kan skickas vidare, exempelvis `./scripts/test-integration.sh -o`
om alla Maven-beroenden redan finns lokalt. Tjänster och volymer lämnas kvar efter
körning för fortsatt utveckling; stoppa med `docker compose stop` vid behov.
Tester använder egna topicnamn och rensar sina cacherader efteråt; testet mot
verklig Kafka tar även bort sin topic. Demons lagrade processtillstånd raderas inte.

Graftesterna ansluter till `bolt://localhost:17687`, med lokal testanvändare
`neo4j` och lösenord `ffa-demo-password`. Direktkörning kan konfigurera
`FFA_NEO4J`, `FFA_NEO4J_USER` och `FFA_NEO4J_PASSWORD`. I `ffa-graph` finns
drivrutinen som testberoende; den valfria `ffa-pipeline` använder den även i
den körbara demon. Testerna verifierar dubletter, äldre tillstånd, borttagna
relationer och samtidiga projektioner, och rensar endast egna slumpade identiteter.

Med tjänsterna redan förberedda kan testerna köras direkt:

```sh
mvn -q -Pgraph -Dffa.integration=true test
```

För hela kedjan, inklusive REST-återläsning och Kafka-kvitton:

```sh
./scripts/test-integration.sh --pipeline
```

Detta startar även RustFS och kör kedjetesterna. Se [pipeline-dokumentationen](pipeline.md)
för serverläge, endpoints, kvittensformat och konfiguration.
