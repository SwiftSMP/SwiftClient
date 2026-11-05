# SwiftClient website

Static site for **https://swiftclient.swiftsmp.net**: no build step, just copy this folder.

```
website/
├─ index.html, styles.css, app.js     home page (same design system as swiftsmp.com)
├─ android/, download/                 search landing pages, generated from index.html
├─ sitemap.xml, robots.txt, site.webmanifest
├─ tools/make_pages.py                 rebuilds android/ and download/ after editing index.html
├─ assets/          fonts (OFL), logo, screenshots (webp), og.jpg
└─ downloads/       put SwiftClient.apk here (not in git)
```

Live data on the page: SwiftClient counters and capes from `https://capes.swiftsmp.com`, SwiftSMP
players from `https://swiftsmp.com/api/status`. In-game screenshots are from SwiftClient 1.4 on 26.3.

After editing the nav, head or footer in `index.html`, run `python tools/make_pages.py` so the
Android and Download pages match. Update `<lastmod>` in `sitemap.xml` when content changes.

Links it uses: the Microsoft Store listing (`9P118N2JM63C`), the `.exe` from the latest GitHub
release, `downloads/SwiftClient.apk` on this server, and the live counters from
`https://capes.swiftsmp.com/stats` (CORS is already open there).

## Deploy on the VPS

1. DNS: an `A` record `swiftclient` → the VPS IP (on Cloudflare, proxied is fine).
2. Copy the site:

   ```bash
   rsync -av --delete --exclude downloads/ --exclude tools/ --exclude README.md website/ root@<vps>:/var/www/swiftclient/
   scp SwiftClient.apk root@<vps>:/var/www/swiftclient/downloads/SwiftClient.apk
   ```

3. nginx, `/etc/nginx/sites-available/swiftclient` (then `ln -s` it into `sites-enabled`):

   ```nginx
   server {
       listen 80;
       server_name swiftclient.swiftsmp.net;
       root /var/www/swiftclient;
       index index.html;

       location / { try_files $uri $uri/ =404; }
       location /assets/ { expires 30d; add_header Cache-Control "public"; }
       location = /downloads/SwiftClient.apk {
           types { application/vnd.android.package-archive apk; }
           add_header Content-Disposition 'attachment; filename="SwiftClient.apk"';
           add_header Cache-Control "no-cache";
       }
       gzip on;
       gzip_types text/css application/javascript image/svg+xml;
   }
   ```

4. HTTPS: `certbot --nginx -d swiftclient.swiftsmp.net`, then `nginx -t && systemctl reload nginx`.

Local preview: `python -m http.server 8090 --directory website`.
