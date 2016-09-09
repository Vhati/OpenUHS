@ECHO OFF
SETLOCAL

REM Get the script dir, replace slashes, for an absolute java classpath.
SET HERE_CP=%~dp0
SET HERE_CP=%HERE_CP:\=/%

SET FAILED=0

java -cp "%HERE_CP%lib/*" net.vhati.openuhs.desktopreader.UHSReaderMain %* || SET FAILED=1&& GOTO die

:die
IF %FAILED%==1 (
  REM Pause after failure, but only if there were no args (GUI)
  IF "%~1"=="" PAUSE 1>&2
)
ENDLOCAL
EXIT /B %FAILED%
