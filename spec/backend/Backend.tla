---------------------------- MODULE Backend ----------------------------
EXTENDS Naturals, FiniteSets

CONSTANTS Processes, Workers, MaxDeliveries, Copies, Conflicts, Foreign, None,
          WriteOrder, Confirmation, ReadPolicy,
          BrokenIndexProof, BrokenRepair, BrokenOverwrite, IndexWorkers
ASSUME /\ WriteOrder \in {"INDEX_FIRST", "OBJECT_FIRST", "MIXED"}
       /\ Confirmation \in {"BOTH_REQUIRED", "OBJECT_SUFFICIENT"}
       /\ ReadPolicy \in {"VERSION", "LAST_WRITE"}
       /\ MaxDeliveries > 0 /\ Copies > 0
       /\ Conflicts \in BOOLEAN /\ BrokenIndexProof \in BOOLEAN
       /\ BrokenRepair \in BOOLEAN /\ BrokenOverwrite \in BOOLEAN
       /\ Foreign \notin Processes
       /\ IndexWorkers \subseteq Workers

Ids == Processes \X (1..MaxDeliveries)
Kinds == IF Conflicts THEN {"original", "changed-body", "changed-correlation"}
         ELSE {"original"}
Messages == [id: Ids, kind: Kinds, copy: 1..Copies]
Data(m) == [id |-> m.id,
            correlation |-> IF m.kind = "changed-correlation" THEN Foreign ELSE m.id[1],
            version |-> m.id[2],
            payload |-> IF m.kind = "changed-body" THEN "B" ELSE "A"]
Records == {Data(m) : m \in Messages}

VARIABLES objects, index, repair, history, completed, rejected, active, stage,
          stable, returned, lastIndexed, readHighest, readOrdered
vars == <<objects, index, repair, history, completed, rejected, active, stage,
          stable, returned, lastIndexed, readHighest, readOrdered>>

\* Varje första skrivning är atomisk: lägg in om nyckeln saknas, annars jämför data.
\* "Nyckeln finns" räcker inte för att acceptera en konflikt.
CanWrite(store, r) == store[r.id] = None \/ store[r.id] = r \/ BrokenOverwrite
HasObject(r) == objects[r.id] = r
HasIndex(r) == index[r.id] = r
Order(w) == IF WriteOrder = "MIXED"
            THEN IF w \in IndexWorkers THEN "INDEX_FIRST" ELSE "OBJECT_FIRST"
            ELSE WriteOrder
FirstStore(w) == IF Order(w) = "INDEX_FIRST" THEN index ELSE objects
SecondStore(w) == IF Order(w) = "INDEX_FIRST" THEN objects ELSE index

Init == /\ objects = [d \in Ids |-> None]
        /\ index = [d \in Ids |-> None]
        /\ repair = [d \in Ids |-> None]
        /\ history = {} /\ completed = {} /\ rejected = {}
        /\ active = [w \in Workers |-> None]
        /\ stage = [w \in Workers |-> "Idle"]
        /\ stable = FALSE /\ returned = {}
        /\ lastIndexed = [p \in Processes |-> None]
        /\ readHighest = [p \in Processes |-> 0]
        /\ readOrdered = TRUE

\* Kafka-meddelandet behålls tills det bekräftats eller avvisats.
\* Flera arbetare kan samtidigt hantera samma id; villkorade skrivningar samordnar dem.
Begin(w, m) ==
    /\ active[w] = None /\ m \notin completed \cup rejected
    /\ active' = [active EXCEPT ![w] = m]
    /\ stage' = [stage EXCEPT ![w] = "First"]
    /\ UNCHANGED <<objects, index, repair, history, completed, rejected, stable,
                   returned, lastIndexed, readHighest, readOrdered>>

\* Skrivningen av indexet kan även upprepas med samma innehåll.
UpdateLast(r) == IF r.correlation \in Processes
                THEN [lastIndexed EXCEPT ![r.correlation] = r.id] ELSE lastIndexed

WriteFirst(w) ==
    /\ stage[w] = "First"
    /\ LET r == Data(active[w]) IN
        /\ CanWrite(FirstStore(w), r)
        /\ index' = IF Order(w) = "INDEX_FIRST" THEN [index EXCEPT ![r.id] = r] ELSE index
        /\ objects' = IF Order(w) = "OBJECT_FIRST" THEN [objects EXCEPT ![r.id] = r] ELSE objects
        /\ repair' = IF Order(w) = "INDEX_FIRST" THEN [repair EXCEPT ![r.id] = None] ELSE repair
        /\ lastIndexed' = IF Order(w) = "INDEX_FIRST" THEN UpdateLast(r) ELSE lastIndexed
        /\ history' = history \cup {r}
    /\ stage' = [stage EXCEPT ![w] = "Second"]
    /\ UNCHANGED <<completed, rejected, active, stable, returned, readHighest, readOrdered>>

WriteSecond(w) ==
    /\ stage[w] = "Second"
    /\ LET r == Data(active[w]) IN
        /\ CanWrite(SecondStore(w), r)
        /\ index' = IF Order(w) = "OBJECT_FIRST" THEN [index EXCEPT ![r.id] = r] ELSE index
        /\ objects' = IF Order(w) = "INDEX_FIRST" THEN [objects EXCEPT ![r.id] = r] ELSE objects
        /\ repair' = IF Order(w) = "OBJECT_FIRST" THEN [repair EXCEPT ![r.id] = None] ELSE repair
        /\ lastIndexed' = IF Order(w) = "OBJECT_FIRST" THEN UpdateLast(r) ELSE lastIndexed
        /\ history' = history \cup {r}
    /\ stage' = [stage EXCEPT ![w] = "Ready"]
    /\ UNCHANGED <<completed, rejected, active, stable, returned, readHighest, readOrdered>>

\* För att acceptera ett indexfel behövs ett hållbart reparationsuppdrag före kvitto.
\* Uppdraget innehåller även metadata; en naken object-store-nyckel räcker inte alltid.
QueueRepair(w) ==
    /\ active[w] # None /\ Confirmation = "OBJECT_SUFFICIENT"
    /\ LET r == Data(active[w]) IN
        /\ HasObject(r) /\ ~HasIndex(r) /\ repair[r.id] = None
        /\ repair' = [repair EXCEPT ![r.id] = r]
    /\ UNCHANGED <<objects, index, history, completed, rejected, active, stage,
                   stable, returned, lastIndexed, readHighest, readOrdered>>

Confirm(w) ==
    /\ active[w] # None
    /\ LET r == Data(active[w]) IN
        /\ (IF BrokenIndexProof THEN HasIndex(r) ELSE HasObject(r))
        /\ (HasIndex(r) \/
              (Confirmation = "OBJECT_SUFFICIENT" /\ (repair[r.id] = r \/ BrokenRepair)))
    /\ completed' = completed \cup {active[w]}
    /\ active' = [active EXCEPT ![w] = None]
    /\ stage' = [stage EXCEPT ![w] = "Idle"]
    /\ UNCHANGED <<objects, index, repair, history, rejected, stable, returned,
                   lastIndexed, readHighest, readOrdered>>

Reject(w) ==
    /\ stage[w] \in {"First", "Second"}
    /\ LET store == IF stage[w] = "First" THEN FirstStore(w) ELSE SecondStore(w)
           r == Data(active[w]) IN
        /\ store[r.id] # None /\ store[r.id] # r /\ ~BrokenOverwrite
    /\ rejected' = rejected \cup {active[w]}
    /\ active' = [active EXCEPT ![w] = None]
    /\ stage' = [stage EXCEPT ![w] = "Idle"]
    /\ UNCHANGED <<objects, index, repair, history, completed, stable, returned,
                   lastIndexed, readHighest, readOrdered>>

\* Krasch eller skrivfel lämnar redan committade effekter kvar, men inget flyktigt arbete.
Crash(w) ==
    /\ ~stable /\ active[w] # None
    /\ active' = [active EXCEPT ![w] = None]
    /\ stage' = [stage EXCEPT ![w] = "Idle"]
    /\ UNCHANGED <<objects, index, repair, history, completed, rejected, stable,
                   returned, lastIndexed, readHighest, readOrdered>>

Repair(d) ==
    /\ repair[d] # None
    /\ LET r == repair[d] IN
        /\ HasObject(r) /\ CanWrite(index, r)
        /\ index' = [index EXCEPT ![d] = r]
        /\ lastIndexed' = UpdateLast(r)
        /\ history' = history \cup {r}
    /\ repair' = [repair EXCEPT ![d] = None]
    /\ UNCHANGED <<objects, completed, rejected, active, stage, stable,
                   returned, readHighest, readOrdered>>

\* Välj senaste indexerade objektversion, men lämna bara ut verifierat lagrat data.
\* En högre indexversion utan objekt är ett ofullständigt tillstånd, inte ett lagringsbevis.
Indexed(p) == {d \in Ids : index[d] # None /\ index[d].correlation = p}
Candidate(p) ==
    IF Indexed(p) = {} THEN None
    ELSE IF ReadPolicy = "LAST_WRITE" THEN lastIndexed[p]
    ELSE CHOOSE d \in Indexed(p) :
             \A other \in Indexed(p) : index[other].version <= index[d].version

Read(p) ==
    /\ Candidate(p) # None
    /\ LET r == index[Candidate(p)] IN
        /\ HasObject(r)
        /\ returned' = returned \cup {r}
        /\ readOrdered' = (readOrdered /\ r.version >= readHighest[p])
        /\ readHighest' = [readHighest EXCEPT ![p] = IF r.version > @ THEN r.version ELSE @]
    /\ UNCHANGED <<objects, index, repair, history, completed, rejected,
                   active, stage, stable, lastIndexed>>

Stabilize == /\ ~stable /\ stable' = TRUE
             /\ UNCHANGED <<objects, index, repair, history, completed, rejected,
                            active, stage, returned, lastIndexed, readHighest, readOrdered>>
Quiescent == /\ \A w \in Workers : active[w] = None
             /\ UNCHANGED vars

Next == \/ \E w \in Workers, m \in Messages : Begin(w, m)
        \/ \E w \in Workers :
             WriteFirst(w) \/ WriteSecond(w) \/ QueueRepair(w) \/ Confirm(w) \/ Reject(w) \/ Crash(w)
        \/ \E d \in Ids : Repair(d)
        \/ \E p \in Processes : Read(p)
        \/ Stabilize \/ Quiescent

SafetySpec == Init /\ [][Next]_vars
LiveSpec == SafetySpec /\ WF_vars(Stabilize)
            /\ (\A w \in Workers :
                  /\ WF_vars(WriteFirst(w)) /\ WF_vars(WriteSecond(w))
                  /\ WF_vars(QueueRepair(w)) /\ WF_vars(Confirm(w)) /\ WF_vars(Reject(w)))
            /\ (\A w \in Workers, m \in Messages : SF_vars(Begin(w, m)))
            /\ (\A d \in Ids : WF_vars(Repair(d)))

TypeOK == /\ objects \in [Ids -> Records \cup {None}]
          /\ index \in [Ids -> Records \cup {None}]
          /\ repair \in [Ids -> Records \cup {None}]
          /\ history \subseteq Records /\ returned \subseteq Records
          /\ completed \subseteq Messages /\ rejected \subseteq Messages
          /\ active \in [Workers -> Messages \cup {None}]
          /\ stage \in [Workers -> {"Idle", "First", "Second", "Ready"}]
          /\ stable \in BOOLEAN /\ readOrdered \in BOOLEAN
          /\ lastIndexed \in [Processes -> Ids \cup {None}]
          /\ readHighest \in [Processes -> 0..MaxDeliveries]
          /\ \A w \in Workers : (active[w] = None) <=> (stage[w] = "Idle")
IdentityImmutable == \A d \in Ids : Cardinality({r \in history : r.id = d}) <= 1
StoresAgree == \A d \in Ids : (index[d] # None /\ objects[d] # None) => index[d] = objects[d]
ConfirmedHasObject == \A m \in completed : HasObject(Data(m))
ConfirmedHasIndexOrRepair == \A m \in completed : HasIndex(Data(m)) \/ repair[m.id] = Data(m)
ReturnedHasObject == \A r \in returned : HasObject(r)
ReadDoesNotRegress == readOrdered
EventuallySettled == \A m \in Messages : <>(m \in completed \cup rejected)
EventuallyDiscoverable == \A m \in Messages : (m \in completed) ~> HasIndex(Data(m))
=============================================================================
