#!/usr/bin/env bash
! grep -rniE 'adhd|autis|audhd|neurodiver|neurotypical|asperger' --include='*.xml' $(find . -path '*/src/main/res' -type d) ; echo "copy check done"
