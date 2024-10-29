# Websocket Communication

## Connection config

`ws://$HOST:8123/api/websocket`

## Authenticate
``` json
{
    "type": "auth",
    "access_token": "xxx"
}
```

## Request States
``` json
{
    "id": 1,
    "type": "get_states"
}
```

## Request Services
``` json
{
    "id": 2,
    "type": "get_services"
}
```

## Request Devices
``` json
{
    "id": 3,
    "type": "config/device_registry/list"
}
```

## Request Areas
``` json
{
    "id": 4,
    "type": "config/area_registry/list"
}
```

## Subscribe to state change
```
{
  "id": 18,
  "type": "subscribe_events",
  "event_type": "state_changed"
}
```
