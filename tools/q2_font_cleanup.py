from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    data = p.read_text(encoding="utf-8")
    count = data.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one literal match, found {count}")
    p.write_text(data.replace(old, new, 1), encoding="utf-8")


def regex_once(path: str, pattern: str, replacement: str) -> None:
    p = Path(path)
    data = p.read_text(encoding="utf-8")
    updated, count = re.subn(
        pattern,
        replacement,
        data,
        count=1,
        flags=re.MULTILINE | re.DOTALL,
    )
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one regex match, found {count}")
    p.write_text(updated, encoding="utf-8")


renderer_pattern = (
    r'^            // Custom Font\n'
    r'            if \(Preferences\.customFont == Constants\.CUSTOM_FONT_GOOGLE_SANS\) \{\n'
    r'.*?'
    r'^            \} else if \(Preferences\.customFont == Constants\.CUSTOM_FONT_DOWNLOADED && typeface != null\) \{\n'
)
renderer_replacement = (
    '            // Custom Font\n'
    '            if (Preferences.customFont == Constants.CUSTOM_FONT_DOWNLOADED && typeface != null) {\n'
)
for renderer in (
    "app/src/main/java/com/tommasoberlose/anotherwidget/ui/widgets/AlignedWidget.kt",
    "app/src/main/java/com/tommasoberlose/anotherwidget/ui/widgets/StandardWidget.kt",
):
    regex_once(renderer, renderer_pattern, renderer_replacement)

replace_once(
    "app/src/main/java/com/tommasoberlose/anotherwidget/global/Constants.kt",
    "    // Retired bundled Google Sans compatibility sentinel. Historical stored value 1 now\n"
    "    // falls through to the system/default font without redistributing Google font binaries.\n"
    "    const val CUSTOM_FONT_GOOGLE_SANS = -1\n",
    "    const val CUSTOM_FONT_DEFAULT = 0\n",
)

replace_once(
    "app/src/main/java/com/tommasoberlose/anotherwidget/global/Preferences.kt",
    "    var customFont by intPref(key = \"PREF_CUSTOM_FONT\", default = Constants.CUSTOM_FONT_GOOGLE_SANS)\n",
    "    var customFont by intPref(key = \"PREF_CUSTOM_FONT\", default = Constants.CUSTOM_FONT_DEFAULT)\n",
)

replace_once(
    "app/src/main/java/com/tommasoberlose/anotherwidget/helpers/SettingsStringHelper.kt",
    "            Constants.CUSTOM_FONT_GOOGLE_SANS -> context.getString(R.string.custom_font_subtitle_1) + \" - ${getVariantLabel(context, Preferences.customFontVariant)}\"\n",
    "",
)

old_picker = '''            val dialog = BottomSheetMenu<Int>(requireContext(), header = getString(R.string.settings_custom_font_title)).setSelectedValue(
                Preferences.customFont)
            dialog.addItem(SettingsStringHelper.getCustomFontLabel(requireContext(), 0), 0)

            if (Preferences.customFont == Constants.CUSTOM_FONT_GOOGLE_SANS) {
                dialog.addItem(SettingsStringHelper.getCustomFontLabel(requireContext(), Constants.CUSTOM_FONT_GOOGLE_SANS), Constants.CUSTOM_FONT_GOOGLE_SANS)
            }

            if (Preferences.customFontFile != "") {
                dialog.addItem(SettingsStringHelper.getCustomFontLabel(requireContext(), Preferences.customFont), Constants.CUSTOM_FONT_DOWNLOADED)
            }
'''
new_picker = '''            val selectedFont = if (
                Preferences.customFont == Constants.CUSTOM_FONT_DOWNLOADED &&
                Preferences.customFontFile.isNotEmpty()
            ) Constants.CUSTOM_FONT_DOWNLOADED else Constants.CUSTOM_FONT_DEFAULT
            val dialog = BottomSheetMenu<Int>(requireContext(), header = getString(R.string.settings_custom_font_title)).setSelectedValue(selectedFont)
            dialog.addItem(SettingsStringHelper.getCustomFontLabel(requireContext(), Constants.CUSTOM_FONT_DEFAULT), Constants.CUSTOM_FONT_DEFAULT)

            if (Preferences.customFontFile != "") {
                dialog.addItem(SettingsStringHelper.getCustomFontLabel(requireContext(), Constants.CUSTOM_FONT_DOWNLOADED), Constants.CUSTOM_FONT_DOWNLOADED)
            }
'''
replace_once(
    "app/src/main/java/com/tommasoberlose/anotherwidget/ui/fragments/tabs/TypographyFragment.kt",
    old_picker,
    new_picker,
)

string_pattern = re.compile(
    r'^\s*<string name="custom_font_subtitle_1">.*?</string>\s*(?:\n|$)',
    flags=re.MULTILINE,
)
removed_strings = 0
for p in sorted(Path("app/src/main/res").glob("values*/strings.xml")):
    data = p.read_text(encoding="utf-8")
    updated, count = string_pattern.subn("", data)
    if count:
        p.write_text(updated, encoding="utf-8")
        removed_strings += count
if removed_strings != 12:
    raise SystemExit(f"expected 12 retired font-label resources, removed {removed_strings}")

font_attr_pattern = re.compile(
    r'^\s*android:fontFamily="@font/google_sans(?:_bold)?"\s*(?:\n|$)',
    flags=re.MULTILINE,
)
removed_attrs = 0
for p in sorted(Path("app/src/main/res/layout").glob("*.xml")):
    data = p.read_text(encoding="utf-8")
    updated, count = font_attr_pattern.subn("", data)
    if count:
        p.write_text(updated, encoding="utf-8")
        removed_attrs += count
if removed_attrs != 7:
    raise SystemExit(f"expected 7 retired layout font attributes, removed {removed_attrs}")

source_text = "\n".join(
    p.read_text(encoding="utf-8", errors="ignore")
    for p in Path("app/src/main").rglob("*")
    if p.is_file() and p.suffix in {".kt", ".java", ".xml", ".gradle", ".properties", ".json", ".md"}
)
forbidden = [
    "Google Sans",
    "google_sans_",
    "GoogleSans",
    "CUSTOM_FONT_GOOGLE_SANS",
    "custom_font_subtitle_1",
]
leftovers = [token for token in forbidden if token in source_text]
if leftovers:
    raise SystemExit(f"retired font residue remains: {leftovers}")

constants = Path(
    "app/src/main/java/com/tommasoberlose/anotherwidget/global/Constants.kt"
).read_text(encoding="utf-8")
if "const val CUSTOM_FONT_DEFAULT = 0" not in constants:
    raise SystemExit("system/default font value 0 was not preserved")
if "const val CUSTOM_FONT_DOWNLOADED = 2" not in constants:
    raise SystemExit("downloaded-font value 2 was not preserved")

helper = Path(
    "app/src/main/java/com/tommasoberlose/anotherwidget/helpers/SettingsStringHelper.kt"
).read_text(encoding="utf-8")
if "Constants.CUSTOM_FONT_DOWNLOADED ->" not in helper:
    raise SystemExit("downloaded-font label handling was lost")
if "else -> context.getString(R.string.custom_font_subtitle_0)" not in helper:
    raise SystemExit("legacy/non-downloaded fallback no longer resolves to system/default")

picker = Path(
    "app/src/main/java/com/tommasoberlose/anotherwidget/ui/fragments/tabs/TypographyFragment.kt"
).read_text(encoding="utf-8")
if "Preferences.customFontFile.isNotEmpty()" not in picker:
    raise SystemExit("downloaded-font picker guard missing")
if ") Constants.CUSTOM_FONT_DOWNLOADED else Constants.CUSTOM_FONT_DEFAULT" not in picker:
    raise SystemExit("historical/non-downloaded picker fallback missing")

print("Retired bundled font cleanup applied with downloaded-font value 2 preserved and historical value 1 falling back to system/default.")
