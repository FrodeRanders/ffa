--------------------------- MODULE Receipts ---------------------------
EXTENDS Naturals, FiniteSets, Sequences

CONSTANTS Deliveries, MaxDuplicates, CorruptReceipt, BrokenOffset,
          BrokenGraphReceipt, BrokenRestore, BrokenIdentity, None, Wrong
ASSUME /\ Deliveries # {} /\ MaxDuplicates \in Nat
       /\ None \notin Deliveries /\ Wrong \notin Deliveries /\ Wrong # None
       /\ CorruptReceipt \in BOOLEAN /\ BrokenOffset \in BOOLEAN
       /\ BrokenGraphReceipt \in BOOLEAN /\ BrokenRestore \in BOOLEAN
       /\ BrokenIdentity \in BOOLEAN

\* Token d representerar hela leveransens oföränderliga bytes och metadata,
\* inte bara objektversionen. Wrong är ett annat innehåll med samma leverans-id.
Receipt(d, step, token) == [id |-> d, step |-> step, token |-> token]
Stages == {"raw", "graph"}
ReceiptSet == [id : Deliveries, step : Stages, token : Deliveries \cup {Wrong}]

VARIABLES cached, accepted, objects, index, forwarded, rawOffsets,
          graphStored, graphOffsets, topic, duplicates, corruptSent,
          inbox, offset, current, sqlCommitted, rawConfirmed, graphConfirmed,
          restored, stable
vars == <<cached, accepted, objects, index, forwarded, rawOffsets,
          graphStored, graphOffsets, topic, duplicates, corruptSent,
          inbox, offset, current, sqlCommitted, rawConfirmed, graphConfirmed,
          restored, stable>>

Init == /\ cached = Deliveries /\ accepted = {}
        /\ objects = {} /\ index = {} /\ forwarded = {} /\ rawOffsets = {}
        /\ graphStored = {} /\ graphOffsets = {} /\ topic = <<>>
        /\ duplicates = 0 /\ corruptSent = FALSE
        /\ inbox = {} /\ offset = 0 /\ current = None /\ sqlCommitted = FALSE
        /\ rawConfirmed = {} /\ graphConfirmed = {} /\ restored = {}
        /\ stable = FALSE

\* N−2:s Kafka-commit kräver den beständiga lokala originalleveransen.
Publish(d) ==
    /\ d \in cached \ accepted
    /\ accepted' = accepted \cup {d}
    /\ UNCHANGED <<cached, objects, index, forwarded, rawOffsets, graphStored,
                   graphOffsets, topic, duplicates, corruptSent, inbox, offset,
                   current, sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>

\* N−1:s externa commits är separata från Kafka och överlever en abort.
PutObject(d) ==
    /\ d \in accepted \ objects /\ objects' = objects \cup {d}
    /\ UNCHANGED <<cached, accepted, index, forwarded, rawOffsets, graphStored,
                   graphOffsets, topic, duplicates, corruptSent, inbox, offset,
                   current, sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>
PutIndex(d) ==
    /\ d \in objects \ index /\ index' = index \cup {d}
    /\ UNCHANGED <<cached, accepted, objects, forwarded, rawOffsets, graphStored,
                   graphOffsets, topic, duplicates, corruptSent, inbox, offset,
                   current, sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>

\* En Kafka-commit omfattar vidareleverans, rådatakvitto och källoffset.
RawCommit(d) ==
    /\ d \in (objects \cap index) \ rawOffsets
    /\ forwarded' = forwarded \cup {d} /\ rawOffsets' = rawOffsets \cup {d}
    /\ topic' = Append(topic, Receipt(d, "raw", d))
    /\ UNCHANGED <<cached, accepted, objects, index, graphStored, graphOffsets,
                   duplicates, corruptSent, inbox, offset, current, sqlCommitted,
                   rawConfirmed, graphConfirmed, restored, stable>>

\* Neo4j-commit sker före grafens separata Kafka-commit. Ett avbrott mellan
\* dem lämnar grafdata kvar; idempotent återbehandling kan sedan kvittera.
WriteGraph(d) ==
    /\ d \in forwarded \ graphStored /\ graphStored' = graphStored \cup {d}
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphOffsets, topic, duplicates, corruptSent, inbox, offset,
                   current, sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>
GraphCommit(d) ==
    /\ d \in forwarded \ graphOffsets
    /\ d \in graphStored \/ BrokenGraphReceipt
    /\ graphOffsets' = graphOffsets \cup {d}
    /\ topic' = Append(topic, Receipt(d, "graph", d))
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, duplicates, corruptSent, inbox, offset, current,
                   sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>

\* Nya Kafka-transaktioner efter osäkert commitkvitto kan ge samma kvitto igen.
Duplicate ==
    /\ duplicates < MaxDuplicates /\ Len(topic) > 0
    /\ \E i \in 1..Len(topic) : topic' = Append(topic, topic[i])
    /\ duplicates' = duplicates + 1
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, corruptSent, inbox, offset, current,
                   sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>
Corrupt ==
    /\ CorruptReceipt /\ ~corruptSent
    /\ \E d \in Deliveries : topic' = Append(topic, Receipt(d, "graph", Wrong))
    /\ corruptSent' = TRUE
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, duplicates, inbox, offset, current,
                   sqlCommitted, rawConfirmed, graphConfirmed, restored, stable>>

\* Flyktig pollposition. Nästa processstart läser från beständig offset.
Poll ==
    /\ current = None /\ offset < Len(topic)
    /\ current' = offset + 1 /\ sqlCommitted' = FALSE
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, topic, duplicates, corruptSent,
                   inbox, offset, rawConfirmed, graphConfirmed, restored, stable>>

\* Leveranslåset serialiserar inkorgsskrivning och återställning i Java-koden.
\* Här abstraheras respektive SQL-transaktion som ett atomiskt steg.
Valid(r) == /\ \A saved \in inbox : saved.id = r.id => saved.token = r.token
            /\ r.id \in cached => r.token = r.id
SqlCommit ==
    /\ current # None /\ ~sqlCommitted
    /\ LET r == topic[current] IN
        /\ Valid(r) \/ BrokenIdentity
        /\ inbox' = inbox \cup {r}
        /\ rawConfirmed' = IF r.id \in cached THEN rawConfirmed \cup {r.id} ELSE rawConfirmed
        /\ graphConfirmed' = IF r.id \in cached /\ r.step = "graph"
                                THEN graphConfirmed \cup {r.id} ELSE graphConfirmed
    /\ sqlCommitted' = TRUE
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, topic, duplicates, corruptSent,
                   offset, current, restored, stable>>
OffsetCommit ==
    /\ current # None /\ (sqlCommitted \/ BrokenOffset)
    /\ offset' = current /\ current' = None /\ sqlCommitted' = FALSE
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, topic, duplicates, corruptSent,
                   inbox, rawConfirmed, graphConfirmed, restored, stable>>
Crash ==
    /\ ~stable /\ current # None
    /\ current' = None /\ sqlCommitted' = FALSE
    /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, topic, duplicates, corruptSent,
                   inbox, offset, rawConfirmed, graphConfirmed, restored, stable>>

\* En redan publicerad cachepost kan saknas; inkorgen gallras inte med posten.
\* Felperioden måste ta slut för att återställning ska kunna garanteras.
Evict(d) ==
    /\ ~stable /\ d \in cached \cap accepted
    /\ cached' = cached \ {d} /\ restored' = restored \ {d}
    /\ rawConfirmed' = rawConfirmed \ {d} /\ graphConfirmed' = graphConfirmed \ {d}
    /\ UNCHANGED <<accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, topic, duplicates, corruptSent,
                   inbox, offset, current, sqlCommitted, stable>>
Restore(d) ==
    /\ d \in (objects \cap index) \ cached
    /\ \A r \in inbox : r.id = d => r.token = d
    /\ cached' = cached \cup {d} /\ restored' = restored \cup {d}
    /\ rawConfirmed' = rawConfirmed \cup {d}
    /\ graphConfirmed' = IF ~BrokenRestore /\ Receipt(d, "graph", d) \in inbox
                            THEN graphConfirmed \cup {d} ELSE graphConfirmed
    /\ UNCHANGED <<accepted, objects, index, forwarded, rawOffsets,
                   graphStored, graphOffsets, topic, duplicates, corruptSent,
                   inbox, offset, current, sqlCommitted, stable>>
Stabilize == /\ ~stable /\ stable' = TRUE
             /\ UNCHANGED <<cached, accepted, objects, index, forwarded, rawOffsets,
                            graphStored, graphOffsets, topic, duplicates, corruptSent,
                            inbox, offset, current, sqlCommitted, rawConfirmed, graphConfirmed, restored>>

Next == \/ \E d \in Deliveries : Publish(d) \/ PutObject(d) \/ PutIndex(d)
                  \/ RawCommit(d) \/ WriteGraph(d) \/ GraphCommit(d) \/ Evict(d) \/ Restore(d)
        \/ Duplicate \/ Corrupt \/ Poll \/ SqlCommit \/ OffsetCommit \/ Crash \/ Stabilize
SafetySpec == Init /\ [][Next]_vars
LiveSpec == SafetySpec /\ WF_vars(Stabilize) /\ WF_vars(Poll)
            /\ WF_vars(SqlCommit) /\ WF_vars(OffsetCommit)
            /\ (\A d \in Deliveries : /\ WF_vars(Publish(d)) /\ WF_vars(PutObject(d))
                 /\ WF_vars(PutIndex(d)) /\ WF_vars(RawCommit(d)) /\ WF_vars(WriteGraph(d))
                 /\ WF_vars(GraphCommit(d)) /\ WF_vars(Restore(d)))

TypeOK == /\ cached \subseteq Deliveries /\ accepted \subseteq Deliveries
          /\ objects \subseteq accepted /\ index \subseteq objects
          /\ forwarded \subseteq index /\ rawOffsets \subseteq index
          /\ graphStored \subseteq forwarded /\ graphOffsets \subseteq forwarded
          /\ topic \in Seq(ReceiptSet) /\ Len(topic) <= 2 * Cardinality(Deliveries) + MaxDuplicates + 1
          /\ duplicates \in 0..MaxDuplicates /\ corruptSent \in BOOLEAN
          /\ inbox \subseteq ReceiptSet /\ offset \in 0..Len(topic)
          /\ (current = None \/ current \in 1..Len(topic))
          /\ sqlCommitted \in BOOLEAN /\ stable \in BOOLEAN
          /\ rawConfirmed \subseteq cached /\ graphConfirmed \subseteq rawConfirmed
          /\ restored \subseteq cached
SourceOffsetHasForward == rawOffsets = forwarded
ReceiptEvidenceSound == \A i \in 1..Len(topic) : LET r == topic[i] IN
    r.token # r.id \/ (IF r.step = "raw" THEN r.id \in objects \cap index ELSE r.id \in graphStored)
RawOffsetHasReceipt == \A d \in rawOffsets : Receipt(d, "raw", d) \in {topic[i] : i \in 1..Len(topic)}
GraphOffsetHasReceipt == \A d \in graphOffsets : Receipt(d, "graph", d) \in {topic[i] : i \in 1..Len(topic)}
OffsetHasInbox == \A i \in 1..offset : topic[i] \in inbox
CacheEvidenceSound == /\ rawConfirmed \subseteq objects \cap index
                      /\ graphConfirmed \subseteq graphStored
ConfirmedIdentity == \A r \in inbox : r.id \in rawConfirmed => r.token = r.id
RestoredGraphEvidence == \A d \in restored : Receipt(d, "graph", d) \in inbox => d \in graphConfirmed
EventuallyIndexed == \A d \in Deliveries : d \in accepted ~> d \in index
EventuallyGraph == \A d \in Deliveries : d \in accepted ~> d \in graphStored
EventuallyReceived == \A d \in Deliveries : d \in accepted ~> Receipt(d, "graph", d) \in inbox
EventuallyRestored == \A d \in Deliveries : d \in accepted ~> d \in cached /\ d \in graphConfirmed
EventuallyOffset == \A r \in ReceiptSet : r \in {topic[i] : i \in 1..Len(topic)} ~> r \in {topic[i] : i \in 1..offset}
=============================================================================
