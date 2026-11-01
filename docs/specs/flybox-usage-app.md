# Spec: Flybox usage app (Android)

- Status: approved
- Date: 2026-11-01

## Goal
A standalone Android app (Kotlin, Jetpack Compose, no server of its own) used by the owner of an Orange Madagascar Flybox 4G (CP02) to see, in a visual and friendly way, how much data was used, how much of the plan remains, which devices are connected, whether the plan will run out before it expires, and to receive a daily usage reminder. The phone talks directly to the router (`http://192.168.1.1`) over the Flybox Wi-Fi and to the Orange self-care page.

## Out of scope
- Per-device usage for devices other than this phone when the router does not provide it (see AC-17 and AC-18); no traffic capture or extra hardware.
- Controlling the router (blocking devices, changing Wi-Fi settings, SMS, USSD, top-up or purchase of plans).
- iOS, tablets in landscape, widgets, multi-router support, cloud sync, any backend or account on a remote server.
- Reaching the router from outside the Flybox Wi-Fi.

## Business rules
- BR-1: Data collection happens only while the phone can reach the router. When unreachable, the app shows the last stored data with its age and never invents values.
- BR-2: Today's usage is the sum of positive deltas of the router cumulative counters (`downlink_traffic` + `uplink_traffic`) since local midnight. A counter that decreases is treated as a reset: the new value counts as the delta.
- BR-3: Remaining plan, total and expiry date come from the Orange page (`remaining Go`, `total Go`, `valid until dd/mm/yyyy at 23:59:59`). If several plans are listed, remaining and total are summed and the earliest expiry is used.
- BR-4: Daily rate = decrease of Orange remaining Go over the latest span with no top-up (a rise in remaining marks a new plan and restarts the window), requiring at least 6 hours of history. Otherwise it falls back to the router counters over the observed span, also requiring at least 6 hours. Below 6 hours no prediction is shown.
- BR-5: Prediction: `days_until_empty = remaining / rate`, `pred_30d_remaining = remaining - 30 * rate`. If `days_until_empty` is less than the days until expiry, the plan is "at risk" and the empty date is shown.
- BR-6: Units use decimal gigabytes (1 Go = 10^9 bytes). Amounts under 1 Go are shown in Mo.
- BR-7: The router admin password is never stored in plain text and never logged: it is kept in Android Keystore backed encrypted storage. The app lock (PIN) is separate from the router password.
- BR-8: The daily reminder is a local notification at a user-chosen hour (default 20:00) containing today's usage, remaining Go, and the at-risk warning when BR-5 applies.

## Acceptance criteria (all testable)
- AC-1: Given the app is first launched, when the user opens it, then an onboarding flow asks to create a 4 to 6 digit PIN, and the main screen is not reachable until it is confirmed.
- AC-2: Given a PIN exists, when the app starts or returns from background after more than 1 minute, then the PIN screen is shown and 5 wrong attempts lock input for 30 seconds.
- AC-3: Given the router is reachable, when the user enters the router login and password in Settings and confirms, then the app logs in to the router and shows "Connected"; with a wrong password it shows an explicit error and stores nothing.
- AC-4: Given the router is reachable, when the dashboard loads, then it shows today's usage, the Orange remaining Go as a ring gauge with total and expiry date, and the network type and signal strength.
- AC-5: Given at least two collected samples across midnight, when computing today's usage, then only the deltas after local midnight are counted (BR-2), including the counter-reset case.
- AC-6: Given more than 6 hours of history, when the dashboard loads, then it shows the average daily rate, the predicted remaining Go in 30 days and, if at risk, a red banner with the empty date; otherwise a green banner.
- AC-7: Given less than 6 hours of history, when the dashboard loads, then no prediction is shown and a "collecting data" message appears.
- AC-8: Given history exists, when the user opens the History screen, then a bar chart of daily usage over 7 or 30 days (selectable) is shown, and a tap on a bar shows its exact value.
- AC-9: Given the user is logged in to the router, when the Devices screen opens, then it lists connected devices with name, IP, MAC and connected time, and offline known devices appear separately in a muted style.
- AC-10: Given the user is not logged in to the router, when the Devices screen opens, then it explains that a router login is required and links to Settings.
- AC-11: Given the phone is connected to the Flybox Wi-Fi, when the app is in background, then a periodic background job collects a sample at least every 15 minutes (Android minimum) and stores it locally.
- AC-12: Given a reminder hour is set, when that hour is reached, then a notification with the BR-8 content is posted once per day, and none is posted if the reminder is disabled.
- AC-13: Given the Orange page or the router is unreachable, when the dashboard loads, then the last stored values are shown with "updated X min ago" and a non-blocking offline indicator.
- AC-15: Given the Usage access permission is not granted, when the Apps screen opens, then it explains why it is needed and opens the Android special access settings screen directly.
- AC-16: Given the permission is granted, when the Apps screen opens, then it lists this phone's apps ranked by data used (horizontal bars with app icon and name) for the selected period (today, 7 days, 30 days), with a note that the figures concern this phone only.
- AC-17: Given the router returns per-device traffic (`AIRTEL_GET_DEVICE_INFO_TRAFFIC` non empty after login), when the Devices screen opens, then each device shows its data used and the list can be sorted by usage, with a share-of-total bar.
- AC-18: Given the router returns no per-device traffic, when the Devices screen opens, then no per-device figure is shown and the screen says so once in a discreet note instead of showing zeros.
- AC-14: Given the app interface, then all text is in French, the theme is light, the font is Montserrat and the accent color is #ff7900.

## Error and edge cases
- EC-1: Router returns empty values for fields requiring a session (for example monthly counters): the app treats them as unavailable and relies on `system_status`.
- EC-2: Orange page layout changes and the plan text is not found: the plan card shows "Forfait indisponible" and the rest of the app keeps working.
- EC-3: Router session expires or the router reboots: the app re-authenticates silently once with the stored credentials, then falls back to the "not connected" state.
- EC-4: Phone date or timezone changes: day boundaries use the device local time zone, samples keep UTC timestamps.
- EC-5: Cleartext HTTP to `192.168.1.1` and `123.orange.mg` must be explicitly allowed for these two hosts only.
- EC-6: Another user is logged in on the router ("account already in use"): the app shows that message and retries later without locking the account.

## Interface
Bottom navigation with five screens, large friendly visuals, light theme, Montserrat, accent #ff7900.
- Home: ring gauge of remaining plan, today's usage, banner (green/red), 30 day prediction, signal chip.
- History: bar chart 7/30 days, tap for value, line of recent throughput.
- Devices: list of online devices (with per-device usage when the router provides it), muted list of offline ones.
- Apps: this phone's per-app usage ranking, period selector (today, 7 days, 30 days).
- Settings: router login, reminder hour and switch, PIN change, units, about.
States for every screen: loading (skeleton), empty (no data yet), error/offline (last known data with age), access denied (router login needed or PIN).

## API (see the contract)
No project API: the app is the only client. The data-source contract is fixed here.
- Router (HTTP GET, no auth): `/reqproc/proc_get?cmd=system_status&isTest=false` returns JSON with string fields `uplink_traffic`, `downlink_traffic` (cumulative bytes), `uplink_rate`, `downlink_rate` (bytes/s), `online_time` (s), `network_type`, `rsrp`, `rssi`, `sinr`, `plmn`.
- Router (needs session): `/reqproc/proc_get?cmd=deviceList_manage&isTest=false` returns `{"deviceList_manage":[{deviceName, macAddress, isOnline("1"/"0"), ip, enable, type}]}`; `cmd=station_list` returns `{"station_list":[{hostname, ip_addr, mac_addr, connect_time, dev_type}]}`.
- Router login: `cmd=get_random_login` (GET) then a POST to `/reqproc/proc_post` with `goformId=LOGIN`; the exact credential encoding is to be confirmed (first task for the mobile agent, see open questions).
- Router per-device traffic (needs session, may be empty on this firmware): `cmd=AIRTEL_GET_DEVICE_INFO_TRAFFIC` returns `{deviceList:[{index, ...}]}`; field names unknown until the router returns data. Possible activation by `goformId=AIRTEL_SET_UPDATE_TRAFFIC` (changes router settings: requires the user's explicit consent at the time, never done silently).
- Phone per-app usage: Android `NetworkStatsManager` (Wi-Fi and mobile), permission `PACKAGE_USAGE_STATS` granted by the user in system settings.
- Orange page: GET `http://123.orange.mg/info-conso/`, parse the text pattern `<plan name> X Go sur Y Go valable jusqu'au dd/mm/yyyy` and the line `Le solde de votre compte de rechargement est de N Ar`.
Reference implementation of parsing and prediction: the earlier Python prototype (`conso.py`, not part of this repository) (functions `collect_router`, `collect_orange`, `stats`).

## Constraints
- Kotlin, Jetpack Compose (Material 3), Room for history, WorkManager for collection and the reminder, DataStore plus Android Keystore for settings and secrets, OkHttp for network. minSdk 26.
- Cleartext traffic limited to the two hosts through a network security config.
- Permissions: network, notifications (Android 13+), and Usage access (user-granted, only for the Apps screen).
- No analytics, no third-party trackers; no data leaves the phone.
- No secrets in the code; the router password is entered by the user.

## Open questions
- Q-1 (blocking for AC-3, AC-9, AC-10): exact router login protocol (`get_random_login` result, hash or encoding of the password, cookie or token handling). The mobile agent must establish it first, from the router web client (`http://192.168.1.1/static/js/app.js`) without ever receiving the real password, and report if it cannot be reproduced.
- Q-2: Is the Orange page reachable from the phone while on the Flybox Wi-Fi only (it was reachable from the PC on that network)? Assumed yes.
- Q-4: Whether the router can be made to return per-device traffic is unknown. The mobile agent must not change router settings; if activation is needed, it reports back so the lead asks the user first.
- Q-3: Assumption made for the user: single user, local PIN lock rather than server accounts, since there is no backend.
