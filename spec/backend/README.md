# Backendindex och object store

Den här TLA+-modellen granskar nästa gräns i masterdataflödet: ett inkommande
Kafka-meddelande skrivs till ett PostgreSQL-index och till object store.
Dataleverans-id är object-store-nyckel och indexets primärnyckel; korrelations-id
används för återläsning av processtillstånd.

Modellen beskriver ett möjligt backendkontrakt. Företagets backendimplementation
finns inte i detta projekt, och modellen är inte ett påstående om dess nuvarande
beteende eller leverantörernas API-garantier.

## Körning

Samma Java-JAR och skript som för [leveransmodellen](../delivery/README.md) används:

```sh
./scripts/test-models.sh
./scripts/test-models.sh backend-index-first backend-object-first
./scripts/test-models.sh backend-index-live backend-object-live backend-repair-live
```

Standardkörningen kontrollerar båda modellerna, inklusive förväntade motexempel.
Ingen databas eller Docker behövs. JAR, tillstånd och loggar ligger under
`target/tla/`. Skriptet accepterar bara ett förväntat negativt resultat om TLC
rapporterar rätt namngiven invariant; ett godtyckligt verktygsfel räcker inte.

## Lagringsbevis och återläsning är olika saker

Tre tillstånd behöver hållas isär:

| Tillstånd | Vad vi vet |
| --- | --- |
| Indexrad finns, objekt saknas | En avsikt eller ett ofullständigt skrivförsök finns. Data är inte bekräftat lagrat. |
| Objekt finns och matchar leveransen, index saknas | Data är lagrat och kan nås med känt leverans-id. Processuppslag är ännu inte färdigt. |
| Index och objekt finns och matchar | Data kan återläsas genom korrelations-id. |

`HasObject` betyder verifierat matchande objekt, inte enbart att nyckeln finns.
Detta behöver motsvaras av ett tillförlitligt skrivkvitto eller återläsning som
kontrollerar rätt innehåll och metadata. En konfliktsvarskod eller en indexrad
är inte i sig ett sådant bevis.

Varje modellerad leverans innehåller id, korrelations-id, objektversion och ett
opaque innehållstoken. Två kopior av samma leverans är tillåtna. Konfliktfall
använder samma id med ändrat innehåll eller ändrad korrelationsmetadata.
Den första accepterade bindningen av id bestämmer vilket data senare försök
måste matcha; modellen kan inte avgöra vilken av två motsägande inleveranser
som var legitim om den felaktiga kommer först. Autentisering och verifiering
av signaturer ligger före denna gräns och modelleras inte här.

## Båda skrivordningarna

`INDEX_FIRST` skriver indexraden före objektet. Ett avbrott kan lämna en rad som
ännu inte har något matchande objekt. Bekräftelse och processuppslag kräver
fortfarande lagringsbevis från object store. Ett nytt försök återanvänder samma
id och originaldata och får fortsätta från en redan befintlig indexrad om den
matchar. Andra indexfel och konflikter får inte behandlas som lyckad lagring.

`OBJECT_FIRST` skriver objektet före indexraden. Ett avbrott kan lämna ett objekt
som ännu inte kan återsökas med korrelations-id. Återförsök måste kunna verifiera
objektet under samma nyckel och fortsätta med indexet. Ett lyckat objektkvitto
är tillräckligt för att veta att JSON är lagrat, men inte för att lova att nästa
processaktivitet kan hitta det.

För båda ordningarna krävs atomiska, villkorade skrivningar **inom respektive
lagringssystem**: lägg in om nyckeln saknas, eller verifiera att befintlig bindning
är identisk. Ett separat läs-före-skriv-test utan atomiskt skydd räcker inte mot
samtidiga skribenter. Modellen antar ingen gemensam transaktion mellan systemen.
Den första lagringen fungerar som bindningspunkt; den andra återanvänder samma
id och data. Inga nya dataleverans-id:n skapas vid backendens återförsök.

Objektets modellerade bindning omfattar även metadata. För en verklig object
store som endast innehåller opaque JSON måste det klarläggas hur originalmetadata
bevaras och jämförs: exempelvis objektmetadata eller ett gemensamt register.
Särskilt vid `OBJECT_FIRST` får man inte anta att metadata kan rekonstrueras från
JSON om det i verkligheten bara kom i Kafka-headers.

## När får mottagningen bekräftas?

`BOTH_REQUIRED` låter backend bekräfta Kafka-meddelandet först när index och
objekt båda finns och matchar. Vid fel före detta behålls meddelandet för nya
försök. En krasch efter lagring men före bekräftelse kan ge en dublett; samma
bindning gör den ofarlig.

`OBJECT_SUFFICIENT` tillåter bekräftelse när objektet är verifierat lagrat, även
om indexet saknas. **Om vi också lovar framtida uppslag med korrelations-id**
måste indexreparation fortfarande vara möjlig efter krasch. Den modellerade
lösningen skriver därför ett hållbart reparationsuppdrag före bekräftelsen.
Uppdraget bevarar leveransmetadata och referensen till originalet.

Detta är inte ett krav på en viss tabellutformning. Ett annat hållbart sätt att
återskapa indexet kan också fungera, men behöver en motsvarande garanti. Enbart
ett felmeddelande i en logg eller flyktig processvariabel ger inte garantin i
denna modell. Utan index och utan bevarad metadata räcker object-store-nyckeln
inte alltid för att återskapa korrelationsuppslaget.

Indexrad och reparationsuppdrag behandlas som två delar av samma PostgreSQL-
transaktion när indexet repareras: uppdraget tas bort först när rätt indexrad
är committad. Object-store-skrivningen är fortfarande en separat operation.
Om skrivning av uppdraget misslyckas efter objektlagring sker ingen bekräftelse;
Kafka-meddelandet finns kvar för ett nytt försök. Om indexet redan finns och
matchar behövs inget nytt uppdrag, även om ett upprepat INSERT rapporterar en
unikhetskonflikt.

## Senaste processtillstånd

Den säkra läspolicyn väljer högsta indexerade objektversion och kontrollerar
sedan att objektet matchar. En högre indexversion med saknat objekt är ett
ofullständigt tillstånd. Modellen lämnar då inte ut ett äldre objekt som om det
vore senaste tillstånd; läsningen behöver vänta eller rapportera ofullständighet.
Ett backend-API bör skilja detta från att processen aldrig har funnits.

Policyn `LAST_WRITE` väljer senast skrivna indexrad. Ett fördröjt meddelande,
ett återförsök eller en reparation av en äldre rad kan då flytta uppslaget bakåt
i objektversion. Motexemplet visar varför mottagningstid eller indexskrivningstid
inte ensam kan definiera senaste verksamhetstillstånd.

Denna modell har olika versioner för olika originalleveranser och prövar inte
konflikten mellan olika verksamhetsinnehåll med samma objektversion. Inte heller
lika versioners leveranshistorik eller tidsstämplar modelleras. Dessa frågor
behöver ett separat kontrakt; versionsordning ensam löser inte sådana konflikter.

## Blandade ordningar under exempelvis en övergång

Ordningarna är säkra var för sig under modellens antaganden. `MIXED` låter en
arbetare använda index först och en annan objekt först.

När inleveranserna har samma id och samma data är även blandningen säker inom
den kontrollerade modellen. Med motsägande inleveranser kan däremot arbetare A
binda id till data A i indexet samtidigt som arbetare B binder samma id till
data B i object store. Båda första skrivningarna är villkorade och atomiska, men
har olika bindningspunkter. Ett efterföljande konfliktsvar kan inte göra den
redan genomförda första skrivningen ogjord.

Om backend ska upptäcka och avvisa sådana konflikter behövs en gemensam regel
för första bindningen av id, även under konfigurationsbyten och omstarter.
Alternativt måste ett separat, hållbart register samordna bindningen. Detta är
ett kontraktskrav som inte följer av atomiska operationer i vardera lagringen.

## Tillstånd och egenskaper

| Tillstånd | Betydelse |
| --- | --- |
| `objects`, `index` | Hållbara bindningar av id till innehåll och metadata |
| `repair` | Hållbara uppdrag för indexreparation |
| `active`, `stage` | Arbetarnas pågående meddelanden och nästa skrivsteg |
| `completed`, `rejected` | Bekräftade respektive avvisade meddelanden |
| `history`, `returned` | Granskningshistorik för bindningar och utlämnat data |
| `lastIndexed`, `readHighest`, `readOrdered` | Hjälpvariabler för läsordningen |
| `stable` | Om den godtyckligt långa felperioden är över |

| Egenskap | Krav |
| --- | --- |
| `IdentityImmutable` | Samma id får inte bindas till två olika innehåll/metadata |
| `StoresAgree` | Index och objekt måste matcha när båda finns |
| `ConfirmedHasObject` | Bekräftade meddelanden har verifierbart lagrat objekt |
| `ConfirmedHasIndexOrRepair` | Bekräftelse lämnar inte indexreparation utan hållbar grund |
| `ReturnedHasObject` | Processläsning lämnar bara ut faktiskt lagrat, matchande data |
| `ReadDoesNotRegress` | Lyckade processuppslag går inte bakåt i objektversion |
| `EventuallySettled` | Varje inkommande meddelande blir till slut bekräftat eller avvisat |
| `EventuallyDiscoverable` | Varje bekräftad leverans får till slut ett matchande index |

Säkerhetsmodellerna tillåter krascher och skrivfel vid alla aktiva steg. De tar
inte bort redan committad data. Framstegsmodellerna kräver att tjänsterna till
slut återhämtar sig, att meddelanden som ännu inte bekräftats finns kvar, och att
arbetare och indexreparation får fortsätta rättvist. Permanent indexfel uppfyller
inte dessa antaganden; modellen lovar därför inte återsökbarhet under ett sådant fel.

## Konfigurationer och förväntade motexempel

| Positiv kontroll | Innehåll |
| --- | --- |
| `backend-index-first`, `backend-object-first` | Två arbetare och processer, båda ordningarna, konfliktande inleveranser |
| `backend-object-confirm` | Objektbekräftelse med hållbart reparationsuppdrag |
| `backend-index-live`, `backend-object-live` | Eventualitet med båda lagringarna som bekräftelsekrav |
| `backend-repair-live` | Eventualitet efter objektbekräftelse och senare indexreparation |
| `backend-duplicates` | Två meddelandekopior per variant av samma id, samtidiga återförsök och konflikter |
| `backend-version-read` | Två objektversioner som kan indexeras i omvänd ordning |
| `backend-mixed-valid` | Blandade ordningar med oförändrade inleveranser |

De vanliga säkerhetsmodellerna har två processer, två arbetare och ett id per
process. Dublettmodellen har en process, två arbetare och två meddelandekopior
för varje innehållsvariant. Framstegs- och versionsmodellerna har en process,
två arbetare och två versioner. Fullständiga gränser och antaganden finns i
respektive `.cfg`.

Skriptet ska också hitta fem bestämda motexempel:

- `backend-index-proof`: en indexrad tas felaktigt som lagringsbevis och
  mottagningen bekräftas innan objektet finns.
- `backend-missing-repair`: objektet är lagrat, men mottagningen bekräftas utan
  index eller hållbar information för att återställa indexet.
- `backend-overwrite`: befintlig bindning får skrivas över utan innehållsjämförelse.
- `backend-arrival-latest`: ett sent indexerat äldre tillstånd ersätter ett nyare
  vid processuppslag.
- `backend-mixed-orders`: två ordningar binder samma id till olika data i varsin
  lagring, trots att varje första skrivning är villkorad.

## Verifierat resultat vid införandet

Med projektets JDK 25 och den fastlagda TLA+-JAR-filen slutfördes de nio positiva
backendkontrollerna utan fel. De två skrivordningarna omfattade vardera 488 346
nåbara tillstånd; totalt kontrollerades 1 086 046 tillstånd över de positiva
backendkonfigurationerna. De tre framstegskonfigurationerna kontrollerade också
de temporala egenskaperna. Alla fem negativa backendkontroller gav det avsedda,
namngivna motexemplet. Backendens 14 konfigurationer ingår även i den utvidgade leveransmodellens
standardkörning.

En lokal backend har nu lagts till i den valfria modulen `ffa-pipeline`.
[Hela-kedjan-testerna](../../docs/pipeline.md) kör objekt-först med både objekt
och index som villkor för Kafka-vidareleverans. De ansluter till riktig RustFS,
PostgreSQL, Kafka och Neo4j. Detta är fortfarande inte företagets backend eller
en mekaniskt verifierad förfining av modellen.

## Kopplingen till den första modellen

`Delivery.Consume` abstraherar hela backendflödet till att data är tillgängligt.
Den nya modellen visar vad den abstraktionen behöver betyda. För uppslag med
korrelations-id krävs matchande index och objekt; en bekräftad objektlagring med
väntande indexreparation motsvarar ännu inte detta tillstånd.

Modellerna är separata och deras samband har inte bevisats med formell förfining.
TLC kontrollerar de angivna ändliga tillståndsrummen, inte företagets Java-kod
eller alla möjliga storlekar. Fullständiga lagringskvitton, atomiska villkorade
skrivningar, verifierade originaldata och hållbara reparationsuppdrag är explicita
antaganden som måste kontrolleras mot det verkliga backendkontraktet.
