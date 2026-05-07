# AWS Deployment Guide — Meme Arena

This guide documents the exact steps to deploy the Meme Arena application to AWS.
The stack is: Spring Boot backend on ECS Fargate + PostgreSQL on RDS + React frontend on S3/CloudFront + ALB.

---

## Architecture Overview

```
Browser → CloudFront (HTTPS) → S3 (React static files)
                    ↓ /api/*
                   ALB (port 80)
                    ↓
              ECS Fargate (Spring Boot, port 8080)
                    ↓
              RDS PostgreSQL (port 5432)
```

---

## Prerequisites

Install and configure the AWS CLI. This lets you run all AWS commands from your terminal.

```bash
winget install Amazon.AWSCLI
```

After installing, open a new terminal and configure your credentials:

```bash
aws configure
# AWS Access Key ID:     <from AWS Console → Security Credentials → Access Keys>
# AWS Secret Access Key: <same page>
# Default region:        ap-south-1
# Default output format: json
```

Verify it works:

```bash
aws sts get-caller-identity
```

---

## Step 1 — RDS PostgreSQL

Creates the production database. The backend connects to this on startup to create tables and run queries.

```bash
aws rds create-db-instance \
  --db-instance-identifier meme-arena-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 16.6 \
  --master-username meme_user \
  --master-user-password <YOUR_PASSWORD> \
  --allocated-storage 20 \
  --db-name meme_arena \
  --no-multi-az \
  --no-publicly-accessible \
  --backup-retention-period 0 \
  --region ap-south-1
```

Wait for it to become available (5-10 minutes):

```bash
aws rds describe-db-instances \
  --db-instance-identifier meme-arena-db \
  --query "DBInstances[0].DBInstanceStatus" \
  --region ap-south-1
```

Get the endpoint (save this — needed for backend config):

```bash
aws rds describe-db-instances \
  --db-instance-identifier meme-arena-db \
  --query "DBInstances[0].Endpoint.Address" \
  --region ap-south-1
```

> **Important:** Do not use special characters like `$`, `@`, `!` in the password. They get misinterpreted as shell variables when passed as ECS environment variables.

---

## Step 2 — ECR (Container Registry)

Creates a private Docker registry on AWS to store the backend image.

```bash
aws ecr create-repository --repository-name meme-arena-backend --region ap-south-1
```

Login Docker to ECR (replace `<ACCOUNT_ID>` with your AWS account ID):

```bash
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com
```

---

## Step 3 — Build and Push Backend Image

The Dockerfile uses a pre-built JAR approach because Docker Desktop on Windows sometimes can't resolve DNS inside build containers.

Build the JAR locally first (Maven runs on your machine, which has internet access):

```bash
cd gdg
./mvnw clean package -DskipTests -B
```

Copy the JAR to a fixed name, build the image, then clean up:

```bash
Copy-Item target\*.jar app.jar
docker build -f Dockerfile.prebuilt -t meme-arena-backend .
Remove-Item app.jar
```

Tag and push to ECR:

```bash
docker tag meme-arena-backend:latest <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend:latest
docker push <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend:latest
```

---

## Step 4 — ECS Cluster and Task Definition

**Create the cluster** (the logical grouping for your containers):

AWS Console → ECS → Clusters → Create cluster
- Name: `meme-cluster`
- Infrastructure: AWS Fargate

**Create the task definition** (the blueprint for running the container):

AWS Console → ECS → Task definitions → Create new task definition
- Family: `meme-arena-backend`
- Launch type: Fargate
- CPU: 0.5 vCPU, Memory: 1 GB
- Container image: `<ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend:latest`
- Container port: 8080

Environment variables to set:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://<RDS_ENDPOINT>:5432/meme_arena?sslmode=require` |
| `DATABASE_USERNAME` | `meme_user` |
| `DATABASE_PASSWORD` | your RDS password (no special chars) |
| `JWT_SECRET` | output of `openssl rand -hex 32` |
| `JWT_EXPIRATION_MS` | `86400000` |
| `CORS_ORIGINS` | `*` |

---

## Step 5 — ECS Service

Runs the task definition as a long-running service with auto-restart.

AWS Console → ECS → Clusters → meme-cluster → Services → Create
- Launch type: Fargate
- Task definition: `meme-arena-backend:1`
- Service name: `meme-arena-service`
- Desired tasks: 1
- Subnets: all 3 (ap-south-1a, 1b, 1c)
- Security group: create new `meme-arena-ecs-sg`
  - Inbound: Custom TCP, port 8080, source Anywhere
- Public IP: ON

---

## Step 6 — Security Groups (Networking)

Allows ECS to talk to RDS. Without this the backend crashes on startup with a connection timeout.

Get the security group IDs:

```bash
# RDS security group
aws rds describe-db-instances \
  --db-instance-identifier meme-arena-db \
  --query "DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId" \
  --region ap-south-1

# ECS security group
aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=meme-arena-ecs-sg" \
  --query "SecurityGroups[0].GroupId" \
  --region ap-south-1
```

Open port 5432 on the RDS security group, allowing traffic from the ECS security group:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id <RDS_SG_ID> \
  --protocol tcp \
  --port 5432 \
  --source-group <ECS_SG_ID> \
  --region ap-south-1
```

---

## Step 7 — ALB (Application Load Balancer)

The ALB gives the backend a stable domain name. This is required because CloudFront cannot proxy to raw IP addresses, and ECS task IPs change on every restart.

```bash
aws elbv2 create-load-balancer \
  --name meme-arena-alb \
  --subnets <SUBNET_1> <SUBNET_2> <SUBNET_3> \
  --security-groups <ECS_SG_ID> \
  --scheme internet-facing \
  --type application \
  --region ap-south-1 \
  --query "LoadBalancers[0].DNSName"
```

Create a target group (tells the ALB where to send traffic):

```bash
aws elbv2 create-target-group \
  --name meme-arena-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id <VPC_ID> \
  --target-type ip \
  --health-check-path /actuator/health \
  --region ap-south-1 \
  --query "TargetGroups[0].TargetGroupArn"
```

Create a listener (ALB listens on port 80 and forwards to the target group):

```bash
aws elbv2 create-listener \
  --load-balancer-arn <ALB_ARN> \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=<TARGET_GROUP_ARN> \
  --region ap-south-1
```

Register the ECS task IP with the target group. Get the current task IP first:

```bash
$task = aws ecs list-tasks --cluster meme-cluster --service-name meme-arena-service --region ap-south-1 --query "taskArns[0]" --output text
aws ecs describe-tasks --cluster meme-cluster --tasks $task --region ap-south-1 \
  --query "tasks[0].attachments[0].details[?name=='privateIPv4Address'].value" --output text
```

Register it:

```bash
aws elbv2 register-targets \
  --target-group-arn <TARGET_GROUP_ARN> \
  --targets Id=<TASK_PRIVATE_IP>,Port=8080 \
  --region ap-south-1
```

Allow port 8080 within the security group (ALB to ECS):

```bash
aws ec2 authorize-security-group-ingress \
  --group-id <ECS_SG_ID> \
  --protocol tcp \
  --port 8080 \
  --source-group <ECS_SG_ID> \
  --region ap-south-1
```

Verify the target is healthy:

```bash
aws elbv2 describe-target-health \
  --target-group-arn <TARGET_GROUP_ARN> \
  --region ap-south-1 \
  --query "TargetHealthDescriptions[*].{Id:Target.Id,State:TargetHealth.State}"
```

---

## Step 8 — Frontend (S3 + CloudFront)

The React app is built as static files and served from S3 via CloudFront (HTTPS).

Set the production API URL in `meme-arena-frontend/.env.production`:

```
VITE_API_URL=https://<CLOUDFRONT_DOMAIN>
```

Build the frontend:

```bash
cd meme-arena-frontend
npm run build
```

Create the S3 bucket:

```bash
aws s3api create-bucket \
  --bucket meme-arena-frontend-<ACCOUNT_ID> \
  --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1
```

Enable static website hosting:

```bash
aws s3 website s3://meme-arena-frontend-<ACCOUNT_ID> \
  --index-document index.html \
  --error-document index.html
```

Make it publicly readable:

```bash
aws s3api put-public-access-block \
  --bucket meme-arena-frontend-<ACCOUNT_ID> \
  --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"

aws s3api put-bucket-policy \
  --bucket meme-arena-frontend-<ACCOUNT_ID> \
  --policy '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::meme-arena-frontend-<ACCOUNT_ID>/*"}]}'
```

Upload the build:

```bash
aws s3 sync build/ s3://meme-arena-frontend-<ACCOUNT_ID> --delete
```

Create CloudFront distribution using a config file (PowerShell doesn't handle inline JSON well):

```json
// cloudfront-config.json
{
  "CallerReference": "meme-arena-1",
  "Origins": { ... },
  "DefaultCacheBehavior": { ... },
  "CacheBehaviors": {
    "Items": [{ "PathPattern": "/api/*", "TargetOriginId": "BackendOrigin" }]
  },
  "DefaultRootObject": "index.html",
  "Enabled": true
}
```

```bash
aws cloudfront create-distribution \
  --distribution-config file://cloudfront-config.json \
  --query "Distribution.{Id:Id,Domain:DomainName}"
```

Wait 5-10 minutes for CloudFront to deploy globally, then open the domain in your browser.

---

## Redeployment (After Code Changes)

### Backend

```bash
cd gdg
./mvnw clean package -DskipTests -B
Copy-Item target\*.jar app.jar
docker build -f Dockerfile.prebuilt -t meme-arena-backend .
Remove-Item app.jar
docker tag meme-arena-backend:latest <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend:latest
docker push <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend:latest
aws ecs update-service --cluster meme-cluster --service meme-arena-service --force-new-deployment --region ap-south-1
```

After ECS restarts the task, the IP changes. Re-register the new IP with the ALB target group (see Step 7).

### Frontend

```bash
cd meme-arena-frontend
npm run build
aws s3 sync build/ s3://meme-arena-frontend-<ACCOUNT_ID> --delete
aws cloudfront create-invalidation --distribution-id <CF_DISTRIBUTION_ID> --paths "/*"
```

---

## CI/CD (GitHub Actions)

The `.github/workflows/deploy.yml` file automates all of the above on every push to `main`.

Add these secrets to your GitHub repo (Settings → Secrets → Actions):
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

After that, every `git push origin main` triggers a full build and deploy automatically.

---

## Current Resource Reference

| Resource | Value |
|----------|-------|
| RDS Endpoint | `meme-arena-db.cv6gcmoks21t.ap-south-1.rds.amazonaws.com` |
| ECR Repository | `562273658670.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend` |
| ECS Cluster | `meme-cluster` |
| ECS Service | `meme-arena-service` |
| ALB DNS | `meme-arena-alb-2075408473.ap-south-1.elb.amazonaws.com` |
| CloudFront Domain | `d1i3pilqzxap5e.cloudfront.net` |
| S3 Bucket | `meme-arena-frontend-562273658670` |
| CloudFront ID | `E331210BH0NF20` |
| Region | `ap-south-1` |
