#!/bin/bash

# run the client with -verbose:class like this:
# (with the server running, of course)
#
# cp classes/Envelope.java .
# java -verbose:class -cp . Envelope > loaded.txt
#
# then click around and cause classes to get loaded.
# that will print all the classes loaded to loaded.txt.
# then, run this file from the same directory as build.xml.

listfile=classes.txt
dir=slim_class_dir
outjar=slim-client

# parse the loaded text file to get a list of class files
grep http:// loaded.txt \
  | egrep -v us.lump.envelope.client.Main\|us.lump.envelope.client.ui.defs.Strings\|us.lump.client.ui.MainFrame \
  | perl -lne '/.*?\s(.*?)\s+.*$/;$a=$1;$a=~s/\./\//g; print $a' > $listfile

rm -rf $dir
mkdir $dir
cd $dir

# get the bootstrap envelope class
cp ../classes/Envelope.class .

# step through list, copying or extracting class files
for i in `cat ../classes.txt` ; do
  found=0
  if [[ -e ../classes/$i.class ]] ; then
    found=1
    cwd=`pwd`
    cd ../classes
    tar -cf - $i.class | tar -xv -C $cwd -f -
    cd $cwd
  else
    for j in ../lib/*.jar ; do
      echo trying $j
      unzip  $j $i.class 2>/dev/null 1>/dev/null
      if [[ $? == 0 ]] ; then
        found=1
        echo "found $i in $j"
        break
      fi
    done
  fi
  if [[ $found == 0 ]] ; then
    echo "couldn't find $i"
    break
  fi
done

set -o errexit

# compress the classes into jar
rm -f ../lib/$outjar-*
jar cvf ../lib/$outjar-unsigned.jar *
cd ..
$JAVA_HOME/bin/pack200 \
  --repack \
  --strip-debug \
  --no-keep-file-order \
  --effort=5 \
  --deflate-hint=true \
  --modification-time=latest \
  --verbose \
  lib/$outjar-repacked.jar \
  lib/$outjar-unsigned.jar

jarsigner \
  -keystore security/keystore \
  -storepass OS.32sf \
  -verbose \
  lib/$outjar-repacked.jar \
  envelope

mv lib/$outjar-repacked.jar lib/$outjar.jar

$JAVA_HOME/bin/pack200 \
  --gzip \
  --strip-debug \
  --no-keep-file-order \
  --effort=5 \
  --deflate-hint=true \
  --modification-time=latest \
  --verbose \
  lib/$outjar.jar.pack.gz \
  lib/$outjar.jar


