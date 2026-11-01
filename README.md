# CloudNote

Notes with file attachments. Spring Boot on EC2, notes in RDS MySQL, files in a private S3 bucket,
app log shipped to CloudWatch, alarm emails you through SNS.

## Run it locally first (no AWS needed)

Needs Java 17+ and Maven.

```bash
mvn test                 # runs the API tests
mvn spring-boot:run      # local profile: H2 in-memory DB + files in ./uploads
```

Open http://localhost:8080. Data resets on every restart in local mode.
Debug endpoint (local only): http://localhost:8080/api/debug/error?count=3

## Push to GitHub

```bash
git init
git add .
git commit -m "CloudNote: Spring Boot app"
git branch -M main
git remote add origin https://github.com/<you>/cloudnote.git
git push -u origin main
```

## Deploy on AWS (handbook steps 3 to 10)

Region used below is `ap-south-1`; change it everywhere if you pick another. Wrong region is the most common failure.

### Step 3: RDS
- MySQL, `db.t3.micro`, Public access = **No**, new security group `rds-sg`.
- Initial database name: `cloudnote`. Note the endpoint, username, password.
- Later, edit `rds-sg` inbound: MySQL/Aurora (3306) from source = the EC2 security group (`ec2-sg`). Nothing else.

### Step 4: S3
- New bucket, any unique name, **Block all public access = ON**, default encryption on.

### Step 5: IAM role for EC2
Role type: EC2. Attach `CloudWatchAgentServerPolicy`, plus this inline policy (replace the bucket name and topic ARN):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::YOUR_BUCKET/*" },
    { "Effect": "Allow", "Action": "sns:Publish", "Resource": "YOUR_SNS_TOPIC_ARN" }
  ]
}
```

The app itself only needs S3. SNS publish is for the alarm, which AWS triggers itself, so it works even without that line; it is kept because your handbook lists it.

### Step 6: EC2
- Amazon Linux 2023, `t3.micro`, attach the role above, security group `ec2-sg` allowing inbound 22 (your IP only) and 8080 (your IP, or anywhere for the demo).
- On your laptop: `mvn clean package -DskipTests` then copy the jar and deploy files:

```bash
scp -i key.pem target/cloudnote-1.0.0.jar deploy/* ec2-user@<EC2_PUBLIC_IP>:~
```

- On the instance:

```bash
sudo bash setup-ec2.sh
sudo mv cloudnote-1.0.0.jar /opt/cloudnote/cloudnote.jar
sudo chown cloudnote:cloudnote /opt/cloudnote/cloudnote.jar
sudo nano cloudnote.service            # fill in DB_URL, DB_USER, DB_PASSWORD, S3_BUCKET, AWS_REGION
sudo cp cloudnote.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now cloudnote
sudo journalctl -u cloudnote -f        # watch startup; Ctrl+C to leave
```

### Step 7: test
Open `http://<EC2_PUBLIC_IP>:8080`, save a note, upload a file, download it.
Health check: `curl http://<EC2_PUBLIC_IP>:8080/actuator/health`

### Step 8: SNS
Create a Standard topic `cloudnote-alerts`, add an Email subscription, **click the confirm link in your inbox**.

### Step 9: CloudWatch
```bash
sudo dnf install -y amazon-cloudwatch-agent
sudo cp ~/cloudwatch-agent.json /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
```
The agent runs as root, so it can read `/var/log/cloudnote/app.log`.
Then in the console: CloudWatch, Log groups, `cloudnote-app`, Metric filters, Create:
- Filter pattern: `ERROR`
- Metric namespace `CloudNote`, name `AppErrors`, value `1`

Create an alarm on that metric: Sum over 1 minute, threshold `>= 3`, treat missing data as **not breaching**, action = your SNS topic.

### Step 10: trigger it
Uncomment `DEBUG_ENDPOINTS=true` in `/etc/systemd/system/cloudnote.service`, then
`sudo systemctl daemon-reload && sudo systemctl restart cloudnote`, then open
`http://<EC2_PUBLIC_IP>:8080/api/debug/error?count=5`. The email arrives after about 1 to 3 minutes.
**Turn the debug flag off again afterwards.**

### Step 12: after the demo
Delete or stop EC2 and RDS. A stopped RDS instance restarts itself after 7 days.

## Design choices (for internals)

| Choice | Why |
|---|---|
| `FileStorage` interface with S3 and local implementations | Same code runs on your laptop and on AWS; the profile picks one |
| No AWS keys in code | `DefaultCredentialsProvider` uses the EC2 instance role automatically |
| Only the S3 key goes in RDS | Keeps big files out of the database |
| Allowlist plus magic-byte check on uploads | A renamed `.exe` cannot pass as `.png` or `.txt` |
| Content type comes from the extension, not the browser | The browser value can be faked |
| Downloads sent as attachment with `nosniff` | Uploaded files never run as pages in your origin |
| Markdown rendered through DOMPurify | Stops script injection from note content |
| Only unexpected exceptions are logged at ERROR | Validation mistakes by users should not trigger the alarm |
| `ddl-auto=update` | Tables are created on first start; fine for a demo, use Flyway for real projects |

## Known limits
- No login: every note is visible to everyone who can reach the URL. Keep the security group tight.
- HTTP only on port 8080. CloudFront or a load balancer would be needed for HTTPS.
