# Admin authentication deployment

The admin now supports password and Google Identity Services sign-in. Both issue
an `HttpOnly` cookie; browser JavaScript does not store the credential.

## Render backend variables

- `JWT_SECRET`: at least 32 cryptographically random bytes (no fallback is used)
- `GOOGLE_CLIENT_ID`: Google OAuth Web client ID
- `ADMIN_SETUP_SECRET`: a long one-time secret; remove it after the first admin exists
- `AUTH_COOKIE_SECURE=true`
- `AUTH_COOKIE_SAME_SITE=Lax`
- `AUTH_COOKIE_NAME=ah_admin_session`
- `AUTH_REMEMBER_DURATION_SECONDS=604800`
- `CORS_ALLOWED_ORIGINS`: the exact production frontend origin
- `FRONTEND_URL=https://hair-braiding-coral.vercel.app`: trusted base URL used in invitation and reset links
- `EMAIL_USERNAME`: Gmail account that sends administrator security emails
- `EMAIL_PASSWORD`: a Google App Password, never the Gmail account password

For local HTTP development set `AUTH_COOKIE_SECURE=false`.

## Vercel frontend variables

- `BACKEND_API_URL=https://backend-hairbraiding.onrender.com`
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID`: the same Google OAuth Web client ID

Do not set `NEXT_PUBLIC_API_URL` in new deployments. Browser requests use the
same-origin `/backend-api` proxy so the secure session works reliably.

## Google Cloud configuration

Create an OAuth 2.0 Web application and add these Authorized JavaScript origins:

- the production frontend origin
- `http://localhost:3000` for local testing

Google login is allowlisted through the existing `admin` table. The verified
Google email must exactly match an existing admin email; signing in with another
Google account never creates an administrator.
