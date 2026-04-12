# TODO

---

## Table of Contents
- [ ] Syncing
- [ ] General UI
- [ ] Grades UI
- [ ] Settings
- [ ] Features to add

---

## Syncing
- [ ] Fix background sync issues
  - [ ] Maybe ask the user for permission to ignore battery optimizations?*
  - [ ] Don't show an error notification for no internet connection, just skip the sync and wait for the next one

---

## General UI
- [ ] Add a "Report an issue" button in the settings that opens a pre-filled email to the support address with device info and logs attached (maybe at the bottom of the kebab menu, but make sure it's shown in all screens)
- [ ] Show the student's name and photo somewhere in the UI?

---

## Grades UI

### General
- [ ] Add a pull-to-refresh feature
- [ ] If available, show calculated average for the semester and the year (calculated in the web interface with details on hover) and the jury decision
- [ ] Maybe detect (how?) when the student is in APP (apprentissage) and show "school"/"company" grades instead of semesters, since APPs don't have semesters so the administration just put all the school-related grades as the first semester and all the company-related grades as the second semester as a workaround, which may be confusing
- [ ] Maybe make grade cards foldable to show more details without cluttering the UI
  - [ ] And add a "Details" button that opens a new screen or popup with all the details of the grade (raw from API?), including the ones that don't fit on the card and the ones that are very rarely useful to show on the card but may be interesting to have in the details
- [ ] Bug: The app doesn't show the 2021/2022 year in the UI (probably because there are missing school years between 2021/2022 and 2024/2025 because I changed school and came back to the same one 2 years later)

### Top bar
- [ ] Show the date of the last successful sync in the top bar instead of the number of notes found
- [ ] Add a search bar/button with filter options
- [ ] Add a kebab menu with advanced features:
  - [ ] Add an "Open in browser" button that opens the corresponding page on the web interface or for missing features
  - [ ] Add an "Export" button that either reuse the web interface export feature (Copier dans le presse-papier, Exporter en XLSX (Excel), Exporter en PDF, Imprimer) and/or export the data in a custom format (CSV, JSON, etc.) that can be shared with other apps

### "Epreuves" tab
- [x] Make cards more compact
- [ ] Add missing info on the grade cards:
  - [ ] Promotion average ("Moy. Promo")
  - [ ] Rank ("Rang")
  - [ ] Comment ("Appréciation")
  - [ ] Coefficient (Not available on the web interface but maybe available in the API? It should be since it's used to calculate the average)
  - [ ] Extra info if any available in the API
- [ ] Feature to sort/group grades (under the top bar):
  - [x] By default it's sorted by date of publication with the most recent first
  - [ ] On the web interface it seems to be sorted by module id, which is not very useful, but allows to easily see which grades belong to the same module, so maybe add an option to group grades by module with a separator between modules

### "Modules" tab
- [ ] Make cards more compact
  - [ ] Module id next to the name instead of below, similar to the "Epreuves" tab
  - [ ] Coeff under grade
- [ ] Add missing info on the module cards:
  - [ ] Promotion average ("Moy. Promo")
  - [ ] Rank ("Rang")
  - [ ] Comment ("Appréciation")
  - [ ] Infos related to retakes if any
- [ ] Group by UEs, and show the UE name at the top of each group, like on the web interface, instead of showing the module name and id on each card

### "UEs" tab
- [ ] Make cards more compact


---

## Settings
- [ ] Separate the sections more clearly instead of having a big "Oasis" section with everything in it
- [ ] Maybe show an "Oasis account" section where the user can manage their login info and test the connection, that shows the profile picture and name of the student to show that the account is correctly linked
- [ ] Allow multiple accounts and switching between them (my use case is to add my brother's and friends' accounts, both for debugging and for personal use, so I figured it could be useful for other users as well, even if it will not be the most used feature and adds some complexity but whatever I'm 100% vibe coding this app anyways lol)
  - [ ] This may require some changes in the data layer to support multiple accounts and sync them separately

---

## Features to add
- [ ] Add other Oasis features:
  - [ ] "Mes informations personnelles" (My personal information): basic info about the student such as name, email, phone number, address, emergency contact, photo, and special needs if any
    - [ ] Read-only? Or allow the user to edit and save changes to Oasis?
  - [ ] "Mon cursus" (My curriculum): info about the student's curriculum, such as the final result, accumulated score for each type of requirement (citizenship/quitus/"polypoints", mobility, school/company presence for APP students, language requirements, etc.), jury decisions, comments, etc.
  - [ ] "Mes Choix" (My choices): Mainly useless page because all students attend the same courses except in rare cases like "UE initiative" (which is a mandatory UE where the student is free to choose any course they want)
  - [ ] "Mes documents" (My documents): list of documents uploaded by the student or the administration, such as the CV, cover letter, internship report, grades certificate, etc.