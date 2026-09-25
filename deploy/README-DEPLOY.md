# Deploying Pandal Office Management to your Hostinger VPS

For **pandaldepartment.tech**. Assumes a fresh Ubuntu 22.04 or 24.04 VPS and that
you can `ssh root@YOUR_VPS_IP`.

Everything in this zip is already built. You are installing, not compiling — the
VPS never needs Maven, Node or the source code.

```
app/user-management-1.0.0.jar   the server (77 MB, Java 21)
web/                            the browser app (static files)
config/pandal.env               settings - YOU MUST EDIT THIS
config/pandal.service           runs the server, and restarts it on reboot
config/nginx-*.conf             the web server, for your domain
scripts/install.sh              steps 4-7 in one command, if you prefer
```

**Three things must be changed before it is safe to use.** They are all in
`config/pandal.env`, and step 5 walks through them: the database password, the
token signing key, and the first admin password.

---

## 1. Point the domain at the VPS first

DNS takes time to spread, so do it before anything else. In **hPanel → Domains →
pandaldepartment.tech → DNS Zone**, set two `A` records to your VPS IP:

| Type | Name | Points to |
|------|------|-----------|
| A | `@` | `YOUR_VPS_IP` |
| A | `www` | `YOUR_VPS_IP` |

Check from your own machine — when this prints your VPS IP, carry on:

```bash
nslookup pandaldepartment.tech
```

It is usually minutes, but can take a few hours. Steps 2–7 can be done while you
wait; only step 8 (HTTPS) needs DNS to have arrived.

## 2. Install what the server needs

```bash
ssh root@YOUR_VPS_IP

apt update && apt upgrade -y
apt install -y openjdk-21-jre-headless mysql-server nginx unzip
java -version          # expect 21
```

## 3. Create the database and its user

The application creates the *tables* itself on first start. It does not create
the MySQL user, so do that now. **Choose your own password** and keep it for
step 5.

```bash
mysql -u root
```

```sql
CREATE DATABASE IF NOT EXISTS office_management
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pandal'@'localhost' IDENTIFIED BY 'your-strong-db-password';
GRANT ALL PRIVILEGES ON office_management.* TO 'pandal'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

## 4. Upload and unpack

From **your own machine**, in the folder holding the zip:

```bash
scp pandal-deploy.zip root@YOUR_VPS_IP:/root/
```

Then back on the VPS:

```bash
mkdir -p /opt/pandal
unzip /root/pandal-deploy.zip -d /tmp/pandal-unzip
cp -r /tmp/pandal-unzip/pandal-deploy/* /opt/pandal/

# a user with no login shell, so a flaw in the app is not a way onto the machine
adduser --system --group --no-create-home pandal
chown -R pandal:pandal /opt/pandal

# the browser app goes where nginx serves from
mkdir -p /var/www/pandaldepartment.tech
cp -r /opt/pandal/web/* /var/www/pandaldepartment.tech/
chown -R www-data:www-data /var/www/pandaldepartment.tech
```

## 5. Set the three secrets

```bash
openssl rand -base64 48        # copy this for JWT_SECRET
nano /opt/pandal/pandal.env    # the file is /opt/pandal/config/pandal.env - move it first:
```

```bash
mv /opt/pandal/config/pandal.env /opt/pandal/pandal.env
nano /opt/pandal/pandal.env
```

Change these three, then save:

- `DB_PASSWORD=` the password from step 3
- `JWT_SECRET=` the `openssl rand` output. **Anyone with this string can sign in
  as anyone.** It must not stay at the default.
- `ADMIN_PASSWORD=` the password for the first `admin` login

Then lock the file down, because it now holds both:

```bash
chown root:root /opt/pandal/pandal.env
chmod 600 /opt/pandal/pandal.env
```

## 6. Start the application

```bash
cp /opt/pandal/config/pandal.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pandal
systemctl status pandal          # expect "active (running)"
```

The first start takes longer than later ones — it is building the tables. Watch
it if you want:

```bash
journalctl -u pandal -f
```

Check it is answering before moving on. A `401` here is the **right** answer: it
means the app is up and correctly refusing an unauthenticated request.

```bash
curl -i http://127.0.0.1:8080/api/auth/me
```

## 7. Put nginx in front

```bash
cp /opt/pandal/config/nginx-pandaldepartment.tech.conf \
   /etc/nginx/sites-available/pandaldepartment.tech
ln -s /etc/nginx/sites-available/pandaldepartment.tech /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

nginx -t && systemctl reload nginx

ufw allow 'Nginx Full'
ufw allow OpenSSH
ufw --force enable
```

Port 8080 is deliberately **not** opened. nginx reaches the app over the
machine's own loopback interface; the internet never touches it directly.

Now open **http://pandaldepartment.tech** and sign in as `admin` with the
password you set in step 5.

## 8. Turn on HTTPS

Only once step 1's DNS has arrived:

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d pandaldepartment.tech -d www.pandaldepartment.tech
```

Choose the redirect option when it offers. Certbot rewrites the nginx file to
serve HTTPS and to send `http://` traffic to `https://`, and installs a timer
that renews the certificate by itself. Confirm with:

```bash
certbot renew --dry-run
```

Your site is then **https://pandaldepartment.tech**.

---

## Afterwards

**Change the admin password in the app** (My Profile → Change Password) even
though you set one in the env file, and create real accounts under User Account
rather than sharing the admin login.

**Back up the database.** Everything — sewadars, attendance, photos — is in
MySQL. Nothing is on disk outside it.

```bash
mysqldump -u pandal -p office_management | gzip > /root/pandal-$(date +%F).sql.gz
```

Worth putting in cron once you have real data in it.

## Starting and stopping

```bash
sudo /opt/pandal/scripts/start.sh        # start, and wait until it really answers
sudo /opt/pandal/scripts/stop.sh         # stop the application
sudo /opt/pandal/scripts/stop.sh --all   # stop nginx and MySQL too
```

`start.sh` does not report success when systemd says "active" - it waits for the
app to answer an HTTP request, and then checks that nginx is forwarding to it, so
a JVM that started and is on its way to failing is caught rather than reported as
running. It prints the last 30 log lines if it gives up.

`stop.sh` leaves nginx and MySQL running on purpose: nginx keeps answering, so the
site returns a clear 502 instead of hanging, and MySQL holds your data. Both
scripts are safe to run twice.

## Updating later

Only two files ever change. Build a new zip the same way, then:

```bash
systemctl stop pandal
cp app/user-management-1.0.0.jar /opt/pandal/app/
cp -r web/* /var/www/pandaldepartment.tech/
chown -R pandal:pandal /opt/pandal
chown -R www-data:www-data /var/www/pandaldepartment.tech
systemctl start pandal
```

Your settings and your data are untouched: `pandal.env` is not in the update, and
the schema upgrades itself on start.

## When something is wrong

| What you see | Where to look |
|---|---|
| 502 Bad Gateway | The app is down. `systemctl status pandal`, then `journalctl -u pandal -n 100` |
| App will not start, "Schema-validation" in the log | MySQL user cannot reach the database — recheck step 3 and `DB_PASSWORD` |
| Login says "Invalid login or password" | Password set in `ADMIN_PASSWORD` only applies to the *first* start, when the admin is created |
| Photo upload fails with 413 | `client_max_body_size` missing from the nginx file; it is set to 20m in the supplied one |
| Page 404s when refreshed on /attendance | The `try_files` line is missing — use the supplied nginx file as-is |
| Certbot fails | DNS has not arrived yet. Recheck `nslookup pandaldepartment.tech` |

Logs:

```bash
journalctl -u pandal -f                              # the application
tail -f /var/log/nginx/pandaldepartment.error.log    # the web server
```
