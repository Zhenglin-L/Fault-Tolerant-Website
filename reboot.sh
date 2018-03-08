#read the current reboot number from the file
reboot_num=$(sed '1!d' /rebootNum.txt)

#overwrite the file with the incremented reboot number
echo "$((reboot_num+1))" > /rebootNum.txt

#restart tomcat
sudo service tomcat8 start
