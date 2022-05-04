@echo off

heroku login

SET currentDir=%~dp0
cd %currentDir%
SET currentUnit=%currentDir:~0,2%
%currentUnit%


::To retrieve current directory name
::for %%I in (.) do set CurrDirName=%%~nxI
::echo %CurrDirName%

heroku git:remote -a shared-software
git commit --allow-empty -m "Deploy on Heroku"
git push heroku main