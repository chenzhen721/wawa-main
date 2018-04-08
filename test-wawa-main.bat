@echo off

echo ================git pull=======================
@call git pull

sleep 5

@call mvn clean package -U -P test -P groovy -Dmaven.test.skip=true

if %ERRORLEVEL% EQU 0 (
	echo ================SUCCESS=======================
	call:pubApi 120.79.52.5 "publish to 120.79.52.5"
	echo ================SUCCESS=======================
	sleep 15

) else (
	COLOR C
	    echo -------         !! FAILD !!      -------------
	pause
)

exit

:pubApi
scp target/wawa-main.war mlsty@%~1:~/test-wawa-main
ssh mlsty@%~1  "source /etc/profile;cd ~/test-wawa-main;rm -rf bak.webapp && mv -f webapp bak.webapp;unzip wawa-main.war -d webapp;cp ROOT.xml webapp;./restart.sh"
pause
