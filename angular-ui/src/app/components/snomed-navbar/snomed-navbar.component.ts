import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Subscription, filter, lastValueFrom } from 'rxjs';
import { User } from '../../models/user';
import { AuthenticationService } from '../../services/authentication/authentication.service';
import { Location } from '@angular/common';
import { LegalAgreementService } from 'src/app/services/legal-agreement/legal-agreement.service';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { CookieService } from 'ngx-cookie-service';
import { DrawerService } from "../../services/drawer/drawer.service";
import { ConfigService, LauncherApp } from "../../services/config/config.service";

@Component({
    selector: 'app-snomed-navbar',
    templateUrl: './snomed-navbar.component.html',
    styleUrls: ['./snomed-navbar.component.scss']
})
export class SnomedNavbarComponent implements OnInit {

    @Input() selectedEdition: any = null;

    environment: string;
    path: string;

    user: User;
    userSubscription: Subscription;

    branches: any;
    branchesSubscription: Subscription;
    activeBranch: any;
    activeBranchSubscription: Subscription;

    projects: any;
    projectsSubscription: Subscription;
    activeProject: any;
    activeProjectSubscription: Subscription;

    tasks: any;
    tasksSubscription: Subscription;
    activeTask: any;
    activeTaskSubscription: Subscription;

    editions = [];
    loading = false;
    private readonly SELECTED_EDITION_COOKIE_NAME = 'selectedEdition';

    apps: LauncherApp[] = [];
    authoringPlatformMode: boolean;

    /** Text beside the logo: reflects current route (e.g. Translation Studio). */
    navSuiteTitle = 'Simplex';

    constructor(
        private authenticationService: AuthenticationService,
        private location: Location,
        private legalAgreementService: LegalAgreementService,
        private uiConfigurationService: UiConfigurationService,
        private configService: ConfigService,
        private route: ActivatedRoute,
        private router: Router,
        private simplexService: SimplexService,
        private drawerService: DrawerService,
        private cookieService: CookieService) {
        this.uiConfigurationService.getAuthoringPlatformMode().subscribe(data => {
            this.authoringPlatformMode = data;
            this.updateNavSuiteTitle();
        });
        this.userSubscription = this.authenticationService.getUser().subscribe(data => {
            this.user = data;
            const allApps = this.configService.getLauncherApps();
            this.apps = allApps.filter(a => !a.clientName || this.user.clientAccess.includes(a.clientName));
        });
    }

    ngOnInit() {
        this.updateNavSuiteTitle();
        // Listen to navigation events to capture route changes
        this.router.events.pipe(
            filter(event => event instanceof NavigationEnd)
        ).subscribe(() => {
            this.updateNavSuiteTitle();
            const url = this.router.url;
            // Do nothing if the current route is /admin
            if (url === '/admin') {
                return;
            }
            const editionParam = this.route.firstChild?.snapshot.paramMap.get('edition');
            // Load editions if the edition parameter is defined
            if (editionParam) {
                this.loadEditions(editionParam);
            } else if (!url.includes('/home')) {
                // Load editions if the edition parameter is not defined
                this.loadEditions(null);
            }
        });
    }

    isInHome(): boolean {
        return this.router.url === '/home';
    }

    private updateNavSuiteTitle(): void {
        if (this.router.url.includes('/translation-studio')) {
            this.navSuiteTitle = 'Simplex: Translation Studio';
        } else if (this.authoringPlatformMode) {
            this.navSuiteTitle = 'Translation Studio';
        } else {
            this.navSuiteTitle = 'Simplex';
        }
    }

    initialize() {
        this.simplexService.refreshUIConfiguration();
        this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
            this.selectedEdition = edition;
        });
    }

    openDrawer() {
        this.drawerService.setDrawerOpen(true);
        document.body.classList.add('app-drawer-open');
    }

    loadEditions(editionParam: string | null) {
        if (!this.uiConfigurationService.getConfiguration()) {
            this.initialize();
        }
        this.editions = [];
        this.loading = true;

        lastValueFrom(this.simplexService.getEditions()).then(
            (editions) => {
                // Remove editions with empty names
                editions.items = editions.items.filter((item) => item.name);
                this.editions = editions.items;

                // Find the edition that matches the 'edition' parameter, if available
                const matchedEdition = this.editions.find((item) => item.shortName === editionParam);

                // Select the matched edition or fall back to the first one
                if (editionParam && matchedEdition) {
                    this.selectEdition(matchedEdition);
                } else {
                    // Check for saved edition in cookie
                    const savedEditionShortName = this.getSavedEdition();
                    const savedEdition = this.editions.find((item) => item.shortName === savedEditionShortName);

                    if (savedEdition) {
                        this.selectEdition(savedEdition);
                    } else {
                        this.selectEdition(this.editions[0]);
                    }
                }
                this.loading = false;
            },
            (error) => {
                console.error(error);
            }
        );
    }


    logout() {
        this.simplexService.logout();
        // this.simplexService.logoutAccount().subscribe(() => {
        //     this.goHome();
        // });
    }

    removeAgreementAndReload() {
        this.legalAgreementService.removeAgreement();
        window.location.reload();
    }

    selectEdition(item: any) {
        this.selectedEdition = item;
        this.uiConfigurationService.setSelectedEdition(item);
        this.saveEditionToCookie(item.shortName);
        const editionInPath = this.route.firstChild?.snapshot.paramMap.get('edition');
        if (editionInPath === item.shortName) {
            return;
        }
        this.updateEditionInUrl(item.shortName);
    }

    private saveEditionToCookie(editionShortName: string): void {
        try {
            // Set cookie to expire in 1 year
            const expiryDate = new Date();
            expiryDate.setFullYear(expiryDate.getFullYear() + 1);
            this.cookieService.set(this.SELECTED_EDITION_COOKIE_NAME, editionShortName, expiryDate);
        } catch (error) {
            console.error('Error saving edition cookie:', error);
        }
    }

    private getSavedEdition(): string | null {
        try {
            return this.cookieService.get(this.SELECTED_EDITION_COOKIE_NAME) || null;
        } catch (error) {
            console.error('Error reading edition cookie:', error);
            return null;
        }
    }

    getInitials(user: User): string {
        let initials = '';

        if (user.firstName) {
            initials += user.firstName?.charAt(0).toUpperCase();
        }

        if (user.lastName) {
            initials += user.lastName?.charAt(0).toUpperCase();
        }

        return initials;
    }

    updateEditionInUrl(edition: string) {
        // Get the current active route
        const currentRoute = this.router.url;
        // Check if the current route has an 'edition' parameter, update it or append if not
        const newRoute = currentRoute.includes('/home') ? ['home'] :
            currentRoute.includes('artifacts') ? ['artifact', edition] :
                currentRoute.includes('artifact') ? ['artifact', edition] :
                    currentRoute.includes('manage') ? ['manage', edition] :
                        currentRoute.includes('info') ? ['info', edition] :
                            currentRoute.includes('releases') ? ['releases', edition] :
                                currentRoute.includes('translation-studio') || currentRoute.includes('translation-dashboard')
                                    ? ['translation-studio', edition]
                                    : ['artifact', edition];

        // Navigate to the new route while keeping the current path structure
        this.router.navigate(newRoute);
    }

    goHome() {
        this.router.navigate(['/home']);
    }

}
