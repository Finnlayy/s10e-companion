# Android platform constraints: Kotlin/Compose desk companion on a Galaxy S10e (Android 12, no root, sideloaded), as of November 2026

Reading notes:
- "AOSP 12" means I read the `android12-release` source directly on 2026-11-01. It is primary evidence for stock Android 12. Samsung may change behaviour in One UI, so every AOSP claim still needs a test on the phone.
- The S10 family's last major update is Android 12 with One UI **4.1**, not 4.0. Rollout began March 2022 ([Notebookcheck](https://www.notebookcheck.net/Samsung-Galaxy-S10-devices-in-the-US-set-to-receive-their-last-big-software-update-in-the-coming-days.615635.0.html); [GSMArena spec page](https://www.gsmarena.com/samsung_galaxy_s10e-9537.php)). Check the exact One UI version under Settings > About.
- One 2022-era report expected updates only up to March 2023 ([Notebookcheck](https://www.notebookcheck.net/Samsung-Galaxy-S10-devices-in-the-US-set-to-receive-their-last-big-software-update-in-the-coming-days.615635.0.html)). Treat the phone as long out of security support and confirm the patch level on the device.
- Many forum and Stack Overflow sources below are old. They are flagged where they matter.

## Child-mode lock: what is possible, what blocks what, how to get out, and is spoken unlock safe?

### Takeaway
A real lock needs Device Owner plus LockTask. Without Device Owner you only get screen pinning, which anyone can exit unless Samsung's "screen lock to unpin" option is on. Device Owner via adb is feasible on a phone with zero accounts and one user, but build it with `android:testOnly="true"` and keep ADB on, or the only way out of a failure may be a factory reset. Spoken-only unlock is not a lock. Use voice as a convenience and a parent PIN as the authority.

### Cited Findings

**Behaviour matrix (LockTask vs pinning)**
- With Device Owner allowlisting, LockTask hides Home and Overview, blanks the status bar, hides notifications, stops other apps launching activities, and disables the lock screen — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- `setLockTaskFeatures()` (API 28+) is deny-by-default: any feature not listed is off. `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` is the only feature on by default. Turning it off typically stops users powering off from the power menu — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- `LOCK_TASK_FEATURE_NOTIFICATIONS` is what enables the shade, and it requires HOME — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode). So the shade stays closed in a full lock by default.
- Back: in a full lock, the system refuses to finish the root activity of the locked task or move it to back, and shows a toast. The code is `activityBlockedFromFinish` and `canMoveTaskToBack` — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java). So system Back cannot leave the app.
- Pinned mode's status bar mask keeps Back, Home and Recents enabled so that the exit chord works. Everything else in the disable mask is disabled, which includes shade expansion — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java).
- Overlays from other apps can still appear in LockTask unless `DISALLOW_CREATE_WINDOWS` is added in `onLockTaskModeEntering()` and removed on exit — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- An emergency-call intent passes the LockTask violation check only if `LOCK_TASK_FEATURE_KEYGUARD` is on — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java).
- A dream (screensaver) may start during LockTask — same source.

**What a non-Device-Owner app gets**
- `startLockTask()` from a non-allowlisted app enters screen pinning, "not the same" as lock task, because the user can exit at any time — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- For a non-allowlisted app, AOSP shows the pinning request dialog and returns. The app cannot enter pinning silently — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java).
- Samsung exit: touch and hold Recents and Back together. With gestures, swipe up and hold. The setting is under Security and privacy > More security settings > "Allow apps to be pinned" — [Samsung support](https://www.samsung.com/us/support/answer/ANS10004865/). The menu wording varies by One UI version.
- Samsung offers "Use screen lock type to unpin" — [Samsung support](https://www.samsung.com/us/support/answer/ANS10004865/). This maps to `LOCK_TO_APP_EXIT_LOCKED`. AOSP locks the keyguard on exit and calls `requireCredentialEntry` — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java).
- `adb shell am task lock <id>` calls `startSystemLockTaskMode`, which treats the caller as the system and enters **pinned** mode, with no dialog in code — [AOSP 12 ActivityManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/am/ActivityManagerShellCommand.java); [AOSP 12 ActivityTaskManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/ActivityTaskManagerService.java).
- `am task lock stop` only clears pinned mode. In a full lock it logs "Attempted to stop LockTask with isSystemCaller=true" and does nothing — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java).

**Device Owner provisioning via adb (AOSP 12)**
- After setup is complete, the adb path requires: no existing device owner, no profile owner on the user, **user count of at most 1**, and no incompatible accounts. Otherwise it returns the "several users" or "some accounts" errors — [AOSP 12 DevicePolicyManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java).
- A non-testOnly owner cannot be set with any account present. A testOnly owner can, but only if every account carries the `DEVICE_OR_PROFILE_OWNER_ALLOWED` feature and none the `DISALLOWED` one — same source. Normal Google or Samsung accounts should not be assumed to qualify.
- The check runs only when setting the owner. AOSP has no such check on later account additions in this path — same source (inference from the code path; see Gaps).
- Field reports: any account listed in Settings blocks it. Work profiles and app cloning count as extra users — [OwnDroid](https://github.com/BinTianqi/OwnDroid).
- One old Samsung report says setup creates an account that Settings does not show, and that only removing accounts plus a factory reset worked — [Stack Overflow mirror, undated, old](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/49279267/how-can-i-set-the-device-owner-on-a-samsung-device). A SIM was also reported as a blocker, without a primary source.

**Reversal**
- `dpm remove-active-admin` (`forceRemoveActiveAdmin`) throws "Attempt to remove non-test admin" unless the admin is testOnly. For a testOnly admin it also clears the device owner — [AOSP 12 DevicePolicyManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java); confirmed by [a field report (2018-era)](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/48990479/disable-a-device-owner-app-from-android-terminal).
- `clearDeviceOwnerApp()` works when called by the device owner itself, with no testOnly check, and requires the user to be unlocked — [AOSP 12 DevicePolicyManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java). A 2018 answer says it was deprecated in Oreo — [Stack Overflow mirror](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/48990479/disable-a-device-owner-app-from-android-terminal). I could not read the current reference text.
- The DPC can end a lock by dropping the package from `setLockTaskPackages()`. An activity can call `stopLockTask()`, but only the one that started the lock — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- A plugin README warns that without a reversal path you cannot unset the owner or uninstall the app without a factory reset — [cordova-plugin-locktask](https://github.com/oddmouse/cordova-plugin-locktask).

**Hardening restrictions and their trade-offs**
- `DISALLOW_SAFE_BOOT` blocks rebooting into safe mode. `DISALLOW_FACTORY_RESET` blocks factory reset "from Settings". `DISALLOW_DEBUGGING_FEATURES` from a Device Owner disables USB debugging altogether — [AOSP 12 UserManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/os/UserManager.java).
- The S10e recovery combination is Bixby + Volume Up + Power from powered off, with `adb reboot recovery` as an alternative. These are third-party guides, and some users report the combo failing — [The Custom Droid](https://thecustomdroid.com/boot-galaxy-s10-download-recovery-mode-guide); [Samsung community](https://r2.community.samsung.com/t5/Galaxy-S/s10e-can-t-enter-recovery-mode-also-can-charge-off-PC-with-usb/td-p/7899975).

**Crash behaviour**
- When the last locked task is removed, LockTask mode is reverted — [AOSP 12 LockTaskController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/LockTaskController.java).
- `android:lockTaskMode="if_whitelisted"` re-enters LockTask automatically when an allowlisted activity launches — [Android: lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
- Do not start LockTask while the device is locked. Start it after unlock, for example in `onResume()` — same source.

**Knox**
- Samsung's Knox SDK kiosk can also restrict the status bar and certain hardware keys — [Knox kiosk sample](https://docs.samsungknox.com/dev/knox-sdk/appendix/sample-apps/kiosk-mode/). The KPE Standard key is listed as free — [Knox licensing FAQ](https://docs.samsungknox.com/dev/knox-sdk/faq/licensing/). Whether the free key covers `KioskMode` on this phone is unverified.

**Voice as authentication**
- Google warned about its own Trusted Voice that it was less secure than a PIN, and that a similar voice or a recording could unlock the device — [The Hacker News, 2015, old but still the clearest vendor statement found](https://thehackernews.com/2015/04/android-trusted-voice.html).

### Inferences
- **Recommended lock tiers:**
  - Tier A, Device Owner + LockTask: the app can enter the lock silently, for instance when child mode is triggered. Recommended if accounts can be removed.
  - Tier B, pinning + "screen lock to unpin": a child can leave the app but lands on the keyguard. Needs a tap on the pinning dialog each time, unless issued from shell identity. Acceptable fallback, with no provisioning risk.
  - Tier C, launcher only: not a lock. See the next section.
- **Provisioning order:** remove all accounts and any Secure Folder, Dual Messenger or work profile → check `adb shell pm list users` and accounts → `adb install -t` the testOnly APK → `adb shell dpm set-device-owner pkg/.Receiver` → re-add accounts if wanted, after a test. Secure Folder and Dual Messenger as extra users is an inference from the user-count check, so verify with `pm list users`.
- **testOnly cost:** a testOnly APK installs only with `adb install -t` by general knowledge, so updates go through adb. That fits a developer-owned device. Decide this before provisioning, because changing an owner from non-test to test without a reset is not possible by the code above unless the app clears itself first.
- **Do not set `DISALLOW_DEBUGGING_FEATURES`.** It removes the adb escape and, by the same mechanism, kills Shizuku. Settings is unreachable from inside a full lock anyway, so adb on is not a child risk unless the child has a trusted computer.
- **Do not set `DISALLOW_FACTORY_RESET` or `DISALLOW_SAFE_BOOT` in early versions.** They remove recovery options while the lock is unproven.
- **Keep `GLOBAL_ACTIONS` on.** Being able to power off is a safety property. The forced-restart key combination is handled below Android by general knowledge, so a reboot cannot be fully prevented. Reboot must therefore lead back into the app, which requires the HOME role.
- **Crash hole:** if the app crashes and it is not the HOME app, the task dies, LockTask ends and the child lands on the normal launcher. Making the app the persistent HOME activity plus `if_whitelisted` closes this. The other side of that coin is a crash loop with no launcher to fall back on. The planner needs a crash counter that drops to a safe, non-agent screen after N crashes.
- **Escape ladder, in order:**
  1. Parent PIN in a hidden gesture (for example 5-second hold on a corner while the eyes are shown) → `stopLockTask()`. In-app.
  2. Separate "disable kiosk" PIN path → drop package from `setLockTaskPackages()`, then optionally `clearDeviceOwnerApp()`. In-app.
  3. Laptop with an authorised adb key → `adb shell dpm remove-active-admin pkg/.Receiver` (testOnly build only). `am force-stop` probably ends the task, but note `am task lock stop` does **not** end a full lock.
  4. Factory reset through recovery. Destroys the vault unless it is synced elsewhere. This is the only way out if the app is non-testOnly, ADB is off and the in-app paths fail.
- The hidden hatch must work when ASR, microphone, camera and the agent are all broken, so it must be plain touch UI in the first composition with no dependency on models or storage beyond the stored PIN hash.
- **Spoken unlock:** the phrase is a shared secret spoken aloud in a home. A child will hear it, repeat it, or replay it. Speaker verification on a child-vs-parent basis is not a reliable answer either. Recommended: a phrase can move the UI out of child mode only together with a PIN, or the phrase opens the PIN pad. Add attempt rate-limiting, store a salted PIN hash, and treat ASR failure as "show PIN pad" rather than "stay locked". Child mode must also hard-disable all agent tools, so the phrase can never reach phone control.

### Gaps
- No One UI 4.1 test data for: side key double-press, Bixby key, Edge panels, or Samsung's own overlays during LockTask. Must be tested.
- The Samsung "hidden account at setup" report is old and was not reproducible from any primary source.
- Whether stock recovery wipe is blocked or affected in any way on this phone, and whether FRP applies after accounts were removed, was not verified.
- Whether `am task lock <id>` from Shizuku works on One UI was not verified. It is AOSP code only.
- Current `clearDeviceOwnerApp` reference page text could not be retrieved (fetch returned navigation only).

## Immersive full screen, FLAG_KEEP_SCREEN_ON, AMOLED burn-in and pixel shift

### Takeaway
Immersive mode and keep-screen-on are trivial, but immersive alone is not a lock: a swipe reveals the bars. Burn-in is a real risk on a phone showing the same shapes all day, and pixel shift alone will not save large solid eyes. No S10e-specific burn-in measurement exists, so the design must reduce luminance × time rather than rely on one trick.

### Cited Findings
- Hide bars with `WindowCompat.getInsetsController(...)` and `hide(WindowInsetsCompat.Type.systemBars())`, with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, where an edge swipe reveals bars temporarily — [Android: immersive](https://developer.android.com/develop/ui/views/layout/immersive).
- `FLAG_KEEP_SCREEN_ON` may only be set from an activity, not a service. The docs warn about battery drain. No permission is described — [Android: keep screen on](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).
- S10e panel: 5.8" Dynamic AMOLED, 2280×1080 — [GSMArena](https://www.gsmarena.com/samsung_galaxy_s10e-9537.php).
- Burn-in depends on static content and brightness, and blue subpixels are reported to decay fastest, red slowest. This comes from vendor and blog pages, not a measured S10 source — [Panox Display](https://www.panoxdisplay.com/solution/amoled-screen-lifespan-burn-in-guide); [Androxus](https://androxus.com/blogs/what-is-amoled-burn-in). TechSpot's panel test also found red degraded least — [TechSpot](https://www.techspot.com/article/3098-oled-burn-in-test/).
- Phone test data, older and not S10: a 2018 Cetizen test ran iPhone X, Note 8 and S7 Edge at full brightness on a static image for 510 hours, with visible marks reported early in some coverage. The "17 hours" headline came from that maximum-brightness setup — [AppleInsider](https://appleinsider.com/articles/18/01/03/extreme-oled-test-finds-apples-iphone-x-takes-much-longer-to-burn-in-images-than-samsung-galaxy-note-8); [ProPakistani](https://propakistani.pk/2018/01/05/iphone-xs-screen-shows-burn-marks-just-17-hours-test/).
- Long-running monitor tests (not phones) found faint burn-in around 15 months — [TechSpot](https://www.techspot.com/article/3098-oled-burn-in-test/).

### Inferences
- Use `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` plus LockTask in child mode. In LockTask the bars have nothing useful to reveal.
- In Compose, set `LocalView.current.keepScreenOn = true` in a `DisposableEffect` and clear it in `onDispose` (general knowledge; the page does not cover Compose). Clear it when the no-presence blanking should let the screen sleep.
- Burn-in budget. All values below are engineering starting points, not sourced limits:
  - Black background, eyes only: most pixels off, which also cuts power.
  - Eye colour with little blue (warm amber or green) rather than white or cyan, since blue ages fastest.
  - Fixed low brightness, for example 25–35 %, with `window.attributes.screenBrightness`, dimmed further at night.
  - **Pixel shift is weak for big solids**: it only moves the edge, while the middle of the eye is lit the whole time. Combine a slow drift (±16–32 px over minutes) with regular size, shape and position changes, so that no pixel is lit 100 % of the time.
  - Best mitigation: **turn the eyes off when nobody is there**. Use camera presence to go to black or a very dim state after a few minutes, and a schedule at night.
  - Avoid any static chrome: clock text, status, buttons. Move or fade them.
- Plan on no-presence blanking from the first version, since the panel cannot be replaced cheaply.
- Continuous charging is the second hardware risk on a 2019 phone. See the Gaps below.

### Gaps
- No S10/S10e burn-in test and no Samsung statement on whether the panel performs its own pixel shift in apps.
- I found no reliable source for a safe hours-at-brightness budget. Any number here would be invented.
- "Protect battery" (85 % limit) on the S10e under One UI 4.1 could not be confirmed. The only hit was a user comment about the S21 Ultra. Check Settings > Battery on the phone. No source was found on swelling risk from 24/7 charging for this model, but a 7-year-old battery on permanent charge deserves a physical check.

## Boot start and default launcher (HOME intent) as a kiosk alternative

### Takeaway
Becoming the HOME app is the correct way to reappear after a reboot, because a boot receiver cannot start an activity on its own. HOME alone is not a kiosk: without LockTask the shade and Settings stay reachable as far as any source shows.

### Cited Findings
- `BOOT_COMPLETED` is not an exception to background activity start limits. The docs say to use a notification instead. Exceptions include `SYSTEM_ALERT_WINDOW` — [Android: background activity starts](https://developer.android.com/guide/components/activities/background-starts).
- `BOOT_COMPLETED` is an exemption for starting foreground services — [Android: FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start). It is not a while-in-use exemption, so a boot-started service must not count on camera or microphone — same source; [Android: FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types).
- `RoleManager` (Android 10+) can request `ROLE_HOME` with `createRequestRoleIntent`. A developer write-up reports that role state may lag and that a launcher chooser can reappear after restart — [ProAndroidDev part 1](https://proandroiddev.com/how-we-built-a-full-fledged-soft-kiosk-app-on-android-part-1-2af189277053); [part 2](https://proandroiddev.com/the-android-kiosk-mode-challenge-a-battle-tested-solution-part-2-255e4e0a25f8).
- Status-bar control is described as a managed-device feature, and an MDM vendor warns that declaring the HOME category may interfere with kiosk settings on some devices — [Applivery kiosk docs](https://docs.applivery.com/en/device-management/android/policies/kiosk-mode.md).
- The Dedicated Devices cookbook is the official place for `addPersistentPreferredActivity`-style HOME pinning by a DPC — [Android: dedicated devices cookbook](https://developer.android.com/work/dpc/dedicated-devices/cookbook?hl=ko). I saw this only as a search hit and did not read the page.

### Inferences
- Non-owner setup: request `ROLE_HOME`, with a manifest activity for `MAIN` + `HOME` + `DEFAULT`. Back and Home then return to the app, but Settings through the shade stays open. That is fine for a desk companion in adult mode and not for a child.
- With Device Owner: HOME + persistent preferred activity + `if_whitelisted` gives boot-to-kiosk.
- Keep a route to the old launcher in the parent settings (`Settings.ACTION_HOME_SETTINGS`), since being HOME removes the usual app drawer. The phone control feature also needs to launch other apps.
- If a device PIN stays set, boot stops at the keyguard before the app appears. A companion on a desk probably wants "None" as the lock type, but Tier B pinning needs a screen lock. These two conflict, and the choice of tier decides it.

### Gaps
- No primary source confirms shade reachability for a non-owner launcher on One UI 4.1, but nothing suggests the launcher role blocks it. Quick to test.
- Gesture-navigation behaviour with a third-party launcher on One UI 4.1 was not researched.

## Foreground services on Android 12, Samsung battery behaviour and exemptions

### Takeaway
Android 12 has only eight FGS types: `dataSync`, `mediaPlayback`, `phoneCall`, `location`, `connectedDevice`, `mediaProjection`, `camera`, `microphone`. `specialUse` does not exist on this OS. While the activity is visible and the screen is forced on, the app needs no FGS for camera or mic. Use a `dataSync` FGS for agent work, started while visible. Samsung is the worst-rated vendor and has no developer-side fix, so the user must set the exemptions by hand.

### Cited Findings
- Android 12's `ServiceInfo` defines exactly: DATA_SYNC, MEDIA_PLAYBACK, PHONE_CALL, LOCATION, CONNECTED_DEVICE, MEDIA_PROJECTION, CAMERA, MICROPHONE — [AOSP 12 ServiceInfo](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/content/pm/ServiceInfo.java). So `health`, `remoteMessaging`, `shortService`, `specialUse`, `systemExempted`, `mediaProcessing` listed in the current guide are later additions — [Android: FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types).
- Apps targeting Android 11+ must declare `camera` / `microphone` types to use those from an FGS, and a service started from the background cannot access them — [Android 11: FGS](https://developer.android.com/about/versions/11/privacy/foreground-services).
- Apps targeting Android 12+ get `ForegroundServiceStartNotAllowedException` when starting an FGS from the background, with exemptions: transition from a visible state, user interaction, exact alarm for a user action, `BOOT_COMPLETED`, device owner or profile owner, **battery optimisation turned off by the user**, `SYSTEM_ALERT_WINDOW` — [Android: FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- Exemptions from the while-in-use restriction for camera/mic include a service started from a notification or widget interaction, or from a DPC running in device owner mode — same source.
- The current page dates the `SecurityException` to Android 14 for target 34+. On Android 12 the older behaviour applies, which is restricted access rather than a thrown exception, by the [Android 11 page](https://developer.android.com/about/versions/11/privacy/foreground-services). Logcat shows a "can not have location/camera/microphone access" line — [Android: FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- Newer limits: the guide states that apps targeting Android 15+ cannot start `dataSync` and some other types from `BOOT_COMPLETED` — [Android: FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types). By general knowledge such rules are gated on the device OS as well, so they do not fire on Android 12.
- dontkillmyapp rates Samsung 5, among the worst. The list of mechanisms: Sleeping / Deep sleeping / Never sleeping lists, Adaptive battery, Auto-optimise, Good Guardians, Game Booster. On Android 11–12: set "Don't optimize", turn off Auto-optimize and Adaptive battery, check sleep lists. Settings can revert after updates. "No known solution on dev end" — [dontkillmyapp: Samsung](https://dontkillmyapp.com/samsung).
- The site also says Android 11 introduced a default restriction stopping apps holding a wake lock in FGS, with battery-optimisation exclusion as the workaround — [dontkillmyapp: Samsung](https://dontkillmyapp.com/samsung). This is a single unconfirmed statement.
- Samsung: "Never sleeping apps" under Battery > Background usage limits — [Samsung CA](https://www.samsung.com/ca/support/mobile-devices/galaxy-phone-sleeping-apps/); [Samsung US](https://www.samsung.com/us/support/answer/ANS00088422/). Path varies by One UI version.
- Developers report no public way to detect or request the Never-sleeping list, and that some apps do not appear in it — [Samsung dev forum](https://forum.developer.samsung.com/t/detect-if-app-is-on-never-sleeping-apps-list-and-request-that-app-is-added-to-that-list/12089); [second thread](https://forum.developer.samsung.com/t/apps-that-wont-be-put-to-sleep-list-dont-include-all-apps/7412).
- `isIgnoringBatteryOptimizations()` checks the list. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission. Exemption allows network and partial wake locks in Doze but is only partial — [Android: Doze](https://developer.android.com/training/monitoring-device-state/doze-standby). Play policy limits are irrelevant for sideload.

### Inferences
- Eyes, camera presence, shake, and mic for the unlock phrase all run in the resumed activity. No FGS is needed for those, as long as the app is on top and the screen is on.
- Agent runs: start a `dataSync` FGS from the visible UI. Starting it while visible avoids the background start rule. `microphone`/`camera` FGS only matter if work must continue with the app in the background, for example when the agent opens another app. Start them while visible, before leaving.
- Do not use `specialUse` as a design input. Compiling with it is possible but it means nothing on API 31.
- A sideloaded app targeting 34+ on this phone would need extra `FOREGROUND_SERVICE_*` declarations for lint and for any future OS, but they have no effect on Android 12. By the "behaviour changes gated on OS version" rule, the Android 14 and 15 rules above do not fire here.
- First-run checklist in the app: show the `isIgnoringBatteryOptimizations` state; deep link to battery settings; give the user manual steps for Never sleeping apps, Adaptive battery off, Auto-optimize off. Repeat the same for Termux.
- A resumed activity with the screen held on should not be hit by Doze, since Doze needs the screen off (general knowledge). The Samsung risk lands mainly on **Termux and background helpers**, not the main app.

### Gaps
- Actual kill rate of FGS on One UI 4.1 is unmeasured. dontkillmyapp has no per-One-UI numbers.
- Exact Device Care menu names on One UI 4.1 need a look at the phone.

## Phone control without root: Accessibility, adb loopback, Shizuku, `input`/`uiautomator`

### Takeaway
On Android 12 the most robust base is an **AccessibilityService**: it survives reboots, needs no Wi-Fi and is reachable from Kotlin. Add **Shizuku** as an optional power layer for shell-only tasks, accepting that it dies at each reboot and needs Wi-Fi to come back. The official auto-start is Android 13+ only, so it does not help here. An ordinary app cannot call `input`. Never give the LLM raw access to either: expose a small allowlisted tool API with human confirmation.

### Cited Findings

**AccessibilityService**
- Declared with `BIND_ACCESSIBILITY_SERVICE`, `canRetrieveWindowContent` for reading the tree, and `canPerformGestures` for `dispatchGesture()` — [Android: accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service).
- `typeAllMask` is expensive because it delivers every event across the OS — same source.
- Enabling: adb can set it with `settings put secure enabled_accessibility_services pkg/.Service`, separating multiple with `:`, since a plain put disables the others — [Key Mapper docs](https://docs.keymapper.club/user-guide/adb-permissions/); [gist comments](https://gist.github.com/mrk-han/67a98616e43f86f8482c5ee6dd3faabe).
- With adb-granted `WRITE_SECURE_SETTINGS`, an app can write the same setting via `Settings.Secure.putString`, plus `ACCESSIBILITY_ENABLED` = 1 — [Stack Overflow mirror, old](https://kaweah.freedombox.rocks/kiwix/content/stackoverflow.com_en_all_2023-11/questions/10061154/how-to-programmatically-enable-disable-accessibility-service-in-android?lq=1). These sources are community level, behaviour varies by device.
- "Restricted settings" that block sideloaded apps from enabling accessibility belong to Android 13 and up — [Google Android Help](https://support.google.com/android/answer/12623953). So they do not apply on this Android 12 phone.
- If the service process dies, AOSP marks it crashed and adds it to `mCrashedServices`. It can clear that on reconnection or on user toggle. `updateServices` skips crashed components — [AOSP 12 AccessibilityServiceConnection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java); [AOSP 12 AccessibilityUserState](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/accessibility/java/com/android/server/accessibility/AccessibilityUserState.java).
- API summary from general knowledge, as the reference page fetch returned no content: `performGlobalAction` (Home, Back, Recents, Notifications, Quick Settings, power dialog, lock screen and screenshot on API 28+), `takeScreenshot` on API 30+, `getRootInActiveWindow` — [AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService). Verify signatures.

**`input` / `uiautomator` from an app**
- Injecting input needs the signature-level `INJECT_EVENTS`, granted to system and shell, which is why `adb shell input` works — [AOSP commit](https://android.googlesource.com/platform/frameworks/native/+/985a1b2d79).
- An app calling `input` through `Runtime.exec` keeps its own UID and fails with a SecurityException. Declaring the permission does not help — [Stack Overflow mirror, old](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/10190784/about-inject-events-securityexception-exception).

**adb loopback**
- LADB shows the method: an adb binary inside the app talks to the phone's own Wireless Debugging over loopback. Pairing needs the Settings dialog open beside the app (split-screen or pop-out) — [LADB](https://github.com/tytydraco/LADB).
- LADB says it is usually incompatible with Shizuku installed — [LADB](https://github.com/tytydraco/LADB).
- Community thread on the difficulty of keeping ADB over Wi-Fi alive — [r/tasker thread](https://redlib.hackliberty.org/r/tasker/comments/z546at/having_trouble_keeping_adb_wifi_alive/j17dzu1/?context=3).
- `adb_wifi_enabled` is in the Device Owner `setGlobalSetting` allowlist on Android 12 (`ADB_WIFI_ENABLED`, as is `ADB_ENABLED`) — [AOSP 12 DevicePolicyManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java). So a Device Owner app could turn wireless debugging on without user steps.

**Shizuku**
- On Android 11+ it starts with wireless debugging and no computer: enable developer options, USB debugging and wireless debugging, pair once. Both non-root methods must be **repeated after each reboot** — [Shizuku setup](https://shizuku.rikka.app/guide/setup/). The page has no Samsung notes.
- Latest official release is v13.6.0 (25 May, year not on the page, presumably 2026 or 2025, Android 16 QPR1 support). It adds auto start without root **on Android 13 and later** on a trusted Wi-Fi — [Shizuku releases](https://github.com/RikkaApps/Shizuku/releases). Not for this phone.
- Fork `thedjchi/Shizuku` offers start on boot for unrooted Android 11+, waiting for Wi-Fi, startup taking minutes, a Watchdog, and TCP mode (like `adb tcpip`) so later restarts need no Wi-Fi. It warns TCP mode is less secure, since anyone can send a debugging prompt — [fork wiki](https://github.com/thedjchi/Shizuku/wiki/Features). USB debugging must stay on — [fork wiki: setup](https://github.com/thedjchi/Shizuku/wiki/Setup).
- API: `dev.rikka.shizuku:api` and `:provider`, version 13.1.5 on the README. `ShizukuProvider` in the manifest. Permission model like runtime permissions. `ShizukuBinderWrapper` for simple binder calls. `UserService` runs your code in a separate process as UID 2000 under adb. Android 6+ — [Shizuku-API](https://github.com/RikkaApps/Shizuku-API).
- Shell-level limits: adb identity cannot read other apps' private data — same source.

### Inferences
| Option | Survives reboot | Needs Wi-Fi | Taps | Reads UI | Shell | Verdict for Android 12 |
|---|---|---|---|---|---|---|
| AccessibilityService | Yes | No | Yes, gestures | Yes, node tree | No | **Primary** |
| Shizuku official | No, manual restart | At start | Via `input` in UserService | `uiautomator dump` | Yes, UID 2000 | Secondary, optional |
| Shizuku fork | Yes, after delay | At first start | Same | Same | Yes | Candidate, third-party trust |
| App-embedded adb loopback | Partly | Yes | Via shell | Via shell | Yes | More fragile, reimplements Shizuku |
| Device Owner | Yes | No | No | No | No | Policy only, not input |

- Suggested layering: (1) AccessibilityService for tap, swipe, global actions, reading the node tree. (2) Use `adb` once from a laptop to grant `WRITE_SECURE_SETTINGS` to the app, so the app can re-enable its own service after a crash. (3) Shizuku only for what accessibility cannot do: `pm`, `am`, `settings`, and `screencap`-type work. All features must degrade cleanly if Shizuku is down, which will be normal after each reboot.
- A shell-identity helper (Shizuku) could also pin the app without the dialog using `am task lock`, which would improve Tier B without Device Owner. This depends on Shizuku being up at the moment, so it is a nicety, not a base.
- Device Owner does not replace these: it has no input injection API in anything read here.
- Other apps cannot be launched in a full lock, so phone control and LockTask exclude each other. If the agent has to drive other apps, leave child mode and LockTask first. Behaviour of accessibility global actions inside LockTask is untested.

**Security when a local LLM drives this**
- OWASP names the failure "Excessive Agency", from excessive functionality, permissions or autonomy, triggered by hallucination or direct or indirect prompt injection. Mitigations: minimal tools, avoid open-ended tools such as shell, complete mediation outside the LLM, human approval for high-impact actions, logging, rate limits — [OWASP LLM06:2025](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/).
- Termux warns that a caller of `RUN_COMMAND` can retrieve output, so be careful which apps are allowed — [Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).
- Inference: screen text, notifications, vault notes and web pages are all injection channels. A node-tree dump passed straight to the model means any message on screen can issue instructions.
- Inference, concrete rules:
  - Fixed tool schema, not a `shell(string)` tool. Allowlist packages for the tap tool.
  - Blocklist banking, password, Settings > Developer, and app install screens in code, not in the prompt.
  - Require a physical on-screen confirmation for send, delete, install, purchase and settings actions.
  - Kill switch outside the LLM: a persistent notification action or on-screen control that stops the agent service and revokes tools.
  - Tools disabled in child mode, enforced at the dispatcher.
  - Treat an unpatched Android 12 phone with accessibility plus shell power as a low-trust device: no primary Google account, no banking, no password manager.
  - Do not leave `adb tcpip` on. Prefer pairing-based wireless debugging.

### Gaps
- `uiautomator dump` from UID 2000 via Shizuku on One UI 4.1 is unverified. It is known to fail on animated screens by general knowledge, which matters as the app itself animates constantly.
- Whether `settings put global adb_wifi_enabled 1` with app-held `WRITE_SECURE_SETTINGS` starts wireless debugging on this phone is unverified, although the fork suggests some such mechanism.
- Fork trust: it is a third-party build with shell power. I did not audit it.
- No source on how long Shizuku stays alive on One UI 4.1. Its process is started by adb, so phantom process rules may be relevant — not confirmed.
- Whether Wireless Debugging on One UI 4.1 keeps running when Wi-Fi drops was not established.

## Network-free camera presence detection

### Takeaway
Feasible with CameraX `ImageAnalysis` and bundled ML Kit, both offline after install. No FGS is needed while the activity is on screen. I found no phone-level power numbers, so measure it. On mains power the concern is heat, not battery.

### Cited Findings
- CameraX stable is 1.6.2 (2026-08-26), 1.7.0-rc01 from 2026-10-21. Default minSdk went from 21 to 23 in 1.5.0 — [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera). 1.7 notes a Java 11 target that may need desugaring, and that compileSdk 37 with Compose needs AGP 9.2+ — same source.
- `ImageAnalysis`: default `STRATEGY_KEEP_ONLY_LATEST`. Default output `YUV_420_888`. Must call `imageProxy.close()`. The guide's example uses `setTargetResolution` — [CameraX analyze](https://developer.android.com/media/camera/camerax/analyze).
- ML Kit bundled face detection: `com.google.mlkit:face-detection:16.1.7`, about 6.9 MB, linked at build time and available immediately. Unbundled depends on Play Services download — [ML Kit face detection](https://developers.google.com/ml-kit/vision/face-detection/android).
- Guidance: `FAST` mode, no contours or classification when not needed, low resolution, throttle calls. Default min face size 0.1 of image width. Images at least 480×360, faces at least 100×100 px — same source. Page says API 23 in the body, and 21 in a summary.
- Android background rule: a UID in the background for a while cannot open the camera and gets an immediate error, since Android P — [AOSP commit, 2018](https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_av/+/a453d0d278ee916bf68c98093fcfd9fa924ca454).
- A background-started FGS cannot use the camera — [Android 11: FGS](https://developer.android.com/about/versions/11/privacy/foreground-services).
- Power trend only: higher preview resolution and higher camera FPS strongly raise power — [arXiv 2011.13075](https://arxiv.org/pdf/2011.13075).
- S10e front camera: 10 MP, f/1.9, 26 mm; battery 3100 mAh — [GSMArena](https://www.gsmarena.com/samsung_galaxy_s10e-9537.php).

### Inferences
- Bind with `bindToLifecycle` to the activity. The camera stops on `onStop`, so no FGS, and no background worries.
- Throttle in the analyzer: drop frames unless 500–1000 ms have passed, so 1–2 detections per second. Note this keeps the sensor running. For real savings, unbind between samples (for example 1 s on, 4 s off when idle) and accept the rebind cost. Measure both.
- A cheap gate before ML: compare the Y plane mean difference on a tiny downsample and only run face detection on motion. Most frames then cost almost nothing.
- Range estimate: a 26 mm-equivalent lens covers roughly 69° horizontally, so at 1.5 m a head is around 8 % of frame width, or about 50 px at 640×480. This is below the 100 px guidance and the 0.1 default, so set `setMinFaceSize` near 0.05 and test at real desk distance, or ask for 1280×720 and accept the cost.
- The Android 12 green camera dot will be shown permanently while sampling. It is a pixel-level static element (burn-in) and also a visible privacy signal in the home. Duty cycling helps with both.
- The Quick Settings camera or mic toggles on Android 12 can block access silently, so the app needs to detect blank frames and show a status.
- Fall-back: camera failure must not break the eyes. Treat presence as an optional input.

### Gaps
- No measured mW or %/hour for CameraX at 640×480 on Exynos 9820 or Snapdragon 855. Needs a test with `dumpsys batterystats` or a USB power meter.
- `ImageAnalysis.Builder.setTargetFrameRate` and `ResolutionSelector` status could not be read from the reference page (fetch truncated). Check the 1.6.2 API. `setTargetResolution` is believed deprecated in favour of `ResolutionSelector`, unverified.
- Default analysis resolution is believed to be 640×480, unverified here.
- The S10e chip differs by region (Exynos 9820 EMEA/LATAM, Snapdragon 855 USA/China) — [GSMArena](https://www.gsmarena.com/samsung_galaxy_s10e-9537.php). Thermal and NPU behaviour will differ.

## Shake detection with SensorManager in lifecycle-aware Compose

### Takeaway
Simple and safe: `TYPE_LINEAR_ACCELERATION`, registered only while resumed, with a fallback to `TYPE_ACCELEROMETER`. No permission is required, and 200 Hz is far beyond what is needed.

### Cited Findings
- Linear acceleration is acceleration minus gravity, in m/s², on the same axes as the accelerometer. It may be hardware or software. AOSP's motion sensors need a gyroscope, so check for null. It has an offset that may need calibration — [Android: motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion).
- Android 12 rate limit for target 31+: 200 Hz through `registerListener` on accelerometer, gyroscope and magnetometer. Higher needs `HIGH_SAMPLING_RATE_SENSORS`. Turning off the microphone toggle always rate-limits these sensors — [Android: sensors overview](https://developer.android.com/guide/topics/sensors/sensors_overview).
- Best practice: unregister in `onPause`, since the system does not stop sensors when the screen turns off. Keep `onSensorChanged` fast. Pick the largest workable delay. Background apps do not get sensor events on Android 9+ — same source.

### Inferences
Sketch, general knowledge, not from a page: use `LifecycleResumeEffect` from `lifecycle-runtime-compose`, or `DisposableEffect(lifecycleOwner)` with an observer.

```kotlin
@Composable
fun ShakeEffect(onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    LifecycleResumeEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = ShakeDetector { currentOnShake() } // pure, unit-testable
        sensor?.let { sm.registerListener(detector, it, SensorManager.SENSOR_DELAY_GAME) }
        onPauseOrDispose { sm.unregisterListener(detector) }
    }
}
```

- `ShakeDetector` starting values, to be tuned: magnitude over about 12 m/s² counted twice or more within 500 ms, then a 1–2 s cooldown. If falling back to the raw accelerometer, subtract gravity with a low-pass filter first.
- Keep it as a pure class taking `(x, y, z, tNanos)` so it can be unit tested with recorded traces.
- A phone on a desk stand: table knocks produce short spikes, so require sign reversals across peaks.
- Do not use shake as an unlock or as a kill switch alone, as a child can shake the phone. Use it as a fun trigger only, unless paired with a confirmation.

### Gaps
- The docs do not say whether `TYPE_LINEAR_ACCELERATION` is covered by the rate limit. It is irrelevant at game rate.
- `LifecycleResumeEffect` was not checked against the current lifecycle docs.

## Compose Canvas animation, testing, and current toolchain versions

### Takeaway
Drive the eyes from a single time state written in a `withFrameNanos` loop and read it only inside the draw lambda, so nothing recomposes per frame. Keep eye geometry as a pure function of time so most tests need no clock. Use Roborazzi rather than Paparazzi. As of 1 November 2026: AGP 9.5.0, Gradle 9.6, JDK 17, Kotlin 2.4.21, Compose BOM 2026.09.00 (UI 1.12.1, Material3 1.4.0), compileSdk 37. minSdk 31 is well above all library floors found.

### Cited Findings

**Versions (fresh, checked 2026-11-01)**
| Item | Version | Source |
|---|---|---|
| AGP | 9.5.0 (Oct 2026, page updated 2026-10-28), 9.6 in preview | [AGP notes](https://developer.android.com/build/releases/gradle-plugin) |
| Gradle | 9.6.0 min and default | same |
| JDK | 17 min and default | same |
| Build Tools | 36.0.0 min and default | same |
| Max compileSdk | API 37 | same |
| Kotlin | 2.4.21 (2026-10-08). 2.5.0 planned December 2026 | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| Compose BOM | 2026.09.00, UI and animation 1.12.1, Material3 1.4.0. Newest listed on the mapping page; matched by the fetch model, re-check on upgrade | [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) |
| Compose | 1.12.1 stable, 1.13.0-beta02 (page updated 2026-10-21) | [Compose releases](https://developer.android.com/jetpack/androidx/releases/compose) |
| CameraX | 1.6.2 stable | [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera) |
| Roborazzi | 1.77.0, 22 Oct (year not shown) | [Roborazzi releases](https://github.com/takahirom/roborazzi/releases) |
| Paparazzi | 2.0.0-alpha05.1, 28 Sep (year not shown), Java 21+ for consumers | [Paparazzi releases](https://github.com/cashapp/paparazzi/releases) |
| Android Studio | Quail 4, 2026.1.4 Patch 1 seen on the download page | [Android Studio](https://developer.android.com/studio?via=geeksmint) |

- Android 17 is API 37 — [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk?hl=ja). A secondary article says stable shipped 2026-06-16 and that Play requires target 36 from 2026-08-31 — [issoh.co.jp](https://www.issoh.co.jp/md/tech/10993). Play rules do not apply to this sideloaded app.

**Animation**
- Compose performance guidance: pass frequently changing state through lambdas, and read it in layout or draw so composition is skipped. Moving a colour read into `drawBehind` goes straight to the draw phase — [Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices).
- Community guidance: use `LaunchedEffect` with `withFrameNanos`, not `awaitFrame`, for time-based state — [kotlinlang Slack archive](https://slack-chats.kotlinlang.org/t/520574/i-am-just-trying-to-get-my-head-around-compose-desktop-how-w). A loop writing state with `drawBehind` reading it is a suggested shape — [kotlinlang Slack archive](https://slack-chats.kotlinlang.org/t/16314069/hello-what-is-the-correct-way-to-animate-some-state-e-g-anim?t=v2).
- `withInfiniteAnimationFrameNanos` is the variant that applies `InfiniteAnimationPolicy` — [composables.com API copy](https://composables.com/docs/androidx.compose.animation/animation-core/functions/withInfiniteAnimationFrameNanos).

**Testing**
- `mainClock.autoAdvance = false`, then `advanceTimeByFrame()` / `advanceTimeBy(ms)`. `MainTestClock` drives recomposition, animation and gestures, not measure or draw. `advanceTimeUntil` conditions must read Compose state — [Compose test synchronization](https://developer.android.com/develop/ui/compose/testing/synchronization).
- With auto-advance on, an infinite animation prevents idleness, so synchronised calls can hang. That point is general knowledge noted in the fetch, not the page itself.
- `runWithoutImplicitWait` for frame loops requires `ui-test` 1.12.0-alpha03+ — same source.
- Roborazzi 1.68.0 added support for the Compose Testing v2 APIs, which are the default in Compose 1.11. v1 rules are deprecated. The feature is marked experimental — [Roborazzi releases](https://github.com/takahirom/roborazzi/releases).
- Google also has a Compose Preview Screenshot Testing tool — [Android Studio docs](https://developer.android.com/studio/preview/compose-screenshot-testing?hl=hi).

### Inferences
Pattern (general knowledge, consistent with the sources):

```kotlin
class EyeClock { var nanos by mutableLongStateOf(0L) }

@Composable
fun Eyes(state: EyeState, clock: EyeClock = rememberRunningClock()) {
    Canvas(Modifier.fillMaxSize()) {
        // Only this lambda reads clock.nanos: draw phase, no recomposition
        drawEyes(eyeGeometry(state, clock.nanos))
    }
}
// rememberRunningClock(): LaunchedEffect + withInfiniteAnimationFrameNanos { clock.nanos = it }
```

- `eyeGeometry(state, tNanos)` is a pure function. Unit test it, and use it for blink schedules and pixel shift.
- Use `Animatable` for event-driven transitions such as mood changes or looking at a face. Read `.value` only in the draw lambda. Avoid `rememberInfiniteTransition` for the master clock, as manual time is easier to test and pause.
- Pre-allocate `Path` objects, or use `drawWithCache`, to avoid per-frame allocation.
- Frame economy: an idle desk device does not need 60 FPS all day. Run 60 FPS during blinks and movement, and stop the loop between events by suspending on a `delay` or a channel. It reduces heat and GPU wakeups. Note `withFrameNanos` is display-bound. The S10e's 60 Hz panel is general knowledge, as GSMArena's page text did not list it.
- Measure on a release build with R8 and a baseline profile. Debug Compose is not representative.
- Testing plan:
  - Pure geometry and blink schedule tests in JVM unit tests.
  - Roborazzi + Robolectric with an injected fake `EyeClock` set to fixed nanos: no clock, no idle problem, deterministic golden images per emotion and phase.
  - For real `withFrameNanos` flow, `mainClock.autoAdvance = false` and step frames.
  - Paparazzi is alpha at its newest release and requires Java 21, so it is not the first choice.
- Pin the Compose compiler via the `org.jetbrains.kotlin.plugin.compose` plugin matching Kotlin. AGP 9's built-in Kotlin has a migration guide — [migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin); I did not read it.
- SDK choice: `compileSdk = 37`, `minSdk = 31`. Since behaviour-change gates also need the device OS version, `targetSdk` above 31 changes little on this device. Pick 36 for lint and library alignment or 31 for the fewest surprises. If Termux-like exec from app storage were ever needed, a target under 29 would be required, which is not worth it.
- Lint will warn about `MANAGE_EXTERNAL_STORAGE` and similar. Those are expected for sideload.

### Gaps
- The minimum Kotlin or AGP for BOM 2026.09.00 was not stated on the pages read. AGP 9.5 + Kotlin 2.4.21 + Compose 1.12.1 should be confirmed with a first `assembleDebug`.
- Compose libraries' exact minSdk was not stated. Nothing found suggests anything above 31.
- Current `Robolectric` version for Roborazzi on compileSdk 37 is not stated. The 1.75.0 benchmark used Robolectric 4.14.1 and Gradle 9.5.1, not a compatibility statement.
- No authoritative doc compares `rememberInfiniteTransition` with a manual loop.
- Whether Roborazzi's and Paparazzi's release dates are 2026 is an inference from version order. An aggregator shows Roborazzi 1.66.0 at 2026-07-06 — [klibs.io](https://klibs.io/project/takahirom/roborazzi).

## Obsidian vault storage on Android 12

### Takeaway
Yes, `/sdcard/Documents/...` is reachable from a sideloaded app. `MANAGE_EXTERNAL_STORAGE` is the practical choice, giving normal `java.io.File` paths. SAF also works and is the choice for a narrower grant, but it is slower and URI-based. Obsidian itself uses `Documents/` by default, so vault sharing works. Conflict handling is the real design issue.

### Cited Findings
- `MANAGE_EXTERNAL_STORAGE`: declare, check `Environment.isExternalStorageManager()`, send the user to the settings page. Grants read and write on shared storage with direct paths. Other apps' `Android/data/` stays off-limits — [Android: all files access](https://developer.android.com/training/data-storage/manage-all-files).
- For testing: `adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow` — same source. Play's restriction applies to Play only. So on sideload it is simply a toggle.
- `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` opens the general list. The per-app `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` is believed to exist but was not confirmed on the page.
- SAF: `ACTION_OPEN_DOCUMENT_TREE` grants recursive access to a chosen folder, `takePersistableUriPermission` keeps it across reboots. On API 30+ the tree picker refuses the storage root, SD roots, `Download`, and `Android/data` and `obb`. `Documents` is not on the refused list. Iterating many files can be slow — [Android: SAF](https://developer.android.com/training/data-storage/shared/documents-files).
- Without these, the docs route non-media files to SAF and do not document direct path access to other apps' files in `Documents/` — [Android: media access](https://developer.android.com/training/data-storage/shared/media). Direct paths can be up to twice as slow for random access — same source.
- Obsidian's legacy help (2021) says the Android vault lives in `Documents/Obsidian`, and that scoped storage gives no practical way to watch for external changes — [Obsidian help mirror](https://demo-obsidian.owenyoung.com/Obsidian/Android%20app/). Stale, verify. A guide confirms `/storage/emulated/0/Documents/Obsidian` — [Stephan Miller](https://www.stephanmiller.com/sync-obsidian-android-free/).
- Forum reports say Obsidian mobile accepts internal `Documents` reliably, while SD-card vaults failed — [Obsidian forum](https://forum.obsidian.md/t/bug-cant-write-to-sd-card-external-storage/20694). Old, 2021-era.
- The Kanban plugin stores boards as markdown. It is "looking for new maintainers", with 554 open issues at fetch time — [obsidian-kanban](https://github.com/mgmeyers/obsidian-kanban).
- Termux needs storage permission for commands that touch `/sdcard` — [Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).

### Inferences
- Use `MANAGE_EXTERNAL_STORAGE` for vault reads, writes and JGit-style file tree work. Reserve SAF as a fallback only.
- The vault path must be configurable. Do not hardcode.
- Writing: temp file in the same directory plus atomic rename, then re-read before patching. Keep a content hash or mtime check to detect external edits.
- Obsidian may be editing the same file. Keep writes small and line-oriented on the kanban file and never rewrite on a stale read.
- Watching: `FileObserver` through the shared storage layer is uncertain. Plan for an mtime poll while the terminal is open and on `onResume`.
- Obsidian may not show external changes at once, per the old statement. Test it and note that a re-open may be needed.
- Keep a git or copy-based backup outside the app, given the factory-reset escape path above.

### Gaps
- No current (2025–2026) statement on whether Obsidian for Android detects external changes.
- `FileObserver`/inotify reliability on `/sdcard` on Android 12 was not researched.
- Exact kanban markdown format was not captured. Open a sample board from the phone and pin the format in tests.

## Self-modification: realistic options, and is OpenCode runnable on the phone?

### Takeaway
A compiled APK cannot patch itself the way the HTML page did. The APK is signed and the app's writable directories cannot hold executables. Keep the shell in Kotlin, and put changeable behaviour in **data and interpreted code**: a WebView or a script engine in a plugin folder managed by git. OpenCode **is** runnable on the phone, but only through unofficial Termux ports. Treat it as an optional, separately supervised helper with a local HTTP API, not a core dependency. On-device Compose builds are not a plan.

### Cited Findings
- Apps targeting Android 10+ cannot `execve()` files in the writable app home directory. The page says apps should load only binary code embedded in the APK — [Android 10 behaviour changes](https://developer.android.com/about/versions/10/behavior-changes-10).
- Files in `jniLibs`, as `lib<name>.so`, run from `nativeLibraryDir` — general knowledge noted in the fetch, not the page.
- Apps targeting Android 14+ must mark dynamically loaded code read-only, and Google recommends avoiding dynamic code loading — [Android 14 behaviour changes](https://developer.android.com/about/versions/14/behavior-changes-14). This runs on Android 14 only, so not here, though it applies if the app ever moves.
- Zipline runs Kotlin/JS inside QuickJS on Android, hot-loading modules with Ed25519 signing. It has unstable APIs, needs matching versions for 0.x, and "no sandbox", so not for untrusted code — [Zipline](https://github.com/cashapp/zipline).

**Git**
- JGit 6+ needs Java 11, and a newer README has a Java 17 badge — [JGit README](https://gerrit.googlesource.com/jgit/+show/HEAD/README.md). JGit's Android history shows `java.nio.file` gaps — [JGit list, 2014](https://eclipse.org/lists/jgit-dev/msg02550.html); [SGit issue, 2016](https://github.com/sheimi/SGit/issues/182). These are old.
- Community fork `ajgit` targets Android API 26+ — [ajgit](https://github.com/msmt2017/ajgit). Google's `nio` desugaring is a further path — [Android blog, 2023](https://android-developers.googleblog.com/2023/02/api-desugaring-supporting-android-13-and-java-nio.html?debug=true). No current statement of a tested JGit version on Android was found.

**Termux**
- Termux latest listed is v0.118.3. Prefer F-Droid or GitHub builds. Play build is experimental. Termux and plugins must share one signing key, so sources cannot be mixed. "Termux may be unstable on Android 12+", due to the phantom limit of 32 and CPU kills — [termux-app](https://github.com/termux/termux-app).
- `RUN_COMMAND`: needs `com.termux.permission.RUN_COMMAND`, user grant, `allow-external-apps=true`, package visibility handling on target 30+. Results come back via `PendingIntent`, with stdout/stderr capped at 100 KB combined. Intent extras are capped at about 500 KB — [Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).
- Phantom killer on Android 12: default 32 ([AOSP 12 ActivityManagerConstants](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/am/ActivityManagerConstants.java); [PhantomProcessList](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/am/PhantomProcessList.java)). Key `max_phantom_processes` in namespace `activity_manager` can be raised by adb with `device_config`, after `set_sync_disabled_for_tests persistent` — [Phantom Process Killer](https://github.com/atamshkai/Phantom-Process-Killer); [Android-Docs](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md).
- GMS can reset the value, per a Reddit comment, and the simpler `settings_enable_monitor_phantom_procs` toggle is only for 12L/13+ — [Andronix](https://docs.andronix.app/android-12/andronix-on-android-12-and-beyond); [r/tasker](https://redlib.hackliberty.org/r/tasker/comments/z546at/having_trouble_keeping_adb_wifi_alive/j17dzu1/?context=3). OEM skins may not honour the toggle — [ivonblog](https://ivonblog.com/en-us/posts/fix-termux-signal9-error/).

**OpenCode**
- Official docs list install script, npm, Homebrew, pacman and Windows options, and do not mention Android or Termux. Homebrew tracks V1 and pacman OpenCode 2 — [OpenCode docs](https://opencode.ai/docs/). So a 2.x line is out, and any older guide may describe 1.x.
- Upstream issue: npm install fails on Termux because `opencode-android-arm64` is missing — [anomalyco/opencode #12515](https://github.com/anomalyco/opencode/issues/12515).
- `bd-loser/opencode-bionic`: native Termux build via an apt repo, tracking upstream 2.2.5 stable. Patched Bun needs API 29+, aarch64 only. Bundles rebuilt opentui and bun-pty — [opencode-bionic](https://github.com/bd-loser/opencode-bionic).
- Other routes: glibc-runner in Termux — [0xScodyx](https://github.com/0xScodyx/opencode-termux); proot Debian/Ubuntu — [Brainshock246](https://github.com/Brainshock246/opencode-binaries). One native build reports testing on a single Android 14 phone — [walidrbh2000-maker](https://github.com/walidrbh2000-maker/opencode-termux). None is official, and none reports Android 12 tests in what I read.
- `opencode serve`: default `127.0.0.1:4096`, basic auth through `OPENCODE_SERVER_PASSWORD`, `POST /session`, `POST /session/:id/message`, `POST /session/:id/prompt_async`, SSE on `GET /event`, OpenAPI at `/doc` — [OpenCode server](https://opencode.ai/docs/server/).
- Local LLM: provider block with `npm: "@ai-sdk/openai-compatible"` and `baseURL`, for example llama.cpp at `http://127.0.0.1:8080/v1` — [OpenCode providers](https://opencode.ai/docs/providers/).

**On-device build**
- AndroidIDE repo was archived on 2024-10-18 and says it is no longer maintained. It needed AGP 7.2+ and JDK 11 or 17 — [AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE).
- Forks exist ([Android Code Studio](https://github.com/hutiang/android-code-studio)), and the Code on the Go product claims full Gradle on the phone — [PeerPush listing, marketing](https://peerpush.com/p/code-on-the-go). No source confirms Compose builds. An April 2026 Reddit thread was sceptical — [Reddit snapshot](https://reddit.sentinel-team.org/posts/1soxcog/snapshots/2026-04-18T14%3A08%3A28.299243Z).

### Inferences
| Approach | What can change at runtime | Risk | Verdict |
|---|---|---|---|
| Config, prompts, tool manifests, kanban (data) | Behaviour, persona, schedules | Low | **Do first** |
| WebView panels from a git-managed folder | UI and logic, as in the old HTML app | Medium, needs a JS bridge allowlist | **Best match for "patches itself"** |
| JS engine (QuickJS binding) or Zipline | Skills and logic | Medium. Zipline's Kotlin/JS compile needs a desktop or Termux, unstable versions | Good for skills |
| `DexClassLoader` plugins | Kotlin code | High, needs a compile step, an app-crash risk, full app privileges | Later, if at all |
| Termux + OpenCode | Edits files, runs tools, patches plugin folder or repo | Medium to high, fragile, unofficial | **Optional power layer** |
| Laptop or CI builds APK, phone installs | Full native app | Low, needs another machine | **Real path for native changes** |
| On-device Gradle/Compose (AndroidIDE and forks) | Full native app | Very high, stale, unverified | No |

- Native changes: the phone-side agent edits a repo, pushes to the laptop or CI, and a signed APK comes back for install. `REQUEST_INSTALL_PACKAGES` or a Device Owner `PackageInstaller` session could do it. With a testOnly build, updates need adb (see the first section). This tension should be decided up front.
- Plugin folder design: `filesDir/plugins/<id>/` with `manifest.json`, permission list, and version. Atomic swap of an "active" pointer, and automatic rollback when a plugin crashes twice. The app's compiled shell must include a "safe mode" that ignores all plugins, reachable through the parent PIN.
- For git, the lowest-risk path is `git` in Termux through `RUN_COMMAND`, avoiding JGit's Android gaps, or simple zip snapshots of the plugin folder if Termux is absent. JGit needs a test on minSdk 31, where `java.nio.file` is natively available from API 26 by general knowledge, so the old failure reports may no longer apply. D8 handling of its bytecode is untested.
- OpenCode on this phone:
  - Feasible on paper: API 31 ≥ 29, aarch64, Bun patched. Not proven on Android 12 or One UI.
  - Before installing: raise the phantom limit via adb, exempt Termux from sleeping and battery optimisation, and run its `wake-lock`.
  - Do not stack it onto a large local model in the same RAM budget without measuring. The S10e has 6 or 8 GB — [GSMArena](https://www.gsmarena.com/samsung_galaxy_s10e-9537.php).
  - Always set `OPENCODE_SERVER_PASSWORD`. Loopback ports are reachable by every local app, by general knowledge, and `/session/:id/shell` is remote code execution by design.
  - The Kotlin app should talk to it by HTTP and SSE, with a health check and clean degrade when it is absent. Starting it comes through `RUN_COMMAND` or Termux:Boot.
- Own-process alternative: bundling helpers in `jniLibs` as `lib*.so` is allowed under the W^X rule, for instance a `llama-server` binary. This gives a stable local backend without Termux. Coordination with the LLM research is needed.
- Any self-modification path must keep the child lock, the kill switch and the safe mode outside the modifiable layer, in the signed APK.

### Gaps
- Actual run of any OpenCode port on Android 12/One UI 4.1, its idle RAM, and its survival under the phantom killer: not verified.
- Whether Termux itself still has its exec exemption by targeting a low SDK was not stated on the page read. General knowledge says the F-Droid build targets SDK 28 for this reason.
- `opencode-bionic` is a third-party binary source with an apt key. Not audited.
- `Code on the Go` Compose capability is unknown. Its own docs were not surfaced.
- No hands-on data on Zipline's fit with Kotlin 2.4.21: its page lists Kotlin 2.3.0, so a version mismatch is possible.
