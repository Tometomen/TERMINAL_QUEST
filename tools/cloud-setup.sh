#!/usr/bin/env bash
set -euo pipefail
cd /workspace/TERMINAL_QUEST
mkdir -p /workspace/.tools/downloads /workspace/.cache/gradle
tq_jdk=/workspace/.tools/jdk-21.0.8+9
if [ ! -x "$tq_jdk/bin/javac" ]; then
    tq_archive=/workspace/.tools/downloads/jdk.tar.gz
    curl --fail --location --silent --show-error --retry 2 \
      'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' \
      --output "$tq_archive"
    echo 'f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219  /workspace/.tools/downloads/jdk.tar.gz' | sha256sum --check --status
    tar xzf "$tq_archive" -C /workspace/.tools
fi
# Java does not automatically read HTTPS_PROXY. Configure the cloud-only cache,
# preserving any unrelated Gradle properties. No credential values are copied.
python3 - <<'PY'
import os, pathlib, urllib.parse
target = pathlib.Path('/workspace/.cache/gradle/gradle.properties')
updates = {}
if os.environ.get('HTTPS_PROXY'):
    proxy = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
    for scheme in ('http', 'https'):
        updates['systemProp.' + scheme + '.proxyHost'] = proxy.hostname
        updates['systemProp.' + scheme + '.proxyPort'] = str(proxy.port or 80)
if pathlib.Path('/etc/ssl/certs/java/cacerts').exists():
    updates['systemProp.javax.net.ssl.trustStore'] = '/etc/ssl/certs/java/cacerts'
existing = target.read_text().splitlines() if target.exists() else []
existing = [line for line in existing if line.split('=', 1)[0] not in updates]
target.write_text('\n'.join(existing + [key + '=' + value for key, value in updates.items()]) + '\n')
PY
source tools/cloud-env.sh
./gradlew core:test lwjgl3:jar html:dist --no-daemon
