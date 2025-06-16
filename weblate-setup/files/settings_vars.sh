export SITE_DOMAIN='dev-weblate.ihtsdotools.org'
export CSRF_TRUSTED_ORIGINS='"https://dev-weblate.ihtsdotools.org", "https://dev-simplex.ihtsdotools.org"'
export ADMINS='"Mr Admin", "admin@example.com"'
export DB_NAME='weblate'
export DB_USER='weblate'
export DB_PASS='PASSWORD'
export DB_HOST='localhost'
# The secret-key.txt file will be created by the deployment script
export WEBLATE_COOKIE_KEY="`cat /opt/weblate/weblate-env/secret-key.txt`"
