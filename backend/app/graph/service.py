import logging
from neo4j import GraphDatabase
from app.core.config import settings
from app.services.threat_intel.lookup import normalize, TYPE_OF

log = logging.getLogger("digital_shield.graph")

LINK = """
MERGE (e:Evidence {key: $ekey})
SET e.type = $etype, e.id = $eid, e.band = $band, e.score = $score
WITH e
UNWIND $iocs AS ioc
MERGE (i:IOC {key: ioc.key})
SET i.type = ioc.type, i.value = ioc.value
MERGE (i)-[:APPEARS_IN]->(e)
"""

NEIGHBOURS = """
MATCH (s:IOC {key: $key})-[:APPEARS_IN]->(e:Evidence)
OPTIONAL MATCH (o:IOC)-[:APPEARS_IN]->(e) WHERE o.key <> s.key
OPTIONAL MATCH (o)-[:APPEARS_IN]->(e2:Evidence) WHERE e2.key <> e.key
RETURN e, o, e2
"""

def ioc_list(iocs, sender=None):
    found = []
    if sender:
        found.append(("phone", normalize("phone", sender)))
    for k, t in TYPE_OF.items():
        for raw in iocs.get(k, []):
            found.append((t, normalize(t, raw)))
    seen, out = set(), []
    for t, v in found:
        key = f"{t}:{v}"
        if v and key not in seen:
            seen.add(key)
            out.append({"type": t, "value": v, "key": key})
    return out

def build_graph(center_key, rows):
    t, v = center_key.split(":", 1)
    nodes = {center_key: {"id": center_key, "kind": "ioc", "type": t, "value": v}}
    edges = set()

    def add_ev(p):
        nodes[p["key"]] = {"id": p["key"], "kind": "evidence", "type": p["type"],
                           "evidence_id": p.get("id"), "band": p.get("band"), "score": p.get("score")}

    def add_ioc(p):
        nodes[p["key"]] = {"id": p["key"], "kind": "ioc", "type": p["type"], "value": p["value"]}

    for e, o, e2 in rows:
        add_ev(e); edges.add((center_key, e["key"]))
        if o:
            add_ioc(o); edges.add((o["key"], e["key"]))
            if e2:
                add_ev(e2); edges.add((o["key"], e2["key"]))

    evidence = [n for n in nodes.values() if n["kind"] == "evidence"]
    return {
        "center": center_key,
        "nodes": list(nodes.values()),
        "edges": [{"source": a, "target": b, "type": "APPEARS_IN"} for a, b in sorted(edges)],
        "summary": {"evidence_count": len(evidence),
                    "linked_iocs": sum(1 for n in nodes.values() if n["kind"] == "ioc") - 1},
    }

class Neo4jGraph:
    def __init__(self, driver):
        self.driver = driver

    def ensure_schema(self):
        with self.driver.session() as s:
            s.run("CREATE CONSTRAINT ioc_key IF NOT EXISTS FOR (i:IOC) REQUIRE i.key IS UNIQUE")
            s.run("CREATE CONSTRAINT evidence_key IF NOT EXISTS FOR (e:Evidence) REQUIRE e.key IS UNIQUE")

    def link(self, evidence_type, evidence_id, band, score, iocs, sender=None):
        try:
            with self.driver.session() as s:
                s.run(LINK, ekey=f"{evidence_type}:{evidence_id}", etype=evidence_type,
                      eid=evidence_id, band=band, score=score, iocs=ioc_list(iocs, sender))
        except Exception as e:
            log.warning("Graph link skipped (Neo4j unavailable?): %s", e)

    def neighbours(self, ioc_type, value):
        key = f"{ioc_type}:{normalize(ioc_type, value)}"
        with self.driver.session() as s:
            rows = [(dict(r["e"]), dict(r["o"]) if r["o"] else None, dict(r["e2"]) if r["e2"] else None)
                    for r in s.run(NEIGHBOURS, key=key)]
        return build_graph(key, rows)

    def stats(self):
        with self.driver.session() as s:
            ev = s.run("MATCH (e:Evidence) RETURN count(e) AS c").single()["c"]
            io = s.run("MATCH (i:IOC) RETURN count(i) AS c").single()["c"]
            ln = s.run("MATCH ()-[r:APPEARS_IN]->() RETURN count(r) AS c").single()["c"]
        return {"evidence": ev, "iocs": io, "links": ln}

class InMemoryGraph:
    """Same behaviour as Neo4jGraph, used in tests."""
    def __init__(self):
        self.evidence, self.iocs, self.links = {}, {}, set()

    def ensure_schema(self):
        pass

    def link(self, evidence_type, evidence_id, band, score, iocs, sender=None):
        ekey = f"{evidence_type}:{evidence_id}"
        self.evidence[ekey] = {"key": ekey, "type": evidence_type, "id": evidence_id, "band": band, "score": score}
        for i in ioc_list(iocs, sender):
            self.iocs[i["key"]] = i
            self.links.add((i["key"], ekey))

    def neighbours(self, ioc_type, value):
        key = f"{ioc_type}:{normalize(ioc_type, value)}"
        rows = []
        for e in [e for (i, e) in self.links if i == key]:
            others = [i for (i, x) in self.links if x == e and i != key]
            if not others:
                rows.append((self.evidence[e], None, None))
            for o in others:
                e2s = [x for (i, x) in self.links if i == o and x != e]
                if not e2s:
                    rows.append((self.evidence[e], self.iocs[o], None))
                for x in e2s:
                    rows.append((self.evidence[e], self.iocs[o], self.evidence[x]))
        return build_graph(key, rows)

    def stats(self):
        return {"evidence": len(self.evidence), "iocs": len(self.iocs), "links": len(self.links)}

_graph = None

def get_graph():
    global _graph
    if _graph is None:
        driver = GraphDatabase.driver(settings.neo4j_uri, auth=(settings.neo4j_user, settings.neo4j_password),
                                      connection_timeout=3, max_transaction_retry_time=2)
        _graph = Neo4jGraph(driver)
    return _graph
