# Security Notes

DeployGuard AI is currently a local development project. Production hardening is not complete.

## Do Not Commit Secrets

Never commit:

- OpenRouter API keys
- database passwords for hosted environments
- RabbitMQ passwords for hosted environments
- tokens
- private URLs
- webhook secrets
- `.env` files

Use `.env.example` files for placeholders only.

## OpenRouter Keys

OpenRouter keys belong only in:

- a local `.env` file ignored by Git
- a hosted secret manager
- a CI/CD secret store

Do not paste real OpenRouter keys into README files, docs, scripts, source code, commit messages, issues, or pull request comments.

## Docker Compose Credentials

The Docker Compose credentials in this repository are intentionally simple:

```text
deployguard / deployguard
```

They are for local development only. Do not reuse them in hosted environments.

## GitHub Push Protection

GitHub push protection and secret scanning can block pushes when a secret is detected in a commit. Treat the block as a real leak unless proven otherwise.

Do not use secret-unblock links for real API keys. Remove the secret from the file and from Git history before pushing.

## If A Key Is Leaked

1. Revoke or rotate the leaked key in the provider dashboard.
2. Remove the key from the working tree.
3. Ensure the matching `.env` file is ignored.
4. Rewrite any local commits that contain the key.
5. Verify the secret is absent from `HEAD`.
6. Push the cleaned commit history.
7. Create a new key only after the repository is clean.

For the OpenRouter key prefix, verify with:

```bash
git grep "sk""-or-" HEAD
```

No output means the key prefix was not found in the current committed tree.

## Current Security Limitations

- No authentication is implemented.
- No authorization is implemented.
- No multi-tenancy is implemented.
- No production secret manager is configured.
- No hosted deployment is configured.
- No distributed tracing is implemented.
