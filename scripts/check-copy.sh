#!/usr/bin/env bash
# Copy guard (also enforced by app's ForbiddenCopyTest): no medical labels, no exclamation marks or guilt words in strings.
dirs=$(find . -path '*/src/main/res' -type d -not -path '*/build/*')
status=0
grep -rniE 'adhd|autis|audhd|neurodiver|neurotypical|asperger' --include='*.xml' $dirs && status=1
grep -rnE '>[^<]*![^<]*<' --include='strings.xml' $dirs | grep -v '<!--' && status=1
grep -rniE '>[^<]*\b(missed|streaks?|behind|forgot|overdue|failed to log)\b[^<]*<' --include='strings.xml' $dirs && status=1
echo "copy check done"
exit $status
