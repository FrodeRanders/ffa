--------------------------- MODULE Delivery ---------------------------
EXTENDS Naturals, FiniteSets

CONSTANTS Processes, Workers, MaxDeliveries, Mode, BrokenLock, BrokenLocalCommit, BrokenObservation, BrokenGraphGuard, BrokenForwarding, None
ASSUME /\ Mode \in {"STRIKT", "LOKAL_RESILIENS"}
       /\ MaxDeliveries > 0
       /\ BrokenLock \in BOOLEAN
       /\ BrokenLocalCommit \in BOOLEAN /\ BrokenObservation \in BOOLEAN
       /\ BrokenGraphGuard \in BOOLEAN /\ BrokenForwarding \in BOOLEAN

\* Ett id är en process och en lokal leveransplats, inte ett innehållshash.
Deliveries == Processes \X (1..MaxDeliveries)
Process(d) == d[1]
Slot(d) == d[2]

VARIABLES created, cached, sent, backend, observed, acknowledged, restored,
          published, version, latest, active, stage, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted
vars == <<created, cached, sent, backend, observed, acknowledged, restored,
          published, version, latest, active, stage, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

Pending == cached \ published
Latest(p) == latest[p]
Max(a, b) == IF a > b THEN a ELSE b
Free(p) == \A w \in Workers :
              IF active[w] = None THEN TRUE ELSE Process(active[w]) # p
CanOwn(w, p) == /\ active[w] = None
               /\ (BrokenLock \/ Free(p))
Oldest(d) == /\ d \in Pending
            /\ \A earlier \in Pending :
                  Process(earlier) = Process(d) => Slot(d) <= Slot(earlier)

Init == /\ created = {} /\ cached = {} /\ sent = {} /\ backend = {}
        /\ observed = {} /\ acknowledged = {} /\ restored = {} /\ published = {}
        /\ version = [d \in Deliveries |-> 0]
        /\ latest = [p \in Processes |-> 0]
        /\ active = [w \in Workers |-> None]
        /\ stage = [w \in Workers |-> "Idle"]
        /\ stable = FALSE /\ ordered = TRUE
        /\ highest = [p \in Processes |-> 0]
        /\ duplicateAccepted = FALSE
        /\ graph = {} /\ rawConfirmed = {} /\ graphConfirmed = {}
        /\ graphLatest = [p \in Processes |-> 0]
        /\ readVersion = [p \in Processes |-> 0]
        /\ forwarded = {} /\ offsetsCommitted = {}

\* Förmånen skapar en ny leverans. Förväntad version läses under processlåset.
Begin(w, d) ==
    /\ d \notin created
    /\ \A earlier \in Deliveries :
           (Process(earlier) = Process(d) /\ Slot(earlier) < Slot(d)) => earlier \in created
    /\ CanOwn(w, Process(d))
    /\ Mode = "LOKAL_RESILIENS" \/ {q \in Pending : Process(q) = Process(d)} = {}
    /\ created' = created \cup {d}
    /\ \E v \in ({Latest(Process(d)), Latest(Process(d)) + 1} \ {0}) :
           version' = [version EXCEPT ![d] = v]
    /\ active' = [active EXCEPT ![w] = d]
    /\ stage' = [stage EXCEPT ![w] = "Prepared"]
    /\ UNCHANGED <<cached, sent, backend, observed, acknowledged, restored,
                   published, latest, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Abstrakt lokalt commitsteg. Ocommittade Kafka-records är ännu inte synliga.
\* Båda lägena gör dokumentet beständigt innan Kafka får committas.
Prepare(w) ==
    /\ stage[w] = "Prepared"
    /\ cached' = IF BrokenLocalCommit /\ Mode = "STRIKT" THEN cached ELSE cached \cup {active[w]}
    /\ latest' = IF BrokenLocalCommit /\ Mode = "STRIKT" THEN latest
                   ELSE [latest EXCEPT ![Process(active[w])] = Max(@, version[active[w]])]
    /\ active' = IF Mode = "LOKAL_RESILIENS" THEN [active EXCEPT ![w] = None] ELSE active
    /\ stage' = [stage EXCEPT ![w] = IF Mode = "LOKAL_RESILIENS" THEN "Idle" ELSE "Send"]
    /\ UNCHANGED <<created, sent, backend, observed, acknowledged, restored,
                   published, version, stable, ordered, highest, duplicateAccepted,
                   graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

AcquireReplay(w, d) ==
    /\ Oldest(d) /\ CanOwn(w, Process(d))
    /\ active' = [active EXCEPT ![w] = d]
    /\ stage' = [stage EXCEPT ![w] = "Send"]
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged,
                   restored, published, version, latest, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Kafka-commit gör leveransen synlig; klienten kan fortfarande sakna commitkvittot.
Accept(w) ==
    /\ stage[w] = "Send"
    /\ LET d == active[w] IN
        /\ sent' = sent \cup {d}
        /\ duplicateAccepted' = (duplicateAccepted \/ d \in sent)
        /\ observed' = observed \cup {<<d, version[d]>>}
        /\ ordered' = (ordered /\ (d \in sent \/ version[d] >= highest[Process(d)]))
        /\ highest' = [highest EXCEPT ![Process(d)] =
                          IF version[d] > @ THEN version[d] ELSE @]
    /\ stage' = [stage EXCEPT ![w] = "Accepted"]
    /\ UNCHANGED <<created, cached, backend, acknowledged, restored,
                   published, version, latest, active, stable, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

Ack(w) ==
    /\ stage[w] = "Accepted"
    /\ acknowledged' = acknowledged \cup {active[w]}
    /\ stage' = [stage EXCEPT ![w] = "Acked"]
    /\ UNCHANGED <<created, cached, sent, backend, observed, restored,
                   published, version, latest, active, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Lokal publiceringsstatus uppdateras efter Kafka-commit. Krascher före detta kan ge replay.
Commit(w) ==
    /\ stage[w] = "Acked"
    /\ cached' = cached \cup {active[w]}
    /\ published' = published \cup {active[w]}
    /\ latest' = [latest EXCEPT ![Process(active[w])] = Max(@, version[active[w]])]
    /\ active' = [active EXCEPT ![w] = None]
    /\ stage' = [stage EXCEPT ![w] = "Idle"]
    /\ UNCHANGED <<created, sent, backend, observed, acknowledged, restored,
                   version, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Krasch, Kafka-fel, förlorat kvitto eller databasfel tappar endast flyktigt arbete.
\* Beständiga Kafka-data och redan committade cacherader finns kvar.
Crash(w) ==
    /\ ~stable /\ active[w] # None
    /\ active' = [active EXCEPT ![w] = None]
    /\ stage' = [stage EXCEPT ![w] = "Idle"]
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged,
                   restored, published, version, latest, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

Consume(d) ==
    /\ d \in sent \ backend
    /\ backend' = backend \cup {d}
    /\ UNCHANGED <<created, cached, sent, observed, acknowledged, restored,
                   published, version, latest, active, stage, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Backendläsning återför originalet utan ny publicering; positivt rådatakvitto avstäms separat.
Restore(d) ==
    /\ d \in backend \ cached /\ Free(Process(d))
    /\ cached' = cached \cup {d}
    /\ restored' = restored \cup {d}
    /\ published' = published \cup {d}
    /\ latest' = [latest EXCEPT ![Process(d)] = Max(@, version[d])]
    /\ UNCHANGED <<created, sent, backend, observed, acknowledged, version,
                   active, stage, stable, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Både rådata och index är beständiga. Kafka-vidareleveransen kan fortfarande saknas.
\* Backend.tla fördjupar de separata externa skrivningarna.
Forward(d) ==
    /\ d \in backend \ offsetsCommitted
    /\ forwarded' = IF BrokenForwarding THEN forwarded ELSE forwarded \cup {d}
    /\ offsetsCommitted' = offsetsCommitted \cup {d}
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged, restored,
                   published, version, latest, active, stage, stable, ordered, highest,
                   duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion>>

\* Endast en committad vidareleverans får mata grafen.
GraphConsume(d) ==
    /\ d \in forwarded \ graph
    /\ graph' = graph \cup {d}
    /\ graphLatest' = [graphLatest EXCEPT ![Process(d)] =
                         IF BrokenGraphGuard THEN version[d] ELSE Max(@, version[d])]
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged, restored,
                   published, version, latest, active, stage, stable, ordered, highest,
                   duplicateAccepted, rawConfirmed, graphConfirmed, readVersion, forwarded, offsetsCommitted>>

\* Positiv REST-bekräftelse eller observation av grafens indatatopic.
ObserveRaw(d) ==
    /\ d \in backend \cap cached /\ d \notin rawConfirmed
    /\ rawConfirmed' = rawConfirmed \cup {d}
    /\ published' = published \cup {d}
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged, restored,
                   version, latest, active, stage, stable, ordered, highest,
                   duplicateAccepted, graph, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Grafkvittot kan nå cachen före rådatakvittot. Frånvaro bevisar inget.
ObserveGraph(d) ==
    /\ d \in cached /\ d \notin graphConfirmed
    /\ (d \in graph \/ BrokenObservation)
    /\ graphConfirmed' = graphConfirmed \cup {d}
    /\ rawConfirmed' = rawConfirmed \cup {d}
    /\ published' = published \cup {d}
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged, restored,
                   version, latest, active, stage, stable, ordered, highest,
                   duplicateAccepted, graph, graphLatest, readVersion, forwarded, offsetsCommitted>>

\* Strikt läge pausar processens återläsning medan någon leverans är obekräftad.
Read(p) ==
    /\ Mode = "LOKAL_RESILIENS" \/ {d \in Pending : Process(d) = p} = {}
    /\ readVersion' = [readVersion EXCEPT ![p] = Latest(p)]
    /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged, restored,
                   published, version, latest, active, stage, stable, ordered, highest,
                   duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, forwarded, offsetsCommitted>>

Stabilize == /\ ~stable /\ stable' = TRUE
             /\ UNCHANGED <<created, cached, sent, backend, observed, acknowledged,
                            restored, published, version, latest, active, stage, ordered, highest, duplicateAccepted, graph, rawConfirmed, graphConfirmed, graphLatest, readVersion, forwarded, offsetsCommitted>>
Quiescent == /\ Pending = {} /\ \A w \in Workers : active[w] = None
             /\ UNCHANGED vars

Next == \/ \E w \in Workers, d \in Deliveries : Begin(w, d) \/ AcquireReplay(w, d)
        \/ \E w \in Workers : Prepare(w) \/ Accept(w) \/ Ack(w) \/ Commit(w) \/ Crash(w)
        \/ \E d \in Deliveries : Consume(d) \/ Restore(d) \/ Forward(d) \/ GraphConsume(d) \/ ObserveRaw(d) \/ ObserveGraph(d)
        \/ \E p \in Processes : Read(p)
        \/ Stabilize \/ Quiescent

SafetySpec == Init /\ [][Next]_vars
\* Eventuell stabilitet och fortsatt, rättvist arbete är antaganden, inte Kafka-garantier.
LiveSpec == SafetySpec /\ WF_vars(Stabilize)
            /\ (\A w \in Workers :
                /\ WF_vars(Prepare(w)) /\ WF_vars(Accept(w))
                /\ WF_vars(Ack(w)) /\ WF_vars(Commit(w)))
            /\ (\A w \in Workers, d \in Deliveries : SF_vars(AcquireReplay(w, d)))
            /\ (\A d \in Deliveries : /\ WF_vars(Consume(d)) /\ WF_vars(Forward(d)) /\ WF_vars(GraphConsume(d))
                                     /\ WF_vars(ObserveRaw(d)) /\ WF_vars(ObserveGraph(d)))

TypeOK == /\ created \subseteq Deliveries /\ cached \subseteq created
          /\ sent \subseteq created /\ backend \subseteq sent
          /\ acknowledged \subseteq sent /\ restored \subseteq backend
          /\ published \subseteq cached /\ observed \subseteq (Deliveries \X (1..MaxDeliveries))
          /\ version \in [Deliveries -> 0..MaxDeliveries]
          /\ latest \in [Processes -> 0..MaxDeliveries]
          /\ active \in [Workers -> Deliveries \cup {None}]
          /\ stage \in [Workers -> {"Idle", "Prepared", "Send", "Accepted", "Acked"}]
          /\ stable \in BOOLEAN /\ ordered \in BOOLEAN /\ duplicateAccepted \in BOOLEAN
          /\ highest \in [Processes -> 0..MaxDeliveries]
          /\ graph \subseteq forwarded /\ forwarded \subseteq backend /\ offsetsCommitted \subseteq backend /\ rawConfirmed \subseteq cached /\ graphConfirmed \subseteq cached
          /\ graphLatest \in [Processes -> 0..MaxDeliveries]
          /\ readVersion \in [Processes -> 0..MaxDeliveries]
          /\ \A w \in Workers : (active[w] = None) <=> (stage[w] = "Idle")
DeliveryIdentity == \A pair \in observed : pair[2] = version[pair[1]]
PublishedMeansAccepted == published \subseteq sent
KafkaHasLocalCopy == sent \subseteq cached
RawEvidenceSound == rawConfirmed \subseteq backend
OffsetHasForwarded == offsetsCommitted = forwarded
GraphEvidenceSound == graphConfirmed \subseteq graph
GraphNeverRegresses == \A d \in graph : version[d] <= graphLatest[Process(d)]
StrictReadHasEvidence == Mode = "STRIKT" => \A p \in Processes :
    readVersion[p] = 0 \/ \E d \in published : Process(d) = p /\ version[d] = readVersion[p]
EventuallyInGraph == \A d \in Deliveries : (d \in backend) ~> (d \in graph)
EventuallyObserved == \A d \in Deliveries : (d \in graph /\ d \in cached) ~> (d \in graphConfirmed)
NoConcurrentProcessWorkers == \A a, b \in Workers :
    (a # b /\ active[a] # None /\ active[b] # None) => Process(active[a]) # Process(active[b])
FirstAcceptanceOrdered == ordered
ResilientFirstAcceptanceOrdered == Mode = "LOKAL_RESILIENS" => ordered
NoCacheRegression == \A d \in Deliveries : (d \in cached) => (version[d] <= Latest(Process(d)))
NoDuplicateAcceptance == ~duplicateAccepted
EventuallyDelivered == \A d \in Deliveries : (d \in cached) ~> (d \in published)
EventuallyInBackend == \A d \in Deliveries : (d \in sent) ~> (d \in backend)
=============================================================================
