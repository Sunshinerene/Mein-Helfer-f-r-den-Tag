"""Verify the standalone prototype's packaged Android contract with SDK aapt2."""

import argparse
import hashlib
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile


PACKAGE = "de.meinhelfer.sunny.launcher.idea"
AVATAR_SHA256 = "06762e4a36085c639ac92eaa9dbefc111f21517a546857b6e9a50d40eafc7af5"


def require(condition, message):
    if not condition:
        raise SystemExit(f"APK verification failed: {message}")


def attributes(tree, name):
    return re.findall(rf"^\s*A: (?:[^\s]+:)?{name}(?:\([^)]+\))?=(\S+)", tree, re.M)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    args = parser.parse_args()

    def dump(mode, *options):
        return subprocess.check_output(
            [args.build_tools / "aapt2", "dump", mode, *options, args.apk], text=True
        )

    badging = dump("badging")
    require(f"package: name='{PACKAGE}' versionCode='1' versionName='0.1-idea'" in badging,
            "expected package identifier and version")
    require("minSdkVersion:'26'" in badging and "targetSdkVersion:'35'" in badging
            and "compileSdkVersion='35'" in badging, "expected SDK levels")
    require("application-debuggable" in badging, "expected a debug build")
    require("uses-permission" not in badging and "uses-implied-permission" not in badging,
            "no requested or implied permissions")
    require(re.findall(r"^launchable-activity: name='([^']+)'", badging, re.M)
            == [f"{PACKAGE}.MainActivity"], "expected a single normally launchable activity")

    manifest = dump("xmltree", "--file", "AndroidManifest.xml")
    tags = re.findall(r"^\s*E: ([\w-]+)", manifest, re.M)
    require(tags == ["manifest", "uses-sdk", "queries", "intent", "action", "category",
                     "application", "activity", "intent-filter", "action", "category"],
            "only the narrow app query and the single entry activity may be declared")
    require(attributes(manifest, "name") == ['"android.intent.action.MAIN"',
                                            '"android.intent.category.LAUNCHER"',
                                            f'"{PACKAGE}.MainActivity"',
                                            '"android.intent.action.MAIN"',
                                            '"android.intent.category.LAUNCHER"'],
            "the query and entry activity must both use MAIN / LAUNCHER")
    require(attributes(manifest, "exported") == ["true"], "only MainActivity must be exported")
    require(not attributes(manifest, "sharedUserId"), "no shared UID")
    require(attributes(manifest, "allowBackup") == ["false"]
            and attributes(manifest, "fullBackupContent") == ["false"], "backup must be disabled")
    require(len(attributes(manifest, "dataExtractionRules")) == 1, "expected data extraction rules")

    rules = dump("xmltree", "--file", "res/xml/data_extraction_rules.xml")
    require(re.findall(r"^\s*E: ([\w-]+)", rules, re.M)
            == ["data-extraction-rules", "cloud-backup", "exclude", "device-transfer", "exclude"]
            and attributes(rules, "domain") == ['"sharedpref"', '"sharedpref"']
            and attributes(rules, "path") == ['"."', '"."'],
            "preferences must be excluded from cloud backup and device transfer")

    with ZipFile(args.apk) as package:
        entries = package.namelist()
        avatar = [name for name in entries if name.endswith("/sunny_su08.webp")]
        require(len(avatar) == 1 and hashlib.sha256(package.read(avatar[0])).hexdigest() == AVATAR_SHA256,
                "the packaged SU08 must match the approved original bytes")
        require(not any(name.startswith("lib/") for name in entries),
                "no native libraries may be packaged")

    print("Package, SDK, manifest, backup rules, native-library isolation and original SU08 verified.")


if __name__ == "__main__":
    main()
