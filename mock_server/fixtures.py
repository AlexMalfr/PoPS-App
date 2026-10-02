"""Synthetic grade scenarios retained from the original mock."""

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
