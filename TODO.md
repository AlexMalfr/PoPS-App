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
  - [x] Maybe ask the user for permission to ignore battery optimizations? (Show a dialog on app launch if the permission is not granted, with a button to open the system settings for battery optimizations, and a link to http://dontkillmyapp.com/ to explain why it's important to grant this permission)
    - [x] Only when background sync is enabled in the app settings, but at every app launch until the permission is granted
  - [x] Don't show an error notification for no internet connection, just skip the sync and wait for the next one

---

## General UI
- [x] General Kebab menu (options that shows in all screens, after all other screen-specific options if any, and separated by a line if there are screen-specific options):
  - [x] Add a "Report an issue" button in the settings that opens a pre-filled email to the support address (cf. settings section)
    - [ ] Pre-fill device info and attach redacted logs to the issue report email
  - [x] Add a "Show GitHub repo" button (or other phrasing) that opens the GitHub repository in the browser to encourage users to check it out and maybe contribute or report issues
  - [x] ⇒ Kebab menu should always be the last item of the top bar
- [x] Add a first-run / onboarding flow (unofficial app disclaimer, data handling disclaimer, notifications permission, background sync limitations + battery optimization permission, etc.)
  - [x] Add a collapsed custom server URL field at the bottom of the onboarding login page, with validation and the default instance as placeholder when empty
  - [ ] Add the "forgot password" feature
  - [ ] fix: to get the first name, get it from the STUDENT page instead of cutting the full name arount the first space

---

## Grades UI

### General
- [x] Add a pull-to-refresh feature
- [ ] If available, show calculated average for the semester and the year (calculated in the web interface with details on hover) and the jury decision
- [ ] Maybe detect (how?) when the student is in APP (apprentissage) and show "school"/"company" grades instead of semesters, since APPs don't have semesters so the administration just put all the school-related grades as the first semester and all the company-related grades as the second semester as a workaround, which may be confusing
  - [ ] Should be possible to detect automatically, I need to figure out how, but it it's *really* not possible, I can add a manual override in the settings to switch to "APP mode"
- [x] Maybe make grade cards foldable to show more details without cluttering the UI
  - [ ] And add a "Details" button that opens a new screen or popup with all the details of the grade (raw from API?), including the ones that don't fit on the card and the ones that are very rarely useful to show on the card but may be interesting to have in the details
- [x] Bug: The app doesn't show the 2021/2022 year in the UI (probably because there are missing school years between 2021/2022 and 2024/2025 because I changed school and came back to the same one 2 years later)
- [x] Always open the most recent year by default

### Top bar
- [x] Show the date of the last successful sync in the top bar instead of the number of notes found
  - [x] Make the string way more compact since there isn't a lot of space
  - [x] Show the error status here too in red if the last sync failed, with a popup that opens when clicking on it with error details
- [x] Add a search bar/button with filter options
  - [x] move the search bar to the top bar instead of the content, and remove the "All/Name/Code" filters, and remove the "search in current tab" string since this is obvious
  - [x] search terms entered in the search bar should only be linked to the tab it was entered on, so if the user switches tab it should clear the search bar (or put the value saved for the tab) and it should be shown again when the user comes back to the tab (only saved in memory, no need to save it in the local storage)
  - [x] close the search bar if it loses focus AND is empty to save space, or when clicking on the search icon or the "X" in the search bar
  - [x] Make the search bar smaller and round
- [x] Add a kebab menu with advanced features:
  - [x] Add an "Open in browser" button that opens the corresponding page on the web interface or for missing features
  - [x] Add an "Export" button in the kebab menu
    - [ ] Wire the export action so it actually exports data
  - [x] Add a sync and a hard refresh buttons
    - [x] Add a "stop sync" when a sync in in progress (that replaces both buttons)

### "Epreuves" tab
- [x] Make cards more compact
  - [x] Make cards foldable with a "Show details" button to show more details without cluttering the UI
  - [x] only show the "Show details" button if there are actually more details to show that don't fit on the card, and hide it otherwise to avoid cluttering the UI with useless buttons
- [ ] Add missing info on the grade cards:
  - [x] Promotion average ("Moy. Promo")
  - [x] Rank ("Rang")
  - [x] Comment ("Appréciation")
    - [x] first line shown, truncated if too long, with the full comment when the card is unfolded and in the details screen
  - [ ] Coefficient (Not available on the web interface but maybe available in the API? It should be since it's used to calculate the average)
  - [ ] Extra info if any available in the API
- [ ] Feature to sort/group grades (under the top bar):
  - [x] By default it's sorted by date of publication with the most recent first
  - [x] On the web interface it seems to be sorted by module id, which is not very useful, but allows to easily see which grades belong to the same module, so maybe add an option to group grades by module with a separator between modules
    - [x] When filtered by module, show the module name and id at the top of each group (like the "Modules" tab)
  - [ ] Make it so we can sort by any field available (make sure it sorts the numbers correctly and not like strings)
    - [x] Add rank and promo avg
    - [x] Clicking a 2nd time on a sort method should reverse the sort (show next to the selected sort method the current sort order with an arrow)
- [x] Show new grades with a "NEW" badge and an more vibrant bg/outline color, and at the top of the list, separated from the rest with a separator, until the user opens the app or refreshes the list
  - [x] This effect should NOT apply when syncing for the very first time, as the grades that will appear are not "new" but rather the current state that we just dumped

### "Modules" tab
- [x] Make cards more compact
  - [x] Module id next to the name instead of below, similar to the "Epreuves" tab
  - [x] Coeff under grade
- [ ] Add missing info on the module cards:
  - [x] Promotion average ("Moy. Promo")
  - [x] Rank ("Rang")
  - [ ] Infos related to retakes if any
- [x] Group by UEs, and show the UE name at the top of each group, like on the web interface, instead of showing the module name and id on each card
- [x] No need for sorting since it's already grouped by UEs
- [x] When clicking on a module, move to the "Epreuves" tab with a search on the name of the module

### "UEs" tab
- [x] Make cards more compact
  - [x] UE id next to the name instead of below, similar to the "Epreuves" tab
- [x] Remove "TC" (idk what it is)
- [x] No need for sorting


---

## Settings
- [x] Separate the sections more clearly instead of having a big "Oasis" section with everything in it

### Account management section
  - [x] Allow multiple accounts and switching between them with a dropdown that shows the user's name, id, and profile picture for each account
    - [x] This may require some changes in the data layer to support multiple accounts and sync them separately
    - [x] You should not be allowed to add the same account two times
    - [x] fix: the profile picture needs authentication to be fetched
  - [x] Instead of just having "login" and "password" fields always showing in the settings, have a more user-friendly "Add an account" flow
    - [x] Add an "edit account" action for selected account
    - [x] Add a "remove account / log out" action for selected account
      - [x] fix: the red "log out" btn is way too long vertically for some reason
    - [ ] Add the "forgot password" feature
    - [x] Fix password manager interaction in onboarding and settings with native Android Autofill hints; offer saving only after successful authentication
      - [ ] Automatic sharing with the Oasis website requires Oasis to publish Digital Asset Links for the app signing certificate
  - [x] Make an "error" state when the last fetch for an account failed (except no internet error), with a "retry" button that only retries that account
  - [x] Explain that the login info is stored locally and encrypted, and that it's only used to fetch data from Oasis and never shared with anyone, not even the developer, and that the user can remove it at any time by deleting their account in the app or uninstalling the app
    - [x] During the login flow
    - [x] In the "about" section

### Notification settings section
  - [x] Add options to configure which notifications the user wants to receive (new grades, updated grades, sync errors, etc.)
  - [x] Make notifications settings saved per account
    - [x] Make it clear in the settings UI
  - [x] Add a "notification preview" feature that sends an example notification for each type of notification to show the user how they look and what info they contain
    - [x] Use real data from the user's account in the preview notification (even if it doesn't really correspond to the actual state of the grade used)
    - [x] Make it clear in the notifs that they are example notifs
  - [x] Add an "open system notification settings" shortcut and make it clear it's where the user can configure the notification channels and categories in more detail (choose the importance, sound, vibration, etc. for each type of notification)
  - [x] NO notification should be send the very first time the app syncs and fetches the existing grades, because they are not "new" for the user, they are just the current state that we just dumped, so only show notifications for new/updated grades from the second sync and on

### Sync settings section
  - [x] Add options to configure the sync frequency (manual, every 15m, every 30m, every 1h, every 6h, every 12h, every 1d, every 1w)
  - [x] Separate "Background sync" toggle from "Sync frequency" options, because it may not be clear that you have to slide the frequency all the way to "manual" to disable background sync
  - [x] Add conditions (only on Wi-Fi or with unmetered connection, only when charging, etc.) off by default (i.e. allow sync on all networks)
    - [x] Group network, charging and TLS options in a collapsed Advanced menu with short explanations
  - [x] Make it per account if multiple accounts are added
  - [ ] Advanced: Add a "clear cached data" action

### About section
  - [x] links to the GitHub repo, support email (to:alexandre.malfreyt+popsapp@universite-paris-saclay.fr cc:alexandre.malfreyt+popsapp@gmail.com)
  - [ ] explains that the app is unofficial, not affiliated with the school, made by a student, and 100% vibe coded, and that they are welcome to contribute, report issues etc.
  - [ ] Make sure exported logs / issue reports redact credentials, cookies, names, addresses, and other personal data by default (how? maybe mark with a certain syntax the parts of logs that contain personal data to be able to redact them easily when attached to an issue report or shared with support)
  - [x] Add a small diagnostics section (app version, last sync time, selected sync mode, mock/prod URL)
    - [ ] fix: The app version shown in the app is not the same as the real app version

---

## Notifications
- [x] Add more granular notification types (new grade, updated grade, sync error, etc.) and let the user choose which ones they want to receive in the settings
- [x] Make notifications per account if multiple accounts are added (i.e. show the notifications categories per account in the OS notification settings)

---

## Mock Oasis server
- [x] Add a mock server that implements the same HTTP interface as the real Oasis server, to be able to test the app without hitting the real server and to have a stable environment for testing and development
- [ ] Add multi-account support in the mock server

---

## Features to add (later)
- [ ] Add other Oasis features:
  - [ ] "Mes informations personnelles" (My personal information, codepage=STUDENT): basic info about the student such as name, email, phone number, address, emergency contact, photo, and special needs if any
    - [ ] Read-only? Or allow the user to edit and save changes to Oasis?
  - [ ] "Mon cursus" (My curriculum, codepage=MYCURSUS): info about the student's curriculum, such as the final result, accumulated score for each type of requirement (citizenship/quitus/"polypoints", mobility, school/company presence for APP students, language requirements, etc.), jury decisions, comments, etc.
  - [x] "Mes notes" (My grades, codepage=MYMARKS): list of all the student's grades, with the ability to filter and sort them, and see details for each grade
  - [ ] "Mes Choix" (My choices, codepage=MYCHOICES): Mainly useless page because all students attend the same courses except in rare cases like "UE initiative" (which is a mandatory UE where the student is free to choose any course they want)
  - [ ] "Mes documents" (My documents, codepage=MYDOCUMENTS): list of documents uploaded by the student or the administration, such as the CV, cover letter, internship report, grades certificate, etc.
- [ ] Add the timetable (from ADE)
- [ ] Add the Moodle/e-campus features
