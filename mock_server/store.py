"""Persistent, validated mock data. No production credentials are seeded."""
from __future__ import annotations

import base64
import copy
import json
import math
import os
from pathlib import Path
import secrets
import threading

from fixtures import SCENARIOS

ROOT = Path(__file__).resolve().parent
PAGES = ("HOME", "STUDENT", "MYCURSUS", "MYMARKS", "MYCHOICES", "MYDOCUMENTS")
BLOCKED_TEXT = "Suite à plusieurs échecs de connexion, votre IP a été bloquée pour la journée."
DEFAULT_SETTINGS = {
    "closed": False, "ip_blocked": False,
    "login_error": "Vos identifiants sont incorrects. Veuillez réessayer ({remaining} tentatives restantes).",
    "closed_error": "Vos identifiants sont incorrects. Veuillez réessayer ({remaining} tentatives restantes).",
    "max_login_attempts": 10,
    "session_ttl_seconds": 3600, "latency_ms": 0, "failure_status": 0,
}


class ValidationError(ValueError):
    pass


class ConflictError(ValueError):
    pass


def validate_password(password: str) -> str:
    if not isinstance(password, str) or not 1 <= len(password) <= 1024:
        raise ValidationError("Le mot de passe doit contenir entre 1 et 1024 caractères.")
    return password


def check_password(password: str, expected: str) -> bool:
    return password == expected


def new_account(login="demo", password="demo", student_id="10000001", name="Camille Martin"):
    pages = json.loads((ROOT / "field_templates.json").read_text(encoding="utf-8"))
    pages.setdefault("MYMARKS", {"sections": [], "nested_tables": []})
    pages.setdefault("MYCHOICES", {"sections": [], "nested_tables": []})
    account = {
        "id": secrets.token_hex(8), "login": login, "password": validate_password(password),
        "student_id": student_id, "display_name": name, "active": True,
        "scenario": "initial", "pages": pages, "semesters": [], "years": [],
        "choices": [], "documents": [], "route_overrides": [],
    }
    for section in pages["STUDENT"]["sections"]:
        for field in section["fields"]:
            field["value"] = {"FNAME": name.split(" ")[0], "LNAME": " ".join(name.split(" ")[1:]),
                              "CODE_LDAP": login, "STUDENT_CARD_NUMBER": student_id,
                              "MAIL": f"{login}@example.test", "CITY": "Orsay"}.get(field["field_name"], "")
    pages["HOME"]["sections"] = [{"title": "Accueil", "fields": [
        {"field_name": "WELCOME", "label": "Message", "value": "Bienvenue sur votre espace étudiant de démonstration."}]}]
    apply_scenario(account, "initial")
    account["choices"] = [{"year": 2025, "semester": 2, "earned_ects": "4", "modules": [{
        "module_code": "g1i3", "title": "Programmation avancée", "module_state": "INSCRIT",
        "attrs": {"ects": "4", "coeff": "5", "choice_type": "i"}, "courses": [{
            "course_code": "g1i3-2", "title": "C++ 1", "type": "Obligatoire", "coefficient": "5",
            "state": "Inscrit", "attrs": {"optional": "", "choice_type": "i", "data_type": "COMPULSORY"}}]}]}]
    account["documents"] = [{"id": "welcome", "page": "MYDOCUMENTS", "field_name": "GRADEBOOK_IC_3",
        "filename": "exemple.txt", "content_type": "text/plain", "year": 2025,
        "content_base64": base64.b64encode("Document synthétique du simulateur Oasis.\n".encode()).decode()}]
    return account


def apply_scenario(account, scenario):
    if scenario not in SCENARIOS:
        raise ValidationError("Scénario inconnu.")
    semesters = [s for s in account["semesters"] if s["year"] != 2025]
    for (year, semester), payload in SCENARIOS[scenario].items():
        item = {"year": year, "semester": semester, **copy.deepcopy(payload),
                "average": "—", "average_formula": "", "ranking": {"text": "", "success": True, "reload": "no"},
                "validation_report": "ATT pour les raisons suivantes :"}
        for index, exam in enumerate(item["exams"]):
            exam["id"] = f"exam-{semester}-{index}"
            exam["coefficient"] = 1
        semesters.append(item)
    account["semesters"] = semesters
    account["scenario"] = scenario


def _require(condition, message):
    if not condition:
        raise ValidationError(message)


def _text(value, label, nonempty=False):
    _require(isinstance(value, str) and (bool(value.strip()) or not nonempty), f"{label} : texte attendu.")


def _unique(rows, key, label):
    _require(isinstance(rows, list), f"{label} : liste attendue.")
    seen = set()
    for row in rows:
        _require(isinstance(row, dict), f"{label} : objet attendu.")
        value = row.get(key)
        _text(value, f"{label}.{key}", True)
        _require(value not in seen, f"{label} : {key} en double ({value}).")
        seen.add(value)
    return seen


def _fields(fields):
    _unique(fields, "field_name", "Champs")
    for field in fields:
        _text(field.get("label", field.get("column", "")), "Libellé")
        _text(field.get("value"), "Valeur")


def validate_account(account):
    _require(isinstance(account, dict), "Compte : objet attendu.")
    for key in ("id", "login", "student_id", "display_name"):
        _text(account.get(key), key, True)
    _require(len(account["login"]) <= 200 and len(account["student_id"]) <= 200, "Identifiant trop long.")
    _require(all(ch.isalnum() or ch in "-_" for ch in account["student_id"]), "Code étudiant : lettres, chiffres, tiret ou underscore.")
    _require(isinstance(account.get("active"), bool), "active : booléen attendu.")
    _require(account.get("scenario") in SCENARIOS, "Scénario inconnu.")
    validate_password(account.get("password"))
    _require(isinstance(account.get("pages"), dict), "pages : objet attendu.")
    for code in PAGES:
        page = account["pages"].get(code)
        _require(isinstance(page, dict), f"Page {code} manquante.")
        _require(isinstance(page.get("sections"), list) and isinstance(page.get("nested_tables"), list), f"Page {code} : sections/tables attendues.")
        for section in page["sections"]:
            _require(isinstance(section, dict), "Section : objet attendu.")
            _text(section.get("title"), "Titre de section")
            _fields(section.get("fields"))
        for table in page["nested_tables"]:
            _text(table.get("title"), "Titre de table")
            _text(table.get("nested_table_name"), "Nom de table", True)
            _require(isinstance(table.get("headers"), list) and all(isinstance(x, str) for x in table["headers"]), "En-têtes invalides.")
            _unique(table.get("rows"), "row_code", "Lignes")
            for row in table["rows"]:
                _fields(row.get("cells"))
    _require(isinstance(account.get("semesters"), list), "semesters : liste attendue.")
    seen = set()
    for sem in account["semesters"]:
        _require(isinstance(sem, dict), "Semestre : objet attendu.")
        _require(type(sem.get("year")) is int and 1900 <= sem["year"] <= 2200, "Année invalide.")
        _require(type(sem.get("semester")) is int and sem["semester"] in (1, 2), "Semestre : 1 ou 2 attendu.")
        key = (sem["year"], sem["semester"])
        _require(key not in seen, "Semestre en double.")
        seen.add(key)
        _text(sem.get("status"), "Statut du semestre")
        units = _unique(sem.get("units"), "code", "UEs")
        modules = _unique(sem.get("modules"), "code", "Matières")
        _unique(sem.get("exams"), "id", "Épreuves")
        for unit in sem["units"]:
            for k in ("title", "ects", "grade", "average", "rank", "result"):
                _text(unit.get(k), f"UE.{k}")
            _require(isinstance(unit.get("commonCore"), bool), "Tronc commun : booléen attendu.")
        for module in sem["modules"]:
            _require(module.get("groupCode") in units, "Une matière référence une UE inexistante.")
            for k in ("groupTitle", "title", "coefficient", "block", "grade", "average", "rank", "credits"):
                _text(module.get(k), f"Matière.{k}")
        for exam in sem["exams"]:
            _require(exam.get("subjectId") in modules, "Une épreuve référence une matière inexistante.")
            for k in ("subject", "name", "date", "rank", "comment"):
                _text(exam.get(k), f"Épreuve.{k}")
            for k in ("grade", "average"):
                val = exam.get(k)
                _require(val is None or (type(val) in (int, float) and math.isfinite(val) and 0 <= val <= 20), f"{k} : nombre de 0 à 20 ou null.")
        _require(isinstance(sem.get("ranking", {}), dict), "Classement : objet attendu.")
        for key in ("average", "average_formula", "validation_report"):
            _text(sem.get(key, ""), f"Semestre.{key}")
    for key in ("choices", "years", "documents", "route_overrides"):
        _require(isinstance(account.get(key), list), f"{key} : liste attendue.")
    for year in account["years"]:
        _require(isinstance(year, dict) and type(year.get("year")) is int, "Bilan annuel invalide.")
        for k in ("status", "average", "average_formula", "validation_report"):
            _text(year.get(k, ""), f"Bilan annuel.{k}")
    choice_keys = set()
    for choice in account["choices"]:
        _require(isinstance(choice, dict) and type(choice.get("year")) is int and choice.get("semester") in (1, 2), "Année/semestre des choix invalide.")
        key = (choice["year"], choice["semester"])
        _require(key not in choice_keys, "Semestre de choix en double.")
        choice_keys.add(key)
        _text(choice.get("earned_ects"), "ECTS acquis")
        _unique(choice.get("modules"), "module_code", "Choix d'UE")
        for module in choice["modules"]:
            for k in ("title", "module_state"):
                _text(module.get(k), f"Choix.{k}")
            _require(isinstance(module.get("attrs"), dict), "Attributs de choix invalides.")
            _unique(module.get("courses"), "course_code", "Choix de matière")
            for course in module["courses"]:
                for k in ("title", "type", "coefficient", "state"):
                    _text(course.get(k), f"Choix matière.{k}")
                _require(isinstance(course.get("attrs"), dict), "Attributs de matière invalides.")
    _unique(account["documents"], "id", "Documents")
    for doc in account["documents"]:
        _require(doc.get("page") in PAGES, "Page du document invalide.")
        for k in ("field_name", "filename", "content_type", "content_base64"):
            _text(doc.get(k), f"Document.{k}", k != "content_base64")
        _require(not any(c in doc["content_type"] for c in "\r\n"), "Type MIME invalide.")
        try:
            _require(len(base64.b64decode(doc["content_base64"], validate=True)) <= 5 * 1024 * 1024, "Fichier limité à 5 Mo.")
        except ValueError:
            raise ValidationError("Contenu base64 du fichier invalide.") from None
    for rule in account["route_overrides"]:
        _require(isinstance(rule, dict), "Réponse personnalisée : objet attendu.")
        _text(rule.get("route"), "Route", True)
        _require(rule.get("method") in ("GET", "POST", "*"), "Méthode : GET, POST ou *.")
        _require(type(rule.get("status")) is int and 200 <= rule["status"] <= 599, "Statut HTTP invalide.")
        _text(rule.get("body"), "Corps de réponse")
        _text(rule.get("content_type"), "Type de réponse", True)
        _require(not any(c in rule["content_type"] for c in "\r\n"), "Type MIME invalide.")
        _require(isinstance(rule.get("params", {}), dict), "Paramètres de réponse invalides.")


def validate_state(state):
    _require(isinstance(state, dict) and state.get("schema_version") == 1, "Version de sauvegarde inconnue.")
    _require(type(state.get("revision")) is int and state["revision"] >= 0, "Révision invalide.")
    settings = state.get("settings")
    _require(isinstance(settings, dict), "Réglages manquants.")
    for k in ("closed", "ip_blocked"):
        _require(isinstance(settings.get(k), bool), f"{k} : booléen attendu.")
    for k in ("login_error", "closed_error"):
        _text(settings.get(k), k, True)
    for key, low, high in (("max_login_attempts", 1, 100), ("session_ttl_seconds", 1, 86400), ("latency_ms", 0, 30000), ("failure_status", 0, 599)):
        v = settings.get(key)
        _require(type(v) is int and low <= v <= high, f"{key} : valeur entre {low} et {high}.")
    _require(settings["failure_status"] == 0 or settings["failure_status"] >= 400, "Panne HTTP : 0 ou 400–599.")
    _unique(state.get("accounts"), "id", "Comptes")
    logins, students = set(), set()
    for account in state["accounts"]:
        validate_account(account)
        _require(account["login"] not in logins, "Login déjà utilisé.")
        _require(account["student_id"] not in students, "Code étudiant déjà utilisé.")
        logins.add(account["login"])
        students.add(account["student_id"])


class Store:
    def __init__(self, path: Path):
        self.path = Path(path)
        self.lock = threading.RLock()
        if self.path.exists():
            self.state = json.loads(self.path.read_text(encoding="utf-8"))
            validate_state(self.state)
        else:
            second = new_account("lea", "lea", "10000002", "Léa Dubois")
            second["semesters"][0]["exams"][0]["grade"] = 14.5
            self.state = {"schema_version": 1, "revision": 0,
                          "settings": copy.deepcopy(DEFAULT_SETTINGS), "accounts": [new_account(), second]}
            validate_state(self.state)
            self._write(self.state)

    def snapshot(self):
        with self.lock:
            data = copy.deepcopy(self.state)
        return data

    def update(self, revision, mutate):
        with self.lock:
            if revision is not None and revision != self.state["revision"]:
                raise ConflictError("Les données ont changé. Rechargez avant d'enregistrer.")
            candidate = copy.deepcopy(self.state)
            mutate(candidate)
            candidate["revision"] = self.state["revision"] + 1
            validate_state(candidate)
            self._write(candidate)
            self.state = candidate
            return self.snapshot()

    def _write(self, state):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_name(self.path.name + ".tmp")
        with temporary.open("w", encoding="utf-8") as file:
            json.dump(state, file, ensure_ascii=False, indent=2, allow_nan=False)
            file.flush()
            os.fsync(file.fileno())
        os.replace(temporary, self.path)


def import_report(report, login, password):
    """Map retained reverse reports to editable data; never reuse their credentials."""
    _require(isinstance(report, dict), "Rapport JSON attendu.")
    profile = report.get("shell", {}).get("profile", {})
    account = new_account(login, password, profile.get("student_code") or secrets.token_hex(4),
                          profile.get("display_name") or login)
    account["semesters"], account["choices"], account["documents"], account["years"] = [], [], [], []
    pages = report.get("pages", [report.get("page", {})])
    if isinstance(pages, dict):
        pages = list(pages.values())
    for page in pages:
        code = page.get("page")
        if code not in PAGES:
            continue
        sections = copy.deepcopy(page.get("field_sections", []))
        tables = copy.deepcopy(page.get("nested_tables", []))
        for field in [f for s in sections for f in s["fields"]] + [c for t in tables for r in t["rows"] for c in r["cells"]]:
            # Links in the report refer to production. Files must be attached locally.
            field.pop("links", None)
            field.setdefault("label", field.get("column", field["field_name"]))
        account["pages"][code] = {"sections": sections, "nested_tables": tables}
        for year in page.get("marks", {}).get("years", []):
            formula = year.get("average_formula") or ""
            formula_text = formula.get("text", "") if isinstance(formula, dict) else formula
            account["years"].append({"year": int(year["year"]), "status": year.get("status", "ATT"),
                "average": year.get("average") or "—", "average_formula": formula_text, "average_formula_raw": formula,
                "validation_report": year.get("validation_report", {}).get("body_excerpt", "")})
            for item in year.get("semesters", []):
                s = copy.deepcopy(item["semester_snapshot"])
                sem = {"year": s["academic_year"], "semester": s["semester"], "status": item.get("status", "ATT"),
                    "average": item.get("average") or "—", "average_formula": item.get("average_formula") or "",
                    "ranking": item.get("ranking", {}).get("response", {"text": "", "success": True, "reload": "no"}),
                    "validation_report": item.get("validation_report", {}).get("body_excerpt", ""),
                    "exams": [], "modules": [], "units": []}
                for i, e in enumerate(s["exams"]):
                    avg = e.get("average", "").replace(",", ".")
                    try:
                        avg = float(avg)
                    except ValueError:
                        avg = None
                    sem["exams"].append({**e, "id": f"import-{i}", "subjectId": e["subject-id"],
                        "date": e["date-str"], "average": avg, "coefficient": 1})
                sem["modules"] = [{**m, "groupCode": m["group-code"], "groupTitle": m["group-title"]} for m in s["modules"]]
                sem["units"] = [{**u, "commonCore": u["common-core"]} for u in s["units"]]
                account["semesters"].append(sem)
        for year in page.get("choices", {}).get("years", []):
            for i, panel in enumerate(year["semester_panels"], 1):
                mods = copy.deepcopy(panel["modules"])
                semester = int(mods[0].get("attrs", {}).get("semester", i)) if mods else i
                account["choices"].append({"year": int(year["year"]), "semester": semester,
                    "heading": panel.get("heading", ""), "earned_ects": panel.get("earned_ects", ""), "modules": mods})
    validate_account(account)
    return account
