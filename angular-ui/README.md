# Angular UI for SNOMED CT Simple Extension Tool

---

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`.

### For SNOMED International Single-Sign-On

Use this Nginx configuration. Create a file `/opt/homebrew/etc/nginx/servers/simplex.conf` with content:
```
server {
    server_name _;
    listen 8090;

    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Host $host;
    proxy_buffering off;
    client_max_body_size 1024m;
    client_body_buffer_size 128k;


    location / {
        proxy_pass http://localhost:4200;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_http_version 1.1;
        proxy_cache_bypass $http_upgrade;
    }

    location /api {
        proxy_pass http://localhost:8080/api;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_pass_header Content-Type;
    }

}
```
Reload Nginx then navigate to `http://local.ihtsdotools.org:4200/`

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory.
