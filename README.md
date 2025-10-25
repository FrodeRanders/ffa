# Förenklad IT-arkitektur för förmåner
Detta är en (fungerande) demo, som är menat att vara utgångspunkt för vidare diskussioner kring hur vi kan
underlätta för team att bygga lösningar baserade på FFA och som integreras med Mimer.

Frågan vi ställer är om det är möjligt att dölja vissa grepp från utvecklingsteam, så att de
helt kan fokusera på affärslogik och inte behöva engagera sig i översättning från "förmånsspråk"
till "organisationsspråk" samt detaljerna kring serialisering av processtillstånd.

## Vi tar utgångspunkt i behoven hos ett team som utvecklar affärslogik med FFAs informations- och datamodell som grund

Affärslogiken utvecklas mot kärnobjekten i FFAs model, så som Kundbehov, Ersättning, Beslut, osv. Ansatsen
är att erbjuda en versionshanterad standard-implementation av dessa kärnobjekt, som teamet kan utvidga/modifiera
vid behov. För utvidning och modifikation används helt enkelt arvsmekanismen i Java.

Vi kan förfara på liknande sätt för en eventuell C++ realisering.

### Basklass för livscykelhanterade objekt enligt FFA
```java
package se.fk.data.modell.v1;

import ...

public class LivscykelHanterad {

    @JsonProperty("id")
    public String id;

    @JsonProperty("version")
    public int version = 0;

    ...
}
```
### Kundbehov
```java
package se.fk.data.modell.v1;

import ...

@Context("https://data.fk.se/kontext/std/kundbehov/1.0")
public class Kundbehov extends LivscykelHanterad {

    @JsonProperty("person")
    public FysiskPerson person;

    @JsonProperty("beskrivning")
    public String beskrivning;

    @JsonProperty("ersattningar")
    public Collection<Ersattning> ersattningar;

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
public class FysiskPerson {

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
public class Beslut extends LivscykelHanterad {

    public enum Typ {
        INTERRIMISTISK (1),
        STALLNINGSTAGANDE (2),
        SLUTLIGT (3);

        ...
    }

    public enum Utfall {
        BEVILJAT (1),
        AVSLAG (2),
        DELVIS_BEVILJANDE (3),
        AVVISNING (4),
        AVSKRIVNING (5);

        ...
    }

    public enum Lagrum {
        SFB_K112_P2a ("SFB Kap. 112 § 2a"),
        SFB_K112_P3 ("SFB Kap. 112 § 3"),
        SFB_K112_P4 ("SFB Kap. 112 § 4"),
        SFB_K113_P3_S1 ("SFB Kap. 113 § 3 p. 1"),
        SFB_K113_P3_S2 ("SFB Kap. 113 § 3 p. 2"),
        SFB_K113_P3_S3 ("SFB Kap. 113 § 3 p. 3"),
        FL_P36 ("FL § 36"),
        FL_P37 ("FL § 37"),
        FL_P38 ("FL § 38");

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
public class Ersattning extends LivscykelHanterad {

    @JsonProperty("typ")
    public String typ;

    @Belopp
    // or @Belopp(valuta="valuta:SEK", skattestatus="sfa:skattepliktig", period="sfa:perdag")
    @JsonProperty("belopp")
    public double belopp;

    ...
}
```

### Utvidgning av Kundbehov för Hundbidraget (löjligt exempel)
I detta exempel, så har FFA-standard Kundbehov utvidgats med uppgift om hundens ras. För
utvidgning används Javas arvsmekanism.
```java
package se.fk.hundbidrag.modell;

import ...

@Context("https://data.fk.se/kontext/hundbidrag/kundbehov/1.0")
public class Kundbehov extends se.fk.data.modell.v1.Kundbehov {

    @JsonProperty("ras")
    String ras;

    ...
}
```

## Realisering av affärslogik

```java
package se.fk.hundbidrag;

import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.Kundbehov;
import ...
...
```
Instansiera objekt efter behov, huvudsakligen från FFAs standardmodell, men också egna utvidgningar.
I detta exempel används Hundbidragets Kundbehov (istället för FFA-standard Kundbehov), som är
utvidgat med uppgift om hundens ras.

```java
// -------------------------------------------------------------------
// Använd FFAs objektmodell för affärslogik i specifik förmånskontext
// -------------------------------------------------------------------
Ersattning ers1 = new Ersattning("Avgift", 1000);
Ersattning ers2 = new Ersattning("Bad", 500);

Kundbehov kundbehov = new Kundbehov("Hundutställning", Arrays.asList(ers1, ers2), "Collie");

FysiskPerson person = new FysiskPerson("19121212-1212");
kundbehov.setPerson(person);

Beslut beslut = new Beslut(Date.from(Instant.now().truncatedTo(DAYS)));
kundbehov.setBeslut(beslut);
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
String jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(kundbehov);
log.debug("Object -> JSON:\n{}", jsonLD);
```
Låt oss titta på loggen som svarar mot serialiseringen:
```terminaloutput
se.fk.data.modell.json.LifecycleAwareSerializer   Created for se.fk.hundbidrag.modell.Kundbehov
se.fk.data.modell.json.MutationSemantics          Initiating state for bean: se.fk.hundbidrag.modell.Kundbehov@13ad5cd3
se.fk.data.modell.json.LifecycleAwareSerializer   ** New bean: se.fk.hundbidrag.modell.Kundbehov@13ad5cd3
se.fk.data.modell.json.LifecycleAwareSerializer   Stepping version of bean: se.fk.hundbidrag.modell.Kundbehov@13ad5cd3
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
se.fk.data.modell.json.LifecycleAwareSerializer   Serialized bean se.fk.hundbidrag.modell.Kundbehov@13ad5cd3
```
Låt oss titta på vad som händer med ```se.fk.data.modell.v1.FysiskPerson#personnummer``` och 
```se.fk.data.modell.v1.Ersattning#belopp``` vid serialisering efter att vi tittat på producerad JSON.

```json
{
    "@context" : "https://data.fk.se/kontext/hundbidrag/kundbehov/1.0",
    "id" : "019a1076-2403-7cc8-be2b-53e1256af498",
    "version" : 1,
    "beskrivning" : "Hundutställning",

    "person" : {
        "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
        "personnummer" : {
            "varde" : "19121212-1212",
            "typ" : "pii:personnummer"
        }
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
    } ],

    "beslut" : {
        "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
        "id" : "019a1076-2404-7791-a7d2-7d669e66fcff",
        "version" : 1,
        "datum" : "2025-10-23T00:00:00.000+00:00"
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
"personnummer" : {
    "varde" : "19121212-1212",
    "typ" : "pii:personnummer"
}
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
Kundbehov deserializedKundbehov = mapper.readValue(jsonLD, Kundbehov.class);
log.debug("JSON -> Object:\n{}", deserializedKundbehov);
```

Låt oss titta på loggen:

```terminaloutput
se.fk.data.modell.json.LifecycleAwareDeserializer    Created for se.fk.hundbidrag.modell.Kundbehov
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
se.fk.data.modell.json.LifecycleAwareDeserializer    Deserialized bean se.fk.hundbidrag.modell.Kundbehov@62727399
se.fk.data.modell.json.MutationSemantics             Initiating state for bean: se.fk.hundbidrag.modell.Kundbehov@62727399

se.fk.hundbidrag.Applikation JSON -> Object:
Kundbehov{
    id='019a15dd-9823-7332-90df-6d5fc9e9c9c7', 
    version=1, 
    beskrivning='Hundutställning', 
    person=FysiskPerson{
        personnummer='19121212-1212'
    },
    ersattningar=[
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
Kundbehovet modifierats och vi har lagt till en ny ersättning (för torkning efter bad -- mycket viktigt):

```java
deserializedKundbehov.beskrivning = "Modifierad beskrivning";
deserializedKundbehov.ersattningar.add(new Ersattning("Tork", 100));

jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedKundbehov);
log.debug("Object -> JSON:\n{}", jsonLD);
```

Låt oss titta på loggen:

```terminaloutput
se.fk.data.modell.json.MutationSemantics         Initiating state for bean: se.fk.hundbidrag.modell.Kundbehov@62727399
se.fk.data.modell.json.LifecycleAwareSerializer  ** Modified bean: se.fk.hundbidrag.modell.Kundbehov#62727399
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.hundbidrag.modell.Kundbehov@62727399
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
se.fk.data.modell.json.LifecycleAwareSerializer  Serialized bean se.fk.hundbidrag.modell.Kundbehov@62727399
```

Notera hur ```se.fk.data.modell.json.LifecycleAwareSerializer``` upptäckt att två object är
modifierade... Kundbehovet har en ändrad beskrivning och vi har en ny Ersattning

```terminaloutput
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** Modified bean: se.fk.hundbidrag.modell.Kundbehov#62727399
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.hundbidrag.modell.Kundbehov@62727399
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** New bean: se.fk.data.modell.v1.Ersattning@737a135b
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.data.modell.v1.Ersattning@737a135b
...
```
Vid serialisering, så höjs versionen av objektet (i LivscykelHanterad) automatiskt och detta syns såväl i
serialiserad JSON som i objektet.

```json
{
    "@context" : "https://data.fk.se/kontext/hundbidrag/kundbehov/1.0",
    "id" : "019a1076-2403-7cc8-be2b-53e1256af498",
    "version" : 2,
    "beskrivning" : "Modifierad beskrivning",

    "person" : {
        "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
        "personnummer" : {
            "varde" : "19121212-1212",
            "typ" : "pii:personnummer"
        }
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

    "beslut" : {
        "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
        "id" : "019a1076-2404-7791-a7d2-7d669e66fcff",
        "version" : 1,
        "datum" : "2025-10-23T00:00:00.000+00:00"
    },

    "ras" : "Collie"
}
```

Vi ändrar objektet igen, bara för att visa att version verkligen ändrats i objektet i samband med serialisering:

```java
deserializedKundbehov.beskrivning = "Modfierad igen...";
deserializedKundbehov.ersattningar.add(new Ersattning("Fön", 200));

jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedKundbehov);
log.debug("Object -> JSON:\n{}", jsonLD);
```

Och så tittar vi på loggen igen:

```terminaloutput
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** Modified bean: se.fk.hundbidrag.modell.Kundbehov#62727399
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.hundbidrag.modell.Kundbehov@62727399
...
se.fk.data.modell.json.LifecycleAwareSerializer  ** New bean: se.fk.data.modell.v1.Ersattning@687ef2e0
se.fk.data.modell.json.LifecycleAwareSerializer  Stepping version of bean: se.fk.data.modell.v1.Ersattning@687ef2e0
...
```

Och så tittar vi på producerad JSON:

```json
{
    "@context" : "https://data.fk.se/kontext/hundbidrag/kundbehov/1.0",
    "id" : "019a1076-2403-7cc8-be2b-53e1256af498",
    "version" : 3,
    "beskrivning" : "Modfierad igen...",

    "person" : {
        "@context" : "https://data.fk.se/kontext/std/fysiskperson/1.0",
        "personnummer" : {
            "varde" : "19121212-1212",
            "typ" : "pii:personnummer"
        }
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

    "beslut" : {
        "@context" : "https://data.fk.se/kontext/std/beslut/1.0",
        "id" : "019a1076-2404-7791-a7d2-7d669e66fcff",
        "version" : 1,
        "datum" : "2025-10-23T00:00:00.000+00:00"
    },

    "ras" : "Collie"
}
```

Ytterligare transformation kan göras på det serialiserade formatet, så som att anpassa detta till
(eventuellt justerade) inleveransformat för Mimer, men bak Mimer-proxyn.
