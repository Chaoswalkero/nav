# Patriot Navigation API - Vollständige Dokumentation

## Überblick

Die Patriot Navigation API ist ein REST-basierter Routing-Service, der optimale Routen zwischen zwei geografischen Punkten berechnet. Der Service berücksichtigt verschiedene Fahrzeugtypen mit individuellen Profilen und bietet detaillierte Turn-by-Turn Navigationsanweisungen.

**Basis-URL**: `http://localhost:8080/api/routing`

---

## Endpoints

### 1. Route berechnen

#### Beschreibung
Berechnet die optimale Route zwischen zwei geografischen Punkten unter Berücksichtigung des gewählten Fahrzeugprofils.  
Der Dienst sucht zunächst den nächstliegenden Knoten im Graphen – nur solche, die für das angegebene Fahrzeugprofil befahrbar sind – und schlägt andernfalls mit einem Fehler fehl.

#### HTTP Methode
`POST /api/routing/route`

#### Content-Type
`application/json`

#### Request Body

```json
{
  "start_lat": 48.1372,
  "start_lon": 11.5755,
  "end_lat": 48.1335,
  "end_lon": 11.5820,
  "vehicle": {
    "name": "car",
    "max_speed": 130,
    "way_multipliers": {
      "motorway": 0.6,
      "trunk": 0.65,
      "primary": 0.7,
      "secondary": 0.8,
      "tertiary": 0.9,
      "residential": 1.2
    },
    "tag_weights": {
      "surface=paved_smooth": 0.95,
      "surface=paved": 1.0,
      "surface=unpaved": 1.5
    },
    "blocked_tags": [
      "foot=only",
      "bicycle=only"
    ],
    "access": {
      "motorVehicle": true,
      "foot": false,
      "bicycle": false
    },
    "heuristic_weight": 1.5,
    "ignore_one_way": false
  }
}
```

#### Parameter-Erklärung

| Parameter | Typ | Erforderlich | Beschreibung |
|-----------|-----|--------------|-------------|
| `start_lat` | Number | Ja | Breitengrad des Startpunktes (-90 bis 90) |
| `start_lon` | Number | Ja | Längengraf des Startpunktes (-180 bis 180) |
| `end_lat` | Number | Ja | Breitengrad des Endpunktes |
| `end_lon` | Number | Ja | Längengraf des Endpunktes |
| `vehicle` | Object | Nein | Fahrzeugprofil (optional, Standard: car) |

#### Vehicle Profile Parameter

| Parameter | Typ | Beschreibung |
|-----------|-----|-------------|
| `name` | String | Name des Fahrzeugs (z.B. car, bicycle, foot) |
| `max_speed` | Number | Maximale Geschwindigkeit in km/h |
| `way_multipliers` | Object | Weg-Typ-Multiplikatoren (< 1 = bevorzugt, > 1 = vermieden) |
| `tag_weights` | Object | OSM-Tag Gewichtungen |
| `blocked_tags` | Array | OSM-Tags, die den Weg blockieren |
| `access` | Object | Zugriffsrechte für verschiedene Fahrzeugtypen |
| `heuristic_weight` | Number | A* Heuristik-Gewicht (1.0-2.0) |
| `ignore_one_way` | Boolean | Einbahnstraßen ignorieren |

#### Response (Success)

```json
{
  "status": "ok",
  "error": null,
  "geometry": {
    "type": "FeatureCollection",
    "features": [
      {
        "type": "Feature",
        "geometry": {
          "type": "LineString",
          "coordinates": [
            [11.5755, 48.1372],
            [11.5760, 48.1370],
            [11.5820, 48.1335]
          ]
        },
        "properties": {}
      }
    ]
  },
  "points": [
    {
      "lat": 48.1372,
      "lon": 11.5755,
      "distance": 0,
      "time": 0
    },
    {
      "lat": 48.1370,
      "lon": 11.5760,
      "distance": 125,
      "time": 15
    },
    {
      "lat": 48.1335,
      "lon": 11.5820,
      "distance": 982,
      "time": 125
    }
  ],
  "distance": 982,
  "time": 125,
  "vehicleType": "car",
  "turn_instructions": [
    {
      "sequence": 0,
      "type": "START",
      "instruction": "Start auf Marienplatz",
      "way_name": "Marienplatz",
      "distance": 125,
      "time": 15,
      "latitude": 48.1372,
      "longitude": 11.5755,
      "bearing": 45.5
    },
    {
      "sequence": 1,
      "type": "LEFT",
      "instruction": "Biegen Sie links auf Kaufingerstraße ab",
      "way_name": "Kaufingerstraße",
      "distance": 857,
      "time": 110,
      "latitude": 48.1370,
      "longitude": 11.5760,
      "bearing": 42.0
    },
    {
      "sequence": 2,
      "type": "FINISH",
      "instruction": "Ziel erreicht",
      "way_name": "Sendlinger Straße",
      "distance": 0,
      "time": 0,
      "latitude": 48.1335,
      "longitude": 11.5820,
      "bearing": 40.0
    }
  ]
}
```

#### Response (Fehler)

The service will return `status: "error"` for a variety of problems. Common cases include:

* Start or end coordinates not provided or outside the loaded graph
* Graph has not been initialized
* **Start or end node is not accessible** for the requested vehicle type (e.g. the
  nearest node lies on a footpath but the profile is for a car)

```json
{
  "status": "error",
  "error": "Start location not accessible for vehicle type",
  "geometry": null,
  "points": null,
  "distance": 0,
  "time": 0,
  "turn_instructions": null,
  "vehicleType": null
}
```

#### Status Codes

| Status | HTTP Code | Beschreibung |
|--------|-----------|------------|
| `ok` | 200 | Route erfolgreich berechnet |
| `error` | 400 | Fehler bei Routenberechnung |
| `no_route` | 200 | Keine Route gefunden |

#### Beispiel cURL Request

```bash
curl -X POST http://localhost:8080/api/routing/route \
  -H "Content-Type: application/json" \
  -d '{
    "start_lat": 48.1372,
    "start_lon": 11.5755,
    "end_lat": 48.1335,
    "end_lon": 11.5820,
    "vehicle": {
      "name": "car"
    }
  }'
```

#### Beispiel JavaScript/Fetch

```javascript
const routeRequest = {
  start_lat: 48.1372,
  start_lon: 11.5755,
  end_lat: 48.1335,
  end_lon: 11.5820,
  vehicle: {
    name: "car",
    max_speed: 130
  }
};

fetch('http://localhost:8080/api/routing/route', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(routeRequest)
})
.then(response => response.json())
.then(data => {
  if (data.status === 'ok') {
    console.log(`Route: ${data.distance}m in ${data.time}s`);
    data.turn_instructions.forEach(instruction => {
      console.log(`${instruction.sequence}: ${instruction.instruction}`);
    });
  }
});
```

---

### 2. Verfügbare Fahrzeuge auflisten

#### Beschreibung
Gibt eine Liste aller vorkonfigurierten Fahrzeugprofile zurück.

#### HTTP Methode
`GET /api/routing/vehicles`

#### Response

```json
[
  "car",
  "bicycle",
  "foot",
  "motorcycle",
  "truck",
  "emergency"
]
```

#### Beispiel cURL Request

```bash
curl http://localhost:8080/api/routing/vehicles
```

#### Beispiel JavaScript/Fetch

```javascript
fetch('http://localhost:8080/api/routing/vehicles')
  .then(response => response.json())
  .then(vehicles => console.log('Verfügbare Fahrzeuge:', vehicles));
```

---

### 3. Fahrzeugprofil abrufen

#### Beschreibung
Ruft das vollständige Profil eines spezifischen Fahrzeugs ab.

#### HTTP Methode
`GET /api/routing/vehicle/{name}`

#### URL Parameter

| Parameter | Typ | Beschreibung |
|-----------|-----|-------------|
| `name` | String | Name des Fahrzeugs (z.B. car, bicycle, foot) |

#### Response

```json
{
  "name": "car",
  "max_speed": 130,
  "way_multipliers": {
    "motorway": 0.6,
    "trunk": 0.65,
    "primary": 0.7,
    "secondary": 0.8,
    "tertiary": 0.9,
    "residential": 1.2,
    "unclassified": 1.0,
    "service": 1.3,
    "living_street": 1.5
  },
  "tag_weights": {
    "surface=paved_smooth": 0.95,
    "surface=paved": 1.0,
    "surface=unpaved": 1.5
  },
  "blocked_tags": [
    "foot=only",
    "bicycle=only",
    "motor_vehicle=no"
  ],
  "access": {
    "motorVehicle": true,
    "foot": false,
    "bicycle": false,
    "bus": false,
    "taxi": false,
    "emergency": false
  },
  "heuristic_weight": 1.5,
  "ignore_one_way": false
}
```

#### Beispiel cURL Request

```bash
curl http://localhost:8080/api/routing/vehicle/car
```

#### Beispiel JavaScript/Fetch

```javascript
fetch('http://localhost:8080/api/routing/vehicle/car')
  .then(response => response.json())
  .then(profile => {
    console.log(`Max Speed: ${profile.max_speed} km/h`);
    console.log(`Way Multipliers:`, profile.way_multipliers);
  });
```

---

### 4. Fahrzeugprofil speichern (Custom)

#### Beschreibung
Speichert ein benutzerdefiniertes Fahrzeugprofil mit einem neuen Namen.

#### HTTP Methode
`POST /api/routing/vehicle/{name}`

#### URL Parameter

| Parameter | Typ | Beschreibung |
|-----------|-----|-------------|
| `name` | String | Name für das neue Profil |

#### Request Body

```json
{
  "name": "my_car",
  "max_speed": 100,
  "way_multipliers": {
    "motorway": 0.5,
    "residential": 1.5
  },
  "tag_weights": {},
  "blocked_tags": [],
  "access": {
    "motorVehicle": true,
    "foot": false,
    "bicycle": false
  },
  "heuristic_weight": 1.5,
  "ignore_one_way": false
}
```

#### Response (Success)

```json
"Profile 'my_car' saved successfully"
```

#### Response (Fehler)

```json
"Error saving profile: IO Exception Message"
```

#### HTTP Status Codes

| Status | Code | Beschreibung |
|--------|------|------------|
| Success | 200 | Profil erfolgreich gespeichert |
| Error | 400 | Fehler beim Speichern |

#### Beispiel cURL Request

```bash
curl -X POST http://localhost:8080/api/routing/vehicle/my_custom_car \
  -H "Content-Type: application/json" \
  -d '{
    "max_speed": 100,
    "way_multipliers": {
      "motorway": 0.5,
      "residential": 1.5
    },
    "access": {
      "motorVehicle": true
    },
    "heuristic_weight": 1.5
  }'
```

#### Beispiel JavaScript/Fetch

```javascript
const customProfile = {
  max_speed: 100,
  way_multipliers: {
    motorway: 0.5,
    residential: 1.5
  },
  access: {
    motorVehicle: true
  },
  heuristic_weight: 1.5
};

fetch('http://localhost:8080/api/routing/vehicle/my_custom_car', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(customProfile)
})
.then(response => response.json())
.then(message => console.log(message));
```

---

### 5. Fahrzeugoptionen (OPTIONS Request)

#### Beschreibung
Gibt die verfügbaren Fahrzeug-Profile und deren Schema mit detaillierten Typ-Informationen zurück.

#### HTTP Methode
`OPTIONS /api/routing/vehicles`

#### Response

```json
{
  "vehicle_schema": {
    "name": { 
      "type": "String" 
    },
    "maxSpeed": { 
      "type": "Int" 
    },
    "wayMultipliers": { 
      "type": "Map", 
      "mapType": "Map<String, Double>" 
    },
    "tagWeights": { 
      "type": "Map", 
      "mapType": "Map<String, Double>" 
    },
    "blockedTags": { 
      "type": "List", 
      "elementType": "String" 
    },
    "access": { 
      "type": "Object",
      "fields": { "motorVehicle": {...}, "foot": {...}, "bicycle": {...} }
    }
  },
  "access_schema": {
    "motorVehicle": { "type": "Boolean" },
    "foot": { "type": "Boolean" },
    "bicycle": { "type": "Boolean" }
  },
  "profile_values": {
    "way_multipliers_keys": ["motorway", "trunk", "primary", "secondary", "tertiary", "residential", "unclassified", "service", "living_street"],
    "tag_weights_keys": ["surface=paved_smooth", "surface=paved", "surface=unpaved"],
    "blocked_tags_values": ["foot=only", "bicycle=only", "motor_vehicle=no"]
  }
}
```

#### Response-Struktur Erklärung

| Element | Beschreibung |
|---------|-------------|
| `vehicle_schema` | Definiert die Struktur und Typen aller Fahrzeugprofil-Parameter |
| `access_schema` | Spezifiziert die Zugriffsrechte-Struktur mit Boolean-Feldern |
| `profile_values` | Enthält konkrete Werte für Multiplier-Keys, Tag-Weights und blockierte Tags |

#### Beispiel cURL Request

```bash
curl -X OPTIONS http://localhost:8080/api/routing/vehicles
```

---

### 6. Health Check

#### Beschreibung
Überprüft, ob der Routing-Service läuft.

#### HTTP Methode
`GET /api/routing/health`

#### Response

```json
"Routing service is running"
```

#### HTTP Status Code

| Status | Code | Beschreibung |
|--------|------|------------|
| Running | 200 | Service läuft |

#### Beispiel cURL Request

```bash
curl http://localhost:8080/api/routing/health
```

---

## Fehlerbehandlung

### Mögliche Fehler

| Fehler | HTTP Code | Beschreibung |
|--------|-----------|------------|
| `Start- oder Endpunkt liegt außerhalb des Kartenmaterials` | 400 | Koordinaten liegen nicht im verfügbaren Kartensatz |
| `Invalid JSON format` | 400 | Request-Body ist keine gültige JSON |
| `Vehicle profile not found` | 400 | Fahrzeugprofil existiert nicht |
| `IO Exception` | 400 | Fehler beim Zugriff auf Datensystem |
| `No route found` | 200 | Keine Route zwischen den Punkten möglich |

### Error Response Format

```json
{
  "status": "error",
  "error": "Fehlerbeschreibung",
  "geometry": null,
  "points": null,
  "distance": 0,
  "time": 0,
  "turn_instructions": null,
  "vehicleType": null
}
```

---

## Authentifizierung

Aktuell ist für die API **keine Authentifizierung** erforderlich. Der Service ist mit `@CrossOrigin(origins = "*")` konfiguriert und akzeptiert Anfragen von allen Ursprüngen.

---

## CORS Configuration

Die API unterstützt CORS (Cross-Origin Resource Sharing):

```
Access-Control-Allow-Origin: *
```

Dies ermöglicht es, die API von Web-Anwendungen aus verschiedenen Domains zu nutzen.

---

## Koordinatensysteme

- **Latitude (Breitengrad)**: -90 bis +90
- **Longitude (Längengraf)**: -180 bis +180
- **Verwendetes Koordinatensystem**: WGS84 (EPSG:4326)

---

## Fahrzeugtypen

### Vorkonfigurierte Profile

#### 1. Car (Auto)

Optimiert für Autos mit Motorwagen-Zugang.

```json
{
  "name": "car",
  "max_speed": 130,
  "way_multipliers": {
    "motorway": 0.6,
    "trunk": 0.65,
    "primary": 0.7,
    "secondary": 0.8,
    "tertiary": 0.9,
    "residential": 1.2
  },
  "blocked_tags": ["foot=only", "bicycle=only", "motor_vehicle=no"],
  "access": {
    "motorVehicle": true
  }
}
```

#### 2. Bicycle (Fahrrad)

Optimiert für Fahrräder mit bevorzugten Radwegen.

```json
{
  "name": "bicycle",
  "max_speed": 40,
  "way_multipliers": {
    "cycleway": 0.5,
    "residential": 0.8,
    "primary": 1.5,
    "motorway": 50
  },
  "blocked_tags": ["motorway=*"],
  "access": {
    "bicycle": true
  }
}
```

#### 3. Foot (Fußgänger)

Optimiert für Fußverkehr.

```json
{
  "name": "foot",
  "max_speed": 5,
  "way_multipliers": {
    "residential": 0.8,
    "trunk": 2.0
  },
  "blocked_tags": ["motorway=*", "trunk=*"],
  "access": {
    "foot": true
  }
}
```

#### 4. Motorcycle (Motorrad)

Optimiert für Motorräder.

```json
{
  "name": "motorcycle",
  "max_speed": 180,
  "way_multipliers": {
    "motorway": 0.5,
    "trunk": 0.6
  },
  "blocked_tags": ["motor_vehicle=no"],
  "access": {
    "motorVehicle": true
  }
}
```

#### 5. Truck (Lastkraftwagen)

Optimiert für LKWs mit Einschränkungen.

```json
{
  "name": "truck",
  "max_speed": 100,
  "way_multipliers": {
    "residential": 1.5,
    "living_street": 50
  },
  "blocked_tags": ["motor_vehicle=no", "hgv=no"],
  "access": {
    "hgv": true
  }
}
```

#### 6. Emergency (Notfall)

Optimiert für Notfallfahrzeuge mit Vorrang.

```json
{
  "name": "emergency",
  "max_speed": 200,
  "way_multipliers": {
    "motorway": 0.4,
    "all": 0.8
  },
  "blocked_tags": [],
  "access": {
    "emergency": true
  }
}
```

---

## Praktische Beispiele

### Beispiel 1: Einfache Route für Auto

```bash
curl -X POST http://localhost:8080/api/routing/route \
  -H "Content-Type: application/json" \
  -d '{
    "start_lat": 48.1372,
    "start_lon": 11.5755,
    "end_lat": 48.1335,
    "end_lon": 11.5820,
    "vehicle": {
      "name": "car"
    }
  }'
```

### Beispiel 2: Route für Fahrrad mit Custom-Profil

```bash
curl -X POST http://localhost:8080/api/routing/route \
  -H "Content-Type: application/json" \
  -d '{
    "start_lat": 48.1372,
    "start_lon": 11.5755,
    "end_lat": 48.1335,
    "end_lon": 11.5820,
    "vehicle": {
      "name": "bicycle",
      "max_speed": 35,
      "way_multipliers": {
        "cycleway": 0.4,
        "residential": 0.7,
        "primary": 2.0
      }
    }
  }'
```

### Beispiel 3: Route für Fußgänger

```bash
curl -X POST http://localhost:8080/api/routing/route \
  -H "Content-Type: application/json" \
  -d '{
    "start_lat": 48.1372,
    "start_lon": 11.5755,
    "end_lat": 48.1335,
    "end_lon": 11.5820,
    "vehicle": {
      "name": "foot"
    }
  }'
```

### Beispiel 4: Komplexes Profil mit benutzerdefinierten Einstellungen

```bash
curl -X POST http://localhost:8080/api/routing/route \
  -H "Content-Type: application/json" \
  -d '{
    "start_lat": 48.1372,
    "start_lon": 11.5755,
    "end_lat": 48.1335,
    "end_lon": 11.5820,
    "vehicle": {
      "name": "my_eco_car",
      "max_speed": 90,
      "way_multipliers": {
        "motorway": 1.5,
        "trunk": 1.2,
        "residential": 0.8
      },
      "tag_weights": {
        "surface=paved_smooth": 0.8,
        "traffic_signals=none": 0.9
      },
      "blocked_tags": ["motor_vehicle=no"],
      "heuristic_weight": 1.2
    }
  }'
```

---

## Response-Objekte

### RoutePoint

Einzelner Punkt auf der Route.

```json
{
  "lat": 48.1372,
  "lon": 11.5755,
  "distance": 0,
  "time": 0
}
```

| Feld | Typ | Beschreibung |
|------|-----|------------|
| `lat` | Number | Breitengrad |
| `lon` | Number | Längengraf |
| `distance` | Number | Distanz vom Start in Metern |
| `time` | Number | Zeit vom Start in Sekunden |

### TurnInstruction

Navigations-Anweisung für einen Abbiegevorgang.

```json
{
  "sequence": 1,
  "type": "LEFT",
  "instruction": "Biegen Sie links auf Kaufingerstraße ab",
  "way_name": "Kaufingerstraße",
  "distance": 857,
  "time": 110,
  "latitude": 48.1370,
  "longitude": 11.5760,
  "bearing": 42.0
}
```

| Feld | Typ | Beschreibung |
|------|-----|------------|
| `sequence` | Number | Reihenfolge der Anweisung |
| `type` | String | START, LEFT, RIGHT, STRAIGHT, etc. |
| `instruction` | String | Menschenlesbare Instruktion |
| `way_name` | String | Name der Straße/Weg |
| `distance` | Number | Distanz bis nächste Anweisung in Metern |
| `time` | Number | Zeit bis nächste Anweisung in Sekunden |
| `latitude` | Number | Breitengrad des Abbiege-Punktes |
| `longitude` | Number | Längengraf des Abbiege-Punktes |
| `bearing` | Number | Kompass-Richtung in Grad (0-360) |

Zusätzlich für Lane-Guidance (optional):

```json
{
  "sequence": 1,
  "type": "LEFT",
  "instruction": "Biegen Sie links auf Kaufingerstraße ab",
  "way_name": "Kaufingerstraße",
  "distance": 857,
  "time": 110,
  "latitude": 48.1370,
  "longitude": 11.5760,
  "bearing": 42.0,
  "total_lanes": 3,
  "recommended_lanes": [0],
  "lane_tokens": ["left", "through", "through"],
  "lane_message": "Nutzen Sie die linke Spur"
}
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `total_lanes` | Number | (optional) Gesamtanzahl Fahrspuren an der Kreuzung bzw. Straße |
| `recommended_lanes` | Array<Number> | (optional) 0-basierte Indizes der empfohlenen Spuren (Reihenfolge: links→rechts) |
| `lane_tokens` | Array<String> | (optional) rohe Tokens aus `turn:lanes` (per Lane, `|` getrennt) |
| `lane_message` | String | (optional) menschenlesbarer Hinweis zur Spurwahl (deutsch) |

### Turn Types

- **START**: Anfangs-Anweisung
- **STRAIGHT**: Geradeaus fahren
- **SLIGHT_LEFT**: Leicht links
- **LEFT**: Links abbiegen
- **SHARP_LEFT**: Scharf links
- **SLIGHT_RIGHT**: Leicht rechts
- **RIGHT**: Rechts abbiegen
- **SHARP_RIGHT**: Scharf rechts
- **U_TURN**: Wende-Manöver
- **FINISH**: Ziel erreicht

---

## Limit und Performance

- **Maximale Koordinaten-Genauigkeit**: 8 Dezimalstellen (~1,1 cm)
- **Kartenmaterial**: Bayern und Schwaben
- **Routing-Algorithmus**: Dijkstra mit komprimiertem Graph für Optimierung

---

## Support und Debugging

### Health Check durchführen

```bash
curl http://localhost:8080/api/routing/health
```

### Verfügbare Fahrzeuge abrufen

```bash
curl http://localhost:8080/api/routing/vehicles
```

### Spezifisches Profil testen

```bash
curl http://localhost:8080/api/routing/vehicle/car
```

---

## Changelog

### Version 1.0.0 (Aktuell)

- ✅ Routing-Engine mit Dijkstra-Algorithmus
- ✅ Vordefinierte Fahrzeugprofile (6 Typen)
- ✅ Benutzerdefinierte Profile speichern
- ✅ Turn-by-Turn Navigationsanweisungen
- ✅ GeoJSON-Format für Karten-Integration
- ✅ CORS-Support für Web-Anwendungen

---

## Kontakt und weitere Hilfe

Für weitere Informationen zur API oder Support:

- Dokumentation: Siehe [README_ROUTING.md](README_ROUTING.md)
- Projektübersicht: Siehe [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)
- Beispiele: Siehe [EXAMPLE_REQUESTS.json](EXAMPLE_REQUESTS.json)
