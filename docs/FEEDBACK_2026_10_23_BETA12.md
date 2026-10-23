# MMGG 1.7.219-beta.12

## Version and evidence

- Source: beta `9b32ae8a13efdd09518dec521358974b95337611`, based on beta.10 `f6d2a25`.
- versionCode 254; arm64-v8a; minSdk 33, targetSdk 34.
- APK SHA256: `4ED9C7F5963A224A35879BF1ACB510D125B37F1DD28C95AF6EBED7D9E65EACBA`.
- Existing beta certificate retained; APK Signature Scheme v2 verification passed.
- JDK 21: `:app:testDebugUnitTest :app:lintDebug :app:assembleBeta` succeeded with R8.
- 529 tests, 0 failures/errors/skips. Lint: 0 errors, 187 warnings, 2 hints.
- Formal branch `repair-from-189` remains at `b40abaf`; no new permanent branch or worktree.

## Changes

| Commit | Scope |
|---|---|
| b2235bd | Stop failed touch sampling, preserve diagnostics, isolate reader callback generations, shared export names |
| f676b4f | Retain corner capture in native-input mode so the existing DOWN takeover can run |
| 63f49d2 | Native host window drag bridge for embedded sidebar stash, input transfer, callbacks and temporary URI grants; remove collapsed handle |
| 73e6c8c | Hold-control semantics, module-UID app classification, independent side-dwell setting |
| 495f90a | Restore menu position settings, bottom trigger priority, 250–3000 ms/default 600, current image payload ownership, OCR text takeover |
| 369bc3f | Selection action placement and reserved top space; preserve existing segmentation actions |
| e177036 | Modern selection animation and native glass adapter with fallback |
| d438ed1 | Remove redundant standalone stash branches |
| 9b32ae8 | beta.12 metadata and test-only Xposed runtime dependency |

The bottom menu failure had a concrete overlap: a central 110 dp direct-drop area returned before the approximately 48 dp bottom menu trigger. Only the active bottom trigger band now takes precedence. Insets and safe layout bounds remain.

WeChat's accepted OCR text panel previously turned back into an image when the same press moved. It now transfers recognized text only after that press's text panel was accepted; pending or unaccepted OCR keeps the existing image behavior. Native Portal images remain preferred. This does not establish the cause of the separately reported failure to trigger the first text panel.

Embedded stash drags now transfer the touch stream to the verified host and start system drag from its real SurfaceView window. Module UID, surface ownership and temporary image grants remain checked. Fake-host tests cover rejection, cancellation, late results, lease death and grant cleanup. A successful system drag result is not proof that another app sent its content.

Configuration keys and stash schema are retained. Legacy zero duration migrates to 600 ms; legitimate custom settings remain. System accessibility services and a no-Root mode were not introduced. Test replacements are not enabled in beta production paths.

## External references

- Segmentation layout: Leaf-lsgtky/HyperDragShare `1a042132194643d470fa58163abf7504c8963f88`.
- SelectToSpeak candidates: peacejoyi/Assists `867cff8f9bd640d346109317f027f6b000310dc6` (GPLv3), zhongpei/jev-chat-jarvis `3a5e6b5be15f13f4513a4d9e92c4621fb3e3883f` (MIT). These register ordinary AccessibilityService components; adding their service name does not change this project's Root UiAutomation identity. No service source was copied.
- Glass API context: ColdP/HyperChanger `5b751b7bacc5a92b089be1139c1217430cb7b1f8` (AGPLv3), reviewed without copying source blocks. The adapter independently invokes public system Miuix utilities and system presets, without global hidden-API exemptions or filter-table changes.

## Validation boundaries and remaining tasks

The final APK was **not installed or tested on the device**: the device disconnected before final installation. Earlier beta.11 generated-text screenshots do not verify the later beta.12 layout, bridge or gesture changes.

A real, UI-free metadata probe confirmed that the device's two system glass presets contain 42 values and that the APIs can load through the system jar loader. It does not prove cross-window permission or rendered glass. Failed setup retains the previous window blur and Miuix theme surface.

Still requiring device evidence: WeChat first-panel failure and text/image drag, native sidebar transfer and target URI reads/default drag shadow, bottom menu invocation, corner tap/non-fan replay and navigation, voice/video controls across apps, actual glass rendering, and the complete theme/font/size interaction matrix. Three original stuck-overlay screenshots and white image corners were not independently reproduced or proven to share one cause.

Xiaomi Search integration remains a later feature as requested. Cleanup is scoped to demonstrated duplication and dead paths; no measured CPU/frame-time improvement or whole-project absence of regressions is claimed. External issues were not closed or commented on.

Install by covering the existing same-certificate beta without clearing data, then use the existing application hot reload to load Portal/SystemUI/sidebar code. Android framework hooks were not changed in this batch; a full device reboot is not the default requirement.
