#!/usr/bin/env bash
# Step 6. Run on a fresh Amazon Linux 2023 EC2 instance:  sudo bash setup-ec2.sh
set -euo pipefail

dnf install -y java-17-amazon-corretto-headless mariadb105   # mariadb105 gives you the `mysql` client
id cloudnote &>/dev/null || useradd --system --shell /sbin/nologin cloudnote
mkdir -p /opt/cloudnote /var/log/cloudnote
chown cloudnote:cloudnote /var/log/cloudnote
echo "Java installed. Next: copy cloudnote.jar to /opt/cloudnote/ and install deploy/cloudnote.service"
