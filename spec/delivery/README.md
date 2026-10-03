# Leverans, återhämtning och milstolpar som TLA+-modell

Modellen följer N−2 (lokal processcache), Kafka, N−1 (beständiga rådata) och N
(grafbehandling), samt hur positiva bekräftelser når tillbaka till cachen.
Den är separat från Maven och den körbara demon. TLC utforskar samtliga nåbara
förlopp inom de angivna gränserna, inte bara en lyckad körning.

## Kör med Java

```sh
./scripts/test-models.sh
./scripts/test-models.sh strict-live resilient-live strict-ordering
./scripts/test-models.sh kafka-before-cache false-graph-confirmation graph-regression offset-without-forward
```

Standardkörningen omfattar även [backendmodellen](../backend/README.md).
Skriptet hämtar officiella `tla2tools.jar` version 1.7.4, kontrollerar en fast
SHA-256 och lägger verktyg, loggar och motexempel under `target/tla/`, utanför
Git. Projektets JDK 25 räcker; Maven och Docker behövs inte. Efter första
hämtningen krävs ingen nätåtkomst.

`TLA_TOOLS_JAR` kan ange en redan hämtad fil med samma checksumma.
`TLC_WORKERS` (standard 2) och `TLC_HEAP` (standard `1g`) styr verktygets
resurser, inte modellens antal arbetare. TLC startar en lokal
övervakningssocket, vilket kan kräva tillstånd utanför en restriktiv sandlåda.

Officiella verktyg: [TLA+ Tools](https://github.com/tlaplus/tlaplus/blob/master/USE.md),
[version 1.7.4](https://github.com/tlaplus/tlaplus/releases/tag/v1.7.4).

## Tillstånd och koppling till implementationen

| Modell | Betydelse |
| --- | --- |
| `created`, `version` | Tilldelade leverans-id:n och deras oföränderliga innehållsversion |
| `cached`, `latest` | Beständiga lokala leveranser och högsta lokala objektversion |
| `sent` | Kafka-transaktioner som faktiskt committats och kan läsas med `read_committed` |
| `acknowledged` | Historik över commitkvitton som klienten faktiskt fått |
| `published` | Lokal positiv bekräftelse på publicering, från Kafka eller ett senare steg |
| `backend` | Både objektlager och sökbart index är beständiga för leveransen |
| `forwarded`, `offsetsCommitted` | Vidareleverans till grafens topic och källoffset som committats i samma Kafka-transaktion |
| `graph`, `graphLatest` | Slutförd grafhantering och grafens högsta version per process |
| `rawConfirmed`, `graphConfirmed` | Milstolpar som den lokala cachen observerat |
| `readVersion` | Senaste version som lämnats ut vid vanlig processläsning |
| `active`, `stage` | Pågående arbete under processens samordning |
| `stable` | Felperioden har upphört; används endast i framstegskontrollerna |

`Begin` motsvarar versionskontroll och processlås. `Prepare` gör leveransen
beständig lokalt i båda lägena. Ocommittade Kafka-records abstraheras bort:
`Accept` är **Kafka-commit**, inte enbart sändningskvitto. `Ack` kan dröja eller
utebli. `Commit` registrerar den lokala publiceringsflaggan.

Strikt läge håller arbetet samordnat över lokal commit och Kafka-commit.
Implementationens sessionslås släpps när den fysiska anslutningen stängs;
efter krasch återstår den beständiga väntande raden. Resilient läge släpper
låset efter lokal commit, och återförsöksarbetaren väljer äldsta väntande
leverans under ett nytt processlås.

`Read` och nya strikta lagringsförsök tillåts inte medan processen har väntande
leveranser. Det förhindrar vanlig fortsättning efter ett osäkert utfall.
Resilient läge får däremot läsa den nya lokala versionen före Kafka-publicering.
Historisk direktläsning per leverans-id för diagnos modelleras inte.

`Consume` abstraherar N−1:s beständiga objekt- och indexlagring. Den kan lyckas
innan vidareleveransen är klar. `Forward` committar vidareleverans och källoffset
atomiskt i Kafka; avbrott före detta lämnar underlaget för återbehandling kvar.
`GraphConsume` motsvarar beständig grafhantering:
äldre versioner behandlas utan att sänka grafens aktuella version.
`ObserveRaw` är ett positivt REST-kvitto eller kvitto från rådatastegets kvittotopic.
Den samlade rådatabekräftelsen abstraherar vilken positiv kanal som används:
REST kan bekräfta lagring före vidareleverans, medan topicobservation kräver
att vidareleveransen också har committats. `ObserveGraph` kräver ett kvitto på slutförd grafhantering, inte enbart dess
indatatopic. Grafkvittot kan observeras före rådatakvittot och etablerar båda
milstolparna. Bekräftelser är monotona.

Återförsök väljer `cached` minus `published`; de använder aldrig `sent` som ett
orakel. Såväl faktisk Kafka-commit utan kvitto som kvitto utan registrerad lokal
flagga kan därför leda till att samma leverans skickas igen.

## Egenskaper

| Kontroll | Krav |
| --- | --- |
| `DeliveryIdentity` | Observerat innehåll för ett id motsvarar dess ursprungliga token |
| `KafkaHasLocalCopy` | Varje committad Kafka-leverans har redan en beständig lokal kopia |
| `PublishedMeansAccepted` | Positiv lokal publiceringsstatus har faktiskt Kafka-underlag |
| `OffsetHasForwarded` | Källoffset och grafens vidareleverans committas tillsammans |
| `RawEvidenceSound`, `GraphEvidenceSound` | Bekräftelser avser faktiskt slutförda steg |
| `StrictReadHasEvidence` | Utlämnad version i strikt läge har positiv publiceringsbekräftelse |
| `NoConcurrentProcessWorkers` | Processens kritiska arbete är samordnat |
| `FirstAcceptanceOrdered` | Första publiceringen av nya leveranser går inte bakåt i version |
| `NoCacheRegression` | Äldre backenddata skymmer inte nyare lokalt tillstånd |
| `GraphNeverRegresses` | Fördröjda äldre leveranser sänker inte grafens version |
| `EventuallyDelivered` | Beständiga lokala leveranser får till slut positiv publiceringsstatus |
| `EventuallyInBackend`, `EventuallyInGraph` | Publicerat data behandlas till slut av de efterföljande stegen |
| `EventuallyObserved` | Slutförd grafhantering av lokalt lagrade leveranser blir till slut observerad |

Framstegskontrollerna kräver uttryckligen att felperioden tar slut och att
arbetare, återförsök, backend, graf och observationer får fortsätta arbeta
rättvist. Det är driftantaganden, inte garantier från Kafka. En avslutad demo
eller en permanent otillgänglig infrastruktur uppfyller inte dem.

## Begränsade körningar och motexempel

| Konfiguration | Processer | Arbetare | Leveranser/process | Resultat: distinkta tillstånd |
| --- | ---: | ---: | ---: | ---: |
| `strict` | 2 | 2 | 1 | 61 554 |
| `resilient` | 2 | 2 | 1 | 105 892 |
| `strict-recovery` | 1 | 2 | 3 | 705 974 |
| `strict-live` | 1 | 2 | 2 | 22 266 |
| `resilient-live` | 1 | 2 | 2 | 28 092 |
| `strict-ordering` | 1 | 2 | 3 | 705 974 |

De tidigare 26 kontrollerna slutfördes den 3 oktober 2026,
inklusive backendmodellen och de namngivna negativa kontrollerna. De två `live`-
konfigurationerna kontrollerar även de temporala egenskaperna. Gränserna hålls
små för att den utvidgade modellen ska gå att köra på en utvecklingsdator.

`strict-ordering` var tidigare ett förväntat motexempel på den gamla
commit-ordningen: Kafka kunde få version 2 medan cachen fortfarande hade
version 1. Det är nu en **positiv kontroll** av det förstärkta protokollet.
Integrationstestet
`striktLageBevararNyVersionOchPausarTillsLeveransenAterhamtats` kontrollerar
motsvarande Java-beteende med riktig PostgreSQL, signerade modellobjekt och
injekterat förlorat Kafka-kvitto. Efter återförsök används version 2 som grund
för version 3; ingen gammal version lämnas ut som normal fortsättning.

Sex negativa kontroller måste ge ett specifikt namngivet motexempel:

- `broken-lock`: två arbetare äger samma process utan samordning.
- `exactly-once`: samma id kan publiceras igen efter Kafka-commit och förlorad
  lokal kvittoflagga. Dublettolerans krävs även med Kafka-transaktioner.
- `kafka-before-cache`: Kafka committas utan beständig lokal kopia.
- `false-graph-confirmation`: lokalt lagrat data felaktigt räknas som grafbehandlat.
- `graph-regression`: en fördröjd äldre leverans ersätter grafens nyare version
  när versionsskyddet tas bort.

- `offset-without-forward`: källoffset committas utan att motsvarande
  vidareleverans till grafens topic finns. Kafka-transaktionen måste omfatta båda.

Syntaxfel och godtyckliga verktygsfel räknas aldrig som lyckade negativa tester.
Motexemplens händelseförlopp finns i körningens loggar.

## Avgränsning och fortsatt granskning

Modellkontrollen är inte ett bevis för godtyckligt många leveranser eller en
formell förfiningskontroll av Java-koden. Processen har ett modellobjekt.
JSON och signatur abstraheras till en oföränderlig token representerad av
objektversionen. Modellen verifierar inte kryptografi eller att två verkliga
JSON-dokument med samma objektversion alltid har samma verksamhetsinnehåll.

Beständig cache och Kafka-data försvinner inte vid processkrasch. Fullständig
lagringsförlust, retention, gallring, nätverksprotokoll och JDBC:s osäkra
commitkvitton modelleras inte i detalj. En krasch precis efter atomisk lokal
commit täcker däremot fallet att data består utan att normal operation avslutats.

`Restore` finns kvar som abstraktion för återställning efter cacheförlust, men
är inte nåbart när alla publicerade leveranser alltid finns kvar lokalt.
`ObserveRaw` täcker i stället avstämning av befintliga väntande rader.

[Backendmodellen](../backend/README.md) fördjupar objektlager och index som
separata skrivningar. För den valda kedjan krävs båda före vidareleverans;
modellerna är inte mekaniskt sammansatta.

Fortsatt arbete bör skilja verksamhetsinnehåll från objektversion i modellen,
pröva rättvis batchselektion vid många permanent felande processer och lägga
till cachegallring med förbud mot att radera obekräftade leveranser.

## Detaljerad kvittensmodell

[Receipts.tla](Receipts.tla) kompletterar leveransmodellen med explicita
kvittensmeddelanden, konsumentoffset, SQL-inkorg, cacheförlust och återställning.
Kör den separat med:

```sh
./scripts/test-models.sh receipts receipts-live receipts-two-deliveries receipts-corrupt
./scripts/test-models.sh receipts-offset-first receipts-graph-first receipts-missing-reconcile receipts-wrong-identity
```

Standardkörningen omfattar nu 34 konfigurationer: de tidigare 26 plus dessa
åtta. De fyra korrekta kvittenskonfigurationerna och de fyra namngivna
motexemplen kontrollerades den 3 oktober 2026.

| Modellsteg | Koppling till implementationen |
| --- | --- |
| `Publish` | N−2:s Kafka-commit för ett lokalt beständigt original |
| `PutObject`, `PutIndex` | Separata externa commits i `Radatalager.lagra` |
| `RawCommit` | `Radatasteg`: vidareleverans, rådatakvitto och källoffset i samma Kafka-commit |
| `WriteGraph`, `GraphCommit` | `Grafsteg`: Neo4j-commit före Kafka-commit av grafkvitto och grafens offset |
| `Poll` | Flyktig postposition hos `Kvittenskonsument` |
| `SqlCommit` | `PostgresKafkaLager.bekrafta(Kvittens)`: inkorg och cachemilstolpar i samma SQL-commit |
| `OffsetCommit` | Kvittenskonsumentens beständiga Kafka-offset efter SQL-commit |
| `Crash` | Tappar pollposition och lokal commitkunskap, behåller SQL-data och Kafka-offset |
| `Duplicate` | Ny Kafka-transaktion återlevererar ett redan publicerat kvitto |
| `Evict`, `Restore` | En publicerad cachepost saknas; verifierad rådataåterställning stämmer av inkorgen |

Kvitton finns som en begränsad sekvens, vilket skiljer två likadana Kafka-
records med olika offsets från en deduplicerad inkorgspost. Modellen binder
varje leverans till en oföränderlig innehållstoken, fristående från objektversion.
Tokenen abstraherar hela leveransens JSON, signatur och metadata; den beräknar
inte SHA-256 eller verifierar kryptografi.

Kontrollerna kräver att varje committad rådataoffset har både vidareleverans
och kvitto, att varje committad grafoffset har grafkvitto, och att kvitton med
rätt innehåll bara publiceras efter beständig hantering. `OffsetHasInbox`
kräver att **varje** post före konsumentens beständiga offset finns i inkorgen.
`ConfirmedIdentity` hindrar fel innehåll från att bekräfta en cachepost.
`RestoredGraphEvidence` kräver att grafkvitton i inkorgen följer med vid
återställning. SQL-transaktionerna är atomiska modellsteg; deras inbördes
samordning motsvarar implementationens leveranslås.

| Konfiguration | Leveranser | Extra dublett | Kontrollerade distinkta tillstånd |
| --- | ---: | ---: | ---: |
| `receipts` | 1 | 1 | 514 |
| `receipts-live` | 1 | 1 | 514, inklusive framstegsegenskaper |
| `receipts-two-deliveries` | 2 | 0 | 9 644 |
| `receipts-corrupt` | 1 | 1 | 2 234, inklusive ett kvitto med fel innehåll |

Fyra mutationer måste ge följande specifika motexempel:

- `receipts-offset-first`: offsetcommit före SQL-commit bryter `OffsetHasInbox`.
- `receipts-graph-first`: grafkvitto före Neo4j-commit bryter `ReceiptEvidenceSound`.
- `receipts-missing-reconcile`: återställning utan inkorgsavstämning bryter `RestoredGraphEvidence`.
- `receipts-wrong-identity`: borttagen innehållskontroll bryter `ConfirmedIdentity`.

`receipts-live` kräver att felperioden upphör och att producenter, lagringar,
konsument och återställning får arbeta rättvist. Då når accepterade leveranser
index och graf, grafkvittot blir beständigt i inkorgen, cacheposten återställs
med grafmilstolpen och topicens publicerade poster får till slut offsetcommit.
`CHECK_DEADLOCK FALSE` tillåter ett slutfört flöde att stanna utan nya aktiviteter;
de temporala egenskaperna kontrollerar att förväntat arbete faktiskt slutförs.

Det korrupta kvittot används endast för säkerhetskontroll, inte som ett löfte
om framsteg. Som i Java-koden kan ett felaktigt kvitto stoppa partitionen. Ett
kvitto för en saknad cachepost kan sparas innan innehållet kan jämföras; om det
sedan inte matchar originalet blockeras återställning. Betrodda avsändare,
behörigheter och operativ isolering av felaktiga kvitton behövs därför fortsatt.

`Receipts` använder samma commitgränser som kedjan, men är inte en mekanisk
förfining av `Delivery`, `Backend` eller Java-koden. Den börjar med lokalt
beständiga leveranser och fördjupar återkopplingen; skapande, strikt/resilient
policy, versionsordning och konkurrerande processkrivningar ligger kvar i
`Delivery`. Konflikter i externa lagrens identitetsbindning fördjupas i `Backend`.
Kvittomodellen antar beständiga Kafka/SQL-data, en kvittenspartition och ingen
retention eller inkorgsgallring. Offsettal abstraherar loggpositioner för synliga
kvitton, utan att modellera Kafkas interna transaktionsmarkörer.

Verifierad REST-dokumentåterläsning kräver både objekt och index och motsvarar
`Restore`. Metadataresursen motsvarar endast indexuppslag. Den återstående
HTTP-transporten, producenternas interna klienttillstånd och osäkra externa
commitkvitton modelleras inte på protokollnivå.
