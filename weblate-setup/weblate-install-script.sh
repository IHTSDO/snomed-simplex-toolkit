#!/bin/bash
# Weblate deployment script, for Ubuntu servers

# The command 'set -e' in a shell script causes the script to exit immediately if any command exits with a non-zero status (indicating an error). This helps to prevent the script from continuing execution when an error occurs.
set -e

# Install Python 3.12
# Remove the deadsnakes repo if it exists
sudo add-apt-repository -y --remove ppa:deadsnakes/ppa || true

# Add the latest version of the deadsnakes repo
sudo add-apt-repository -y ppa:deadsnakes/ppa
sudo apt update
sudo apt install -y python3.12 python3.12-venv python3.12-dev

# Install required libraries
sudo apt install -y \
   libxml2-dev libxslt-dev libfreetype6-dev libjpeg-dev libz-dev libyaml-dev \
   libffi-dev libcairo-dev gir1.2-pango-1.0 gir1.2-rsvg-2.0 libgirepository1.0-dev \
   libacl1-dev liblz4-dev libzstd-dev libxxhash-dev libssl-dev libpq-dev libjpeg-dev build-essential pkg-config \
   python3-gdbm python3.12-dev python3-gi python3-gi-cairo git default-libmysqlclient-dev cmake

sudo apt install -y gir1.2-girepository-2.0

DEBIAN_FRONTEND=noninteractive sudo apt install -y \
   libldap2-dev libldap-common libsasl2-dev \
   libxmlsec1-dev gettext

# Nginx should already be installed.

# Install Redis version 7
# https://redis.io/docs/latest/operate/oss_and_stack/install/
sudo apt-get install lsb-release curl gpg
curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
sudo chmod 644 /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list
sudo apt-get update
sudo apt-get install -y redis
sudo systemctl enable redis-server
sudo systemctl start redis-server
sudo systemctl status redis-server

# Postgress should already be installed

# Install git 2.28 or newer
sudo add-apt-repository -y --remove ppa:git-core/ppa || true
sudo add-apt-repository -y ppa:git-core/ppa
sudo apt update
sudo apt install -y git

# Create the weblate user
sudo adduser --disabled-password --gecos "" weblate
sudo userdel -r weblate

sudo mkdir /opt/weblate
cd /opt/weblate
sudo python3.12 -m venv weblate-env
sudo chown -R weblate:weblate .
cd weblate-env
sudo su weblate
. bin/activate
pip install --upgrade pip
pip install 'PyGobject<3.52'
pip install weblate[postgres,amazon,google,openai]==5.12.1
pip install gunicorn

# Run command to create new secret key (used for weblate cookies)
weblate-generate-secret-key > secret-key.txt
# Exit virtual environment
exit


# Deploy customised weblate setup files
cd /opt/weblate_setup_files
. settings_vars.sh
envsubst < settings.template > settings.py
mv settings.py /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/settings.py

# Disable CSRF in Django to enable Simplex integration
sudo cp django-middleware-csrf.py /opt/weblate/weblate-env/lib/python3.12/site-packages/django/middleware/csrf.py
sudo cp djando-custommiddleware.py /opt/weblate/weblate-env/lib/python3.12/site-packages/django/contrib/auth/custommiddleware.py
sudo cp simplex.css /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/static/styles/simplex.css
sudo cp meta-css.html /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/templates/snippets/meta-css.html
# Disable nearby_keys as performance enhancement. (Weblate attempts to find similar SCTID, which is not useful).
sudo cp unit.py /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/trans/models/unit.py
sudo chown -R weblate:weblate /opt/weblate


cd /opt/weblate/weblate-env/
sudo su weblate
. bin/activate

# Cache web static files
weblate collectstatic --noinput

# Setup database tables etc
weblate migrate

# Exit virtual environment
exit

# Service setup
cd /opt/weblate_setup_files
sudo cp gunicorn.socket /etc/systemd/system/gunicorn.socket
sudo cp gunicorn.service /etc/systemd/system/gunicorn.service
sudo systemctl start gunicorn
sudo systemctl enable gunicorn
sudo systemctl status gunicorn

# Deploy Nginx config
sudo cp nginx.conf /etc/nginx/conf.d/weblate.conf
sudo nginx -s reload


# Setup Celery workers
sudo cp celery-weblate.service /etc/systemd/system/celery-weblate.service
sudo cp celery-weblate /etc/default/celery-weblate
sudo mkdir /var/log/celery
sudo cp celery-logrotate /etc/logrotate.d/celery
sudo systemctl enable celery-weblate
sudo systemctl start celery-weblate

# Additional libraries required for the SNOMED Diagram API
sudo apt install -y libgbm1 libx11-xcb1 libxkbcommon0
