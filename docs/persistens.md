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
`ordning`. Senaste lokala tillstånd väljs med `ordning`, så att lika tidsstämplar
eller en justerad klocka inte ger tvetydig ordning. Tiderna finns kvar för sökning
och spårning. `kafka_publicerad`, `kafka_publicerad_tid`, `leveransforsok` och
`senaste_fel` beskriver leveransstatus. Återförsök flyttar inte en gammal rad framåt
i processhistoriken.

## Två lagringslägen

Kafka och PostgreSQL har inte en gemensam atomisk transaktion. En Kafka-transaktion
omfattar Kafka; den kan inte rulla tillbaka en committad PostgreSQL-transaktion.
Demon använder därför två namngivna policyer i stället för att utlova atomisk
commit mellan systemen.

### KAFKA_FORST (standard)

1. Öppna PostgreSQL-transaktion och lås den lokala topicens skrivflöde.
2. Kontrollera processens identitet, förväntad objektversion och eventuella väntande leveranser.
3. Skicka JSON och headers med `acks=all`, och invänta Kafka-kvitto.
4. Skriv exakt samma dokument och signatur i cachen med `kafka_publicerad=true`.
5. Committa PostgreSQL-transaktionen och returnera Java-objektet.

Ett Kafka-fel ger ingen ny lokal kopia. Det motsvarar kravet att lokal lagring
aldrig sker utan Kafka-kvitto. Om Kafka lyckas men INSERT eller commit misslyckas
kan leveransen däremot finnas i masterdataflödet utan lokal kopia. `Leveransfel`
rapporterar dataleverans-id och om Kafka redan kvitterat. Reparation av cachen
från masterdataflödet ingår ännu inte i PoC:en. Även ett förlorat kvitto kan ge
ett osäkert utfall: meddelandet kan ha tagits emot trots att klienten får timeout.

### LOKAL_RESILIENS

1. Lås och kontrollera versionen i PostgreSQL-transaktionen.
2. Skriv dokument, signatur och metadata som en väntande leverans i samma transaktion.
3. Committa den lokala kopian.
4. Försök skicka väntande leveranser i ursprunglig lagringsordning.
5. Efter kvitto markeras leveransen som publicerad.

Ett Kafka-fel hindrar inte framgångsrik lokal lagring. Återläsning av processens
senaste tillstånd inkluderar även väntande leveranser. Ett databasfel före commit
returneras som fel och leder inte till någon Kafka-publicering.

Väntande leveranser överlever omstart. Varje ny lokal lagring försöker tömma en
batch om högst 100 rader; ytterligare batcher kan köras med återförsökskommandot.
Återförsök återanvänder id, JSON och signatur. Vid fel stoppas batchen innan senare
leveranser skickas. Ett lås per topic samordnar skribenter och replay även mellan
flera instanser i denna PoC. Ett mer skalbart genomförande kan dela upp låsningen
per process, men måste då bevara ordning och versionskontroll.

Om processen kraschar efter Kafka-kvitto men före uppdaterad lokal flagga kan
samma dataleverans skickas igen. Leveransgarantin är därför minst en gång.
Producentens idempotens skyddar dess interna nätverksåterförsök, inte replay
efter en omstart. Dubletter accepteras i det befintliga masterdataflödet enligt
beskrivningen nedan; separat deduplicering behöver därför inte införas i förmånen.
Det finns ingen automatisk bakgrundsarbetare i demon; använd det uttryckliga
återförsökskommandot eller anropa `skickaVantande` från en schemalagd körning.

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
Den lokala lagringsordningen används fortsatt för senaste tillstånd; UUID:t
ersätter inte versionskontroll eller ordning vid återförsök.

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
| `FFA_LEVERANSLAGE` | `KAFKA_FORST` eller `LOKAL_RESILIENS` |
| `FFA_PROCESS_ID` | Nytt process-id per demokörning om inget anges |
| `FFA_NYCKLAR` | `.demo/nycklar` |

En annan förmån konfigurerar en egen topic, som behöver skapas före körning.
Utvecklingsnyckeln sparas utanför Git och `target`; radera eller ersätt den inte
om gamla signerade cacherader ska kunna verifieras. Nyckelrotation och identifiering
av olika betrodda nycklar behöver förvaltad hantering inför produktion.

Inspektera cachehistoriken:

```sh
docker compose exec postgres psql -U ffa -d ffa -c 'SELECT dataleverans_id, korrelations_id, objekt_version, skapad, lagrad, kafka_publicerad FROM ffa_dataleverans ORDER BY ordning;'
```

Förbered den lokala Docker-miljön och kör integrationstester:

```sh
./scripts/test-integration.sh
```

Skriptet kan anropas från valfri katalog och förutsätter att Docker-daemonen
är startad. Det hämtar vid behov avbildningarna, startar Kafka och PostgreSQL,
väntar på båda hälsokontrollerna och skapar `ffa.hundbidrag` om topicen saknas.
Om förberedelsen misslyckas startas inte Maven-testerna. Testanslutningarna sätts
till projektets lokala Compose-tjänster, även om andra `FFA_*`-anslutningar finns
i den anropande miljön.

Vanliga enhetstester och graftester körs tillsammans med integrationstesterna.
Extra Maven-argument kan skickas vidare, exempelvis `./scripts/test-integration.sh -o`
om alla Maven-beroenden redan finns lokalt. Tjänster och volymer lämnas kvar efter
körning för fortsatt utveckling; stoppa med `docker compose stop` vid behov.
Tester använder egna topicnamn och rensar sina cacherader efteråt; testet mot
verklig Kafka tar även bort sin topic. Demons lagrade processtillstånd raderas inte.

Med tjänsterna redan förberedda kan testerna köras direkt:

```sh
mvn -q -Pgraph -Dffa.integration=true test
```
