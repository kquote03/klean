#!/usr/bin/env bash
# Prints the latest release version for each coordinate. Google Maven first, then Maven Central.
set -uo pipefail
coords=(
  com.android.tools.build:gradle
  org.jetbrains.kotlin:kotlin-gradle-plugin
  com.google.devtools.ksp:symbol-processing-gradle-plugin
  androidx.compose:compose-bom
  androidx.compose.material3:material3
  androidx.compose.material3:material3-adaptive-navigation-suite
  androidx.activity:activity-compose
  androidx.navigation:navigation-compose
  androidx.lifecycle:lifecycle-runtime-compose
  androidx.room:room-runtime
  androidx.sqlite:sqlite-bundled
  androidx.work:work-runtime-ktx
  androidx.glance:glance-appwidget
  androidx.datastore:datastore-preferences
  androidx.core:core-ktx
  androidx.hilt:hilt-navigation-compose
  androidx.hilt:hilt-work
  com.google.dagger:hilt-android
  org.jetbrains.kotlinx:kotlinx-coroutines-core
  org.jetbrains.kotlinx:kotlinx-serialization-json
  org.jetbrains.kotlinx:kotlinx-datetime
  io.ktor:ktor-client-okhttp
  org.robolectric:robolectric
  app.cash.turbine:turbine
  androidx.test.ext:junit
  androidx.test:runner
  com.google.ai.edge.litertlm:litertlm-android
  androidx.test:core
  androidx.exifinterface:exifinterface
  androidx.documentfile:documentfile
  com.google.mediapipe:tasks-genai
)
for c in "${coords[@]}"; do
  g=${c%%:*}; a=${c##*:}; p=${g//.//}
  v=$(curl -fs "https://dl.google.com/android/maven2/$p/$a/maven-metadata.xml" 2>/dev/null \
      || curl -fs "https://repo1.maven.org/maven2/$p/$a/maven-metadata.xml")
  echo "$c = $(grep -o '<release>[^<]*' <<<"$v" | sed 's/<release>//') (latest: $(grep -o '<latest>[^<]*' <<<"$v" | sed 's/<latest>//'))"
done
