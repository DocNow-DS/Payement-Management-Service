# Payment Service Kubernetes Deployment

## Prerequisites
- Kubernetes cluster (Minikube, Docker Desktop Kubernetes, or cloud cluster)
- kubectl configured
- Docker image available in cluster runtime

## 1) Build Docker image
From the payment service root:

```bash
docker build -t payment-service:latest .
```

If you use Minikube:

```bash
minikube image load payment-service:latest
```

## 2) Set Stripe values in secret
Edit `k8s/secret.yaml` and set:
- `STRIPE_SECRET_KEY`
- `STRIPE_PUBLISHABLE_KEY`
- `STRIPE_WEBHOOK_SECRET`

## 3) Deploy resources

```bash
kubectl apply -k k8s
```

## 4) Verify deployment

```bash
kubectl get pods -n healthcare
kubectl get svc -n healthcare
```

## 5) Access service
Payment API is exposed via NodePort:
- http://localhost:30085

## 6) Delete deployment

```bash
kubectl delete -k k8s
```
