package se.fk.hundbidrag;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.Beslut;
import se.fk.data.modell.v1.Ersattning;
import se.fk.data.modell.v1.FysiskPerson;
import se.fk.data.modell.v1.Intyg;
import se.fk.data.modell.v1.Period;
import se.fk.data.modell.v1.RattenTillPeriod;
import se.fk.hundbidrag.modell.YrkanOmHundbidrag;
import se.fk.mimer.klient.MimerProxy;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ApplikationLifecycleTest {
    private static final Logger log = LoggerFactory.getLogger(ApplikationLifecycleTest.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Test
    void serialize_incrementsVersionAndSetsAttentionOnFirstSerialize() throws Exception {
        log.info("*** Testcase *** Serialize new Yrkan and verify version increment plus __attention flag");
        MimerProxy proxy = MimerProxy.defaultInstance();
        YrkanOmHundbidrag yrkan = buildDemoYrkan();

        assertEquals(0, yrkan.version);

        String json = proxy.serializePretty(yrkan);

        assertEquals(1, yrkan.version);
        Map<String, Object> root = readJson(json);
        assertEquals(1, asInt(root.get("version")));
        assertEquals(Boolean.TRUE, root.get("__attention"));
        assertEquals("Hundutställning (inkl. bad)", root.get("beskrivning"));
        assertEquals("Collie", root.get("ras"));

        Map<String, Object> ersattning = findProduceratResultatByBelopp(root, 1000.0);
        assertNotNull(ersattning);
        assertEquals(Boolean.TRUE, ersattning.get("__attention"));
        assertEquals(1, asInt(ersattning.get("version")));
        assertEquals(4, producedResults(root).size());
        for (Map<String, Object> result : producedResults(root)) {
            assertEquals(1, asInt(result.get("version")));
            assertEquals(Boolean.TRUE, result.get("__attention"));
        }
    }

    @Test
    void serializeDeserializeModify_stepsVersionAndFlagsAttention() throws Exception {
        log.info("*** Testcase *** Serialize, deserialize, modify, re-serialize and verify version/attention behavior");
        MimerProxy proxy = MimerProxy.defaultInstance();
        YrkanOmHundbidrag yrkan = buildDemoYrkan();

        String json1 = proxy.serializePretty(yrkan);
        assertEquals(1, yrkan.version);

        YrkanOmHundbidrag roundTripped = proxy.deserialize(json1, YrkanOmHundbidrag.class);
        assertEquals(1, roundTripped.version);
        assertEquals("Hundutställning (inkl. bad)", roundTripped.beskrivning);

        roundTripped.beskrivning = "Hundutställning (inkl. bad och tork)";
        Ersattning nyErsattning = new Ersattning();
        nyErsattning.typ = Ersattning.Typ.HUNDBIDRAG;
        nyErsattning.belopp = 999.0;
        roundTripped.addProduceratResultat(nyErsattning);

        String json2 = proxy.serializePretty(roundTripped);

        assertEquals(2, roundTripped.version);
        assertEquals(1, nyErsattning.version);

        Map<String, Object> root = readJson(json2);
        assertEquals(2, asInt(root.get("version")));
        assertEquals(Boolean.TRUE, root.get("__attention"));

        Map<String, Object> added = findProduceratResultatByBelopp(root, 999.0);
        assertNotNull(added);
        assertEquals(Boolean.TRUE, added.get("__attention"));
        assertEquals(1, asInt(added.get("version")));
    }

    @Test
    void serializeTwiceWithoutChanges_doesNotBumpVersionOrAttention() throws Exception {
        log.info("*** Testcase *** Serialize twice without changes and ensure version/attention stay stable");
        MimerProxy proxy = MimerProxy.defaultInstance();
        YrkanOmHundbidrag yrkan = buildDemoYrkan();

        String json1 = proxy.serializePretty(yrkan);
        assertEquals(1, yrkan.version);
        Map<String, Object> first = readJson(json1);
        assertEquals(Boolean.TRUE, first.get("__attention"));

        String json2 = proxy.serializePretty(yrkan);
        assertEquals(1, yrkan.version);
        Map<String, Object> second = readJson(json2);
        assertEquals(1, asInt(second.get("version")));
        assertEquals(false, second.containsKey("__attention"));
        for (Map<String, Object> result : producedResults(second)) {
            assertEquals(false, result.containsKey("__attention"));
            assertEquals(1, asInt(result.get("version")));
        }
    }

    private static YrkanOmHundbidrag buildDemoYrkan() {
        YrkanOmHundbidrag yrkan = new YrkanOmHundbidrag("Hundutställning (inkl. bad)", "Collie");
        yrkan.setPerson(new FysiskPerson("19121212-1212"));

        RattenTillPeriod rattenTillPeriod = new RattenTillPeriod();
        rattenTillPeriod.omfattning = RattenTillPeriod.Omfattning.HEL;
        rattenTillPeriod.ersattningstyp = Ersattning.Typ.HUNDBIDRAG;
        yrkan.addProduceratResultat(rattenTillPeriod);

        Ersattning ersattning = new Ersattning();
        ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
        ersattning.belopp = 1000.0;
        ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));
        yrkan.addProduceratResultat(ersattning);

        Ersattning ersattning2 = new Ersattning();
        ersattning2.typ = Ersattning.Typ.HUNDBIDRAG;
        ersattning2.belopp = 500.0;
        ersattning2.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));
        yrkan.addProduceratResultat(ersattning2);

        Intyg intyg = new Intyg();
        intyg.beskrivning = "Hittepå";
        intyg.giltighetsperiod = new Period(Date.from(Instant.now().truncatedTo(DAYS)));
        yrkan.addProduceratResultat(intyg);

        Beslut beslut = new Beslut();
        beslut.datum = Date.from(Instant.now().truncatedTo(DAYS));
        yrkan.setBeslut(beslut);

        return yrkan;
    }

    private static Map<String, Object> readJson(String json) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = JSON.readValue(json, Map.class);
        return root;
    }

    private static Map<String, Object> findProduceratResultatByBelopp(
            Map<String, Object> root,
            double belopp
    ) {
        for (Map<String, Object> result : producedResults(root)) {
            Object value = result.get("belopp");
            Double varde = extractBeloppVarde(value);
            if (varde != null && Double.compare(varde, belopp) == 0) {
                return result;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> producedResults(Map<String, Object> root) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) root.get("producerat_resultat");
        return results == null ? List.of() : results;
    }

    private static Double extractBeloppVarde(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Map<?, ?> map) {
            Object varde = map.get("varde");
            if (varde instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
