#!/bin/sh

#file='app/src/main/res/values/strings.xml'
#file='app/src/main/res/values/colors.xml'
#file='app/src/main/res/values/ids.xml'
file='app/src/main/res/values/integers.xml'

strings=$(grep 'name=\"[a-zA-Z0-9_]\{1,\}\"' $file |
  awk -F"\"" '{print $2}')
while read -r line; do
  res=`ag -w "$line" . | wc -l`
  if [ $res -lt 2 ];
  then
    echo "removing $line";
    sed -i '/\"'$line'\"/d' $file
  fi;
done <<< "$strings"
