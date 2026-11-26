# SnowClear

SnowClear is a Spring Boot prototype for requesting snow clearing on a user-drawn map area. Customers can attach reference photos; clearers can review open requests and their pictures, then claim a request. Order details and map geometry are kept in a local H2 database, while photo bytes are stored in a private S3-compatible Garage bucket.

## Requirements

- JDK 27 (the project compiles for Java 27)
- Garage with its S3 API enabled and reachable from the Spring Boot process
- A pre-created Garage bucket and an access key with object read/write permissions for that bucket
- Internet access in the browser for the OpenStreetMap tile layer and Leaflet map libraries loaded from their public CDNs

The default Garage S3 endpoint is `http://localhost:3900`; set it to the S3 API endpoint for your Garage deployment if it differs. Create the bucket and restricted access key using the Garage administration tools for your deployment before starting the application. The application does not create buckets or return Garage credentials to browsers.

## Configure and run

Set the Garage settings in PowerShell before starting the app (replace the example values with your own):

```powershell
$env:GARAGE_S3_ENDPOINT = "http://localhost:3900"
$env:GARAGE_S3_REGION = "garage"
$env:GARAGE_S3_BUCKET = "snow-clearing"
$env:GARAGE_S3_ACCESS_KEY = "your-access-key"
$env:GARAGE_S3_SECRET_KEY = "your-secret-key"
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`. Use **Request clearing** to enter contact and property information, draw a closed polygon with the map's polygon tool, optionally attach up to five photos, and post the request. Use **Find a job** to review open requests, see the marked area and photos, and claim a job.

The local H2 database is stored under `./data/snow-clearing`. Back up this directory along with the Garage bucket if you need to preserve local prototype data. The S3 bucket must be reachable and its configured access key must be permitted to put, get, and delete objects. Photo uploads are limited to JPEG, PNG, and GIF; each file can be at most 10 MB, with up to five files per request.

## Tests

Run the integration tests with:

```powershell
.\mvnw.cmd test
```

The tests use an isolated in-memory H2 database and in-memory photo storage, so they do not require a running Garage instance.

## Prototype limitations

There is no sign-in, role management, ownership verification, payment, or moderation yet. Names and contact details are supplied by the requester/clearer and are not authenticated; order details and photos are available to anyone who can reach the app. Do not expose this prototype to the public internet or use it for sensitive personal information without adding authentication, authorization, privacy controls, and operational hardening. The map defaults to Stockholm and uses browser-loaded OpenStreetMap/Leaflet assets; the address is entered as text and is not geocoded.
