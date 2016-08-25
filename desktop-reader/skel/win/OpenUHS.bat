@ECHO OFF
SETLOCAL

SET FAILED=0

java -cp "./lib/*" net.vhati.openuhs.desktopreader.UHSReaderMain %* || SET FAILED=1&& GOTO die

:die
IF %FAILED%==1 (
  REM Pause after failure, but only if there were no args (GUI)
  IF x%*x==xx PAUSE 1>&2
)
ENDLOCAL
EXIT /B %FAILED%
