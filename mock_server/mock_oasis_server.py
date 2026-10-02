#!/usr/bin/env python3
"""Persistent Oasis simulator with local web administration (Python stdlib only)."""
from __future__ import annotations

import argparse
import base64
from collections import deque
import copy
from datetime import datetime
import hmac
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import threading
import time
from urllib.parse import parse_qs, unquote, urlparse

from fixtures import SCENARIO_SEQUENCE
from rendering import LABELS, esc, layout, login_page, page_fields, render_page, render_semester, route_url, shell_page
from store import BLOCKED_TEXT, ConflictError, PAGES, ROOT, Store, ValidationError, apply_scenario, check_password, import_report, new_account, validate_state

LOGIN_ROUTE = r"BO\Connection\User::login"
MAIN_ROUTE = r"BO\Layout\MainContent::load"
MENU_ROUTE = r"BO\Layout\Menu::reload"
RELOAD_ROUTE = r"Oasis\Common\Model\Cursus\StudentCursus\StudentCursus::reload_semester"
RANKING_ROUTE = RELOAD_ROUTE.replace("reload_semester", "get_ranking")
VALIDATION_ROUTE = RELOAD_ROUTE.replace("reload_semester", "get_validation_report")
CHOICES_ROUTE = r"Oasis\PolytechParis\Page\Student\MyChoices::load"
CHOICE_PREFIX = "Oasis\\Common\\Model\\Cursus\\Choice\\StudentChoices::"
UPLOAD_PREFIX = "BO\\Form\\FormField\\Upload\\AbstractUploadField::"
LOGOUT_ROUTE = r"BO\Connection\Logout::logout"
EXTEND_ROUTE = r"BO\Connection\Session::extend"
FORGOT_ROUTE = r"Oasis\PolytechParis\Override\Connection\LoginPage::password_forgot"
PASSWORD_INIT_ROUTE = r"BO\Connection\User::password_init"
MAX_BODY = 16 * 1024 * 1024


def normalize_route(route):
    return re.sub(r"\\+", r"\\", route)


class MockServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, store, admin_token=""):
        super().__init__(address, Handler)
        self.store, self.admin_token = store, admin_token
        self.csrf = secrets.token_urlsafe(32)
        self.sessions = {}
        self.login_attempts = {}
        self.runtime_lock = threading.RLock()
        self.events = deque(maxlen=200)
        self.coverage = json.loads((ROOT / "route_inventory.json").read_text(encoding="utf-8"))

    def event(self, **data):
        with self.runtime_lock:
            self.events.append({"time": datetime.now().isoformat(timespec="seconds"), **data})


class Handler(BaseHTTPRequestHandler):
    server_version = "PoPSMock/3.0"

    def do_GET(self):
        self._handle()

    def do_POST(self):
        self._handle()

    def do_PUT(self):
        self._handle()

    def do_DELETE(self):
        self._handle()

    def log_message(self, format, *args):
        pass  # URLs, form data and cookies can contain credentials.

    def _handle(self):
        self._event_route, self.path_only, self.route = "unknown", "", ""
        try:
            parsed = urlparse(self.path)
            self.path_only = parsed.path
            self.params = {k: v[-1] for k, v in parse_qs(parsed.query, keep_blank_values=True).items()}
            self.route = normalize_route(self.params.get("route") or self.params.get("popup_route", ""))
            self._event_route = self.route or self.path_only
            self.body = {}
            if self.command != "GET":
                self._read_body()
                origin = self.headers.get("Origin")
                if origin and urlparse(origin).netloc != self.headers.get("Host"):
                    return self._json({"error": "Origine refusée."}, 403)
            if self.path_only.startswith("/api/admin/"):
                return self._admin()
            if self.command == "GET" and self.path_only in ("/admin", "/admin/"):
                return self._html((ROOT / "web/admin.html").read_text(encoding="utf-8").replace("__CSRF__", self.server.csrf))
            if self.command == "GET" and self.path_only.startswith("/assets/"):
                return self._asset()
            if self.command == "GET" and self.path_only in ("/health", "/api/scenarios"):
                state = self.server.store.snapshot()
                scenario = state["accounts"][0]["scenario"] if state["accounts"] else "initial"
                return self._json({"current": scenario, "available": SCENARIO_SEQUENCE} if self.path_only.endswith("scenarios") else
                    {"status": "ok", "scenario": scenario, "closed": state["settings"]["closed"], "schema_version": 1})
            if self.route == LOGIN_ROUTE:
                return self._login() if self.command == "POST" else self._html(login_page())
            if self.route in (FORGOT_ROUTE, PASSWORD_INIT_ROUTE):
                if self.command != "POST":
                    return self._json({"error": "POST attendu."}, 405)
                delivery = self.params.get("MAIL_TYPE", "SCHOOL")
                if delivery not in ("SCHOOL", "OWN", "BOTH"):
                    raise ValidationError("Type d'adresse invalide.")
                self.server.event(kind="password_reset", mail_type=delivery, simulated=True)
                return self._json({"success": True, "text": "Demande simulée. Aucun e-mail envoyé.", "reload": "no"})
            account = self._account()
            if self.path_only in ("/", "/index.php") and self.command == "GET":
                code = self.params.get("codepage", "HOME")
                return self._html(shell_page(account, code if code in PAGES else "HOME") if account else login_page())
            if not account:
                return self._html(login_page(), 403)
            for key in ("student", "student_code"):
                if self.params.get(key) not in (None, "", account["student_id"], account["login"]):
                    return self._json({"error": "Accès à un autre étudiant refusé."}, 403)
            settings = self.server.store.snapshot()["settings"]
            if self._fault(settings):
                return
            if self.command == "GET" and (self.path_only.startswith("/files/") or self.path_only.startswith("/prod/file/")):
                return self._file(account)
            for rule in account["route_overrides"]:
                if normalize_route(rule["route"]) == self.route and rule["method"] in ("*", self.command) and all(
                    self.params.get(k) == str(v) for k, v in rule.get("params", {}).items()):
                    return self._send(rule["body"], rule["content_type"], rule["status"])
            if self.route == LOGOUT_ROUTE:
                with self.server.runtime_lock:
                    self.server.sessions.pop(self._cookie(), None)
                return self._json({"success": True, "reload": "yes"}, headers={"Set-Cookie": self._clear_cookie()})
            if self.route == EXTEND_ROUTE:
                with self.server.runtime_lock:
                    session = self.server.sessions.get(self._cookie())
                    if session:
                        session["created"] = time.monotonic()
                return self._json({"success": True, "reload": "no"})
            if self.route == MENU_ROUTE:
                return self._html(''.join(f'<li id="Menu{c}"><a href="#codepage={c}">{esc(label)}</a></li>' for c, label in LABELS.items()))
            if self.route in (MAIN_ROUTE, CHOICES_ROUTE):
                code = "MYCHOICES" if self.route == CHOICES_ROUTE else self.params.get("codepage", "HOME")
                if code not in PAGES:
                    return self._json({"error": "Page inconnue."}, 404)
                if code == "STUDENT" and self.params.get("code") not in (None, "", account["student_id"]):
                    return self._json({"error": "Accès refusé."}, 403)
                return self._html(render_page(account, code, self._integer("year", None)))
            if self.route in (RELOAD_ROUTE, RANKING_ROUTE, VALIDATION_ROUTE):
                year, semester = self._integer("year", 2025), self._integer("semester_in_year", 1)
                if semester not in (0, 1, 2) or (self.route == RELOAD_ROUTE and semester == 0):
                    raise ValidationError("Semestre invalide.")
                payload = next((s for s in account["semesters"] if s["year"] == year and s["semester"] == semester),
                    {"status": "ATT", "exams": [], "modules": [], "units": []})
                if self.route == RELOAD_ROUTE:
                    return self._html(render_semester(year, semester, payload))
                if self.route == RANKING_ROUTE:
                    return self._json(payload.get("ranking", {"text": "", "success": True, "reload": "no"}))
                if semester == 0:
                    payload = next((v for v in account["years"] if v["year"] == year), {})
                return self._html(layout(f'<p>{esc(payload.get("validation_report", "ATT pour les raisons suivantes :"))}</p>'))
            if self.route.startswith(UPLOAD_PREFIX):
                return self._upload_route(account)
            return self._json({"error": "Route non simulée : configurez une réponse personnalisée dans /admin.", "route": self.route}, 501)
        except ConflictError as error:
            self._json({"error": str(error)}, 409)
        except (ValidationError, ValueError, TypeError, KeyError, UnicodeError, AttributeError) as error:
            self._json({"error": str(error) if isinstance(error, ValidationError) else "Requête ou structure de données invalide."}, 400)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except OSError:
            self._json({"error": "Impossible de lire ou d'enregistrer les données locales."}, 500)

    def _read_body(self):
        if self.headers.get("Transfer-Encoding"):
            raise ValidationError("Transfert chunked non pris en charge.")
        size = int(self.headers.get("Content-Length", "0"))
        if not 0 <= size <= MAX_BODY:
            raise ValidationError("Requête limitée à 16 Mo.")
        self.connection.settimeout(15)
        raw = self.rfile.read(size)
        if len(raw) != size:
            raise ValidationError("Requête incomplète.")
        content_type = self.headers.get("Content-Type", "").split(";")[0]
        if content_type == "application/json":
            self.body = json.loads(raw.decode("utf-8") or "{}")
            if not isinstance(self.body, dict):
                raise ValidationError("Objet JSON attendu.")
            self.params.update({k: str(v) for k, v in self.body.items() if isinstance(v, (str, int, float, bool))})
        elif content_type in ("application/x-www-form-urlencoded", ""):
            self.body = {k: v[-1] for k, v in parse_qs(raw.decode("utf-8"), keep_blank_values=True).items()}
            self.params.update(self.body)
        else:
            raise ValidationError("Utilisez JSON ou un formulaire encodé. Les fichiers utilisent content_base64.")

    def _integer(self, name, default):
        value = self.params.get(name)
        return default if value in (None, "") else int(value)

    def _cookie(self):
        cookie = SimpleCookie()
        try:
            cookie.load(self.headers.get("Cookie", ""))
            return cookie["PHPSESSID"].value if "PHPSESSID" in cookie else None
        except Exception:
            return None

    @staticmethod
    def _clear_cookie():
        return "PHPSESSID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"

    def _account(self):
        state = self.server.store.snapshot()
        with self.server.runtime_lock:
            token = self._cookie()
            session = self.server.sessions.get(token)
            if not session:
                return None
            account = next((a for a in state["accounts"] if a["id"] == session["account_id"]), None)
            attempts = self._attempts_today()
            if not account or not account["active"] or state["settings"]["closed"] or state["settings"]["ip_blocked"] or attempts >= state["settings"]["max_login_attempts"] or (
                time.monotonic() - session["created"] >= state["settings"]["session_ttl_seconds"] or
                session["auth"] != (account["login"], account["student_id"], account["password"])):
                self.server.sessions.pop(token, None)
                return None
            return account

    def _attempts_today(self):
        day, count = self.server.login_attempts.get(self.client_address[0], (None, 0))
        return count if day == datetime.now().date().isoformat() else 0

    def _fault(self, settings):
        if settings["latency_ms"]:
            time.sleep(settings["latency_ms"] / 1000)
        if settings["failure_status"]:
            self._json({"error": "Panne HTTP simulée."}, settings["failure_status"])
            return True
        return False

    def _login(self):
        state = self.server.store.snapshot()
        settings = state["settings"]
        with self.server.runtime_lock:
            self.server.sessions.pop(self._cookie(), None)
        if self._fault(settings):
            return
        account = next((a for a in state["accounts"] if a["login"] == self.params.get("login")), None)
        valid = account and account["active"] and check_password(self.params.get("password", ""), account["password"])
        with self.server.runtime_lock:
            count = self._attempts_today()
            blocked = settings["ip_blocked"] or count >= settings["max_login_attempts"]
            if not blocked and (settings["closed"] or not valid):
                count += 1
                self.server.login_attempts[self.client_address[0]] = (datetime.now().date().isoformat(), count)
                blocked = count >= settings["max_login_attempts"]
        if blocked or settings["closed"] or not valid:
            text = BLOCKED_TEXT if blocked else settings["closed_error"] if settings["closed"] else settings["login_error"]
            text = text.replace("{remaining}", str(max(0, settings["max_login_attempts"] - count)))
            return self._json({"text": text, "success": False, "reload": "no"}, headers={"Set-Cookie": self._clear_cookie()})
        token = secrets.token_urlsafe(32)
        with self.server.runtime_lock:
            self.server.login_attempts.pop(self.client_address[0], None)
            now = time.monotonic()
            self.server.sessions = {k: s for k, s in self.server.sessions.items() if now - s["created"] < settings["session_ttl_seconds"]}
            if len(self.server.sessions) >= 1000:
                self.server.sessions.pop(next(iter(self.server.sessions)))
            self.server.sessions[token] = {"account_id": account["id"], "created": now,
                "auth": (account["login"], account["student_id"], account["password"])}
        return self._json({"text": "Connexion réussie", "success": True, "reload": "yes"},
            headers={"Set-Cookie": f"PHPSESSID={token}; Path=/; HttpOnly; SameSite=Lax"})

    def _admin_allowed(self):
        if self.server.admin_token:
            return hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + self.server.admin_token)
        host = urlparse("http://" + self.headers.get("Host", "")).hostname
        return ipaddress.ip_address(self.client_address[0]).is_loopback and host in ("localhost", "127.0.0.1", "::1")

    def _admin(self):
        if not self._admin_allowed():
            return self._json({"error": "Administration locale uniquement, ou jeton Bearer requis."}, 403)
        if self.command != "GET" and not hmac.compare_digest(self.headers.get("X-CSRF-Token", ""), self.server.csrf):
            return self._json({"error": "X-CSRF-Token requis (fourni par GET /api/admin/state)."}, 403)
        store, path = self.server.store, self.path_only
        if self.command == "GET":
            if path == "/api/admin/state":
                return self._json(store.snapshot(), headers={"X-CSRF-Token": self.server.csrf})
            if path == "/api/admin/export":
                return self._json(store.snapshot(), headers={"Content-Disposition": 'attachment; filename="oasis-mock-backup.json"'})
            if path == "/api/admin/events":
                with self.server.runtime_lock:
                    today = datetime.now().date().isoformat()
                    counts = [n for day, n in self.server.login_attempts.values() if day == today]
                    return self._json({"events": list(self.server.events), "sessions": len(self.server.sessions),
                                       "failed_attempts": sum(counts), "blocked_clients": sum(n >= store.state["settings"]["max_login_attempts"] for n in counts)})
            if path == "/api/admin/coverage":
                return self._json({"routes": self.server.coverage})
            return self._json({"error": "Route d'administration inconnue."}, 404)
        revision = self.body.get("revision")
        if path == "/api/admin/auth/reset" and self.command == "POST":
            with self.server.runtime_lock:
                self.server.login_attempts.clear()
            return self._json({"success": True})
        if path == "/api/admin/sessions/expire" and self.command == "POST":
            with self.server.runtime_lock:
                self.server.sessions.clear()
            return self._json({"success": True})
        if path == "/api/admin/settings" and self.command == "PUT":
            return self._json(store.update(revision, lambda s: s["settings"].update(self.body["settings"])))
        if path == "/api/admin/import" and self.command == "POST":
            imported = copy.deepcopy(self.body["state"])
            validate_state(imported)
            result = store.update(revision, lambda s: s.update(imported))
            with self.server.runtime_lock:
                self.server.sessions.clear()
            return self._json(result)
        if path == "/api/admin/import-report" and self.command == "POST":
            account = import_report(self.body["report"], self.body["login"], self.body["password"])
            return self._json(store.update(revision, lambda s: s["accounts"].append(account)))
        if path == "/api/admin/accounts" and self.command == "POST":
            account = new_account(self.body["login"], self.body["password"], self.body["student_id"], self.body["display_name"])
            if self.body.get("empty", False):
                for key in ("semesters", "choices", "documents", "years"):
                    account[key] = []
            return self._json(store.update(revision, lambda s: s["accounts"].append(account)))
        if path.startswith("/api/admin/accounts/"):
            account_id = path.rsplit("/", 1)[-1]
            def mutate(state):
                previous = next((a for a in state["accounts"] if a["id"] == account_id), None)
                if not previous:
                    raise ValidationError("Compte inexistant.")
                if self.command == "DELETE":
                    state["accounts"].remove(previous)
                elif self.command == "PUT":
                    account = copy.deepcopy(self.body["account"])
                    if account.get("id") != account_id:
                        raise ValidationError("L'identifiant interne du compte ne peut pas changer.")
                    account["password"] = self.body.get("password") or account.get("password", previous["password"])
                    state["accounts"][state["accounts"].index(previous)] = account
                else:
                    raise ValidationError("PUT ou DELETE attendu.")
            return self._json(store.update(revision, mutate))
        if path in ("/api/admin/scenario", "/api/admin/cycle") and self.command == "POST":
            def mutate(state):
                accounts = [a for a in state["accounts"] if not self.body.get("account_id") or a["id"] == self.body["account_id"]]
                if not accounts:
                    raise ValidationError("Compte inexistant.")
                for account in accounts:
                    scenario = self.body.get("scenario") if path.endswith("scenario") else SCENARIO_SEQUENCE[(SCENARIO_SEQUENCE.index(account["scenario"]) + 1) % len(SCENARIO_SEQUENCE)]
                    apply_scenario(account, scenario)
            return self._json(store.update(revision, mutate))
        return self._json({"error": "Route d'administration inconnue."}, 404)

    def _file(self, account):
        if self.path_only.startswith("/files/"):
            doc = next((d for d in account["documents"] if d["id"] == unquote(self.path_only[7:])), None)
        else:
            filename = unquote(self.path_only.rstrip("/").rsplit("/", 1)[-1])
            if filename != account["student_id"] + ".png" or "/student/photo/" not in self.path_only:
                return self._json({"error": "Fichier introuvable."}, 404)
            doc = next((d for d in account["documents"] if d["field_name"] == "PHOTO" and d["content_type"] == "image/png"), None)
        if not doc:
            return self._json({"error": "Fichier introuvable."}, 404)
        safe = re.sub(r'[^a-zA-Z0-9._-]', '_', doc["filename"])
        return self._send(base64.b64decode(doc["content_base64"]), doc["content_type"], headers={"Content-Disposition": f'inline; filename="{safe}"'})

    def _upload_route(self, account):
        if self.params.get("code") not in (None, "", account["student_id"]):
            return self._json({"error": "Accès refusé."}, 403)
        action = self.route.split("::")[-1]
        if action == "download":
            doc = next((d for d in account["documents"] if d["field_name"] == self.params.get("field_name")), None)
            if not doc:
                return self._json({"error": "Fichier introuvable."}, 404)
            return self._send(base64.b64decode(doc["content_base64"]), doc["content_type"])
        if action == "reload":
            return self._html(page_fields(account, self.params.get("page", "STUDENT")))
        return self._json({"error": "Mutation non capturée. Utilisez l'administration ou une réponse personnalisée."}, 501)

    def _asset(self):
        name = self.path_only.rsplit("/", 1)[-1]
        if name == "shell-context.js":
            content = "var TargetProject='oasis_polytech_paris';var BoCorePathRel='/prod/bo';var BoProjectPath='/prod/oasis/polytech_paris';var FilePath='/prod/file/oasis_polytech_paris';"
            content += "var sLoadMainContentRoute=" + json.dumps(route_url(MAIN_ROUTE)) + ";var sReloadMenuRoute=" + json.dumps(route_url(MENU_ROUTE)) + ";"
            return self._send(content, "application/javascript; charset=utf-8")
        if name not in ("admin.js", "admin.css", "oasis.js", "oasis.css"):
            return self._json({"error": "Fichier introuvable."}, 404)
        return self._send((ROOT / "web" / name).read_text(encoding="utf-8"), "application/javascript; charset=utf-8" if name.endswith(".js") else "text/css; charset=utf-8")

    def _html(self, body, status=200):
        return self._send(body, "text/html; charset=utf-8", status)

    def _json(self, body, status=200, headers=None):
        return self._send(json.dumps(body, ensure_ascii=False, allow_nan=False), "application/json; charset=utf-8", status, headers)

    def _send(self, body, content_type, status=200, headers=None):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(status)
        for key, value in {"Content-Type": content_type, "Content-Length": str(len(data)), "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff", "Referrer-Policy": "no-referrer",
            "Content-Security-Policy": "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'", **(headers or {})}.items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(data)
        # Retain route names only: no arbitrary path/query fragments in diagnostics.
        known = {r["route"] for r in self.server.coverage}
        admin_paths = {"/api/admin/" + name for name in ("state", "settings", "events", "coverage", "export", "import", "import-report", "accounts", "scenario", "cycle", "auth/reset", "sessions/expire")}
        route = self._event_route if self._event_route in known else self.path_only if self.path_only in admin_paths else "/api/admin/accounts/:id" if self.path_only.startswith("/api/admin/accounts/") else "custom" if self.route else "page/file/asset"
        self.server.event(kind="request", method=self.command, route=route, status=status)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--data", type=Path, default=ROOT / "data/state.json")
    parser.add_argument("--admin-token-env", default="OASIS_MOCK_ADMIN_TOKEN")
    args = parser.parse_args()
    server = MockServer((args.host, args.port), Store(args.data), os.environ.get(args.admin_token_env, ""))
    print(f"Oasis: http://127.0.0.1:{args.port}/ | administration: http://127.0.0.1:{args.port}/admin", flush=True)
    print(f"Data: {args.data.resolve()} | demo/demo and lea/lea on first launch", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
