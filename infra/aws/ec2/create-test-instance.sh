#!/usr/bin/env bash
set -euo pipefail

NAME="${NAME:-coreable-saf-test}"
REGION="${AWS_REGION:-ap-northeast-2}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t4g.micro}"
AMI_ID="${AMI_ID:-ami-0cac45084c40c4162}"
KEY_NAME="${KEY_NAME:-coreable-saf-deploy-key}"
PUBLIC_KEY_PATH="${PUBLIC_KEY_PATH:-$HOME/.ssh/id_ed25519.pub}"
VPC_ID="${VPC_ID:-vpc-048552fd2bb35068b}"
SUBNET_ID="${SUBNET_ID:-subnet-00d38c2c9d2a05dda}"
SECURITY_GROUP_NAME="${SECURITY_GROUP_NAME:-coreable-saf-test-sg}"
VOLUME_SIZE="${VOLUME_SIZE:-20}"
SSH_CIDR="${SSH_CIDR:-0.0.0.0/0}"

aws_args=(--region "$REGION")

if ! aws "${aws_args[@]}" ec2 describe-key-pairs --key-names "$KEY_NAME" >/dev/null 2>&1; then
  aws "${aws_args[@]}" ec2 import-key-pair \
    --key-name "$KEY_NAME" \
    --public-key-material "fileb://${PUBLIC_KEY_PATH}" >/dev/null
fi

security_group_id="$(
  aws "${aws_args[@]}" ec2 describe-security-groups \
    --filters "Name=vpc-id,Values=${VPC_ID}" "Name=group-name,Values=${SECURITY_GROUP_NAME}" \
    --query 'SecurityGroups[0].GroupId' \
    --output text
)"

if [[ "$security_group_id" == "None" ]]; then
  security_group_id="$(
    aws "${aws_args[@]}" ec2 create-security-group \
      --group-name "$SECURITY_GROUP_NAME" \
      --description "coreable-saf test deployment" \
      --vpc-id "$VPC_ID" \
      --query 'GroupId' \
      --output text
  )"
fi

authorize_ingress() {
  local port="$1"
  local cidr="$2"
  aws "${aws_args[@]}" ec2 authorize-security-group-ingress \
    --group-id "$security_group_id" \
    --ip-permissions "IpProtocol=tcp,FromPort=${port},ToPort=${port},IpRanges=[{CidrIp=${cidr}}]" \
    >/dev/null 2>&1 || true
}

authorize_ingress 22 "$SSH_CIDR"
authorize_ingress 80 "0.0.0.0/0"
authorize_ingress 443 "0.0.0.0/0"

instance_id="$(
  aws "${aws_args[@]}" ec2 describe-instances \
    --filters "Name=tag:Name,Values=${NAME}" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' \
    --output text
)"

if [[ "$instance_id" == "None" ]]; then
  instance_id="$(
    aws "${aws_args[@]}" ec2 run-instances \
      --image-id "$AMI_ID" \
      --instance-type "$INSTANCE_TYPE" \
      --key-name "$KEY_NAME" \
      --subnet-id "$SUBNET_ID" \
      --security-group-ids "$security_group_id" \
      --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=${VOLUME_SIZE},VolumeType=gp3,DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${NAME}}]" \
      --query 'Instances[0].InstanceId' \
      --output text
  )"
fi

aws "${aws_args[@]}" ec2 wait instance-running --instance-ids "$instance_id"

allocation_id="$(
  aws "${aws_args[@]}" ec2 describe-addresses \
    --filters "Name=tag:Name,Values=${NAME}-eip" \
    --query 'Addresses[0].AllocationId' \
    --output text
)"

if [[ "$allocation_id" == "None" ]]; then
  allocation_id="$(
    aws "${aws_args[@]}" ec2 allocate-address \
      --domain vpc \
      --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=${NAME}-eip}]" \
      --query 'AllocationId' \
      --output text
  )"
fi

associated_instance_id="$(
  aws "${aws_args[@]}" ec2 describe-addresses \
    --allocation-ids "$allocation_id" \
    --query 'Addresses[0].InstanceId' \
    --output text
)"

if [[ "$associated_instance_id" != "$instance_id" ]]; then
  aws "${aws_args[@]}" ec2 associate-address \
    --instance-id "$instance_id" \
    --allocation-id "$allocation_id" >/dev/null
fi

public_ip="$(
  aws "${aws_args[@]}" ec2 describe-addresses \
    --allocation-ids "$allocation_id" \
    --query 'Addresses[0].PublicIp' \
    --output text
)"

cat <<EOF
InstanceId=${instance_id}
ElasticIp=${public_ip}
SecurityGroupId=${security_group_id}
KeyName=${KEY_NAME}
SSH=ssh ubuntu@${public_ip}
EOF
