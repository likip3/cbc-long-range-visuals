"""Install a project-local JDK 21 and the official Gradle wrapper (Windows x64)."""

from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tools"
GRADLE_VERSION = "9.2.1"
JDK_URL = (
    "https://github.com/adoptium/temurin21-binaries/releases/download/"
    "jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip"
)
JDK_SHA256 = "f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e"


def download(url, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".part")
    print(f"Downloading {url}", flush=True)
    request = urllib.request.Request(url, headers={"User-Agent": "cbc-long-range-visuals-dev-setup/0.1"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output)
        partial.replace(target)
    finally:
        partial.unlink(missing_ok=True)


def check_sha256(path, expected):
    with path.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    if actual != expected:
        raise RuntimeError(f"SHA-256 mismatch for {path.name}: {actual}")


def setup_java():
    java_home = TOOLS / "java21"
    if (java_home / "bin" / "javac.exe").is_file():
        print("Project-local Java 21 is already installed.", flush=True)
        return
    archive = TOOLS / "downloads" / "temurin21.zip"
    if not archive.is_file():
        download(JDK_URL, archive)
    check_sha256(archive, JDK_SHA256)
    with zipfile.ZipFile(archive) as bundle:
        entries = bundle.namelist()
        for entry in entries:
            if not (TOOLS / entry).resolve().is_relative_to(TOOLS.resolve()):
                raise RuntimeError("Unsafe JDK archive entry")
        folder = entries[0].split("/")[0]
        bundle.extractall(TOOLS)
    (TOOLS / folder).rename(java_home)
    archive.unlink()
    print(f"Java 21 installed at {java_home}", flush=True)


def setup_wrapper():
    # These files become part of the project; subsequent runs do not replace them.
    base = "https://raw.githubusercontent.com/gradle/gradle/v9.2.1/"
    for name in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar"):
        path = ROOT / name
        if not path.is_file():
            download(base + name, path)
    license_file = ROOT / "licenses/Apache-2.0.txt"
    if not license_file.is_file():
        download("https://www.apache.org/licenses/LICENSE-2.0.txt", license_file)
    checksum_path = TOOLS / "downloads" / "gradle-wrapper.sha256"
    download(f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-wrapper.jar.sha256", checksum_path)
    check_sha256(ROOT / "gradle/wrapper/gradle-wrapper.jar", checksum_path.read_text().strip())
    checksum_path = TOOLS / "downloads" / "gradle-distribution.sha256"
    download(f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip.sha256", checksum_path)
    checksum = checksum_path.read_text().strip()
    properties = ROOT / "gradle/wrapper/gradle-wrapper.properties"
    expected = (
        "distributionBase=GRADLE_USER_HOME\n"
        "distributionPath=wrapper/dists\n"
        f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip\n"
        f"distributionSha256Sum={checksum}\n"
        "networkTimeout=120000\n"
        "validateDistributionUrl=true\n"
        "zipStoreBase=GRADLE_USER_HOME\n"
        "zipStorePath=wrapper/dists\n"
    )
    if not properties.exists():
        properties.write_text(expected, encoding="utf-8")
    elif properties.read_text(encoding="utf-8") != expected:
        raise RuntimeError("Wrapper properties differ from the pinned Gradle version; review them manually.")
    print("Gradle wrapper checksums verified.", flush=True)


if __name__ == "__main__":
    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(setup_java), executor.submit(setup_wrapper)]
        for future in futures:
            future.result()
    print("Ready. Run dev.cmd build or dev.cmd runClient.", flush=True)
