#!/bin/sh

# Get the absolute path to this script's folder.
if echo "$0" | awk '{exit(!/^\//);}'; then
  maindir=$(dirname "$0");
else
  maindir=$PWD/$(dirname "$0");
fi

cd "${maindir}";
java -cp "./lib/*" net.vhati.openuhs.desktopreader.UHSReaderMain "$@";
