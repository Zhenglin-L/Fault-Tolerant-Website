#!/bin/bash

AMI="ami-c229c0a2"
S3_BUCKET="edu-cornell-cs-cs5300s16-xw395"
key_name="ForwardArsenal-key-pair-uswest2.pem"
security_groups="CS5300Proj1b"

aws s3 cp CS5300.war s3://${S3_BUCKET}/hello.war
aws ec2 run-instances --image-id ${AMI} --count 5 --instance-type t2.micro --key-name $key_name --security-groups $security_groups --user-data file://installation.sh