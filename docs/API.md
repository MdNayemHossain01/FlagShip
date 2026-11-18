# FlagShip REST API Reference

The FlagShip Server exposes two primary API interfaces:
1. **SDK Client APIs (`/api/v1/sdk/**`)**: High-performance endpoints consumed by the `flagship-spring-boot-starter`.
2. **Administrative Management APIs (`/api/v1/admin/**`)**: Role-guarded endpoints for automated flag operations, approvals, and audits.

---

## 1. SDK Client APIs

### 1.1 Fetch Environment Snapshot
Retrieves the complete set of active flags, variations, targeting rules, and rollout definitions for local evaluation.

- **URL**: `GET /api/v1/sdk/snapshot`
- **Query Parameters**:
  - `project` (string, optional, default: `"default"`): Project identifier.
  - `env` (string, optional, default: `"production"`): Environment key (`development`, `staging`, `production`).
- **Headers**:
  - `X-FlagShip-Key`: Environment API key (e.g., `fs_live_default`).
- **Response `200 OK`**:
```json
{
  "revision": 101,
  "syncedAt": "2026-11-18T07:46:14.325565700Z",
  "flags": {
    "bkash-one-click-checkout": {
      "key": "bkash-one-click-checkout",
      "type": "BOOLEAN",
      "enabled": true,
      "offVariation": "off",
      "defaultVariation": "off",
      "variations": {
        "on": true,
        "off": false
      },
      "individualTargets": {
        "on": ["nayem1541", "vip-user-1"]
      },
      "rules": [
        {
          "id": "rule-bd-v2",
          "name": "Bangladesh App v2+",
          "variation": "on",
          "rootNode": {
            "type": "AND",
            "children": [
              { "type": "LEAF", "attribute": "country", "operator": "EQUALS", "values": ["BD"] },
              { "type": "LEAF", "attribute": "appVersion", "operator": "SEMVER_GREATER_THAN", "values": ["2.0.0"] }
            ]
          }
        }
      ],
      "percentageRollout": {
        "buckets": [
          { "variation": "on", "weight": 20000 },
          { "variation": "off", "weight": 80000 }
        ]
      },
      "salt": "v1"
    }
  }
}
```

---

### 1.2 Live Configuration Stream (Server-Sent Events)
Establishes a real-time HTTP event stream for live snapshot updates.

- **URL**: `GET /api/v1/sdk/stream`
- **Query Parameters**:
  - `project`: Project identifier.
  - `env`: Environment key.
- **Headers**:
  - `Accept: text/event-stream`
- **Stream Event Format**:
```
event: snapshot
data: {"revision":102,"flags":{...}}
```

---

### 1.3 Batch Event Ingestion
Ingests batched exposure and goal conversion events from client SDK instances for statistical analytics.

- **URL**: `POST /api/v1/sdk/events`
- **Content-Type**: `application/json`
- **Body**:
```json
[
  {
    "type": "EXPOSURE",
    "flagKey": "bkash-one-click-checkout",
    "variation": "on",
    "userKey": "usr_991823"
  },
  {
    "type": "GOAL",
    "flagKey": "bkash-one-click-checkout",
    "goalName": "checkout_completed",
    "variation": "on",
    "userKey": "usr_991823",
    "value": 1500.00
  }
]
```
- **Response `200 OK`**:
```json
{
  "status": "ok",
  "ingested": 2
}
```

---

## 2. Administrative Management APIs

### 2.1 Instant Kill Switch Toggle
- **URL**: `POST /api/v1/admin/flags/{flagKey}/toggle`
- **Role Required**: `EDITOR` or `ADMIN`
- **Body (form-data / query)**:
  - `project`: Project key.
  - `env`: Environment key.
  - `enabled`: `true` / `false`.
  - `reason`: Reason for change.
- **Response `200 OK`**:
```json
{
  "appliedImmediately": true,
  "requiresApproval": false,
  "message": "Flag state updated successfully."
}
```
*(Note: If targeting `production` and approval is required, `requiresApproval` is `true` and change is routed to `/approvals`)*.

---

### 2.2 Submit Production Approval Decision
- **URL**: `POST /api/v1/admin/approvals/{id}/decision`
- **Role Required**: `APPROVER` or `ADMIN` (Original change author cannot approve their own request)
- **Body**:
  - `decision`: `APPROVE` or `REJECT`.
  - `reason`: Optional reviewer note.
- **Response `200 OK`**:
```json
{
  "status": "APPROVED",
  "appliedAt": "2026-11-18T13:43:40Z",
  "newVersion": 3
}
```

---

### 2.3 One-Action Rollback
- **URL**: `POST /audit/rollback`
- **Role Required**: `EDITOR` or `ADMIN`
- **Form Data**:
  - `project`: Project key.
  - `env`: Environment key.
  - `flagKey`: Feature flag key.
  - `targetVersion`: Version number to restore (e.g., `2`).
- **Response `302 Found`**: Redirects to `/audit` with status notification.
