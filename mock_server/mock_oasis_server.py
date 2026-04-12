#!/usr/bin/env python3
"""Oasis-compatible mock HTTP server for PoPS mobile development."""

from __future__ import annotations

import argparse
import html
import json
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


LOGIN_ROUTE = "BO\\Connection\\User::login"
RELOAD_ROUTE = "Oasis\\Common\\Model\\Cursus\\StudentCursus\\StudentCursus::reload_semester"

SCENARIOS = {
    "initial": {
        (2025, 1): {
            "status": "ATT",
            "exams": [
                {"subjectId": "g1i1-2", "subject": "Algorithmique numérique 2", "name": "Examen", "date": "13 janvier 2026", "grade": 8.0, "average": 11.091, "rank": "17/22", "comment": ""},
                {"subjectId": "g1i1-2", "subject": "Algorithmique numérique 2", "name": "TP", "date": "16 janvier 2026", "grade": 15.0, "average": 13.727, "rank": "10/22", "comment": ""},
                {"subjectId": "g1i2-3", "subject": "Réseaux 1", "name": "Exam", "date": "8 décembre 2025", "grade": 17.5, "average": 15.381, "rank": "3/22", "comment": "Très solide"},
            ],
            "modules": [
                {"groupCode": "g1i1", "groupTitle": "Fondements théoriques II", "code": "g1i1-2", "title": "Algorithmique numérique 2", "coefficient": "4", "block": "A", "grade": "11,500", "average": "12,409", "rank": "12/22", "credits": "2"},
                {"groupCode": "g1i2", "groupTitle": "Systèmes d'information II", "code": "g1i2-3", "title": "Réseaux 1", "coefficient": "3", "block": "B", "grade": "17,500", "average": "15,381", "rank": "3/22", "credits": "2"},
                {"groupCode": "g1c1", "groupTitle": "Langue et communication II", "code": "g1c1-1", "title": "Anglais", "coefficient": "6", "block": "—", "grade": "16,000", "average": "15,987", "rank": "30/72", "credits": "—"},
            ],
            "units": [
                {"code": "g1i1", "title": "Fondements théoriques II", "ects": "6", "commonCore": True, "grade": "13,250", "average": "12,800", "rank": "9/22", "result": "VAL"},
                {"code": "g1i2", "title": "Systèmes d'information II", "ects": "6", "commonCore": True, "grade": "16,200", "average": "14,900", "rank": "4/22", "result": "VAL"},
                {"code": "g1c1", "title": "Langue et communication II", "ects": "4", "commonCore": True, "grade": "16,000", "average": "15,100", "rank": "22/72", "result": "ATT"},
            ],
        },
        (2025, 2): {
            "status": "ATT",
            "exams": [
                {"subjectId": "g1i3-2", "subject": "C++ 1", "name": "Projet", "date": "22 mai 2026", "grade": None, "average": None, "rank": "", "comment": ""},
                {"subjectId": "g1c2-3", "subject": "Gestion d'entreprise", "name": "Contrôle commun", "date": "31 mars 2026", "grade": None, "average": None, "rank": "", "comment": ""},
            ],
            "modules": [
                {"groupCode": "g1i3", "groupTitle": "Programmation avancée", "code": "g1i3-2", "title": "C++ 1", "coefficient": "5", "block": "C", "grade": "—", "average": "—", "rank": "", "credits": "2"},
                {"groupCode": "g1c2", "groupTitle": "L'entreprise et son environnement II", "code": "g1c2-3", "title": "Gestion d'entreprise", "coefficient": "3", "block": "—", "grade": "—", "average": "—", "rank": "", "credits": "—"},
            ],
            "units": [
                {"code": "g1i3", "title": "Programmation avancée", "ects": "4", "commonCore": True, "grade": "—", "average": "—", "rank": "", "result": "ATT"},
                {"code": "g1c2", "title": "L'entreprise et son environnement II", "ects": "4", "commonCore": True, "grade": "—", "average": "—", "rank": "", "result": "ATT"},
            ],
        },
    },
    "new_note": {
        (2025, 1): {
            "status": "ATT",
            "exams": [
                {"subjectId": "g1i1-2", "subject": "Algorithmique numérique 2", "name": "Examen", "date": "13 janvier 2026", "grade": 8.0, "average": 11.091, "rank": "17/22", "comment": ""},
                {"subjectId": "g1i1-2", "subject": "Algorithmique numérique 2", "name": "TP", "date": "16 janvier 2026", "grade": 15.0, "average": 13.727, "rank": "10/22", "comment": ""},
                {"subjectId": "g1i2-3", "subject": "Réseaux 1", "name": "Exam", "date": "8 décembre 2025", "grade": 17.5, "average": 15.381, "rank": "3/22", "comment": "Très solide"},
            ],
            "modules": [
                {"groupCode": "g1i1", "groupTitle": "Fondements théoriques II", "code": "g1i1-2", "title": "Algorithmique numérique 2", "coefficient": "4", "block": "A", "grade": "11,500", "average": "12,409", "rank": "12/22", "credits": "2"},
                {"groupCode": "g1i2", "groupTitle": "Systèmes d'information II", "code": "g1i2-3", "title": "Réseaux 1", "coefficient": "3", "block": "B", "grade": "17,500", "average": "15,381", "rank": "3/22", "credits": "2"},
                {"groupCode": "g1c1", "groupTitle": "Langue et communication II", "code": "g1c1-1", "title": "Anglais", "coefficient": "6", "block": "—", "grade": "16,000", "average": "15,987", "rank": "30/72", "credits": "—"},
            ],
            "units": [
                {"code": "g1i1", "title": "Fondements théoriques II", "ects": "6", "commonCore": True, "grade": "13,250", "average": "12,800", "rank": "9/22", "result": "VAL"},
                {"code": "g1i2", "title": "Systèmes d'information II", "ects": "6", "commonCore": True, "grade": "16,200", "average": "14,900", "rank": "4/22", "result": "VAL"},
                {"code": "g1c1", "title": "Langue et communication II", "ects": "4", "commonCore": True, "grade": "16,000", "average": "15,100", "rank": "22/72", "result": "ATT"},
            ],
        },
        (2025, 2): {
            "status": "ATT",
            "exams": [
                {"subjectId": "g1i3-2", "subject": "C++ 1", "name": "Projet", "date": "22 mai 2026", "grade": 18.0, "average": 15.432, "rank": "2/18", "comment": "Nouvelle note"},
                {"subjectId": "g1c2-3", "subject": "Gestion d'entreprise", "name": "Contrôle commun", "date": "31 mars 2026", "grade": None, "average": None, "rank": "", "comment": ""},
            ],
            "modules": [
                {"groupCode": "g1i3", "groupTitle": "Programmation avancée", "code": "g1i3-2", "title": "C++ 1", "coefficient": "5", "block": "C", "grade": "18,000", "average": "15,432", "rank": "2/18", "credits": "2"},
                {"groupCode": "g1c2", "groupTitle": "L'entreprise et son environnement II", "code": "g1c2-3", "title": "Gestion d'entreprise", "coefficient": "3", "block": "—", "grade": "—", "average": "—", "rank": "", "credits": "—"},
            ],
            "units": [
                {"code": "g1i3", "title": "Programmation avancée", "ects": "4", "commonCore": True, "grade": "18,000", "average": "15,432", "rank": "2/18", "result": "VAL"},
                {"code": "g1c2", "title": "L'entreprise et son environnement II", "ects": "4", "commonCore": True, "grade": "—", "average": "—", "rank": "", "result": "ATT"},
            ],
        },
    },
    "updated_note": {
        (2025, 1): {
            "status": "ATT",
            "exams": [
                {"subjectId": "g1i1-2", "subject": "Algorithmique numérique 2", "name": "Examen corrigé", "date": "13 janvier 2026", "grade": 12.0, "average": 11.091, "rank": "8/22", "comment": "Note révisée"},
                {"subjectId": "g1i1-2", "subject": "Algorithmique numérique 2", "name": "TP", "date": "16 janvier 2026", "grade": 15.0, "average": 13.727, "rank": "10/22", "comment": ""},
                {"subjectId": "g1i2-3", "subject": "Réseaux 1", "name": "Exam", "date": "8 décembre 2025", "grade": 17.5, "average": 15.381, "rank": "3/22", "comment": "Très solide"},
            ],
            "modules": [
                {"groupCode": "g1i1", "groupTitle": "Fondements théoriques II", "code": "g1i1-2", "title": "Algorithmique numérique 2", "coefficient": "4", "block": "A", "grade": "13,500", "average": "12,409", "rank": "7/22", "credits": "2"},
                {"groupCode": "g1i2", "groupTitle": "Systèmes d'information II", "code": "g1i2-3", "title": "Réseaux 1", "coefficient": "3", "block": "B", "grade": "17,500", "average": "15,381", "rank": "3/22", "credits": "2"},
                {"groupCode": "g1c1", "groupTitle": "Langue et communication II", "code": "g1c1-1", "title": "Anglais", "coefficient": "6", "block": "—", "grade": "16,000", "average": "15,987", "rank": "30/72", "credits": "—"},
            ],
            "units": [
                {"code": "g1i1", "title": "Fondements théoriques II", "ects": "6", "commonCore": True, "grade": "14,100", "average": "12,800", "rank": "6/22", "result": "VAL"},
                {"code": "g1i2", "title": "Systèmes d'information II", "ects": "6", "commonCore": True, "grade": "16,200", "average": "14,900", "rank": "4/22", "result": "VAL"},
                {"code": "g1c1", "title": "Langue et communication II", "ects": "4", "commonCore": True, "grade": "16,000", "average": "15,100", "rank": "22/72", "result": "ATT"},
            ],
        },
        (2025, 2): {
            "status": "ATT",
            "exams": [
                {"subjectId": "g1i3-2", "subject": "C++ 1", "name": "Projet", "date": "22 mai 2026", "grade": 18.0, "average": 15.432, "rank": "2/18", "comment": "Nouvelle note"},
                {"subjectId": "g1c2-3", "subject": "Gestion d'entreprise", "name": "Contrôle commun", "date": "31 mars 2026", "grade": 14.0, "average": 12.1, "rank": "6/30", "comment": "Corrigé"},
            ],
            "modules": [
                {"groupCode": "g1i3", "groupTitle": "Programmation avancée", "code": "g1i3-2", "title": "C++ 1", "coefficient": "5", "block": "C", "grade": "18,000", "average": "15,432", "rank": "2/18", "credits": "2"},
                {"groupCode": "g1c2", "groupTitle": "L'entreprise et son environnement II", "code": "g1c2-3", "title": "Gestion d'entreprise", "coefficient": "3", "block": "—", "grade": "14,000", "average": "12,100", "rank": "6/30", "credits": "—"},
            ],
            "units": [
                {"code": "g1i3", "title": "Programmation avancée", "ects": "4", "commonCore": True, "grade": "18,000", "average": "15,432", "rank": "2/18", "result": "VAL"},
                {"code": "g1c2", "title": "L'entreprise et son environnement II", "ects": "4", "commonCore": True, "grade": "14,000", "average": "12,100", "rank": "6/30", "result": "VAL"},
            ],
        },
    },
}

SCENARIO_SEQUENCE = ["initial", "new_note", "updated_note"]
STATE = {"scenario": "initial"}
SESSIONS: set[str] = set()


class Handler(BaseHTTPRequestHandler):
    server_version = "PoPSMock/2.0"

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self._send_json({"status": "ok", "scenario": STATE["scenario"]})
            return
        if parsed.path == "/api/scenarios":
            self._send_json({"current": STATE["scenario"], "available": SCENARIO_SEQUENCE})
            return
        if self._is_login_route(parsed):
            self._send_html("<html><body>mock login ready</body></html>")
            return
        self._send_json({"error": "Not found"}, status=HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/admin/scenario":
            self._handle_admin_scenario()
            return
        if parsed.path == "/api/admin/cycle":
            self._handle_admin_cycle()
            return
        if self._is_login_route(parsed):
            self._handle_login()
            return
        if self._is_reload_route(parsed):
            self._handle_reload()
            return
        self._send_json({"error": "Not found"}, status=HTTPStatus.NOT_FOUND)

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        print(f"[{self.address_string()}] {format % args}")

    def _handle_login(self) -> None:
        form = self._read_form()
        login = form.get("login", "")
        password = form.get("password", "")
        if not login or not password:
            self._send_json({"success": False, "text": "Identifiants manquants"}, status=HTTPStatus.OK)
            return

        session_id = f"mock-{login}"
        SESSIONS.add(session_id)
        self._send_json(
            {"success": True, "text": "Connexion réussie"},
            status=HTTPStatus.OK,
            extra_headers={"Set-Cookie": f"POPSMOCK={session_id}; Path=/; HttpOnly"},
        )

    def _handle_reload(self) -> None:
        session_id = self._read_cookie("POPSMOCK")
        if session_id not in SESSIONS:
            self._send_html("<html><body>forbidden</body></html>", status=HTTPStatus.FORBIDDEN)
            return

        form = self._read_form()
        year = int(form.get("year", "0") or 0)
        semester = int(form.get("semester_in_year", "0") or 0)
        semester_payload = SCENARIOS[STATE["scenario"]].get((year, semester), {"status": "ATT", "exams": [], "modules": [], "units": []})
        self._send_html(self._render_semester_payload(year, semester, semester_payload))

    def _handle_admin_scenario(self) -> None:
        body = self._read_json()
        scenario = body.get("scenario")
        if scenario not in SCENARIOS:
            self._send_json({"error": "Unknown scenario"}, status=HTTPStatus.BAD_REQUEST)
            return
        STATE["scenario"] = scenario
        self._send_json({"success": True, "scenario": scenario})

    def _handle_admin_cycle(self) -> None:
        current_index = SCENARIO_SEQUENCE.index(STATE["scenario"])
        STATE["scenario"] = SCENARIO_SEQUENCE[(current_index + 1) % len(SCENARIO_SEQUENCE)]
        self._send_json({"success": True, "scenario": STATE["scenario"]})

    def _is_login_route(self, parsed) -> bool:
        query = parse_qs(parsed.query)
        return parsed.path.endswith("/ajax.php") and query.get("route", [""])[0] == LOGIN_ROUTE

    def _is_reload_route(self, parsed) -> bool:
        query = parse_qs(parsed.query)
        return parsed.path.endswith("/ajax.php") and query.get("route", [""])[0] == RELOAD_ROUTE

    def _render_semester_payload(self, year: int, semester: int, payload: dict) -> str:
        suffix = f"Mock_{year}_{semester}"
        exams = payload["exams"]
        modules = payload["modules"]
        units = payload["units"]
        semester_label = "Semestre impair" if semester == 1 else "Semestre pair"

        return f"""
<h2 class="text-center semesterTitle" data-num_semester="{semester}" data-year="{year}">
    {semester_label} : <span class="semesterInfo">{html.escape(payload['status'])}</span>
</h2>
<div class="tabsSemester{suffix}">
    <div class="box-header">
        <ul class="nav nav-tabs">
            <li><a id="TestsSemester{suffix}" href="#TabTestsSemester{suffix}">Épreuves ({len(exams)})</a></li>
            <li class="active"><a id="CoursesSemester{suffix}" href="#TabCoursesSemester{suffix}">Modules ({len(modules)})</a></li>
            <li><a id="ModulesSemester{suffix}" href="#TabModulesSemester{suffix}">UEs ({len(units)})</a></li>
        </ul>
    </div>
    <div class="tab-content">
        <div class="tab-pane with-dataTable" id="TabTestsSemester{suffix}">
            <table class="table" id="Tests{semester}{year}">
                <thead>
                    <tr>
                        <th class="title"><span>Module</span></th>
                        <th class="title"><span>Nom</span></th>
                        <th class="title"><span>Date</span></th>
                        <th class="title"><span>Note</span></th>
                        <th class="title"><span>Moy. Promo</span></th>
                        <th class="title"><span>Rang</span></th>
                        <th class="title"><span>Appréciations</span></th>
                    </tr>
                </thead>
                <tbody>
                    {''.join(self._render_exam_row(exam) for exam in exams)}
                </tbody>
            </table>
        </div>
        <div class="tab-pane with-dataTable active" id="TabCoursesSemester{suffix}">
            <div class="table-responsive">
                <table class="table table-bordered table-condensed" id="Courses{semester}{year}" style="opacity: 0">
                    <thead>
                        <tr>
                            <th class="title exportable"><span>Module</span></th>
                            <th class="title exportable"><span>Sigle du module</span></th>
                            <th class="title exportable"><span>Titre fr</span></th>
                            <th class="title exportable"><span>Coeff</span></th>
                            <th class="title exportable"><span>Bloc</span></th>
                            <th class="title exportable"><span>Note</span></th>
                            <th class="title exportable"><span>Moy. Promo</span></th>
                            <th class="title exportable"><span>Rang</span></th>
                            <th class="title exportable"><span>EC</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        {''.join(self._render_module_row(module) for module in modules)}
                    </tbody>
                </table>
            </div>
        </div>
        <div class="tab-pane with-dataTable" id="TabModulesSemester{suffix}">
            <table class="table" id="Modules{semester}{year}">
                <thead>
                    <tr>
                        <th class="title"><span>Code</span></th>
                        <th class="title"><span>Titre fr</span></th>
                        <th class="title"><span>Nombre d'ECTS</span></th>
                        <th class="title"><span>TC</span></th>
                        <th class="title"><span>Note</span></th>
                        <th class="title"><span>Moy. Promo</span></th>
                        <th class="title"><span>Rang</span></th>
                        <th class="title"><span>Résultat</span></th>
                    </tr>
                </thead>
                <tbody>
                    {''.join(self._render_unit_row(unit) for unit in units)}
                </tbody>
            </table>
        </div>
    </div>
</div>
""".strip()

    def _render_exam_row(self, exam: dict) -> str:
        return (
            "<tr>"
            f"<td><div class='courseLine' data-code='{html.escape(exam['subjectId'])}'>{html.escape(exam['subjectId'])} &mdash;<b>{html.escape(exam['subject'])}</b></div></td>"
            f"<td>{html.escape(exam['name'])}</td>"
            f"<td>{html.escape(exam['date'])}</td>"
            f"<td>{html.escape(self._format_score(exam['grade']))}</td>"
            f"<td>{html.escape(self._format_score(exam['average']))}</td>"
            f"<td>{html.escape(exam['rank'])}</td>"
            f"<td>{html.escape(exam['comment'])}</td>"
            "</tr>"
        )

    def _render_module_row(self, module: dict) -> str:
        return (
            "<tr>"
            f"<td><div class='moduleLine' data-custom='false' data-code='{html.escape(module['groupCode'])}'>{html.escape(module['groupCode'])} &mdash;<b>{html.escape(module['groupTitle'])}</b></div></td>"
            f"<td>{html.escape(module['code'])}</td>"
            f"<td>{html.escape(module['title'])}</td>"
            f"<td>{html.escape(module['coefficient'])}</td>"
            f"<td>{html.escape(module['block'])}</td>"
            f"<td>{html.escape(module['grade'])}</td>"
            f"<td>{html.escape(module['average'])}</td>"
            f"<td>{html.escape(module['rank'])}</td>"
            f"<td>{html.escape(module['credits'])}</td>"
            "</tr>"
        )

    def _render_unit_row(self, unit: dict) -> str:
        core_icon = "<i class='icon-radio-checked'></i>" if unit["commonCore"] else ""
        return (
            "<tr>"
            f"<td>{html.escape(unit['code'])}</td>"
            f"<td>{html.escape(unit['title'])}</td>"
            f"<td>{html.escape(unit['ects'])}</td>"
            f"<td>{core_icon}</td>"
            f"<td>{html.escape(unit['grade'])}</td>"
            f"<td>{html.escape(unit['average'])}</td>"
            f"<td>{html.escape(unit['rank'])}</td>"
            f"<td>{html.escape(unit['result'])}</td>"
            "</tr>"
        )

    def _format_score(self, value: float | None) -> str:
        if value is None:
            return "—"
        return f"{value:.3f}".replace(".", ",")

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return {}

    def _read_form(self) -> dict[str, str]:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b""
        form = parse_qs(raw.decode("utf-8"), keep_blank_values=True)
        return {key: values[-1] for key, values in form.items()}

    def _read_cookie(self, name: str) -> str | None:
        raw = self.headers.get("Cookie", "")
        if not raw:
            return None
        cookie = SimpleCookie()
        cookie.load(raw)
        morsel = cookie.get(name)
        return morsel.value if morsel else None

    def _send_json(
        self,
        payload: dict,
        status: HTTPStatus = HTTPStatus.OK,
        extra_headers: dict[str, str] | None = None,
    ) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        for header, value in (extra_headers or {}).items():
            self.send_header(header, value)
        self.end_headers()
        self.wfile.write(encoded)

    def _send_html(self, payload: str, status: HTTPStatus = HTTPStatus.OK) -> None:
        encoded = payload.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main() -> None:
    parser = argparse.ArgumentParser(description="Oasis-compatible mock server for PoPS Android app")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"PoPS mock server listening on http://{args.host}:{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()