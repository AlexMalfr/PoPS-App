# TODO

---

## Github repository & app package
- [ ] README.md:
  - [ ] Make it clear that the app is unofficial, not affiliated with the school, made by a student, and 100% vibe coded slop but with standards (as the student vibe coding it uses it and is a perfectionist), and that they are welcome to contribute, report issues etc.
  - [ ] Add screenshots of the app
  - [ ] List all the features of the app
  - [ ] Add instructions for development (how to build, run, test, install the app, run the mock server, etc.)
  - [ ] Add a short privacy / data handling note (what is stored locally, what is sent to Oasis, what is never shared)
- [ ] Add an app icon and find a definitive name for the app

---

## Syncing
- [ ] Fix background sync issues
  - [ ] Maybe ask the user for permission to ignore battery optimizations? (Show a dialog on app launch if the permission is not granted, with a button to open the system settings for battery optimizations, and a link to http://dontkillmyapp.com/ to explain why it's important to grant this permission)
    - [ ] Only when background sync is enabled in the app settings, but at every app launch until the permission is granted
  - [ ] Don't show an error notification for no internet connection, just skip the sync and wait for the next one

---

## General UI
- [ ] General Kebab menu (options that shows in all screens, after all other screen-specific options if any, and separated by a line if there are screen-specific options):
  - [ ] Add a "Report an issue" button in the settings that opens a pre-filled email to the support address with device info and logs attached (cf. settings section)
  - [ ] Add a "Show GitHub repo" button (or other phrasing) that opens the GitHub repository in the browser to encourage users to check it out and maybe contribute or report issues
- [ ] Add a first-run / onboarding flow (notifications, background sync limitations, unofficial app disclaimer, data handling disclaimer, etc.)

---

## Grades UI

### General
- [ ] Add a pull-to-refresh feature
- [ ] If available, show calculated average for the semester and the year (calculated in the web interface with details on hover) and the jury decision
- [ ] Maybe detect (how?) when the student is in APP (apprentissage) and show "school"/"company" grades instead of semesters, since APPs don't have semesters so the administration just put all the school-related grades as the first semester and all the company-related grades as the second semester as a workaround, which may be confusing
  - [ ] Should be possible to detect automatically, I need to figure out how, but it it's *really* not possible, I can add a manual override in the settings to switch to "APP mode"
- [ ] Maybe make grade cards foldable to show more details without cluttering the UI
  - [ ] And add a "Details" button that opens a new screen or popup with all the details of the grade (raw from API?), including the ones that don't fit on the card and the ones that are very rarely useful to show on the card but may be interesting to have in the details
- [ ] Bug: The app doesn't show the 2021/2022 year in the UI (probably because there are missing school years between 2021/2022 and 2024/2025 because I changed school and came back to the same one 2 years later)
- [ ] Always open the most recent year by default

### Top bar
- [ ] Show the date of the last successful sync in the top bar instead of the number of notes found
- [ ] Add a search bar/button with filter options
- [ ] Add a kebab menu with advanced features:
  - [ ] Add an "Open in browser" button that opens the corresponding page on the web interface or for missing features
  - [ ] Add an "Export" button that either reuse the web interface export feature (Copier dans le presse-papier, Exporter en XLSX (Excel), Exporter en PDF, Imprimer) and/or export the data in a custom format (CSV, JSON, etc.) that can be shared with other apps
- [ ] Add a visible sync status indicator (background sync off / last sync failed / cached data only)

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
  - [ ] Make it so we can sort by any field available (make sure it sorts the numbers correctly and not like strings)
- [ ] Show new grades with a "NEW" badge and an more vibrant bg/outline color, and at the top of the list, separated from the rest with a separator, until the user opens the app or refreshes the list

### "Modules" tab
- [ ] Make cards more compact
  - [ ] Module id next to the name instead of below, similar to the "Epreuves" tab
  - [ ] Coeff under grade
- [ ] Add missing info on the module cards:
  - [ ] Promotion average ("Moy. Promo")
  - [ ] Rank ("Rang")
  - [ ] Comment ("Appréciation"): first line, truncated if too long, with the full comment when the card is unfolded and in the details screen
  - [ ] Infos related to retakes if any
- [ ] Group by UEs, and show the UE name at the top of each group, like on the web interface, instead of showing the module name and id on each card
- [x] No need for sorting since it's already grouped by UEs

### "UEs" tab
- [ ] Make cards more compact
  - [ ] UE id next to the name instead of below, similar to the "Epreuves" tab
- [ ] Remove "TC" (idk what it is)
- [x] No need for sorting


---

## Settings
- [ ] Separate the sections more clearly instead of having a big "Oasis" section with everything in it

### Account management section
  - [ ] Allow multiple accounts and switching between them with a dropdown that shows the user's name, id, and profile picture for each account
    - [ ] This may require some changes in the data layer to support multiple accounts and sync them separately
  - [ ] Instead of just having "login" and "password" fields always showing in the settings, have a more user-friendly "Add an account" flow
    - [ ] Add an "edit account" action for selected account
    - [ ] Add a "remove account / log out" action for selected account
  - [ ] Make an "error" state when the last fetch for an account failed (except no internet error), with a "retry" button that only retries that account
  - [ ] Explain that the login info is stored locally and encrypted, and that it's only used to fetch data from Oasis and never shared with anyone, not even the developer, and that the user can remove it at any time by deleting their account in the app or uninstalling the app
    - [ ] During the login flow
    - [ ] In the "about" section

### Notification settings section
  - [x] Add options to configure which notifications the user wants to receive (new grades, updated grades, sync errors, etc.)
  - [ ] Make it per account if multiple accounts are added
  - [ ] Add an "open system notification settings" shortcut and make it clear it's where the user can configure the notification channels and categories in more detail (choose the importance, sound, vibration, etc. for each type of notification)

### Sync settings section
  - [x] Add options to configure the sync frequency (manual, every 15m, every 30m, every 1h, every 6h, every 12h, every 1d, every 1w)
  - [ ] Separate "Background sync" toggle from "Sync frequency" options, because it may not be clear that you have to slide the frequency all the way to "manual" to disable background sync
  - [ ] Add conditions (only on Wi-Fi, only when charging, etc.)
  - [ ] Make it per account if multiple accounts are added
  - [ ] Advanced: Add a "clear cached data" action

### About section
  - [ ] links to the GitHub repo, support email (to:alexandre.malfreyt+popsapp@universite-paris-saclay.fr cc:alexandre.malfreyt+popsapp@gmail.com)
  - [ ] explains that the app is unofficial, not affiliated with the school, made by a student, and 100% vibe coded, and that they are welcome to contribute, report issues etc.
  - [ ] Make sure exported logs / issue reports redact credentials, cookies, names, addresses, and other personal data by default (how? maybe mark with a certain syntax the parts of logs that contain personal data to be able to redact them easily when attached to an issue report or shared with support)
  - [ ] Add a small diagnostics section (app version, last sync time, selected sync mode, mock/prod URL)

---

## Notifications
- [x] Add more granular notification types (new grade, updated grade, sync error, etc.) and let the user choose which ones they want to receive in the settings
- [ ] Make notifications per account if multiple accounts are added (i.e. show the notifications categories per account in the OS notification settings)
  - [ ] Show the account name ONLY if multiple accounts are added

---

## Mock Oasis server
- [x] Add a mock server that implements the same HTTP interface as the real Oasis server, to be able to test the app without hitting the real server and to have a stable environment for testing and development
- [ ] Add multi-account support in the mock server

---

## Features to add (later)
- [ ] Add other Oasis features:
  - [ ] "Mes informations personnelles" (My personal information): basic info about the student such as name, email, phone number, address, emergency contact, photo, and special needs if any
    - [ ] Read-only? Or allow the user to edit and save changes to Oasis?
  - [ ] "Mon cursus" (My curriculum): info about the student's curriculum, such as the final result, accumulated score for each type of requirement (citizenship/quitus/"polypoints", mobility, school/company presence for APP students, language requirements, etc.), jury decisions, comments, etc.
  - [ ] "Mes Choix" (My choices): Mainly useless page because all students attend the same courses except in rare cases like "UE initiative" (which is a mandatory UE where the student is free to choose any course they want)
  - [ ] "Mes documents" (My documents): list of documents uploaded by the student or the administration, such as the CV, cover letter, internship report, grades certificate, etc.
- [ ] Add the timetable (from ADE)
- [ ] Add the Moodle/e-campus features
