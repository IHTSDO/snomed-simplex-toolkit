import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Subscription, filter, lastValueFrom } from 'rxjs';
import { User } from '../../models/user';
import { AuthenticationService } from '../../services/authentication/authentication.service';
import {Location} from '@angular/common';
import { LegalAgreementService } from 'src/app/services/legal-agreement/legal-agreement.service';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';

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
    roles: any[] = [];

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

    constructor(
        private authenticationService: AuthenticationService, 
        private location: Location,
        private snackBar: MatSnackBar,
        private legalAgreementService: LegalAgreementService,
        private uiConfigurationService: UiConfigurationService,
        private route: ActivatedRoute,
        private router: Router,
        private simplexService: SimplexService) {
        }

    ngOnInit() {
        this.getRoles();
        // Listen to navigation events to capture route changes
        this.router.events.pipe(
            filter(event => event instanceof NavigationEnd)
        ).subscribe(() => {
            const url = this.router.url;
            // Do nothing if the current route is /admin
            if (url === '/admin') {
                return;
            }
            const editionParam = this.route.firstChild.snapshot.paramMap.get('edition?');
            // Load editions if the edition parameter is defined
            if (editionParam) {
                this.loadEditions(editionParam);
            } else if (!url.includes('/home')) {
                // Load editions if the edition parameter is not defined
                this.loadEditions(null);
            }
        });
    }

    getRoles() {
        lastValueFrom(this.simplexService.getRoles()).then(
          (roles) => {
            this.roles = roles;
          },
          (error) => {
            console.error(error);
            this.loading = false;
            this.snackBar.open('Failed to load roles', 'Dismiss', {
              duration: 5000
            });
          }
        );
    }
    isAdmin(): boolean {
        return this.roles.includes('ADMIN');
    }

    isInHome(): boolean {
        return this.router.url === '/home';
    }

    initialize() {
        this.simplexService.refreshUIConfiguration();
        this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
            this.selectedEdition = edition;
        });
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
                this.selectEdition(this.editions[0]);
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
        let url = this.router.url;
        this.updateEditionInUrl(item.shortName);
    }

    updateEditionInUrl(edition: string) {
        // Get the current active route
        const currentRoute = this.router.url;
        // Check if the current route has an 'edition' parameter, update it or append if not
        const newRoute = currentRoute.includes('/home') ? ['home'] :
                         currentRoute.includes('artifacts/') ? ['artifact', edition] :
                         currentRoute.includes('artifact/') ? ['artifact', edition] :                 
                         currentRoute.includes('manage/') ? ['manage', edition] :
                         currentRoute.includes('info/') ? ['info', edition] :
                         currentRoute.includes('releases/') ? ['releases', edition] :
                         currentRoute.includes('translation-dashboard/') ? ['translation-dashboard', edition] : ['artifact', edition];
    
        // Navigate to the new route while keeping the current path structure
        this.router.navigate(newRoute);
    }

    goHome() {
        this.router.navigate(['/home']);
    }

    goAdmin() {
        this.router.navigate(['/admin']);
    }
    
}
