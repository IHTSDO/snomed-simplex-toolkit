# Weblate Deployment and Setup

Follow these steps to deploy SNOMED-Weblate and complete setup via the management dashboard.

## Weblate Deployment
Weblate deployment is automated via script. The `files` directory contains a templated configuration file and some files that are required for this customised Weblate deployment.  

1. Copy the `files` directory on your local machine and name the copy `weblate_setup_files`.


2. Within that directory edit `settings_vars.sh` adding the Admin user name and email, DB configuration and hosting domain names.


3. Copy the whole `weblate_setup_files` to the deployment server as `/opt/weblate_setup_files`.

 
4. Copy the script `weblate-install-script.sh` to any directory on the deployment server and run it. At the moment running the scripts manually line by line works better than running the whole thing. Some improvements could be made around switching users and virtual environments.

The script installs the required software, and then uses the extra setup files to configure and customise Weblate.

You should now have Weblate deployed and running under the Gunicorn service, hosted by Nginx.  
Confirm that it's running using:
```
sudo systemctl status gunicorn
```
Use the steps below to complete the setup.  

## Admin Users Setup
1. Login to Simplex to get authenticated
2. Access weblate (uses single-sign-on), to create the internal user record.
3. Create weblate **admin** user:
```
cd /opt/weblate/weblate-env/
sudo su weblate
. bin/activate
weblate createadmin
```
Make a note of the generated password.

4. Disable Weblate SSO authentication
   - Comment out nginx "auth_request /auth" line within "location /"
   - Reload nginx `sudo nginx -s reload`
5. Login to Weblate as **admin** via URL /accounts/login/
6. Grant admin users "super-admin" role
   - Find user under /manage/users/, select and click Edit
   - Check box "Superuser status"
   - Save
7. Enable Weblate SSO authentication
    - Uncomment nginx "auth_request /auth" line within "location /"
    - Reload nginx `sudo nginx -s reload`
8. Log out of Simplex and ensure access is denied to Weblate

## Complete Git Repository Setup
Login to Simplex with an account that has Weblate super-user status.
Access the Weblate management page at URL /manage/. In the **SSH keys** page take the "Public RSA SSH key" and add that to the github repo with write access, using deploy keys section or other method.

## Appearance Setup
As an admin user navigate to the management appearance screen under /manage/appearance/.

Change the following RGB colours:
- Navigation color (Light) = 18 / 79 / 107
- Focus color (Light) = 0 / 169 / 224
- Hover color (Light) = 100 / 190 / 224

Also select "Hide page footer".

---
## Weblate Upgrade
Weblate upgrade will require code changes. The Weblate files in the `files` directory that have been modified in this repository need to be updated using the source git repo, but modifications need to be kept.
The upgrade script should be modified to set the new version.

The deployment of the upgrade can be done via script. 

1. Copy the `files` directory on your local machine and name the copy `weblate_setup_files`.


2. Within that directory edit `settings_vars.sh` adding the Admin user name and email, DB configuration and hosting domain names.


3. Copy the whole `weblate_setup_files` to the deployment server as `/opt/weblate_setup_files`.


4. Copy the script `weblate-upgrade-script.sh` to any directory on the deployment server and run it.

The script upgrades weblate, and then uses the extra setup files to configure and customise the new version of Weblate.

You should now have an upgraded version of Weblate deployed and running under the Gunicorn service.  
Confirm that it's running using:
```
sudo systemctl status gunicorn
```
