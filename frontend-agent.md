# Frontend Agent Brief

Build a frontend for this uptime monitoring backend.

## Product

The app lets users sign up, sign in, create URL monitors, view monitor uptime, inspect logs, toggle monitors active/inactive, and delete monitors.

## Backend

- API base path: `/api/v1`
- Auth: cookie-based JWT
- Cookie name: `JwtToken`
- The backend sets the auth cookie on sign in.
- Frontend requests must include credentials/cookies.
- CORS is disabled in the backend, so use same-origin hosting or a dev proxy to the backend.

Example fetch defaults:

```js
fetch("/api/v1/monitors", {
  headers: { "Content-Type": "application/json" },
  credentials: "include",
});
```

## Required Screens

- Sign up
- Sign in
- Dashboard monitor list
- Create monitor form
- Monitor detail with status and logs
- Empty states
- Loading states
- Error states

## Routes

- `/signin`
- `/signup`
- `/monitors`
- `/monitors/:monitorId`

Redirect unauthenticated users to `/signin`.

There is no single-monitor endpoint. For `/monitors/:monitorId`, use the monitor from `GET /api/v1/monitors`, then load status and logs with the detail endpoints.

## API

### Health

`GET /api/v1/health`

Response:

```json
{
  "status": "up"
}
```

### Sign Up

`POST /api/v1/auth/signup/user`

Request:

```json
{
  "username": "Vinay",
  "email": "vinay@example.com",
  "password": "password123"
}
```

Response `201`:

```json
{
  "id": "uuid",
  "username": "Vinay"
}
```

### Sign In

`POST /api/v1/auth/signin/user`

Request:

```json
{
  "email": "vinay@example.com",
  "password": "password123"
}
```

Response `200`:

```json
{
  "success": true
}
```

After this response, the browser should store the `JwtToken` cookie.

### Validate Session

`GET /api/v1/auth/validate`

Response `200`:

```json
{
  "success": true
}
```

Use this endpoint on app load to check auth.

### Create Monitor

`POST /api/v1/monitors`

Request:

```json
{
  "name": "Main API",
  "url": "https://example.com/health",
  "intervalMilliseconds": 60000,
  "timeoutMilliseconds": 5000,
  "monitorMethod": "GET"
}
```

Notes:

- `monitorMethod`: `GET` or `POST`
- `monitorMethod` is case-insensitive
- blank method defaults to `GET`
- `intervalMilliseconds` must be greater than `0`
- `timeoutMilliseconds` must be greater than `0`

Response `201`:

```json
{
  "id": "uuid",
  "name": "Main API",
  "url": "https://example.com/health",
  "active": true,
  "createdAt": "2026-05-07T20:30:00"
}
```

Important: Java boolean field `isActive` serializes as `active`.

### List Monitors

`GET /api/v1/monitors`

Response `200`:

```json
[
  {
    "id": "uuid",
    "name": "Main API",
    "url": "https://example.com/health",
    "active": true,
    "method": "GET",
    "nextCheckAt": "2026-05-07T20:31:00",
    "uptimePercentage": 99.5
  }
]
```

### Toggle Monitor

`PATCH /api/v1/monitors/:monitorId/toggle`

Request:

```json
{
  "active": false
}
```

Response: `202`

### Delete Monitor

`DELETE /api/v1/monitors/:monitorId`

Response: `204`

### Monitor Status

`GET /api/v1/monitors/:monitorId/status`

Response `200`:

```json
{
  "up": true,
  "uptimePercentage": 99.5,
  "totalChecks": 200,
  "totalUp": 199,
  "totalDown": 1,
  "lastDowntimeAt": "2026-05-07T19:10:00",
  "lastCheckedAt": "2026-05-07T20:30:00"
}
```

Important: Java boolean field `isUp` serializes as `up`.

### Monitor Logs

`GET /api/v1/monitors/:monitorId/logs?page=0&size=20`

Response `200`:

```json
{
  "content": [
    {
      "statusCode": 200,
      "up": true,
      "responseTimeInMilli": 123,
      "errorMessage": null,
      "checkedAt": "2026-05-07T20:30:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

Important: Java boolean field `isUp` serializes as `up`.
Spring may include extra `pageable` and `sort` objects. Frontend should read `content`, `number`, `size`, `totalElements`, and `totalPages`.

## Error Shape

Errors may return:

```json
{
  "timestamp": "2026-05-07T20:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "intervalMilliseconds must be greater than 0",
  "path": "/api/v1/monitors",
  "validationErrors": null
}
```

Show `message` when present.

## UI Requirements

- Dashboard should show monitor name, URL, method, active state, next check time, and uptime.
- Monitor detail should show current up/down state, uptime percentage, totals, last checked time, last downtime time, and paginated logs.
- Create form should validate URL, interval, timeout, and method before submit.
- Toggle control should optimistically update only if the request succeeds.
- Delete should ask for confirmation.
- Use clear status colors:
  - up: green
  - down: red
  - inactive: gray
- Keep the UI practical and dashboard-like, not a marketing landing page.

## Auth Behavior

- On app load, call `GET /api/v1/auth/validate`.
- If `200`, load monitors.
- If `401`, redirect to `/signin`.
- After sign in, redirect to `/monitors`.
- After sign up, redirect to `/signin` or sign the user in if desired.
- Sign in cookie currently has `HttpOnly`, path `/`, and `Secure=false`, so it is intended for local HTTP/same-origin development.

## Development Notes

- Backend default port: `8080`
- If frontend runs on another port, proxy `/api` to `http://localhost:8080`.
- Always send requests with credentials.
- No logout endpoint exists yet. Because the auth cookie is `HttpOnly`, the frontend cannot reliably clear it; logout UI can only clear local app state until the backend adds a logout endpoint.
- Ignore the empty `/api/monitors` controller; all real monitor APIs are under `/api/v1/monitors`.
