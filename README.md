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

public class LivscykelHanterad {

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

## Realisering av affärslogik

```java
package se.fk.hundbidrag;

import ...
        ...
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
    FysiskPerson person = new FysiskPerson("19121212-1212");

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

## Serialisering av processtillstånd och översättning från "förmånsspråk" till "organisationsspråk"
Vi har nu en uppsättning nya objekt, som inte persisterats. I samband med persistering,
så görs en serialisering av processens hela nuvarande tillstånd till JSON. Tanken är
att paketera denna funktionalitet i en Mimer-proxy:
```java
ObjectMapper mapper = new ObjectMapper()
    // Date-relaterat
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setDateFormat(new StdDateFormat().withColonInTimeZone(true))
    .setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    //
    .registerModules(getModules())
    .addHandler(new DeserializationSnooper());

// Initial serialize to JSON
String jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(yrkan);
log.debug("Object -> JSON:\n{}", jsonLD);
```
Låt oss titta på loggen som svarar mot serialiseringen:
```terminaloutput
se.fk.data.modell.json.PropertySerializerModifier @Som property se.fk.hundbidrag.modell.Yrkan#person
se.fk.data.modell.json.LifecycleAwareSerializer   Created for se.fk.hundbidrag.modell.Yrkan
se.fk.data.modell.json.MutationSemantics          Initiating state for bean: se.fk.hundbidrag.modell.Yrkan@13ad5cd3
se.fk.data.modell.json.LifecycleAwareSerializer   ** New bean: se.fk.hundbidrag.modell.Yrkan@13ad5cd3
se.fk.data.modell.json.LifecycleAwareSerializer   Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@13ad5cd3
se.fk.data.modell.json.PropertySerializerModifier @PII property se.fk.data.modell.v1.FysiskPerson#personnummer)
se.fk.data.modell.json.PropertySerializerModifier @Belopp property se.fk.data.modell.v1.Ersattning#belopp
se.fk.data.modell.json.LifecycleAwareSerializer   Created for se.fk.data.modell.v1.Ersattning
se.fk.data.modell.json.MutationSemantics          Initiating state for bean: se.fk.data.modell.v1.Ersattning@4426bff1
se.fk.data.modell.json.LifecycleAwareSerializer   ** New bean: se.fk.data.modell.v1.Ersattning@4426bff1
se.fk.data.modell.json.LifecycleAwareSerializer   Stepping version of bean: se.fk.data.modell.v1.Ersattning@4426bff1
se.fk.data.modell.json.LifecycleAwareSerializer   Serialized bean se.fk.data.modell.v1.Ersattning@4426bff1
se.fk.data.modell.json.MutationSemantics          Initiating state for bean: se.fk.data.modell.v1.Ersattning@2f16c6b3
se.fk.data.modell.json.LifecycleAwareSerializer   ** New bean: se.fk.data.modell.v1.Ersattning@2f16c6b3
se.fk.data.modell.json.LifecycleAwareSerializer   Stepping version of bean: se.fk.data.modell.v1.Ersattning@2f16c6b3
se.fk.data.modell.json.LifecycleAwareSerializer   Serialized bean se.fk.data.modell.v1.Ersattning@2f16c6b3
se.fk.data.modell.json.LifecycleAwareSerializer   Created for se.fk.data.modell.v1.Beslut
se.fk.data.modell.json.MutationSemantics          Initiating state for bean: se.fk.data.modell.v1.Beslut@34158c08
se.fk.data.modell.json.LifecycleAwareSerializer   ** New bean: se.fk.data.modell.v1.Beslut@34158c08
se.fk.data.modell.json.LifecycleAwareSerializer   Stepping version of bean: se.fk.data.modell.v1.Beslut@34158c08
se.fk.data.modell.json.LifecycleAwareSerializer   Serialized bean se.fk.data.modell.v1.Beslut@34158c08
se.fk.data.modell.json.LifecycleAwareSerializer   Serialized bean se.fk.hundbidrag.modell.Yrkan@13ad5cd3
```
Låt oss titta på vad som händer med ```se.fk.data.modell.v1.FysiskPerson#personnummer``` och 
```se.fk.data.modell.v1.Ersattning#belopp``` vid serialisering efter att vi tittat på producerad JSON.

```json
{
    "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
    "id" : "019a1076-2403-7cc8-be2b-53e1256af498",
    "version" : 1,
    "beskrivning" : "Hundutställning",

    "producerade-resultat" : [ {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-2401-76b3-a145-74d25ffdd489",
        "version" : 1,
        "typ" : "Avgift",
        "belopp" : {
            "varde" : 1000.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    }, {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-2403-7520-b9b7-9d344c7f3d4d",
        "version" : 1,
        "typ" : "Bad",
        "belopp" : {
            "varde" : 500.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    } ],

    "beslut" : {
        "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
        "id" : "019a1076-2404-7791-a7d2-7d669e66fcff",
        "version" : 1,
        "datum" : "2025-10-23T00:00:00.000+00:00"
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

    "ras" : "Collie"
}
```

Notera hur fältet ```belopp``` i ```Ersättning```, som i Java var annnoterat med ```@Belopp```, har
expanderats i samband med serialiseringen. ```@Belopp```-annoteringen indikerar att vi för detta fält
behöver fånga förmånskontext kring beloppsuppgiften. Fältet ```belopp``` har i samband med serialisering
expanderats till:

```json
"belopp" : {
    "varde" : 1000.0,
    "valuta" : null,
    "skattestatus" : null,
    "period" : null
}
```
Samma sak gäller ```personnummer``` i ```FysiskPerson```, som annoterats med ```@PII(typ="pii:personnummer")``` 
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

I fallet med ```@Belopp```-annoteringen så vore det inte lämpligt att göra detta i en basklass
(där annoteringen sitter på standard-realiseringen av ```Ersättning```) eftersom vi har både
dagförmåner och periodbaserade förmåner, samt både skattade och oskattade ersättningar.

Här bör kontext kring överföring av 'belopp' tillföras i ett eftersteg via context-hanteringen,
som beror på ```@Context```-annoteringen.

Nåväl; vi föreställer oss att vi vid ett senare tillfälle återhämtar processes tillstånd, varvid
vi erhåller en JSON (samma JSON som vi tidigare producerade) och deserialiserar denna:

```java
Yrkan deserializedYrkan = mapper.readValue(jsonLD, Yrkan.class);
log.debug("JSON -> Object:\n{}", deserializedYrkan);
```

Låt oss titta på loggen:

```terminaloutput
se.fk.data.modell.json.LifecycleAwareDeserializer    Created for se.fk.hundbidrag.modell.Yrkan
se.fk.data.modell.json.PropertyDeserializerModifier  @PII property se.fk.data.modell.v1.FysiskPerson#personnummer
se.fk.data.modell.json.LifecycleAwareDeserializer    Created for se.fk.data.modell.v1.Beslut
se.fk.data.modell.json.PropertyDeserializerModifier  @Belopp property se.fk.data.modell.v1.Ersattning#belopp
se.fk.data.modell.json.PropertyDeserializerModifier  Handling annotated property se.fk.data.modell.v1.Ersattning#belopp
se.fk.data.modell.json.LifecycleAwareDeserializer    Created for se.fk.data.modell.v1.Ersattning
se.fk.data.modell.json.LifecycleAwareDeserializer    Deserialized bean se.fk.data.modell.v1.Ersattning@12c7a01b
se.fk.data.modell.json.MutationSemantics             Initiating state for bean: se.fk.data.modell.v1.Ersattning@12c7a01b
se.fk.data.modell.json.LifecycleAwareDeserializer    Deserialized bean se.fk.data.modell.v1.Ersattning@13d9b21f
se.fk.data.modell.json.MutationSemantics             Initiating state for bean: se.fk.data.modell.v1.Ersattning@13d9b21f
se.fk.data.modell.json.LifecycleAwareDeserializer    Deserialized bean se.fk.data.modell.v1.Beslut@02826f61
se.fk.data.modell.json.MutationSemantics             Initiating state for bean: se.fk.data.modell.v1.Beslut@02826f61
se.fk.data.modell.json.LifecycleAwareDeserializer    Deserialized bean se.fk.hundbidrag.modell.Yrkan@62727399
se.fk.data.modell.json.MutationSemantics             Initiating state for bean: se.fk.hundbidrag.modell.Yrkan@62727399

se.fk.hundbidrag.Applikation JSON -> Object:
Yrkan{
    id='019a15dd-9823-7332-90df-6d5fc9e9c9c7', 
    version=1, 
    beskrivning='Hundutställning', 
    person=FysiskPerson{
        personnummer='19121212-1212'
    },
    produceradeResultat[
        Ersattning{
            id='019a15dd-9821-7b60-96ae-aeb2121fbba5', 
            version=1, 
            typ='Avgift', 
            belopp=1000.0
        }, 
        Ersattning{
            id='019a15dd-9822-76e5-b180-1208168739c7', 
            version=1, 
            typ='Bad', 
            belopp=500.0
        }
    ], 
    beslut=Beslut{
        id='019a15dd-9823-7756-8969-b433d72e58e4', 
        version=1, 
        datum='2025-10-24', 
        ...
  }
} + {
    ras='Collie'
}
```
Notera hur det expanderade beloppet i JSON-serialiseringen nu återuppstår som ```belopp``` i ```Ersattning```.

Nästa steg är att simulera en ändring i processens tillstånd -- i detta fall så har beskrivningen av
Yrkanet modifierats och vi har lagt till en ny ersättning (för torkning efter bad -- mycket viktigt):

```java
deserializedYrkan.beskrivning = "Modifierad beskrivning";
deserializedYrkan.ersattningar.add(new Ersattning("Tork", 100));

jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedYrkan);
log.debug("Object -> JSON:\n{}", jsonLD);
```

Låt oss titta på loggen:

```terminaloutput
se.fk.data.modell.json.MutationSemantics         Initiating state for bean: se.fk.hundbidrag.modell.Yrkan@62727399
se.fk.data.modell.json.LifecycleAwareSerializer  ** Modified bean: se.fk.hundbidrag.modell.Yrkan#62727399
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@62727399
se.fk.data.modell.json.MutationSemantics         Initiating state for bean: se.fk.data.modell.v1.Ersattning@12c7a01b
se.fk.data.modell.json.LifecycleAwareSerializer  Serialized bean se.fk.data.modell.v1.Ersattning@12c7a01b
se.fk.data.modell.json.MutationSemantics         Initiating state for bean: se.fk.data.modell.v1.Ersattning@13d9b21f
se.fk.data.modell.json.LifecycleAwareSerializer  Serialized bean se.fk.data.modell.v1.Ersattning@13d9b21f
se.fk.data.modell.json.MutationSemantics         Initiating state for bean: se.fk.data.modell.v1.Ersattning@737a135b
se.fk.data.modell.json.LifecycleAwareSerializer  ** New bean: se.fk.data.modell.v1.Ersattning@737a135b
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.data.modell.v1.Ersattning@737a135b
se.fk.data.modell.json.LifecycleAwareSerializer  Serialized bean se.fk.data.modell.v1.Ersattning@737a135b
se.fk.data.modell.json.MutationSemantics         Initiating state for bean: se.fk.data.modell.v1.Beslut@02826f61
se.fk.data.modell.json.LifecycleAwareSerializer  Serialized bean se.fk.data.modell.v1.Beslut@02826f61
se.fk.data.modell.json.LifecycleAwareSerializer  Serialized bean se.fk.hundbidrag.modell.Yrkan@62727399
```

Notera hur ```se.fk.data.modell.json.LifecycleAwareSerializer``` upptäckt att två object är
modifierade... Yrkanet har en ändrad beskrivning och vi har en ny Ersattning

```terminaloutput
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** Modified bean: se.fk.hundbidrag.modell.Yrkan#62727399
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@62727399
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** New bean: se.fk.data.modell.v1.Ersattning@737a135b
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.data.modell.v1.Ersattning@737a135b
...
```
Vid serialisering, så höjs versionen av objektet (i LivscykelHanterad) automatiskt och detta syns såväl i
serialiserad JSON som i objektet.

```json
{
    "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
    "id" : "019a1076-2403-7cc8-be2b-53e1256af498",
    "version" : 2,
    "beskrivning" : "Modifierad beskrivning",

    "beslut" : {
        "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
        "id" : "019a1076-2404-7791-a7d2-7d669e66fcff",
        "version" : 1,
        "datum" : "2025-10-23T00:00:00.000+00:00"
    },

    "ersattningar" : [ {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-2401-76b3-a145-74d25ffdd489",
        "version" : 1,
        "typ" : "Avgift",
        "belopp" : {
            "varde" : 1000.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    }, {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-2403-7520-b9b7-9d344c7f3d4d",
        "version" : 1,
        "typ" : "Bad",
        "belopp" : {
            "varde" : 500.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    }, {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-24a1-7a49-91a5-7c5986586039",
        "version" : 1,
        "typ" : "Tork",
        "belopp" : {
            "varde" : 100.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    } ],

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

    "ras" : "Collie"
}
```

Vi ändrar objektet igen, bara för att visa att version verkligen ändrats i objektet i samband med serialisering:

```java
deserializedYrkan.beskrivning = "Modfierad igen...";
deserializedYrkan.ersattningar.add(new Ersattning("Fön", 200));

jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedYrkan);
log.debug("Object -> JSON:\n{}", jsonLD);
```

Och så tittar vi på loggen igen:

```terminaloutput
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** Modified bean: se.fk.hundbidrag.modell.Yrkan#62727399
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@62727399
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** New bean: se.fk.data.modell.v1.Ersattning@687ef2e0
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.data.modell.v1.Ersattning@687ef2e0
...
```

Och så tittar vi på producerad JSON:

```json
{
    "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
    "id" : "019a1076-2403-7cc8-be2b-53e1256af498",
    "version" : 3,
    "beskrivning" : "Modfierad igen...",

    "beslut" : {
        "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
        "id" : "019a1076-2404-7791-a7d2-7d669e66fcff",
        "version" : 1,
        "datum" : "2025-10-23T00:00:00.000+00:00"
    },

    "producerade-resultat" : [ {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-2401-76b3-a145-74d25ffdd489",
        "version" : 1,
        "typ" : "Avgift",
        "belopp" : {
            "varde" : 1000.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    }, {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-2403-7520-b9b7-9d344c7f3d4d",
        "version" : 1,
        "typ" : "Bad",
        "belopp" : {
            "varde" : 500.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    }, {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-24a1-7a49-91a5-7c5986586039",
        "version" : 1,
        "typ" : "Tork",
        "belopp" : {
            "varde" : 100.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    }, {
        "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
        "id" : "019a1076-24a4-7bd9-a25e-6c9f14177fd4",
        "version" : 1,
        "typ" : "Fön",
        "belopp" : {
            "varde" : 200.0,
            "valuta" : null,
            "skattestatus" : null,
            "period" : null
        }
    } ],

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

    "ras" : "Collie"
}
```

Ytterligare transformation kan göras på det serialiserade formatet, så som att anpassa detta till
(eventuellt justerade) inleveransformat för Mimer, men bak Mimer-proxyn.


Detta är en dump av loggfilen som produceras:
```terminaloutput
2025-11-27 09:46:00.926 [TRACE] [main] se.fk.data.modell.json.PropertySerializerModifier @Som property se.fk.hundbidrag.modell.Yrkan#person
2025-11-27 09:46:00.930 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.hundbidrag.modell.Yrkan
2025-11-27 09:46:00.958 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.hundbidrag.modell.Yrkan@25e2ab5a
2025-11-27 09:46:00.958 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@25e2ab5a
2025-11-27 09:46:00.961 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.Beslut
2025-11-27 09:46:00.962 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Beslut@3401a114
2025-11-27 09:46:00.962 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Beslut@3401a114
2025-11-27 09:46:00.962 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Beslut@3401a114
2025-11-27 09:46:00.963 [TRACE] [main] se.fk.data.modell.json.PropertySerializerModifier @PII property se.fk.data.modell.v1.FysiskPerson#personnummer
2025-11-27 09:46:00.965 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.RattenTillPeriod
2025-11-27 09:46:00.965 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.RattenTillPeriod@6bb2d00b
2025-11-27 09:46:00.965 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.RattenTillPeriod@6bb2d00b
2025-11-27 09:46:00.966 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.RattenTillPeriod@6bb2d00b
2025-11-27 09:46:00.967 [TRACE] [main] se.fk.data.modell.json.PropertySerializerModifier @Belopp property se.fk.data.modell.v1.Ersattning#belopp
2025-11-27 09:46:00.968 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.Ersattning
2025-11-27 09:46:00.968 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@1a9c38eb
2025-11-27 09:46:00.968 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@1a9c38eb
2025-11-27 09:46:00.969 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@1a9c38eb
2025-11-27 09:46:00.969 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@319bc845
2025-11-27 09:46:00.969 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@319bc845
2025-11-27 09:46:00.970 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@319bc845
2025-11-27 09:46:00.970 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Created for se.fk.data.modell.v1.Intyg
2025-11-27 09:46:00.971 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Intyg@4c5474f5
2025-11-27 09:46:00.971 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Intyg@4c5474f5
2025-11-27 09:46:00.972 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Intyg@4c5474f5
2025-11-27 09:46:00.972 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.hundbidrag.modell.Yrkan@25e2ab5a
2025-11-27 09:46:00.972 [DEBUG] [main] se.fk.hundbidrag.Applikation Object -> JSON:
```
```json
{
  "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
  "__attention" : true,
  "beskrivning" : "Hundutställning (inkl. bad)",
  "beslut" : {
    "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
    "__attention" : true,
    "datum" : "2025-11-27T00:00:00.000Z",
    "id" : "019ac47d-a830-7847-9001-c560965035e2",
    "version" : 1
  },
  "id" : "019ac47d-a82e-7506-885a-1e30a5d7840b",
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
    "ersattningstyp" : "HUNDBIDRAG",
    "id" : "019ac47d-a82f-77fd-ac64-1f67bd65c4a9",
    "omfattning" : "HEL",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "belopp" : {
      "varde" : 1000.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a830-70b4-9cb9-7a26168e1eca",
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "belopp" : {
      "varde" : 500.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a830-7b21-9392-b26fec29ab4d",
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/intyg/1.0",
    "__attention" : true,
    "beskrivning" : "Hittepå",
    "giltighetsperiod" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "id" : "019ac47d-a830-7a49-ae64-b2e008676336",
    "version" : 1
  } ],
  "ras" : "Collie",
  "version" : 1
}
```
```terminaloutput
2025-11-27 09:46:00.984 [TRACE] [main] se.fk.data.modell.json.PropertyDeserializerModifier @Som property se.fk.hundbidrag.modell.Yrkan#person
2025-11-27 09:46:00.987 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.hundbidrag.modell.Yrkan
2025-11-27 09:46:00.988 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.Beslut
2025-11-27 09:46:00.995 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.ProduceratResultat
2025-11-27 09:46:00.998 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Beslut@72758afa
2025-11-27 09:46:01.003 [TRACE] [main] se.fk.data.modell.json.PropertyDeserializerModifier @PII property se.fk.data.modell.v1.FysiskPerson#personnummer
2025-11-27 09:46:01.006 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.RattenTillPeriod
2025-11-27 09:46:01.007 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.RattenTillPeriod@338494fa
2025-11-27 09:46:01.008 [TRACE] [main] se.fk.data.modell.json.PropertyDeserializerModifier @Belopp property se.fk.data.modell.v1.Ersattning#belopp
2025-11-27 09:46:01.008 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.Ersattning
2025-11-27 09:46:01.013 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Ersattning@7446d8d5
2025-11-27 09:46:01.013 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Ersattning@5c3b6c6e
2025-11-27 09:46:01.014 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Created for se.fk.data.modell.v1.Intyg
2025-11-27 09:46:01.015 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.data.modell.v1.Intyg@4fbda97b
2025-11-27 09:46:01.015 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareDeserializer Deserialized bean se.fk.hundbidrag.modell.Yrkan@75f5fd58
2025-11-27 09:46:01.027 [DEBUG] [main] se.fk.hundbidrag.Applikation JSON -> Object:
Yrkan{id='019ac47d-a82e-7506-885a-1e30a5d7840b', version=1, beskrivning='Hundutställning (inkl. bad)', person=FysiskPerson{personnummer='19121212-1212'}, beslut=Beslut{id='019ac47d-a830-7847-9001-c560965035e2', version=1, datum='2025-11-27', beslutsfattare=, typ=, utfall=, organisation=, lagrum=}, producerade-resultat=[RattenTillPeriod{ProduceratResultat{id='019ac47d-a82f-77fd-ac64-1f67bd65c4a9', version=1}, ersattningstyp='HUNDBIDRAG', omfattning='HEL'}, Ersattning{ProduceratResultat{id='019ac47d-a830-70b4-9cb9-7a26168e1eca', version=1}, typ='ersattningstyp:HUNDBIDRAG', belopp=1000.0}, Ersattning{ProduceratResultat{id='019ac47d-a830-7b21-9392-b26fec29ab4d', version=1}, typ='ersattningstyp:HUNDBIDRAG', belopp=500.0}, Intyg{ProduceratResultat{id='019ac47d-a830-7a49-ae64-b2e008676336', version=1}, giltighetsperiod=Period{from='Thu Nov 27 01:00:00 CET 2025', tom='Thu Nov 27 01:00:00 CET 2025'}, institution='null', beskrivning='Hittepå', utfardatDatum='null'}, ]}+{ras='Collie'}
2025-11-27 09:46:01.028 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** Modified bean: se.fk.hundbidrag.modell.Yrkan#75f5fd58
2025-11-27 09:46:01.028 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@75f5fd58
2025-11-27 09:46:01.029 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Beslut@72758afa
2025-11-27 09:46:01.029 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.RattenTillPeriod@338494fa
2025-11-27 09:46:01.030 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@7446d8d5
2025-11-27 09:46:01.030 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@5c3b6c6e
2025-11-27 09:46:01.030 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Intyg@4fbda97b
2025-11-27 09:46:01.030 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@40f1be1b
2025-11-27 09:46:01.031 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@40f1be1b
2025-11-27 09:46:01.031 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@40f1be1b
2025-11-27 09:46:01.031 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.hundbidrag.modell.Yrkan@75f5fd58
2025-11-27 09:46:01.031 [DEBUG] [main] se.fk.hundbidrag.Applikation Object -> JSON:
```
```json
{
  "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
  "__attention" : true,
  "beskrivning" : "Hundutställning (inkl. bad och tork)",
  "beslut" : {
    "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
    "datum" : "2025-11-27T00:00:00.000Z",
    "id" : "019ac47d-a830-7847-9001-c560965035e2",
    "version" : 1
  },
  "id" : "019ac47d-a82e-7506-885a-1e30a5d7840b",
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
    "ersattningstyp" : "HUNDBIDRAG",
    "id" : "019ac47d-a82f-77fd-ac64-1f67bd65c4a9",
    "omfattning" : "HEL",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "belopp" : {
      "varde" : 1000.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a830-70b4-9cb9-7a26168e1eca",
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "belopp" : {
      "varde" : 500.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a830-7b21-9392-b26fec29ab4d",
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/intyg/1.0",
    "beskrivning" : "Hittepå",
    "giltighetsperiod" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "id" : "019ac47d-a830-7a49-ae64-b2e008676336",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "belopp" : {
      "varde" : 100.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a943-75ca-bcbe-d170912071d6",
    "typ" : "HUNDBIDRAG",
    "version" : 1
  } ],
  "ras" : "Collie",
  "version" : 2
}
```
```terminaloutput
2025-11-27 09:46:01.031 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** Modified bean: se.fk.hundbidrag.modell.Yrkan#75f5fd58
2025-11-27 09:46:01.032 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.hundbidrag.modell.Yrkan@75f5fd58
2025-11-27 09:46:01.032 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Beslut@72758afa
2025-11-27 09:46:01.032 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.RattenTillPeriod@338494fa
2025-11-27 09:46:01.033 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@7446d8d5
2025-11-27 09:46:01.033 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@5c3b6c6e
2025-11-27 09:46:01.033 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Intyg@4fbda97b
2025-11-27 09:46:01.033 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@40f1be1b
2025-11-27 09:46:01.033 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer ** New bean: se.fk.data.modell.v1.Ersattning@7a791b66
2025-11-27 09:46:01.034 [TRACE] [main] se.fk.data.modell.json.LifecycleAwareSerializer Stepping version of bean: se.fk.data.modell.v1.Ersattning@7a791b66
2025-11-27 09:46:01.034 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.data.modell.v1.Ersattning@7a791b66
2025-11-27 09:46:01.034 [DEBUG] [main] se.fk.data.modell.json.LifecycleAwareSerializer Serialized bean se.fk.hundbidrag.modell.Yrkan@75f5fd58
2025-11-27 09:46:01.034 [DEBUG] [main] se.fk.hundbidrag.Applikation Object -> JSON:
```
```json
{
  "@context" : "https://data.fk.se/kontext/hundbidrag/yrkan/1.0",
  "__attention" : true,
  "beskrivning" : "Hundutställning (inkl. bad, tork och fön)",
  "beslut" : {
    "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
    "datum" : "2025-11-27T00:00:00.000Z",
    "id" : "019ac47d-a830-7847-9001-c560965035e2",
    "version" : 1
  },
  "id" : "019ac47d-a82e-7506-885a-1e30a5d7840b",
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
    "ersattningstyp" : "HUNDBIDRAG",
    "id" : "019ac47d-a82f-77fd-ac64-1f67bd65c4a9",
    "omfattning" : "HEL",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "belopp" : {
      "varde" : 1000.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a830-70b4-9cb9-7a26168e1eca",
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "belopp" : {
      "varde" : 500.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a830-7b21-9392-b26fec29ab4d",
    "period" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/intyg/1.0",
    "beskrivning" : "Hittepå",
    "giltighetsperiod" : {
      "@context" : "https://data.fk.se/kontext/std/period/1.0",
      "from" : "2025-11-27T00:00:00.000Z",
      "tom" : "2025-11-27T00:00:00.000Z"
    },
    "id" : "019ac47d-a830-7a49-ae64-b2e008676336",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "belopp" : {
      "varde" : 100.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a943-75ca-bcbe-d170912071d6",
    "typ" : "HUNDBIDRAG",
    "version" : 1
  }, {
    "@context" : "https://data.fk.se/kontext/std/ersattning/1.0",
    "__attention" : true,
    "belopp" : {
      "varde" : 200.0,
      "valuta" : null,
      "skattestatus" : null,
      "period" : null
    },
    "id" : "019ac47d-a947-7c6c-bdbd-60b2d2b0f42c",
    "typ" : "HUNDBIDRAG",
    "version" : 1
  } ],
  "ras" : "Collie",
  "version" : 3
}
```