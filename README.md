# Förenklad IT-arkitektur för förmåner
Detta är en (fungerande) demo, som är menat att vara utgångspunkt för vidare diskussioner kring hur vi kan
underlätta för team att bygga lösningar baserade på FFA och som integreras med Mimer.

Frågan vi ställer är om det är möjligt att dölja vissa grepp från utvecklingsteam, så att de
helt kan fokusera på affärslogik och inte behöva engagera sig i översättning från "förmånsspråk"
till "organisationsspråk" samt detaljerna kring serialisering av processtillstånd.

## Vi tar utgångspunkt i behoven hos ett team som utvecklar affärslogik med FFAs informations- och datamodell som grund

Affärslogiken utvecklas mot kärnobjekten i FFAs model, så som Yrkan, Ersättning, Beslut, osv. Ansatsen
är att erbjuda en versionshanterad standard-implementation av dessa kärnobjekt, som teamet kan utvidga/modifiera
vid behov. För utvidning och modifikation används helt enkelt arvsmekanismen i Java.

Vi kan förfara på liknande sätt för en eventuell C++ realisering.

### Basklass för livscykelhanterade objekt enligt FFA
```java
package se.fk.data.modell.v1;

import ...

/**
 * Utgör bas för att identifiera ett objekt (“id”), för att upptäcka att ett
 * livscykelhanterat objekt har ändrats (genom användning av en kontrollsumma/digest),
 * för att automatiskt inkrementera versionsnumret när en ändring upptäckts,
 * samt för att markera att ett objekt har ändrats (via en uppmärksamhetsflagga) så att
 * mottagaren inte behöver jämföra med redan lagrade data. 
 */
public class Livscykelhanterad {

    @JsonIgnore
    private transient byte[] __digest;

    @JsonIgnore
    public Boolean __attention = null;

    @JsonProperty("id")
    public String id;

    @JsonProperty("version")
    public int version = 0;

    ...
}
```

### Yrkan

```java
package se.fk.data.modell.v1;

import ...

@Context("https://data.fk.se/kontext/std/yrkan/1.0")
public class Yrkan extends Livscykelhanterad {

    @Som(typ = "ffa:yrkande")
    @JsonProperty("person")
    public Person person;

    @JsonProperty("beskrivning")
    public String beskrivning;

    @JsonProperty("producerade-resultat")
    public Collection<ProduceratResultat> produceradeResultat;

    @JsonProperty("beslut")
    public Beslut beslut;

    ...
}
```

### Fysisk person
```java
package se.fk.data.modell.v1;

import ...

@Context("https://data.fk.se/kontext/std/fysiskperson/1.0")
public class FysiskPerson extends Person{

    @PII(typ="pii:personnummer")
    @JsonProperty("personnummer")
    public String personnummer;

    ...
}
```

### Beslut

```java
package se.fk.data.modell.v1;

import ...

@Context("https://data.fk.se/kontext/std/beslut/1.0")
public class Beslut extends Livscykelhanterad {

    public enum Typ {
        INTERRIMISTISK(1),
        STALLNINGSTAGANDE(2),
        SLUTLIGT(3);

        ...
    }

    public enum Utfall {
        BEVILJAT(1),
        AVSLAG(2),
        DELVIS_BEVILJANDE(3),
        AVVISNING(4),
        AVSKRIVNING(5);

        ...
    }

    public enum Lagrum {
        SFB_K112_P2a("SFB Kap. 112 § 2a"),
        SFB_K112_P3("SFB Kap. 112 § 3"),
        SFB_K112_P4("SFB Kap. 112 § 4"),
        SFB_K113_P3_S1("SFB Kap. 113 § 3 p. 1"),
        SFB_K113_P3_S2("SFB Kap. 113 § 3 p. 2"),
        SFB_K113_P3_S3("SFB Kap. 113 § 3 p. 3"),
        FL_P36("FL § 36"),
        FL_P37("FL § 37"),
        FL_P38("FL § 38");

        ...
    }

    @JsonProperty("datum")
    public Date datum;

    @JsonProperty("beslutsfattare")
    public String beslutsfattare;

    @JsonProperty("typ")
    public Typ typ;

    @JsonProperty("utfall")
    public Utfall utfall;

    @JsonProperty("organisation")
    public String organisation;

    @JsonProperty("lagrum")
    public Lagrum lagrum;

    ...
}
```

### Ersättning

```java
package se.fk.data.modell.v1;

import ...

@Context("https://data.fk.se/kontext/std/ersattning/1.0")
public class Ersattning extends Livscykelhanterad {

    @JsonProperty("typ")
    public String typ;

    @Belopp
    @JsonProperty("belopp")
    public double belopp;

    ...
}
```

Detta är en generell basklass för ersättning, så vi vill inte säga något särskilt kring vilken typ
av `belopp` detta rör sig om. Om man finner det lämpligt så kan man specialisera ersättningsobjektet 
vid användning och direkt i koden ange detta:

```java
    @Belopp(valuta = "SEK", skattestatus = "SKATTEPLIKTIG", period = "PER_DAG")
    @JsonProperty("belopp")
    public double belopp;
```

### Utvidgning av Yrkan för Hundbidraget (löjligt exempel)
I detta exempel, så har FFA-standard Yrkan utvidgats med uppgift om hundens ras. För
utvidgning används Javas arvsmekanism.

```java
package se.fk.hundbidrag.modell;

import
import se.fk.data.modell.v1.Yrkan; ...

@Context("https://data.fk.se/kontext/hundbidrag/yrkan/1.0")
public class Yrkan extends se.fk.data.modell.v1.Yrkan {

    @JsonProperty("ras")
    String ras;

    ...
}
```

## MimerProxy
Standardkonfigurationen för serialisering/deserialisering finns i `MimerProxy.defaultInstance()`.

Serialisering:
```java
MimerProxy proxy = MimerProxy.defaultInstance();
String json = proxy.serializePretty(yrkan); 
...
```

Deserialisering:
```java
MimerProxy proxy = MimerProxy.defaultInstance();
Yrkan value = proxy.deserialize(json, Yrkan.class); 
...
```

Serialisering och signering:
```java
KeyMaterialLoader.KeyMaterial km = KeyMaterialLoader.loadFromFiles(
        Path.of("/etc/keys/tls.key"),
        Path.of("/etc/keys/tls.crt"),
        Path.of("/etc/keys/chain.crt"),
        Path.of("/etc/keys/ca.crt")
);
```
alternativt
```java
KeyMaterialLoader.KeyMaterial km = KeyMaterialLoader.loadFromEnv(
        "MIMER_PRIVATE_KEY",
        "MIMER_SIGNER_CERT",
        "MIMER_CERT_CHAIN",
        "MIMER_TRUST_ANCHORS"
);
```

```java
MimerProxy proxy = MimerProxy.defaultInstance();
MimerProxy.SignedJson signed = proxy.serializeAndSign(objekt, km, "key-1");
MyType value = proxy.verifyAndDeserialize(
        signed.jsonBytes(),
        signed.signatureBytes(),
        km.signerCertificate(),
        km.certificateChain(),
        km.trustAnchors(),
        false,
        MyType.class
);
```

## Realisering av affärslogik

```java
package se.fk.hundbidrag;

import ...
```
Instansiera objekt efter behov, huvudsakligen från FFAs standardmodell, men också egna utvidgningar.
I detta exempel används Hundbidragets Yrkan (istället för FFA-standard Yrkan), som är
utvidgat med uppgift om hundens ras.

```java
// -------------------------------------------------------------------
// Använd FFAs objektmodell för affärslogik i specifik förmånskontext
// -------------------------------------------------------------------

// Efter etablering av yrkan och i samband med initiering av yrkansflöde
Yrkan yrkan = new Yrkan("Hundutställning (inkl. bad)","Collie");
{
    FysiskPerson person = new FysiskPerson("<personnummer>");

    yrkan.setPerson(person);
}

// Efter bedömning av rätten till...
{
    RattenTillPeriod rattenTillPeriod = new RattenTillPeriod();
    rattenTillPeriod.omfattning = RattenTillPeriod.Omfattning.HEL;
    rattenTillPeriod.ersattningstyp = Ersattning.Typ.HUNDBIDRAG;

    yrkan.addProduceradeResultat(rattenTillPeriod);
}

// Efter beräkning...
{
    Ersattning ersattning = new Ersattning();
    ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
    ersattning.belopp = 1000.0;
    ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));
    
    yrkan.addProduceradeResultat(ersattning);
}
{
    Ersattning ersattning = new Ersattning();
    ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
    ersattning.belopp = 500.0;
    ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));
    
    yrkan.addProduceradeResultat(ersattning);
}

// I samband med beslut, så utfärdar vi ett "Hittepå"-intyg
{
    Intyg intyg = new Intyg();
    intyg.beskrivning = "Hittepå";
    intyg.giltighetsperiod = new Period(Date.from(Instant.now().truncatedTo(DAYS)));
    
    yrkan.addProduceradeResultat(intyg);
}
{
    Beslut beslut = new Beslut();
    beslut.datum = Date.from(Instant.now().truncatedTo(DAYS));
    
    yrkan.setBeslut(beslut);
}
```

Vi har nu en uppsättning nya objekt, som inte persisterats. I samband med persistering,
så görs en serialisering av processens hela nuvarande tillstånd till JSON. 

```java
// Initial serialize to JSON
MimerProxy proxy = MimerProxy.defaultInstance();
String json = proxy.serializePretty(yrkan);
log.debug("Object -> JSON:\n{}", json);
```
Låt oss titta på loggen som svarar mot serialiseringen:
```terminaloutput
se.fk.data.modell.json.PropertySerializerModifier @Som property se.fk.hundbidrag.modell.Yrkan#person
se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.hundbidrag.modell.Yrkan
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.hundbidrag.modell.Yrkan@57f1e6fd
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@57f1e6fd
se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.Beslut
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Beslut@3e993999
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Beslut@3e993999
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Beslut@3e993999
se.fk.data.modell.json.PropertySerializerModifier @PII property se.fk.data.modell.v1.FysiskPerson#personnummer
se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.RattenTillPeriod
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.RattenTillPeriod@78c9f86a
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.RattenTillPeriod@78c9f86a
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.RattenTillPeriod@78c9f86a
se.fk.data.modell.json.PropertySerializerModifier @Belopp property se.fk.data.modell.v1.Ersattning#belopp
se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.Ersattning
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@77aba078
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@77aba078
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@77aba078
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@4de844c7
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@4de844c7
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@4de844c7
se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.Intyg
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Intyg@78b4ecd8
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Intyg@78b4ecd8
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Intyg@78b4ecd8
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.hundbidrag.modell.Yrkan@57f1e6fd
```
Låt oss titta på vad som händer med `se.fk.data.modell.v1.FysiskPerson#personnummer` och 
`se.fk.data.modell.v1.Ersattning#belopp` vid serialisering efter att vi tittat på producerad JSON.

```json
{
  "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
  "__attention" : true,
  "id" : "019c0abb-460a-7314-9ea9-d42e157f6b9f",
  "version" : 1,
  "beskrivning" : "Hundutställning (inkl. bad)",
  "beslut" : {
    "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
    "__attention" : true,
    "id" : "019c0abb-460b-76b2-92d9-35ece1079334",
    "version" : 1,
    "datum" : "2026-01-29T00:00:00.000Z"
  },
  "person" : {
    "varde" : {
      "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
      "personnummer" : {
        "varde" : "19121212-1212",
        "typ" : "pii:personnummer"
      }
    },
    "typ" : "ffa:yrkande"
  },
  "producerade_resultat" : [ {
    "@context" : "https://data.fk.se/kontext/std/ratten-till-period/1.0",
    "__attention" : true,
    "id" : "019c0abb-460a-749c-ade6-5c4ea602d184",
    "version" : 1,
    "ersattningstyp" : "HUNDBIDRAG",
    "omfattning" : "HEL"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "id" : "019c0abb-460a-7720-ba46-bb0d70c64a76",
    "version" : 1,
    "belopp" : {
      "varde" : 1000.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "id" : "019c0abb-460a-72ce-82ff-c2a595e3738f",
    "version" : 1,
    "belopp" : {
      "varde" : 500.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/intyg/1.0",
    "__attention" : true,
    "id" : "019c0abb-460a-722f-bb81-752e510c749b",
    "version" : 1,
    "beskrivning" : "Hittepå",
    "giltighetsperiod" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    }
  } ],
  "ras" : "Collie"
}
```

Notera hur fältet `belopp` i `Ersättning`, som i Java var annnoterat med `@Belopp`, har
expanderats i samband med serialiseringen. `@Belopp`-annoteringen indikerar att vi för detta fält
behöver fånga förmånskontext kring beloppsuppgiften. Fältet `belopp` har i samband med serialisering
expanderats till:

```json
"belopp" : {
    "varde" : 1000.0,
    "valuta" : null,
    "skattestatus" : null,
    "period" : null
}
```
Samma sak gäller `personnummer` i `FysiskPerson`, som annoterats med `@PII(typ="pii:personnummer")` 
och som därför expanderats till:

```json
"person" : {
    "varde" : {
        "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
        "personnummer" : {
            "varde" : "19121212-1212",
            "typ" : "pii:personnummer"
        }
    },
    "typ" : "ffa:yrkande"
},

```

Detta möjliggör att vi (senare) kan tillföra kontext vid överföring från Hundbidragets
förmånskontext (förmånsspråk) till FFAs utbyteskontext (organisationsspråk). 

Det finns i allmänhet två varianter av dessa annoteringar:
 * en som expanderas i producerad JSON men med 'null'-värden och som möjliggör att dessa värden tillförs i ett eftersteg, samt
 * en som expanderas men med hårdkodade värden från källkoden, där man tillför kontext direkt i realiseringen.

I fallet med `@Belopp`-annoteringen så vore det inte lämpligt att göra detta i en basklass
(där annoteringen sitter på standard-realiseringen av `Ersättning`) eftersom vi har både
dagförmåner och periodbaserade förmåner, samt både skattade och oskattade ersättningar.

Här bör kontext kring överföring av 'belopp' tillföras i ett eftersteg via context-hanteringen,
som beror på `@Context`-annoteringen.

Nåväl; vi föreställer oss att vi vid ett senare tillfälle återhämtar processes tillstånd, varvid
vi erhåller en JSON (samma JSON som vi tidigare producerade) och deserialiserar denna:

```java
MimerProxy proxy = MimerProxy.defaultInstance();
Yrkan yrkan = proxy.deserialize(json, Yrkan.class);
log.debug("JSON -> Object:\n{}", yrkan);
```

Låt oss titta på loggen:

```terminaloutput
se.fk.data.modell.json.PropertyDeserializerModifier @Som property se.fk.hundbidrag.modell.Yrkan#person
se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.hundbidrag.modell.Yrkan
se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.Beslut
se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.ProduceratResultat
se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Beslut@0cfb8015
se.fk.data.modell.json.PropertyDeserializerModifier @PII property se.fk.data.modell.v1.FysiskPerson#personnummer
se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.RattenTillPeriod
se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.RattenTillPeriod@23139d2a
se.fk.data.modell.json.PropertyDeserializerModifier @Belopp property se.fk.data.modell.v1.Ersattning#belopp
se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.Ersattning
se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Ersattning@5a87223c
se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Ersattning@6d9d66e4
se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.Intyg
se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Intyg@7d89542a
se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.hundbidrag.modell.Yrkan@0242194c
se.fk.hundbidrag.Applikation JSON -> Object:
Yrkan{
   id='019c0abb-460a-7314-9ea9-d42e157f6b9f', 
   version=1, 
   beskrivning='Hundutställning (inkl. bad)', 
   person=FysiskPerson{
      personnummer='19121212-1212'}, 
      beslut=Beslut{
         id='019c0abb-460b-76b2-92d9-35ece1079334', 
         version=1, 
         datum='2026-01-29', 
         beslutsfattare=, 
         typ=, 
         utfall=, 
         organisation=, 
         lagrum=
      }, 
      producerade-resultat=[
         RattenTillPeriod{
            ProduceratResultat{
               id='019c0abb-460a-749c-ade6-5c4ea602d184', 
               version=1
            }, 
            ersattningstyp='HUNDBIDRAG', 
            omfattning='HEL'
         }, 
         Ersattning{
            ProduceratResultat{
               id='019c0abb-460a-7720-ba46-bb0d70c64a76', 
               version=1
            }, 
            typ='ersattningstyp:HUNDBIDRAG', 
            belopp=1000.0
         }, 
         Ersattning{
            ProduceratResultat{
               id='019c0abb-460a-72ce-82ff-c2a595e3738f', 
               version=1
            }, 
            typ='ersattningstyp:HUNDBIDRAG', 
            belopp=500.0
         }, 
         Intyg{
            ProduceratResultat{
               id='019c0abb-460a-722f-bb81-752e510c749b', 
               version=1
            }, 
            giltighetsperiod=Period{
               from='Thu Jan 29 01:00:00 CET 2026', 
               tom='Thu Jan 29 01:00:00 CET 2026'
            }, 
            institution='null', 
            beskrivning='Hittepå', 
            utfardatDatum='null'
         }
      ]
   }
   +
   {
      ras='Collie'
   }
}
```
Notera hur det expanderade beloppet i JSON-serialiseringen nu återuppstår som `belopp` i `Ersattning`.

Nästa steg är att simulera en ändring i processens tillstånd -- i detta fall så har beskrivningen av
Yrkanet modifierats och vi har lagt till en ny ersättning (för torkning efter bad -- mycket viktigt):

```java
yrkan.beskrivning = "Modifierad beskrivning";
yrkan.ersattningar.add(new Ersattning("Tork", 100));

json = proxy.serializePretty(yrkan);
log.debug("Object -> JSON:\n{}", json);
```

Låt oss titta på loggen:

```terminaloutput
se.fk.data.modell.json.LifecycleAwareSerializer ** Modified bean: se.fk.hundbidrag.modell.Yrkan#0242194c
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@0242194c
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Beslut@0cfb8015
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.RattenTillPeriod@23139d2a
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@5a87223c
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@6d9d66e4
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Intyg@7d89542a
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@28cb5281
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@28cb5281
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@28cb5281
se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.hundbidrag.modell.Yrkan@0242194c
```

Notera hur `se.fk.data.modell.json.LifecycleAwareSerializer` upptäckt att ett object är
modifierat och ett objekt är nytt... Yrkanet har en ändrad beskrivning och vi har en ny Ersattning

```terminaloutput
...
se.fk.data.modell.json.LifecycleAwareSerializer ** Modified bean: se.fk.hundbidrag.modell.Yrkan#0242194c
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@0242194c
...
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@28cb5281
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@28cb5281
...
```
Vid serialisering, så höjs versionen av objektet (i Livscykelhanterad) automatiskt och detta syns såväl i
serialiserad JSON som i objektet. Notera hur version har ändrats, men också hur förändrade objekt har
flaggats med `__attention`-flaggan.

```json
{
  "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
  "__attention" : true,
  "id" : "019c0abb-460a-7314-9ea9-d42e157f6b9f",
  "version" : 2,
  "beskrivning" : "Hundutställning (inkl. bad och tork)",
  "beslut" : {
    "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
    "id" : "019c0abb-460b-76b2-92d9-35ece1079334",
    "version" : 1,
    "datum" : "2026-01-29T00:00:00.000Z"
  },
  "person" : {
    "varde" : {
      "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
      "personnummer" : {
        "varde" : "19121212-1212",
        "typ" : "pii:personnummer"
      }
    },
    "typ" : "ffa:yrkande"
  },
  "producerade_resultat" : [ {
    "@context" : "https://data.fk.se/kontext/std/ratten-till-period/1.0",
    "id" : "019c0abb-460a-749c-ade6-5c4ea602d184",
    "version" : 1,
    "ersattningstyp" : "HUNDBIDRAG",
    "omfattning" : "HEL"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "id" : "019c0abb-460a-7720-ba46-bb0d70c64a76",
    "version" : 1,
    "belopp" : {
      "varde" : 1000.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "id" : "019c0abb-460a-72ce-82ff-c2a595e3738f",
    "version" : 1,
    "belopp" : {
      "varde" : 500.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/intyg/1.0",
    "id" : "019c0abb-460a-722f-bb81-752e510c749b",
    "version" : 1,
    "beskrivning" : "Hittepå",
    "giltighetsperiod" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    }
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "id" : "019c0abb-4696-70e1-9654-07a0f5c8e80b",
    "version" : 1,
    "belopp" : {
      "varde" : 100.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "typ" : "HUNDBIDRAG"
  } ],
  "ras" : "Collie"
}
```

Vi ändrar objektet igen, bara för att visa att version verkligen ändrats i objektet i samband med serialisering:

```java
yrkan.beskrivning = "Modfierad igen...";
yrkan.ersattningar.add(new Ersattning("Fön", 200));

json = proxy.serializePretty(yrkan);
log.debug("Object -> JSON:\n{}", json);
```

Och så tittar vi på loggen igen:

```terminaloutput
...
se.fk.data.modell.json.LifecycleAwareSerializer ** Modified bean: se.fk.hundbidrag.modell.Yrkan#0242194c
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@0242194c
...
se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@604474b4
se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@604474b4
...
```

Och så tittar vi på producerad JSON:

```json
{
  "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
  "__attention" : true,
  "id" : "019c0abb-460a-7314-9ea9-d42e157f6b9f",
  "version" : 3,
  "beskrivning" : "Hundutställning (inkl. bad, tork och fön)",
  "beslut" : {
    "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
    "id" : "019c0abb-460b-76b2-92d9-35ece1079334",
    "version" : 1
    "datum" : "2026-01-29T00:00:00.000Z",
  },
  "person" : {
    "varde" : {
      "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
      "personnummer" : {
        "varde" : "19121212-1212",
        "typ" : "pii:personnummer"
      }
    },
    "typ" : "ffa:yrkande"
  },
  "producerade_resultat" : [ {
    "@context" : "https://data.fk.se/kontext/std/ratten-till-period/1.0",
    "id" : "019c0abb-460a-749c-ade6-5c4ea602d184",
    "version" : 1,
    "ersattningstyp" : "HUNDBIDRAG",
    "omfattning" : "HEL"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "id" : "019c0abb-460a-7720-ba46-bb0d70c64a76",
    "version" : 1,
    "belopp" : {
      "varde" : 1000.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "id" : "019c0abb-460a-72ce-82ff-c2a595e3738f",
    "version" : 1,
    "belopp" : {
      "varde" : 500.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/intyg/1.0",
    "id" : "019c0abb-460a-722f-bb81-752e510c749b",
    "version" : 1,
    "beskrivning" : "Hittepå",
    "giltighetsperiod" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2026-01-29T00:00:00.000Z",
      "tom" : "2026-01-29T00:00:00.000Z"
    }
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "id" : "019c0abb-4696-70e1-9654-07a0f5c8e80b",
    "version" : 1,
    "belopp" : {
      "varde" : 100.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "typ" : "HUNDBIDRAG"
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "id" : "019c0abb-469c-7592-887f-40b9403896af",
    "version" : 1,
    "belopp" : {
      "varde" : 200.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "typ" : "HUNDBIDRAG"
  } ],
  "ras" : "Collie"
}
```
