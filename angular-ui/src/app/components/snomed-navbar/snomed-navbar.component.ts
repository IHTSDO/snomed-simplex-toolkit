import { Component, ChangeDetectorRef, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription, filter, lastValueFrom } from 'rxjs';
import { User } from '../../models/user';
import { AuthenticationService } from '../../services/authentication/authentication.service';
import { LegalAgreementService } from 'src/app/services/legal-agreement/legal-agreement.service';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { CookieService } from 'ngx-cookie-service';
import { DrawerService } from "../../services/drawer/drawer.service";
import { ConfigService, LauncherApp } from "../../services/config/config.service";
import { formatEffectiveTimeParts } from 'src/app/utils/effective-time-format.util';

export interface DependencyLabelInfo {
    parentName: string;
    monthYear: string;
    effectiveTime: string;
}

@Component({
    selector: 'app-snomed-navbar',
    templateUrl: './snomed-navbar.component.html',
    styleUrls: ['./snomed-navbar.component.scss']
})
export class SnomedNavbarComponent implements OnInit, OnDestroy {

    @Input() selectedEdition: any = null;

    dependencyInfo: DependencyLabelInfo | null = null;

    environment: string;
    path: string;

    user: User;
    userSubscription: Subscription;
    selectedEditionSubscription: Subscription;

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
    private loadEditionsGeneration = 0;

    apps: LauncherApp[] = [];
    authoringPlatformMode: boolean;

    /** Text beside the logo: reflects current route (e.g. Translation Studio). */
    navSuiteTitle = 'Simplex';

    constructor(
        private authenticationService: AuthenticationService,
        private legalAgreementService: LegalAgreementService,
        private uiConfigurationService: UiConfigurationService,
        private configService: ConfigService,
        private route: ActivatedRoute,
        private router: Router,
        private simplexService: SimplexService,
        private drawerService: DrawerService,
        private cookieService: CookieService,
        private changeDetectorRef: ChangeDetectorRef) {
        this.uiConfigurationService.getAuthoringPlatformMode().subscribe(data => {
            this.authoringPlatformMode = data;
            this.updateNavSuiteTitle();
        });
        this.userSubscription = this.authenticationService.getUser().subscribe(data => {
            this.user = data;
            if (!this.user) {
                return;
            }
            const allApps = this.configService.getLauncherApps();
            this.apps = allApps.filter(a => !a.clientName || this.user.clientAccess?.includes(a.clientName));
        });
    }

    ngOnInit() {
        this.updateNavSuiteTitle();
        this.selectedEditionSubscription = this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
            if (edition) {
                this.selectedEdition = edition;
            }
            this.loadDependencyLabel(edition);
        });
        // Listen to navigation events to capture route changes
        this.router.events.pipe(
            filter(event => event instanceof NavigationEnd)
        ).subscribe(() => {
            this.updateNavSuiteTitle();
            this.syncEditionSelectionFromRoute();
        });
        this.syncEditionSelectionFromRoute();
    }

    ngOnDestroy() {
        this.selectedEditionSubscription?.unsubscribe();
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

    private syncEditionSelectionFromRoute(): void {
        const url = this.router.url.split('?')[0];
        if (url === '/admin') {
            return;
        }
        const urlEdition = this.getEditionShortNameFromRoute();
        if (urlEdition) {
            if (urlEdition === this.selectedEdition?.shortName && this.editions.length > 0) {
                this.saveEditionToCookie(urlEdition);
                return;
            }
            this.loadEditions();
            return;
        }
        if (!url.includes('/home')) {
            if (this.selectedEdition && this.editions.length > 0) {
                return;
            }
            this.loadEditions();
        }
    }

    loadEditions() {
        if (!this.uiConfigurationService.getConfiguration()) {
            this.initialize();
        }
        const generation = ++this.loadEditionsGeneration;
        this.editions = [];
        this.loading = true;

        lastValueFrom(this.simplexService.getEditions()).then(
            (editions) => {
                if (generation !== this.loadEditionsGeneration) {
                    return;
                }
                editions.items = editions.items.filter((item) => item.name);
                this.editions = editions.items;
                if (this.editions.length === 0) {
                    this.loading = false;
                    return;
                }

                const urlEdition = this.getEditionShortNameFromRoute();
                if (urlEdition) {
                    this.saveEditionToCookie(urlEdition);
                    const matchedEdition = this.editions.find((item) => item.shortName === urlEdition);
                    this.applyEditionSelection(matchedEdition ?? this.editions[0], { updateUrl: false });
                } else {
                    const savedEditionShortName = this.getSavedEdition();
                    const savedEdition = this.editions.find((item) => item.shortName === savedEditionShortName);
                    this.applyEditionSelection(savedEdition ?? this.editions[0]);
                }
                this.loading = false;
            },
            (error) => {
                if (generation === this.loadEditionsGeneration) {
                    this.loading = false;
                }
                console.error(error);
            }
        );
    }

    /** Edition shortName from route params or URL segments (e.g. /artifact/:edition). */
    private getEditionShortNameFromRoute(): string | null {
        let child = this.route.snapshot.firstChild;
        while (child) {
            const edition = child.paramMap.get('edition');
            if (edition) {
                return edition;
            }
            child = child.firstChild;
        }
        const path = this.router.url.split('?')[0];
        const patterns = [
            /^\/artifact\/([^/]+)/,
            /^\/manage\/([^/]+)/,
            /^\/info\/([^/]+)/,
            /^\/releases\/([^/]+)/,
            /^\/translation-studio\/([^/]+)/,
            /^\/translation-dashboard\/([^/]+)/
        ];
        for (const pattern of patterns) {
            const match = path.match(pattern);
            if (match) {
                return match[1];
            }
        }
        return null;
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
        this.loadEditionsGeneration++;
        this.applyEditionSelection(item);
    }

    private applyEditionSelection(item: any, options?: { updateUrl?: boolean }): void {
        if (!item) {
            return;
        }
        this.selectedEdition = item;
        this.uiConfigurationService.setSelectedEdition(item);
        this.saveEditionToCookie(item.shortName);
        this.loadDependencyLabel(item);
        if (options?.updateUrl === false) {
            return;
        }
        const editionInPath = this.getEditionShortNameFromRoute();
        if (editionInPath === item.shortName) {
            return;
        }
        this.updateEditionInUrl(item.shortName);
    }

    private loadDependencyLabel(edition: any) {
        if (!edition?.shortName) {
            this.dependencyInfo = null;
            this.changeDetectorRef.detectChanges();
            return;
        }

        const requestedShortName = edition.shortName;

        const applyDetail = (detail: any) => {
            if (detail?.shortName !== requestedShortName) {
                return;
            }
            const timeParts = formatEffectiveTimeParts(detail?.dependantVersionEffectiveTime);
            if (!detail?.dependantEditionName || !timeParts) {
                this.dependencyInfo = null;
            } else {
                this.dependencyInfo = {
                    parentName: detail.dependantEditionName,
                    monthYear: timeParts.monthYear,
                    effectiveTime: timeParts.effectiveTime
                };
            }
            this.changeDetectorRef.detectChanges();
        };

        if (edition.dependantEditionName && edition.dependantVersionEffectiveTime != null) {
            applyDetail(edition);
            return;
        }

        this.simplexService.invalidateEditionDetailCache(requestedShortName);
        lastValueFrom(this.simplexService.getEdition(requestedShortName)).then(
            (detail) => {
                applyDetail(detail);
                if (detail?.shortName === requestedShortName) {
                    this.uiConfigurationService.setSelectedEdition(detail);
                }
            },
            (error) => {
                console.error(error);
                if (this.selectedEdition?.shortName === requestedShortName) {
                    this.dependencyInfo = null;
                    this.changeDetectorRef.detectChanges();
                }
            }
        );
    }

    private saveEditionToCookie(editionShortName: string): void {
        try {
            const expiryDate = new Date();
            expiryDate.setFullYear(expiryDate.getFullYear() + 1);
            const path = this.getAppCookiePath();
            this.cookieService.set(this.SELECTED_EDITION_COOKIE_NAME, editionShortName, expiryDate, path);
        } catch (error) {
            console.error('Error saving edition cookie:', error);
        }
    }

    /** Cookie path scoped to the app base href so the value is readable from /artifacts and sibling routes. */
    private getAppCookiePath(): string {
        const baseHref = document.querySelector('base')?.getAttribute('href') ?? '/simplex/';
        try {
            const pathname = new URL(baseHref, window.location.origin).pathname;
            if (pathname.endsWith('/') && pathname.length > 1) {
                return pathname.slice(0, -1);
            }
            return pathname || '/';
        } catch {
            return '/simplex';
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

    refreshCache(): void {
        const shortName = this.selectedEdition?.shortName;
        if (!shortName) {
            return;
        }
        this.simplexService.refreshCache(shortName).subscribe({
            next: () => window.location.reload(),
            error: () => { /* handleError shows snackbar */ }
        });
    }

}
