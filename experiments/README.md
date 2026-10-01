# Tidigare experiment

Här finns de delar av den ursprungliga PoC:en som inte behövs för att visa den
förvaltade objektmodellen och dess gräns mot förmånssystemen:

- JSON-LD-expansion, inramning, RDF och olika sätt att paketera grafdata.
- GraphQL-baserad typmappning, kontexter och ontologigenerering.
- De tidigare fiktiva migreringsreglerna (motorn finns nu i `ffa-core`).
- Den tidigare `MimerProxy` med signeringsalternativ, certifikatkedjor och kodningar.
- Visualisering av kontrollsummor och de tidigare testerna för dessa studier.

Filerna är sparade som studiematerial och ingår inte i huvudprojektets Maven-bygge.
`pom-original.xml` och `README-original.md` dokumenterar den tidigare strukturen;
de är inte en fristående, körbar distribution. De gamla skripten förutsätter
ursprungsprojektets sökvägar och beroenden. Använd projektets Git-historik om du
vill köra den ursprungliga PoC:en i sin helhet.

Den generella JSONPath-migreringsmotorn har återförts till `ffa-core` som en
central del av inläsningen, före bindningen till Java-modellen. Den använder
officiella `com.jayway.jsonpath:json-path:3.0.0` med Jackson 3-providers.
Forkens snapshot behövs inte. Även beroendet i `pom-original.xml` har uppdaterats.
Motorns tester ingår nu i huvudprojektets vanliga testkörning.

Den aktiva demonstrationen och dess körkommandon beskrivs i [projektets README](../README.md).
