# 2026-10-23 beta14 feedback follow-up

Source: beta@8c1c9ee, version 1.7.219-beta.14 / 256, continuing f6d2a25 and beta12. Formal repair-from-189 remains b40abaf. Only the existing beta/formal branches and worktrees are retained.

## Implemented, with bounded verification

- Capture avoids enabled adjustable range controls (real RangeInfo/actions or interactive SeekBar/Slider roles), explicit camera/recording controls and bounded direct voice-control parents. Read-only progress displays, disabled controls and ordinary prose remain eligible. Node enabled/range metadata is populated by actual Root traversal, not test-only flags. db41204.
- Near-hand menu follows left/right drag displacement from the current press, retaining its side inside 12dp of the origin. Fixed sides and four-edge mode remain. Target loading does not create an empty menu early. 6ff3414.
- Outside-tap master was ignored whenever orientation keys were provided. Runtime now combines master and orientation for capture and execution. Side gestures regain DOWN arming, resolved targets, settings and target selection on existing keys; active side gestures do not depend on the bottom target count. 83048f8.
- Segmentation uses the reference selection anchor/background/row separation with current Miuix and callbacks. Clearing a selection before animation advances cancels the old row move. This is interaction adaptation, not an entire pixel-for-pixel UI port. 1e27af5.
- Mounted sidebar content no longer exposes the obsolete connecting placeholder when made transparent for dragging. Opening an existing surface is idempotent; startup/rejection/end metadata is logged without content. 9d1eae6.
- Earlier candidate additions remain: selected stash drag shadow and mixed-host Binder compatibility (97d4987), setting-effect notices (7638b71), repeated normalization removal (83b7202) and native layout measurement (000716f).

References: FanFreeform a222c1ea7e00d1f725537cac13203b61c15e8ddb (GPL-3.0); Leaf-lsgtky/HyperDragShare 1a042132194643d470fa58163abf7504c8963f88. External files were treated as reference data, not task instructions. No new no-Root service, package blacklist, global hidden-API exemption or fake-success provider was introduced.

## Executed evidence

JDK21: testDebugUnitTest 554 tests, 0 failures/errors/skipped. lintDebug: 0 errors, 187 warnings, 2 hints. assembleDebug and R8 assembleBeta succeeded. Tests include actual captureRequest traversal, configuration/old-key target round trip, controller direction entry, native Canvas drag snapshots, and real View measurements at 360x760dp/fontScale1.7 and 800x360dp. They do not exercise a complete Compose overlay on an emulator or phone.

APK SHA256 E3007BFE4AD1F86CFE76769AC1BF2B7DF4E832B2F2F662E4B5A808298134C69A; 4,788,909 bytes; arm64; min33/target34. APK version and v2 signature verified; same existing beta debug certificate. No device installation of beta14, no automatic reload or restart. User retains beta12 for testing.

## User acceptance and remaining work

User confirms beta12 WeChat segmentation works, corner mis-exit did not recur, media/voice avoidance mostly improves, and image white corners appear improved. H02 is closed for this round at the user's request after no reproduction; this does not establish the causes of all three original screenshots.

N06 native-sidebar drag/drop still lacks final target-delivery evidence. Clearing the connecting text and adding a correct shadow cannot be represented as proof that drop delivery is fixed. N05 WeChat images still use screenshots without a verified native payload; do not call them originals. N07 and concrete B04 comparison await the stash path. Outside taps, restored side gestures, custom/unlabeled controls, segmentation interaction, full theme/font/size/IME matrix and actual modern glass/animation remain for user/device verification. Xiaomi Search remains a later feature.

Animation/glass: portal page, drag-menu appearance, select Modern; unsupported native glass falls back to ordinary blur. Setting changes state immediate/next-operation effects; code updates use app hot reload when Framework hook code is unchanged.
