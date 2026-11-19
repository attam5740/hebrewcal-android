# Widget Chitas pill fix

## Change
HebrewDateWidget.kt, nested pill Column: removed all three standalone Spacers (8dp leading, two 4dp separators). The Column now has at most 3 children (one Text per visible pill). The gaps became `GlanceModifier.padding(top = ...)` on each pill, with the same actionStartActivity params, text styles and gating as before.

## Spacing per combination (top padding: Tehillim / Parsha / Chitas)
- T only: 8 / - / -
- P only: - / 8 / -
- C only: - / - / 8
- T+P: 8 / 4 / -
- T+C: 8 / - / 4
- P+C: - / 8 / 4
- T+P+C: 8 / 4 / 4
Computed as: first visible pill gets 8dp, every pill after another visible pill gets 4dp. Identical to the old Spacer layout.

## Confidence
Moderate (about 65-70%) that this fixes it. The child-count theory fits the symptoms (last child lost, no error, notification fine), but I could not reproduce on a device. Caveat: the old count was 6 in the nested Column, which is below the 10 cited in the code comment, so if the limit is really 10 the theory is weaker. The change is harmless if wrong, and it lowers the child count either way. If Chitas is still missing, suspect other causes (e.g. the outer Column's own child count, since the nested Column counts as a child there; or RemoteViews total size).

## Not verified
- On-device rendering. Tap target now includes the top padding (padding applied before clickable), so each pill's touch area is about 4-8dp taller.
- Build: testDebugUnitTest + assembleDebug succeed; 171 tests, 0 failures.
