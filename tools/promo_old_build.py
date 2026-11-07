"""A capture-only patch for an old commit checked out in another work folder (git worktree), for the
"before" pictures of docs/promo/2026-11 (X-026: the rain button as it was). Never committed there.

    git worktree add --detach ../PocketGarden-promo-old 979e699^
    python tools/promo_old_build.py ../PocketGarden-promo-old
    python tools/promo_old_build.py ../PocketGarden-promo-old --bird   # X-012: 9fb5fc1^, the bird before the fix
    (cd ../PocketGarden-promo-old && ./gradlew assembleDebug)

With it, the showcase's hide_ui hides only the figures at the top, the tool bar and the debug
button (the rest of the controls stay), and the window fills a wide display. The showcase garden is
this checkout's, so that the old pictures show the same garden as the new ones. --bird also lets the
receiver call a bird onto the ground (--es bird 5,4) and load --es garden birdcrops, as this checkout does.
"""
import re
import shutil
import sys
from pathlib import Path

root = str(Path(sys.argv[1]).resolve())
cur = str(Path(__file__).resolve().parent.parent)
shutil.copy(cur + r"\app\src\debug\java\com\studioguu\miniaturegarden\debug\ShowcaseGarden.kt",
            root + r"\app\src\debug\java\com\studioguu\miniaturegarden\debug\ShowcaseGarden.kt")
p = root + r"\app\src\main\java\com\studioguu\miniaturegarden\ui\GardenScreen.kt"
s = open(p, encoding="utf-8").read()
def rep(a, b):
    global s
    assert a in s, a[:60]
    s = s.replace(a, b, 1)
rep("""        if (hideUi) bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { if (hideUi) bars?.show(WindowInsetsCompat.Type.systemBars()) }""",
"""        val activity = context.findActivity()
        val locked = activity?.requestedOrientation
        if (hideUi) {
            bars?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.decorView?.postDelayed({ bars?.hide(WindowInsetsCompat.Type.systemBars()) }, 800)
        }
        onDispose {
            if (hideUi) {
                bars?.show(WindowInsetsCompat.Type.systemBars())
                if (locked != null) activity.requestedOrientation = locked
            }
        }""")
# The controls stay but the figures, the tool bar and the debug button (not for the bird: nothing but the garden).
if "--bird" not in sys.argv:
    s = re.sub(r"\n        if \(hideUi\) \{\n            // Nothing but the garden\.\n        \} else if \(cameraMode\) \{", "\n        if (cameraMode) {", s, count=1)
    assert "if (cameraMode) {" in s
    rep("""                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp),
                onWeatherTap""", """                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp).alpha(if (hideUi) 0f else 1f),
                onWeatherTap""")
    rep("""                if (BuildConfig.DEBUG) {""", """                if (BuildConfig.DEBUG && !hideUi) {""")
    rep("""                    .align(Alignment.BottomCenter)""", """                    .align(Alignment.BottomCenter)
                    .alpha(if (hideUi) 0f else 1f)""")
if "import androidx.compose.ui.draw.alpha\n" not in s:
    s = s.replace("import androidx.compose.ui.Modifier\n", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.draw.alpha\n", 1)
open(p, "w", encoding="utf-8").write(s)
if "--bird" in sys.argv:
    p = root + r"\app\src\main\java\com\studioguu\miniaturegarden\ui\debug\Showcase.kt"
    s = open(p, encoding="utf-8").read()
    rep("""    val rainGlint: Float? = null,
) {""", """    val rainGlint: Float? = null,
    val bird: String? = null,
    val birdAt: Long = 0L,
) {""")
    found = re.search(r"        val allowed = creatures\?\.let \{ kinds -> visits\.filter \{ it\.creature in kinds \} \} \?: visits\n", s)
    assert found, "visitors"
    s = s.replace(found.group(0), """        val kept = creatures?.let { kinds -> visits.filter { it.creature in kinds } } ?: visits
        val called = bird?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }
            ?.let { CreatureVisit(VisitRule.BIRD_FENCE, GridCell(it[0], it[1]), null, birdAt, birdAt + 120_000L) }
        val allowed = if (called != null) kept.filter { it.creature != Creature.BIRD } + called else kept
""")
    old = """        _showcase.value = showcase?.let { if (it.butterfly != null) it.copy(butterflyAt = now) else it }"""
    rep(old, old + "\n            ?.let { if (it.bird != null) it.copy(birdAt = now) else it }")
    open(p, "w", encoding="utf-8").write(s)
    p = root + r"\app\src\debug\java\com\studioguu\miniaturegarden\debug\ShowcaseReceiver.kt"
    s = open(p, encoding="utf-8").read()
    old = """                rainGlint = intent.takeIf { it.hasExtra("rain_glint") }?.getFloatExtra("rain_glint", 0f),\n"""
    rep(old, old + """                bird = intent.getStringExtra("bird"),\n""")
    old = "                    else -> ShowcaseGarden.objects(now)"
    rep(old, """                    garden == "birdcrops" -> ShowcaseGarden.birdCrops(now)\n""" + old)
    open(p, "w", encoding="utf-8").write(s)

print("patched", root)
