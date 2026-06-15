# DeployGuard AI Frontend

Next.js dashboard for DeployGuard AI.

Current status: connects to the local Spring Boot backend APIs. Authentication is not implemented yet.

## Requirements

- Node.js 20+
- npm 10+
- DeployGuard backend running locally

## Configuration

The frontend uses `NEXT_PUBLIC_API_BASE_URL` for backend requests.

Default:

```text
http://localhost:8080
```

Override example:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

## Install Dependencies

```bash
cd deployguard-ai/frontend
npm install
```

## Run Development Server

```bash
npm run dev
```

Local URL:

```text
http://localhost:3000
```

## Pages

- `/` Dashboard
- `/projects` Projects
- `/deployments` Deployments
- `/deployments/{deploymentId}` Deployment detail

## Backend Data Used

- Projects
- Deployments
- Deployment logs
- CI runs by project and commit
- AI summaries
- Async AI jobs
- Risk recalculation action
- Sync and async AI analysis actions
