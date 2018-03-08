#!/bin/bash
#logging user-data script output, simply for debugging purpose
exec > >(tee /var/log/user-data.log | logger -t user-data -s 2>/dev/console) 2>&1

key_id="AKIAJT26FUHFI7OCB6QQ"
secret_key="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"
default_region="us-west-2"
s3_bucket="edu-cornell-cs-cs5300s16-xw395"
#configure AWS CLI
aws configure set aws_access_key_id ${key_id}
aws configure set aws_secret_access_key ${secret_key}
aws configure set default.region ${default_region}
aws configure set preview.sdb true

#install java8 on the instance
yum -y remove java-1.7.0-openjdk
yum -y install java-1.8.0


#install tomcat8 on the instance
yum -y install tomcat8-webapps tomcat8-docs-webapp tomcat8-admin-webapps

#deploy the war file on tomcat
aws s3 cp s3://${s3_bucket}/CS5300.war /usr/share/tomcat8/webapps/CS5300.war

#Determine the internal IP address of the instance
local_ipv4=$(curl http://169.254.169.254/latest/meta-data/local-ipv4)

#Determine the instance’s ami-launch-index for use as a server ID
ami_launch_index=$(curl http://169.254.169.254/latest/meta-data/ami-launch-index)

#Determine the instance's public hostname
public_hostname=$(curl http://169.254.169.254/latest/meta-data/public-hostname)

#Upload the internal IP address and public hostname to SimpleDB
aws sdb create-domain --domain-name metadata
aws sdb put-attributes --domain-name metadata --item-name $ami_launch_index --attributes "Name=local-ipv4,Value=$local_ipv4,Replace=true" "Name=public-hostname,Value=$public_hostname,Replace=true"

#install and configure jq, the command line json parser
wget http://stedolan.github.io/jq/download/linux64/jq
chmod +x ./jq
sudo cp jq /usr/bin


#Download the internal IP addresses of all the other EC2 instances from SimpleDB
function check {
	local counter=0
	local result=true
    while [ $counter -lt 4 ]
    do 
        if [ -z "$(aws sdb get-attributes --domain-name metadata --item-name $counter)" ]
        then
           result=false
           break
        fi 
        ((counter++))
    done 
    echo $result
}

while true
do  
    if [ "$(check)" == true ]
    then
        cnt=0
        ipv4=0
        hostname=0
        while [ "$cnt" -lt 4 ]
        do
            hostname=$(aws sdb get-attributes --domain-name metadata --item-name $cnt | jq '.Attributes[0].Value')
            ipv4=$(aws sdb get-attributes --domain-name metadata --item-name $cnt | jq '.Attributes[1].Value')
            echo "$cnt $ipv4 $hostname" >> /metadata.txt
            #aws s3api put-object --acl bucket-owner-full-control --body metadata.txt --bucket edu-cornell-cs-cs5300s16-xw395 --key metadata.txt
            ((cnt++))
        done
        break
    fi
done
#store reboot number into a file
reboot_num=0
echo "$reboot_num" >> /rebootNum.txt
#start tomcat server
sudo service tomcat8 start
#set the access control for the entire file path
chmod 777 /metadata.txt
chmod 777 /rebootNum.txt


