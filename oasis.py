#!/usr/bin/env python3
"""
Oasis grade scraper - Version 2026
Uses the AJAX reload_semester endpoint directly
"""

import requests
from bs4 import BeautifulSoup
from dotenv import load_dotenv
import os
from datetime import datetime
import urllib3
import re

# Disable SSL warnings (Oasis certificate issues)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

months = {
    'janvier': 1, 'février': 2, 'mars': 3, 'avril': 4,
    'mai': 5, 'juin': 6, 'juillet': 7, 'août': 8,
    'septembre': 9, 'octobre': 10, 'novembre': 11, 'décembre': 12
}

load_dotenv()
LOGIN = os.environ['LOGIN']
PASSWORD = os.environ['PASSWORD']
OASIS_BASE_URL = os.environ.get('OASIS_BASE_URL', 'https://polytech-saclay.oasis.aouka.org/')

def getGrades():
    """Fetch all grades from current academic year (both semesters)"""
    grades = []
    
    with requests.Session() as s:
        # Login
        url1 = OASIS_BASE_URL + r"prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&route=BO\Connection\User::login"
        s.get(url1, verify=False)
        login_data = {'login': LOGIN, 'password': PASSWORD, 'url': 'codepage=MYMARKS'}
        r = s.post(url1, data=login_data, verify=False)
        
        if not r.json().get('success'):
            raise Exception(f"Login failed: {r.json().get('text', 'Unknown error')}")
        
        # Get current year
        current_year = datetime.now().year
        # Academic year: if we're before September, we're in year-1/year
        # If after September, we're in year/year+1
        if datetime.now().month < 9:
            academic_year = current_year - 1
        else:
            academic_year = current_year
        
        # Fetch both semesters for current academic year
        reload_url = OASIS_BASE_URL + r"prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&route=Oasis\Common\Model\Cursus\StudentCursus\StudentCursus::reload_semester"
        
        for semester in [1, 2]:
            params = {
                'student': LOGIN,  # Student ID is same as login
                'year': str(academic_year),
                'semester_in_year': str(semester),
                'tab': 'Tests'  # Tab with individual grades
            }
            
            r2 = s.post(reload_url, data=params, verify=False)
            
            if r2.status_code != 200:
                continue
            
            # Parse HTML
            soup = BeautifulSoup(r2.text, 'html.parser')
            
            # Find all grade rows in the Tests table
            # Note: rows don't have role="row" in AJAX response
            rows = soup.find_all('tr')
            
            for row in rows:
                cells = row.find_all('td')
                if len(cells) < 4:
                    continue
                
                try:
                    # Module/Subject
                    course_div = cells[0].find('div', class_='courseLine')
                    if not course_div:
                        continue
                    
                    course_text = course_div.get_text(strip=True)
                    # Format: "CODE — Subject Name"
                    parts = course_text.split('—')
                    if len(parts) >= 2:
                        subject_id = parts[0].strip()
                        subject = parts[1].strip()
                    else:
                        subject_id = course_text
                        subject = course_text
                    
                    # Name of the test/assignment
                    name = cells[1].get_text(strip=True)
                    if not name:
                        continue
                    
                    # Date
                    date_str = cells[2].get_text(strip=True)
                    if not date_str or date_str == '—':
                        continue
                    
                    # Grade
                    grade_str = cells[3].get_text(strip=True).replace(',', '.')
                    if grade_str == '—' or not grade_str:
                        grade = None
                    else:
                        try:
                            grade = float(grade_str)
                        except ValueError:
                            grade = None
                    
                    # Parse date
                    try:
                        # Format: "DD month YYYY" e.g. "26 septembre 2025"
                        date_parts = date_str.split()
                        if len(date_parts) >= 3:
                            day = int(date_parts[0])
                            month = months.get(date_parts[1].lower(), 1)
                            year = int(date_parts[2])
                            date_obj = datetime(year, month, day)
                        else:
                            date_obj = None
                    except:
                        date_obj = None
                    
                    grade_entry = {
                        'subject-id': subject_id,
                        'subject': subject,
                        'name': name,
                        'grade': grade,
                        'date-str': date_str,
                        'date': date_obj,
                        'semester': semester,
                        'year': academic_year
                    }
                    
                    grades.append(grade_entry)
                
                except Exception as e:
                    # Skip malformed rows
                    continue
    
    return grades

if __name__ == '__main__':
    from pprint import pprint
    all_grades = getGrades()
    print(f"\nTotal grades: {len(all_grades)}\n")
    pprint(sorted(all_grades, key=lambda x: x['date'] if x['date'] else datetime.min))
