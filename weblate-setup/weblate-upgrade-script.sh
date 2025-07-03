#!/bin/bash
# Weblate upgrade script, for Ubuntu servers

# The command 'set -e' in a shell script causes the script to exit immediately if any command exits with a non-zero status (indicating an error). This helps to prevent the script from continuing execution when an error occurs.
set -e

NEW_WEBLATE_VERSION="5.12.1"

# Take secret key out of existing settings file
grep SECRET_KEY /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/settings.py | cut -d'"' -f2 | sudo tee /opt/weblate/weblate-env/secret-key.txt

# Enter virtual environment
cd /opt/weblate/weblate-env/
sudo su weblate
. bin/activate

# Upgrade Weblate python packages
pip install --upgrade pip
pip install weblate[postgres,amazon,google,openai]==${NEW_WEBLATE_VERSION}

# Exit virtual environment
exit


# Deploy customised weblate setup files
cd /opt/weblate_setup_files
. settings_vars.sh
envsubst < settings.template > settings.py
sudo mv settings.py /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/settings.py
sudo rm -rf /opt/weblate/weblate-env/secret-key.txt

# Disable CSRF in Django to enable Simplex integration
sudo cp django-middleware-csrf.py /opt/weblate/weblate-env/lib/python3.12/site-packages/django/middleware/csrf.py
sudo cp djando-custommiddleware.py /opt/weblate/weblate-env/lib/python3.12/site-packages/django/contrib/auth/custommiddleware.py
sudo cp simplex.css /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/static/styles/simplex.css
sudo cp meta-css.html /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/templates/snippets/meta-css.html
# Add custom API endpoints for faster bulk processing
sudo cp views.py /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/api/views.py
# Disable nearby_keys as performance enhancement. (Weblate attempts to find similar SCTID, which is not useful).
sudo cp unit.py /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/trans/models/unit.py
# Optimised version of translation file processing to use less memory when processing huge files
sudo cp translation.py /opt/weblate/weblate-env/lib/python3.12/site-packages/weblate/trans/models/translation.py
sudo chown -R weblate:weblate /opt/weblate

# Restart gunicorn web server
sudo systemctl restart gunicorn

# Enter virtual environment
cd /opt/weblate/weblate-env/
sudo su weblate
. bin/activate

# Migrate database
weblate migrate --noinput

# Cache web static files
weblate collectstatic --noinput --clear

# Verify setup
weblate check --deploy

# Exit virtual environment
exit

# Restart gunicorn web server
sudo systemctl start gunicorn

# Restart celery workers
sudo systemctl restart celery-weblate
