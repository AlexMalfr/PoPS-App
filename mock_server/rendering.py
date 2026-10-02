"""Oasis HTML rendering; keep the selectors consumed by PoPS."""

import html


class SemesterRenderer:
    def _render_semester_payload(self, year: int, semester: int, payload: dict) -> str:
        suffix = f"Mock_{year}_{semester}"
        exams = payload["exams"]
        modules = payload["modules"]
        units = payload["units"]
        semester_label = "Semestre impair" if semester == 1 else "Semestre pair"
        average = html.escape(payload.get("average", "—"))
        formula = html.escape(payload.get("average_formula", ""), quote=True)

        return f"""
<h2 class="text-center semesterTitle" data-num_semester="{semester}" data-year="{year}">
    {semester_label} : <span class="semesterInfo">{html.escape(payload['status'])}</span>
</h2>
<div class="semesterAverage" title="{formula}">Moyenne : {average}</div>
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
        if isinstance(value, str):
            return value
        return f"{value:.3f}".replace(".", ",")



def render_semester(year, semester, payload):
    return SemesterRenderer()._render_semester_payload(year, semester, payload)


from urllib.parse import urlencode

AJAX = "/prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&"
LABELS = {"HOME": "Accueil", "STUDENT": "Mes informations personnelles", "MYCURSUS": "Mon cursus",
          "MYMARKS": "Mes notes", "MYCHOICES": "Mes choix", "MYDOCUMENTS": "Mes documents"}


def esc(value):
    return html.escape(str(value if value is not None else ""), quote=True)


def route_url(route, **params):
    return AJAX + urlencode({"route": route, **params})


def document_url(doc):
    return "/files/" + doc["id"]


def page_fields(account, code):
    page = account["pages"][code]
    docs = [d for d in account["documents"] if d["page"] == code]
    def field_content(field):
        files = [d for d in docs if d["field_name"] == field["field_name"]]
        return esc(field["value"]) + "".join(
            f'<br><a href="{esc(document_url(d))}">{esc(d["filename"])}</a>' for d in files)
    out = []
    rendered = set()
    for section in page["sections"]:
        out.append(f'<div class="separator">{esc(section["title"])}</div>')
        for field in section["fields"]:
            rendered.add(field["field_name"])
            out.append(f'<div class="form-group form_field-horizontal"><label>{esc(field["label"])}</label>'
                f'<div class="form_field-value" data-name="{esc(field["field_name"])}"><div class="field-wrapper">{field_content(field)}</div></div></div>')
    for table in page["nested_tables"]:
        out.append(f'<div class="separator">{esc(table["title"])}</div><div class="form-nested-wrapper" data-nested_table_name="{esc(table["nested_table_name"])}"><div class="form-nested">')
        out.append('<div class="table-header">' + ''.join(f'<span>{esc(h)}</span>' for h in table["headers"]) + '</div>')
        for row in table["rows"]:
            out.append(f'<div class="table-body" data-code="{esc(row["row_code"])}">')
            for cell in row["cells"]:
                out.append(f'<div class="form_field-value" data-name="{esc(cell["field_name"])}"><div class="field-wrapper">{esc(cell["value"])}</div></div>')
            out.append('</div>')
        out.append('</div></div>')
    for d in docs:
        if d["field_name"] not in rendered:
            out.append(f'<div class="form-group form_field-horizontal"><label>{esc(d["field_name"])}</label><div class="form_field-value" data-name="{esc(d["field_name"])}"><div class="field-wrapper"><a href="{esc(document_url(d))}">{esc(d["filename"])}</a></div></div></div>')
    return ''.join(out)


def render_choices(account, year=None):
    choices = [c for c in account["choices"] if year is None or c["year"] == year]
    out = ['<div id="StudentChoices">']
    for choice in choices:
        y, s = choice["year"], choice["semester"]
        out.append(f'<div class="SemesterPanel" id="SemesterPanel{s}" data-earned-ects="{esc(choice["earned_ects"])}"><h2>{esc(choice.get("heading") or f"{y} / {y+1} — Semestre {s}")}</h2>')
        for module in choice["modules"]:
            code = module["module_code"]
            attrs = {**module["attrs"], "student_code": account["student_id"], "year": y, "semester": s, "code": code}
            data = ' '.join(f'data-{esc(k)}="{esc(v)}"' for k, v in attrs.items())
            checked = 'checked' if module["module_state"].upper() == 'INSCRIT' else ''
            out.append(f'<div class="modulePanel" id="Panel{esc(code)}"><h3 class="moduleName">{esc(module["title"])}</h3><label class="moduleCheckbox"><input class="module-cb" type="checkbox" {data} {checked}>{esc(module["module_state"])}</label><table><tbody>')
            for course in module["courses"]:
                attrs = {**course["attrs"], "student_code": account["student_id"], "year": y, "semester": s,
                         "module_code": code, "course_code": course["course_code"], "type": course["attrs"].get("data_type", "")}
                data = ' '.join(f'data-{esc(k)}="{esc(v)}"' for k, v in attrs.items())
                checked = 'checked' if course["state"].lower() == 'inscrit' else ''
                out.append(f'<tr><td><input class="course-cb" type="checkbox" {data} {checked}></td><td>{esc(course["title"])}</td><td>{esc(course["type"])}</td><td>{esc(course["coefficient"])}</td><td>{esc(course["state"])}</td></tr>')
            out.append('</tbody></table></div>')
        out.append('</div>')
    return ''.join(out) + '</div>'


def render_page(account, code, year=None):
    content = f'<h1>{esc(LABELS[code])}</h1>' + page_fields(account, code)
    if code == "MYMARKS":
        semesters = [s for s in account["semesters"] if year is None or s["year"] == year]
        for y in sorted({s["year"] for s in semesters}, reverse=True):
            info = next((v for v in account["years"] if v["year"] == y), {})
            content += f'<h2 class="yearTitle" data-year="{y}">{y} / {y+1} : {esc(info.get("status", "ATT"))} <span title="{esc(info.get("average_formula", ""))}">{esc(info.get("average", ""))}</span></h2>'
            for s in semesters:
                if s["year"] == y:
                    content += render_semester(y, s["semester"], s)
    if code == "MYCHOICES":
        choices_route = r"Oasis\PolytechParis\Page\Student\MyChoices::load"
        content += '<div class="year-switch">' + ''.join(f'<a class="year_link" data-year="{y}" href="{esc(route_url(choices_route, year=y))}">{y} / {y+1}</a> ' for y in sorted({c["year"] for c in account["choices"]}, reverse=True)) + '</div>'
        content += render_choices(account, year)
    return content


def layout(content, title="Oasis mock"):
    return f'<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{esc(title)}</title><link rel="stylesheet" href="/assets/oasis.css"><script src="/assets/oasis.js" defer></script></head><body>{content}</body></html>'


def login_page():
    return layout('''<main class="login"><p class="eyebrow">OASIS · SIMULATEUR</p><h1>Veuillez vous identifier</h1>
<form id="LoginForm"><label>Identifiant<input name="login" autocomplete="username" required></label><label>Mot de passe<input type="password" name="password" autocomplete="current-password" required></label><button>Se connecter</button></form>
<p id="login-error" role="alert"></p><a href="#forgot">Mot de passe oublié ?</a>
<details id="forgot"><summary>Mot de passe oublié</summary><form id="PassWordForgottenForm"><label>Identifiant<input name="login" required></label><label><input type="checkbox" id="StudentCase" name="IS_STUDENT"> Élève ou ancien élève</label><label>Envoyer sur<select name="MAIL_TYPE"><option value="SCHOOL">E-mail université</option><option value="OWN">E-mail personnel</option><option value="BOTH">Les deux adresses e-mail</option></select></label><button>Envoyer</button></form><p>Les demandes sont consignées dans le simulateur, aucun e-mail n’est envoyé.</p></details><p><a href="/admin">Gérer le simulateur</a></p></main>''')


def shell_page(account, code="HOME"):
    def menu_hash(c):
        return "#" + urlencode({"name": "STUDENT", "codepage": c, "type": "DETAIL", "code": account["student_id"]}) if c == "STUDENT" else f"#codepage={c}"
    menu = ''.join(f'<li id="Menu{c}"><a href="{esc(menu_hash(c))}" data-page="{c}">{esc(label)}</a></li>' for c, label in LABELS.items())
    photo = next((d for d in account["documents"] if d["field_name"] == "PHOTO"), None)
    avatar = f'<div class="avatar"><img src="{esc(document_url(photo))}" alt="Photo de profil"></div>' if photo else ''
    script = '<script src="/assets/shell-context.js"></script>'
    return layout(f'<aside class="menu_wrapper">{avatar}<div align="center"><div align="center"><span class="username">{esc(account["display_name"])}</span></div></div><ul id="Menu">{menu}</ul><button id="logout">Se déconnecter</button><a href="/admin">Administration du mock</a></aside><main id="MainContent">{render_page(account, code)}</main>{script}')
