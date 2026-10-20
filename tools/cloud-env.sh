#!/usr/bin/env bash
# Source this only in Codex cloud; normal machines just use an installed JDK21.
export JAVA_HOME="/workspace/.tools/jdk-21.0.8+9"
export GRADLE_USER_HOME="/workspace/.cache/gradle"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${HTTPS_PROXY:-}" ]; then
    tq_proxy_options="$(python3 - <<'PY'
import os, urllib.parse
p = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
if p.hostname:
    print('-Dhttps.proxyHost=' + p.hostname + ' -Dhttps.proxyPort=' + str(p.port or 80)
          + ' -Dhttp.proxyHost=' + p.hostname + ' -Dhttp.proxyPort=' + str(p.port or 80))
PY
)"
    export JAVA_OPTS="${JAVA_OPTS:-} $tq_proxy_options"
    unset tq_proxy_options
fi
if [ -r /etc/ssl/certs/java/cacerts ]; then
    export JAVA_OPTS="${JAVA_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
