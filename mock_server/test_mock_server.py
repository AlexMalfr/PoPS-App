"""Contract/integration tests. All writes use isolated temporary data files."""
import base64
import copy
from datetime import datetime, timedelta
import http.cookiejar
import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request

from mock_oasis_server import (MockServer, LOGIN_ROUTE, MAIN_ROUTE, RELOAD_ROUTE, RANKING_ROUTE,
                               VALIDATION_ROUTE, CHOICES_ROUTE, UPLOAD_PREFIX, LOGOUT_ROUTE)
from store import BLOCKED_TEXT, ROOT, Store, import_report


class SimulatorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "state.json"
        self.server = MockServer(("127.0.0.1", 0), Store(self.path))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"
        self.client = self.new_client()
        self.csrf = self.server.csrf

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temp.cleanup()

    def new_client(self):
        return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, method="GET", body=None, client=None, headers=None, raw=False):
        request_headers = dict(headers or {})
        if body is not None:
            request_headers.setdefault("Content-Type", "application/json")
            data = body if isinstance(body, bytes) else json.dumps(body).encode()
        else:
            data = None
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=request_headers)
        try:
            response = (client or self.client).open(request, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = response.read()
            value = json.loads(data) if not raw and "application/json" in response.headers.get("Content-Type", "") else data
            return response.status, value, response.headers

    def oasis(self, route, method="GET", params=None, client=None, popup=False):
        params = params or {}
        path = "/prod/bo/core/Router/" + ("Popup/popup.php" if popup else "Ajax/ajax.php")
        path += "?" + urllib.parse.urlencode({"targetProject": "oasis_polytech_paris", "popup_route" if popup else "route": route,
                                             **(params if method == "GET" else {})})
        body = urllib.parse.urlencode(params).encode() if method == "POST" else None
        return self.request(path, method, body, client, {"Content-Type": "application/x-www-form-urlencoded"})

    def admin(self, path="state", method="GET", body=None, **kwargs):
        return self.request("/api/admin/" + path, method, body, headers={"X-CSRF-Token": self.csrf, **kwargs})

    def state(self):
        return self.admin()[1]

    def login(self, login="demo", password="demo", client=None):
        return self.oasis(LOGIN_ROUTE, "POST", {"login": login, "password": password, "url": "codepage=MYMARKS"}, client)

    def save(self, account, revision=None, password=""):
        return self.admin("accounts/" + account["id"], "PUT", {
            "account": account, "revision": self.state()["revision"] if revision is None else revision, "password": password})

    def settings(self, **values):
        return self.admin("settings", "PUT", {"revision": self.state()["revision"], "settings": values})

    def test_authentication_countdown_block_reset_and_day_rollover(self):
        for attempt in range(1, 10):
            status, value, _ = self.login(password="wrong")
            self.assertEqual(status, 200)
            self.assertFalse(value["success"])
            self.assertEqual(value["text"], f"Vos identifiants sont incorrects. Veuillez réessayer ({10-attempt} tentatives restantes).")
        self.assertEqual(self.login(password="wrong")[1]["text"], BLOCKED_TEXT)
        self.assertFalse(self.login()[1]["success"])
        self.assertEqual(self.admin("events")[1]["blocked_clients"], 1)
        self.admin("auth/reset", "POST", {})
        self.assertTrue(self.login()[1]["success"])
        self.login(password="wrong")
        self.assertTrue(self.login()[1]["success"])
        self.assertIn("9 tentatives", self.login(password="wrong")[1]["text"])
        yesterday = (datetime.now() - timedelta(days=1)).date().isoformat()
        self.server.login_attempts["127.0.0.1"] = (yesterday, 10)
        self.assertTrue(self.login()[1]["success"])

    def test_random_sessions_failure_clears_session_logout_expiry_and_extend(self):
        first = self.login()[2]["Set-Cookie"]
        second = self.login()[2]["Set-Cookie"]
        self.assertNotEqual(first, second)
        self.assertIn("HttpOnly", first)
        self.assertNotIn("demo", first)
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST", {"year": 2025, "semester_in_year": 1})[0], 200)
        self.login(password="wrong")
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST", {})[0], 403)
        self.login()
        self.oasis(LOGOUT_ROUTE, "POST")
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST")[0], 403)
        self.login()
        for session in self.server.sessions.values():
            session["created"] -= 4000
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST")[0], 403)
        self.login()
        self.assertTrue(self.oasis(r"BO\Connection\Session::extend", "POST")[1]["success"])

    def test_closed_mode_and_http_failures_leave_admin_accessible(self):
        self.login()
        self.settings(closed=True)
        rejected = self.login()[1]
        self.assertFalse(rejected["success"])
        self.assertIn("9 tentatives", rejected["text"])
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST")[0], 403)
        self.settings(closed=False, ip_blocked=True)
        self.assertEqual(self.login()[1]["text"], BLOCKED_TEXT)
        self.settings(ip_blocked=False, failure_status=503)
        self.assertEqual(self.login()[0], 503)
        self.assertEqual(self.admin()[0], 200)
        self.settings(failure_status=0)
        self.assertTrue(self.login()[1]["success"])

    def test_accounts_isolated_and_cross_student_access_denied(self):
        other = self.new_client()
        self.login()
        self.login("lea", "lea", other)
        one = self.oasis(RELOAD_ROUTE, "POST", {"year": 2025, "semester_in_year": 1})[1]
        two = self.oasis(RELOAD_ROUTE, "POST", {"year": 2025, "semester_in_year": 1}, other)[1]
        self.assertIn(b"8,000", one)
        self.assertIn(b"14,500", two)
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST", {"student": "10000002"})[0], 403)
        self.assertEqual(self.oasis(MAIN_ROUTE, params={"codepage": "STUDENT", "code": "10000002"})[0], 403)
        self.assertIn(b"Camille Martin", self.request("/")[1])

    def test_saved_grades_escape_html_and_survive_reload(self):
        account = self.state()["accounts"][0]
        account["semesters"][0]["exams"][0].update(grade=19.25, comment='<script>alert("test")</script>')
        account["future_field"] = {"new_data": [1, 2]}
        self.assertEqual(self.save(account)[0], 200)
        persisted = Store(self.path).snapshot()
        self.assertEqual(persisted["accounts"][0]["future_field"], account["future_field"])
        self.login()
        page = self.oasis(RELOAD_ROUTE, "POST", {"year": 2025, "semester_in_year": 1})[1]
        self.assertIn(b"19,250", page)
        self.assertIn(b"&lt;script&gt;", page)
        self.assertNotIn(b"<script>alert", page)
        for selector in (b"semesterTitle", b"data-num_semester", b"TabTestsSemester", b"courseLine", b"moduleLine", b"icon-radio-checked"):
            self.assertIn(selector, page)
        self.assertEqual(persisted["revision"], 1)
        self.assertEqual(self.state()["accounts"][0]["password"], "demo")
        self.assertNotIn("password_hash", self.state()["accounts"][0])

    def test_validation_conflicts_and_malformed_requests_are_atomic(self):
        state = self.state()
        account = copy.deepcopy(state["accounts"][0])
        account["semesters"][0]["exams"][0]["subjectId"] = "nonexistent"
        self.assertEqual(self.save(account)[0], 400)
        self.assertEqual(self.state()["revision"], state["revision"])
        account = copy.deepcopy(state["accounts"][0])
        account["semesters"][0]["exams"][0]["grade"] = 25
        self.assertEqual(self.save(account)[0], 400)
        self.settings(session_ttl_seconds=120)
        self.assertEqual(self.save(state["accounts"][0], state["revision"])[0], 409)
        self.assertEqual(self.admin("accounts", "POST", b"[")[0], 400)
        self.assertEqual(self.admin("settings", "PUT", {"settings": {"latency_ms": -1}})[0], 400)
        self.login()
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST", {"year": "garbage"})[0], 400)
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST", {"semester_in_year": "3"})[0], 400)
        self.assertEqual(self.request("/health")[0], 200)

    def test_admin_csrf_host_origin_and_optional_token(self):
        self.assertEqual(self.request("/api/admin/settings", "PUT", {"settings": {"closed": True}})[0], 403)
        self.assertEqual(self.admin("settings", "PUT", {"settings": {}}, Origin="https://untrusted.example")[0], 403)
        self.assertEqual(self.admin(Host="untrusted.example")[0], 403)
        self.server.admin_token = "test-only-token"
        self.assertEqual(self.admin()[0], 403)
        self.assertEqual(self.admin(Authorization="Bearer test-only-token")[0], 200)
        self.assertEqual(self.request("/health")[0], 200)

    def test_create_disable_password_change_delete(self):
        state = self.state()
        status, state, _ = self.admin("accounts", "POST", {"revision": state["revision"], "login": "test-user",
            "password": "test-password", "student_id": "10000003", "display_name": "Test User", "empty": True})
        self.assertEqual(status, 200)
        account = state["accounts"][-1]
        self.assertEqual(account["semesters"], [])
        self.assertTrue(self.login("test-user", "test-password")[1]["success"])
        account["active"] = False
        self.save(account)
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST")[0], 403)
        account["active"] = True
        self.save(account, password="new-test-password")
        self.assertFalse(self.login("test-user", "test-password")[1]["success"])
        self.assertTrue(self.login("test-user", "new-test-password")[1]["success"])
        self.assertEqual(self.admin("accounts/" + account["id"], "DELETE", {"revision": self.state()["revision"]})[0], 200)
        self.assertEqual(self.oasis(RELOAD_ROUTE, "POST")[0], 403)

    def test_all_pages_ranking_validation_choices_and_double_backslashes(self):
        self.login()
        for code in ("HOME", "STUDENT", "MYCURSUS", "MYMARKS", "MYCHOICES", "MYDOCUMENTS"):
            self.assertEqual(self.oasis(MAIN_ROUTE.replace("\\", "\\\\"), params={"codepage": code})[0], 200)
        self.assertEqual(self.oasis(RANKING_ROUTE, params={"year": 2025, "semester_in_year": 1})[1], {"text": "", "success": True, "reload": "no"})
        self.assertIn(b"ATT pour les raisons", self.oasis(VALIDATION_ROUTE, params={"year": 2025}, popup=True)[1])
        self.assertIn(b"SemesterPanel2", self.oasis(CHOICES_ROUTE, params={"year": 2025})[1])
        self.assertNotIn(b"SemesterPanel2", self.oasis(CHOICES_ROUTE, params={"year": 2000})[1])
        self.assertIn(b"CODE_LDAP", self.oasis(MAIN_ROUTE, params={"codepage": "STUDENT"})[1])

    def test_documents_and_authenticated_photo(self):
        account = self.state()["accounts"][0]
        png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
        account["documents"].append({"id": "photo", "page": "STUDENT", "field_name": "PHOTO", "filename": "photo.png",
                                    "content_type": "image/png", "year": 2026, "content_base64": base64.b64encode(png).decode()})
        self.save(account)
        self.assertEqual(self.request("/files/photo")[0], 403)
        self.login()
        self.assertEqual(self.request("/files/photo", raw=True)[1], png)
        self.assertEqual(self.request("/prod/file/oasis_polytech_paris//2026/student/photo/10000001.png", raw=True)[1], png)
        self.assertEqual(self.oasis(UPLOAD_PREFIX + "download", params={"field_name": "PHOTO", "code": "10000001"})[1], png)
        self.assertEqual(self.request("/prod/file/oasis_polytech_paris/2026/student/photo/10000002.png")[0], 404)
        self.assertIn(b"exemple.txt", self.oasis(MAIN_ROUTE, params={"codepage": "MYDOCUMENTS"})[1])
        account["documents"][0]["content_type"] = "text/plain\r\nInjected: yes"
        self.assertEqual(self.save(account)[0], 400)

    def test_scenarios_backup_overrides_unknown_route_and_redacted_logs(self):
        state = self.state()
        account = state["accounts"][0]
        self.admin("scenario", "POST", {"scenario": "new_note", "account_id": account["id"]})
        self.assertEqual(self.state()["accounts"][1]["scenario"], "initial")
        account = self.state()["accounts"][0]
        account["route_overrides"] = [{"route": r"Oasis\Future::load", "method": "POST", "status": 200,
            "body": '{"simulated":true}', "content_type": "application/json", "params": {"year": "2026"}}]
        self.save(account)
        self.login()
        self.assertEqual(self.oasis(r"Oasis\Future::load", "POST", {"year": 2026})[1], {"simulated": True})
        self.assertEqual(self.oasis(r"Oasis\Future::load", "POST", {"year": 2025})[0], 501)
        self.oasis(LOGIN_ROUTE, "POST", {"login": "DO_NOT_LOG_THIS_LOGIN", "password": "DO_NOT_LOG_THIS_PASSWORD"})
        events = self.admin("events")[1]
        self.assertNotIn("DO_NOT_LOG", json.dumps(events))
        backup = self.admin("export")[1]
        self.assertEqual(backup["accounts"][0]["password"], "demo")
        self.assertEqual(self.admin("import", "POST", {"state": backup, "revision": self.state()["revision"]})[0], 200)
        self.assertTrue(self.login()[1]["success"])

    def test_import_retained_reports_without_network(self):
        reports = ROOT.parent / "python-tests/oasis-reverse/page_reports"
        for name in ("all_pages", "student", "mycursus", "mymarks", "mychoices", "mydocuments"):
            with self.subTest(report=name):
                report = json.loads((reports / (name + ".json")).read_text(encoding="utf-8"))
                account = import_report(report, "test-import-" + name, "synthetic-password")
                status, _, _ = self.admin("import-report", "POST", {"report": report, "login": account["login"], "password": "synthetic-password"})
                self.assertEqual(status, 200)
                client = self.new_client()
                self.assertTrue(self.login(account["login"], "synthetic-password", client)[1]["success"])
                for code in ("STUDENT", "MYCURSUS", "MYCHOICES", "MYDOCUMENTS", "MYMARKS"):
                    self.assertEqual(self.oasis(MAIN_ROUTE, params={"codepage": code}, client=client)[0], 200)
                for semester in account["semesters"]:
                    self.assertEqual(self.oasis(RELOAD_ROUTE, "POST", {"year": semester["year"], "semester_in_year": semester["semester"]}, client)[0], 200)
                # Reports share the production student ID; remove between imports.
                saved = next(a for a in self.state()["accounts"] if a["login"] == account["login"])
                self.admin("accounts/" + saved["id"], "DELETE", {})


if __name__ == "__main__":
    unittest.main()
