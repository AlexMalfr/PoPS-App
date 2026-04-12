#!/usr/bin/env python3
"""Oasis semester scraper using the reload_semester AJAX endpoint directly."""

from __future__ import annotations

import os
from datetime import datetime

import requests
import urllib3
from bs4 import BeautifulSoup
from dotenv import load_dotenv

# Disable SSL warnings (Oasis certificate issues)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

months = {
    'janvier': 1, 'février': 2, 'mars': 3, 'avril': 4,
    'mai': 5, 'juin': 6, 'juillet': 7, 'août': 8,
    'septembre': 9, 'octobre': 10, 'novembre': 11, 'décembre': 12
}

load_dotenv()
LOGIN = os.environ["LOGIN"]
PASSWORD = os.environ["PASSWORD"]
OASIS_BASE_URL = os.environ.get("OASIS_BASE_URL", "https://polytech-saclay.oasis.aouka.org/")

LOGIN_ROUTE = r"prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&route=BO\Connection\User::login"
RELOAD_ROUTE = r"prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&route=Oasis\Common\Model\Cursus\StudentCursus\StudentCursus::reload_semester"


def current_academic_year() -> int:
    now = datetime.now()
    return now.year - 1 if now.month < 9 else now.year


def ensure_trailing_slash(url: str) -> str:
    return url if url.endswith("/") else f"{url}/"


def parse_code_and_title(raw_text: str, code_hint: str = "") -> tuple[str, str]:
    parts = [part.strip() for part in raw_text.split("—")]
    code = code_hint.strip() or (parts[0] if parts else raw_text.strip())
    title = "—".join(parts[1:]).strip() or raw_text.strip()
    return code, title


def display_text(value: str) -> str:
    normalized = " ".join(value.replace("\xa0", " ").split())
    return normalized if normalized else "—"


def parse_french_date(date_str: str):
    try:
        date_parts = date_str.split()
        if len(date_parts) < 3:
            return None
        day = int(date_parts[0])
        month = months.get(date_parts[1].lower(), 1)
        year = int(date_parts[2])
        return datetime(year, month, day)
    except (ValueError, TypeError):
        return None


def parse_semester_html(html_text: str, academic_year: int, semester: int) -> dict:
    soup = BeautifulSoup(html_text, "html.parser")
    return {
        "academic_year": academic_year,
        "semester": semester,
        "exams": parse_exams(soup, academic_year, semester),
        "modules": parse_modules(soup, academic_year, semester),
        "units": parse_units(soup, academic_year, semester),
    }


def parse_exams(soup: BeautifulSoup, academic_year: int, semester: int) -> list[dict]:
    exams = []
    for row in soup.select("div[id^=TabTestsSemester] tbody tr"):
        cells = row.find_all("td")
        if len(cells) < 4:
            continue

        course_div = cells[0].find("div", class_="courseLine")
        if course_div is None:
            continue

        subject_id, subject = parse_code_and_title(course_div.get_text(" ", strip=True), course_div.get("data-code", ""))
        name = display_text(cells[1].get_text(" ", strip=True))
        date_str = display_text(cells[2].get_text(" ", strip=True))
        if name == "—" or date_str == "—":
            continue

        grade_text = display_text(cells[3].get_text(" ", strip=True)).replace(",", ".")
        grade = None if grade_text == "—" else float(grade_text)

        exams.append({
            "subject-id": subject_id,
            "subject": subject,
            "name": name,
            "grade": grade,
            "date-str": date_str,
            "date": parse_french_date(date_str),
            "average": display_text(cells[4].get_text(" ", strip=True)) if len(cells) > 4 else "—",
            "rank": display_text(cells[5].get_text(" ", strip=True)) if len(cells) > 5 else "—",
            "comment": display_text(cells[6].get_text(" ", strip=True)) if len(cells) > 6 else "—",
            "semester": semester,
            "year": academic_year,
        })

    return exams


def parse_modules(soup: BeautifulSoup, academic_year: int, semester: int) -> list[dict]:
    modules = []
    for row in soup.select("div[id^=TabCoursesSemester] tbody tr"):
        cells = row.find_all("td")
        if len(cells) < 9:
            continue

        module_div = cells[0].find("div", class_="moduleLine")
        if module_div is None:
            continue

        group_code, group_title = parse_code_and_title(module_div.get_text(" ", strip=True), module_div.get("data-code", ""))
        modules.append({
            "group-code": group_code,
            "group-title": group_title,
            "code": display_text(cells[1].get_text(" ", strip=True)),
            "title": display_text(cells[2].get_text(" ", strip=True)),
            "coefficient": display_text(cells[3].get_text(" ", strip=True)),
            "block": display_text(cells[4].get_text(" ", strip=True)),
            "grade": display_text(cells[5].get_text(" ", strip=True)),
            "average": display_text(cells[6].get_text(" ", strip=True)),
            "rank": display_text(cells[7].get_text(" ", strip=True)),
            "credits": display_text(cells[8].get_text(" ", strip=True)),
            "semester": semester,
            "year": academic_year,
        })

    return modules


def parse_units(soup: BeautifulSoup, academic_year: int, semester: int) -> list[dict]:
    units = []
    for row in soup.select("div[id^=TabModulesSemester] tbody tr"):
        cells = row.find_all("td")
        if len(cells) < 8:
            continue

        units.append({
            "code": display_text(cells[0].get_text(" ", strip=True)),
            "title": display_text(cells[1].get_text(" ", strip=True)),
            "ects": display_text(cells[2].get_text(" ", strip=True)),
            "common-core": cells[3].find("i", class_="icon-radio-checked") is not None,
            "grade": display_text(cells[4].get_text(" ", strip=True)),
            "average": display_text(cells[5].get_text(" ", strip=True)),
            "rank": display_text(cells[6].get_text(" ", strip=True)),
            "result": display_text(cells[7].get_text(" ", strip=True)),
            "semester": semester,
            "year": academic_year,
        })

    return units


def getSemesterSnapshots(year: int | None = None) -> list[dict]:
    academic_year = year or current_academic_year()
    snapshots = []
    base_url = ensure_trailing_slash(OASIS_BASE_URL)

    with requests.Session() as session:
        login_url = base_url + LOGIN_ROUTE
        reload_url = base_url + RELOAD_ROUTE

        session.get(login_url, verify=False)
        response = session.post(
            login_url,
            data={"login": LOGIN, "password": PASSWORD, "url": "codepage=MYMARKS"},
            verify=False,
        )

        payload = response.json()
        if not payload.get("success"):
            raise RuntimeError(f"Login failed: {payload.get('text', 'Unknown error')}")

        for semester in (1, 2):
            semester_response = session.post(
                reload_url,
                data={
                    "student": LOGIN,
                    "year": str(academic_year),
                    "semester_in_year": str(semester),
                    "tab": "Tests",
                },
                verify=False,
            )

            if semester_response.status_code != 200:
                continue

            snapshots.append(parse_semester_html(semester_response.text, academic_year, semester))

    return snapshots


def getGrades():
    """Backward-compatible helper returning only exam rows."""
    return [exam for snapshot in getSemesterSnapshots() for exam in snapshot["exams"]]

if __name__ == '__main__':
    from pprint import pprint
    semesters = getSemesterSnapshots()
    all_grades = [exam for semester in semesters for exam in semester["exams"]]
    all_modules = [module for semester in semesters for module in semester["modules"]]
    all_units = [unit for semester in semesters for unit in semester["units"]]

    print(f"\nSemesters: {len(semesters)}")
    print(f"Exams: {len(all_grades)}")
    print(f"Modules: {len(all_modules)}")
    print(f"UEs: {len(all_units)}\n")

    pprint(semesters)
